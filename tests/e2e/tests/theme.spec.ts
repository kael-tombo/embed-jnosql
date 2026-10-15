import { expect, test } from "../fixtures/console";

const THEME_STORAGE_KEY = "embedjnosql.theme";
const LIGHT_BODY_COLOR = "rgb(244, 246, 250)";
const LIGHT_CARD_COLOR = "rgb(255, 255, 255)";
const DARK_BODY_COLOR = "rgb(11, 14, 20)";
const DARK_CARD_COLOR = "rgb(17, 21, 31)";

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
