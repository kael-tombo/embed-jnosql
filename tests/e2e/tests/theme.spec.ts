import { expect, test } from "../fixtures/console";

const THEME_STORAGE_KEY = "embedjnosql.theme";
const LIGHT_BODY_COLOR = "rgb(244, 246, 250)";
const LIGHT_CARD_COLOR = "rgb(255, 255, 255)";
const DARK_BODY_COLOR = "rgb(11, 14, 20)";
const DARK_CARD_COLOR = "rgb(17, 21, 31)";

function contrastRatio(foreground: string, background: string): number {
  const channels = (color: string) => {
    const values = color.match(/[\d.]+/g)?.map(Number);
    if (!values || values.length < 3) {
      throw new Error(`Expected a computed RGB color, received: ${color}`);
    }
    return values.slice(0, 3).map((channel) => {
      const normalized = channel / 255;
      return normalized <= 0.04045
        ? normalized / 12.92
        : ((normalized + 0.055) / 1.055) ** 2.4;
    });
  };
  const luminance = (color: string) => {
    const [red, green, blue] = channels(color);
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
  };
  const first = luminance(foreground);
  const second = luminance(background);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

function compositeColor(foreground: string, background: string): string {
  const parse = (color: string) => {
    const values = color.match(/[\d.]+/g)?.map(Number);
    if (!values || values.length < 3) {
      throw new Error(`Expected a computed RGB color, received: ${color}`);
    }
    return { channels: values.slice(0, 3), alpha: values.length > 3 ? values[3] : 1 };
  };
  const front = parse(foreground);
  const back = parse(background);
  const channels = front.channels.map((channel, index) =>
    channel * front.alpha + back.channels[index] * (1 - front.alpha),
  );
  return `rgb(${channels.join(", ")})`;
}

async function expectConsoleColors(
  page: import("@playwright/test").Page,
  theme: "light" | "dark",
) {
  const colors = await page.evaluate(() => ({
    body: getComputedStyle(document.body).backgroundColor,
    card: getComputedStyle(
      document.querySelector("#panel-collections .card")!,
    ).backgroundColor,
  }));

  expect(colors.body).toBe(theme === "light" ? LIGHT_BODY_COLOR : DARK_BODY_COLOR);
  expect(colors.card).toBe(theme === "light" ? LIGHT_CARD_COLOR : DARK_CARD_COLOR);
}

async function signIn(
  page: import("@playwright/test").Page,
  credentials: { username: string; password: string },
) {
  await page.goto("/login.html?next=%2F");
  await page.getByLabel("Username").fill(credentials.username);
  await page.getByLabel("Password").fill(credentials.password);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL((url) => url.pathname === "/" || url.pathname.endsWith("/index.html"));
  await expect(page.locator("#chipHealthText")).toHaveText("Healthy");
}

test("uses light theme with empty storage", async ({ page }) => {
  await page.evaluate((key) => localStorage.removeItem(key), THEME_STORAGE_KEY);
  await page.reload();

  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await page.getByRole("navigation", { name: "Console navigation" })
    .getByRole("button", { name: "Collections" }).click();
  await expect(page.locator("#panelTitle")).toHaveText("Collections");
  await expectConsoleColors(page, "light");
});

test("persists dark theme across reload and panel navigation", async ({ page }) => {
  await page.evaluate((key) => localStorage.setItem(key, "light"), THEME_STORAGE_KEY);
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");

  await page.locator("#btnTheme").click();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expectConsoleColors(page, "dark");

  await page.getByRole("navigation", { name: "Console navigation" })
    .getByRole("button", { name: "Collections" }).click();
  await expect(page.locator("#panelTitle")).toHaveText("Collections");
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
  await expectConsoleColors(page, "dark");
});

test("switches back to light without reload", async ({ page }) => {
  await page.evaluate((key) => localStorage.setItem(key, "dark"), THEME_STORAGE_KEY);
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");

  await page.evaluate(() => {
    (window as typeof window & { themeToggleSentinel?: string }).themeToggleSentinel =
      "same-document";
  });
  await page.locator("#btnTheme").click();

  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expectConsoleColors(page, "light");
  await expect.poll(() => page.evaluate(() =>
    (window as typeof window & { themeToggleSentinel?: string }).themeToggleSentinel,
  )).toBe("same-document");
  await expect.poll(() => page.evaluate((key) => localStorage.getItem(key), THEME_STORAGE_KEY))
    .toBe("light");
});

test("recovers from invalid theme storage", async ({ page }) => {
  await page.evaluate((key) => localStorage.setItem(key, "ultraviolet"), THEME_STORAGE_KEY);
  await page.reload();

  await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
  await expectConsoleColors(page, "light");
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
});

test("keeps the UI usable when storage access is denied", async ({
  browser,
  baseURL,
  consoleServer,
}) => {
  const context = await browser.newContext({ baseURL });
  await context.addInitScript(() => {
    const getItem = Storage.prototype.getItem;
    const setItem = Storage.prototype.setItem;
    Storage.prototype.getItem = function (key) {
      if (key === "embedjnosql.theme") {
        throw new DOMException("Storage access denied", "SecurityError");
      }
      return getItem.call(this, key);
    };
    Storage.prototype.setItem = function (key, value) {
      if (key === "embedjnosql.theme") {
        throw new DOMException("Storage access denied", "SecurityError");
      }
      return setItem.call(this, key, value);
    };
  });

  try {
    const page = await context.newPage();
    await signIn(page, consoleServer.credentials);

    await expect(page.locator("html")).toHaveAttribute("data-theme", "light");
    await expectConsoleColors(page, "light");
    await page.locator("#btnTheme").click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await page.getByRole("navigation", { name: "Console navigation" })
      .getByRole("button", { name: "Collections" }).click();
    await expect(page.locator("#panelTitle")).toHaveText("Collections");
    await expectConsoleColors(page, "dark");
  } finally {
    await context.close();
  }
});

test("applies the saved theme on sign-in", async ({
  browser,
  baseURL,
  consoleServer,
}) => {
  const context = await browser.newContext({ baseURL });
  await context.addInitScript(
    (key) => {
      if (localStorage.getItem(key) === null) {
        localStorage.setItem(key, "light");
      }
    },
    THEME_STORAGE_KEY,
  );

  try {
    const page = await context.newPage();
    await signIn(page, consoleServer.credentials);
    await page.locator("#btnTheme").click();
    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect.poll(() => page.evaluate((key) => localStorage.getItem(key), THEME_STORAGE_KEY))
      .toBe("dark");

    await page.getByRole("button", { name: "Sign out" }).click();
    await page.waitForURL((url) => url.pathname.endsWith("/login.html"));

    await expect(page.locator("html")).toHaveAttribute("data-theme", "dark");
    await expect(page.locator("#loginForm")).toBeVisible();
    const colors = await page.evaluate(() => ({
      body: getComputedStyle(document.body).backgroundColor,
      card: getComputedStyle(document.querySelector(".login-card")!).backgroundColor,
    }));
    expect(colors.body).toBe(DARK_BODY_COLOR);
    expect(colors.card).toBe(DARK_CARD_COLOR);
  } finally {
    await context.close();
  }
});

test("sign-in secondary and error text meet WCAG AA contrast in both themes", async ({
  browser,
  baseURL,
}) => {
  for (const theme of ["light", "dark"] as const) {
    const context = await browser.newContext({ baseURL });
    await context.addInitScript(
      ({ key, savedTheme }) => localStorage.setItem(key, savedTheme),
      { key: THEME_STORAGE_KEY, savedTheme: theme },
    );

    try {
      const page = await context.newPage();
      await page.goto("/login.html");
      const colors = await page.evaluate(() => {
        const body = getComputedStyle(document.body);
        const card = getComputedStyle(document.querySelector(".login-card")!);
        const inputElement = document.querySelector(".form-input")!;
        const input = getComputedStyle(inputElement);
        const error = document.querySelector<HTMLElement>("#errorMessage")!;
        error.textContent = "Sign-in error";
        error.style.display = "block";
        const errorStyle = getComputedStyle(error);
        return {
          theme: document.documentElement.dataset.theme,
          bodyBackground: body.backgroundColor,
          bodyBackgroundImage: body.backgroundImage,
          cardBackground: card.backgroundColor,
          inputBackground: input.backgroundColor,
          placeholderColor: getComputedStyle(inputElement, "::placeholder").color,
          labelColor: getComputedStyle(document.querySelector(".form-label")!).color,
          footerColor: getComputedStyle(document.querySelector(".login-footer")!).color,
          badgeColor: getComputedStyle(document.querySelector(".login-badge")!).color,
          badgeBackground: getComputedStyle(document.querySelector(".login-badge")!).backgroundColor,
          errorBackground: errorStyle.backgroundColor,
          errorColor: errorStyle.color,
        };
      });

      expect(colors.theme).toBe(theme);
      expect(colors.bodyBackground).toBe(theme === "light" ? LIGHT_BODY_COLOR : DARK_BODY_COLOR);
      expect(colors.bodyBackgroundImage).toContain("radial-gradient");
      expect(colors.cardBackground).toBe(theme === "light" ? LIGHT_CARD_COLOR : DARK_CARD_COLOR);
      expect(colors.inputBackground).not.toBe("rgba(0, 0, 0, 0)");
      expect(colors.errorBackground).toBe(colors.cardBackground);

      const badgeBackground = compositeColor(colors.badgeBackground, colors.cardBackground);
      const badgeContrast = contrastRatio(colors.badgeColor, badgeBackground);
      expect(
        badgeContrast,
        `badge contrast ${badgeContrast.toFixed(2)}:1; badge=${colors.badgeColor} on ${badgeBackground}; card=${colors.cardBackground}`,
      ).toBeGreaterThanOrEqual(4.5);

      for (const [text, background, description] of [
        [colors.placeholderColor, colors.inputBackground, "input placeholder"],
        [colors.labelColor, colors.cardBackground, "form label"],
        [colors.footerColor, colors.cardBackground, "footer"],
        [colors.errorColor, colors.errorBackground, "error message"],
      ] as const) {
        expect(
          contrastRatio(text, background),
          `${description} contrast; body=${colors.bodyBackground}; card=${colors.cardBackground}; input=${colors.inputBackground}; error=${colors.errorBackground}`,
        ).toBeGreaterThanOrEqual(4.5);
      }
    } finally {
      await context.close();
    }
  }
});
