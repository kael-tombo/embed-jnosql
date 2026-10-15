import { expect, test } from "../fixtures/console";

test("redirects an anonymous deep link to sign-in with its requested panel", async ({
  anonymousPage,
}) => {
  await anonymousPage.goto("/#collections");
  await expect(anonymousPage).toHaveURL(/\/login\.html\?/);
  await expect(anonymousPage.getByRole("heading", { name: "EmbedJNoSQL Console" }))
    .toBeVisible();

  const requestedPath = new URL(anonymousPage.url()).searchParams.get("next");
  expect(requestedPath).toBe("/#collections");
});

test("valid sign-in returns to the requested panel", async ({
  browser,
  baseURL,
  consoleServer,
}) => {
  const context = await browser.newContext({ baseURL });
  try {
    const page = await context.newPage();
    await page.goto("/login.html?next=%2F%23collections");
    await page.getByLabel("Username").fill(consoleServer.credentials.username);
    await page.getByLabel("Password").fill(consoleServer.credentials.password);
    await page.getByRole("button", { name: "Sign In" }).click();

    await page.waitForURL((url) => url.pathname === "/" && url.hash === "#collections");
    await expect(page.locator("#panel-collections h1")).toHaveText("Collections");
    await expect(page.locator("#chipHealthText")).toHaveText("Healthy");
  } finally {
    await context.close();
  }
});

test("invalid sign-in displays an actionable error without redirecting", async ({
  browser,
  baseURL,
  consoleServer,
}) => {
  const context = await browser.newContext({ baseURL });
  try {
    const page = await context.newPage();
    await page.goto("/login.html?next=%2F%23collections");
    await page.getByLabel("Username").fill(consoleServer.credentials.username);
    await page.getByLabel("Password").fill("incorrect-e2e-password");
    await page.getByRole("button", { name: "Sign In" }).click();

    await expect(page.getByRole("alert")).toContainText(/invalid username or password/i);
    await expect(page).toHaveURL(/\/login\.html\?/);
  } finally {
    await context.close();
  }
});

test("logout clears browser credentials and invalidates the old session", async ({ page }) => {
  const oldSessionToken = await page.evaluate(() => sessionStorage.getItem("session"));
  expect(oldSessionToken).not.toBeNull();

  await page.locator("#btnLogout").click();
  await expect(page).toHaveURL(/\/login\.html\?/);
  await expect(page.getByRole("button", { name: "Sign In" })).toBeVisible();

  const browserStorage = await page.evaluate(() => ({
    apiKey: localStorage.getItem("apiKey"),
    session: sessionStorage.getItem("session"),
    csrfToken: sessionStorage.getItem("csrfToken"),
  }));
  expect(browserStorage).toEqual({ apiKey: null, session: null, csrfToken: null });

  const protectedRequestStatus = await page.evaluate(async () =>
    (await fetch("/api/health", { credentials: "same-origin" })).status,
  );
  expect(protectedRequestStatus).toBe(401);

  const oldTokenStatus = await page.evaluate(async (sessionToken) =>
    (await fetch("/api/health", {
      credentials: "omit",
      headers: { Authorization: `Bearer ${sessionToken}` },
    })).status,
  oldSessionToken);
  expect(oldTokenStatus).toBe(401);
});

test("a polling 401 redirects to sign-in once and stops further polling", async ({ page }) => {
  await page.locator('#nav [data-panel="collections"]').click();

  let unauthorizedPolls = 0;
  let signInNavigations = 0;
  await page.route("**/api/health", async (route) => {
    unauthorizedPolls += 1;
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ error: "Unauthorized" }),
    });
  });
  page.on("framenavigated", (frame) => {
    if (frame === page.mainFrame() && new URL(frame.url()).pathname.endsWith("/login.html")) {
      signInNavigations += 1;
    }
  });

  await expect.poll(() => unauthorizedPolls, {
    timeout: 12_000,
    message: "The scheduled health poll should reach the unauthorized response",
  }).toBe(1);
  await page.waitForURL((url) => url.pathname.endsWith("/login.html"));
  expect(new URL(page.url()).searchParams.get("next")).toBe("/#collections");

  await page.waitForTimeout(10_500);
  expect(unauthorizedPolls).toBe(1);
  expect(signInNavigations).toBe(1);
});

test("a write rejected by an expired session is not automatically replayed", async ({ page }) => {
  let writeAttempts = 0;
  let signInNavigations = 0;
  await page.route("**/api/collections/*", async (route) => {
    if (route.request().method() === "POST") {
      writeAttempts += 1;
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({ error: "Unauthorized" }),
      });
      return;
    }
    await route.continue();
  });
  page.on("framenavigated", (frame) => {
    if (frame === page.mainFrame() && new URL(frame.url()).pathname.endsWith("/login.html")) {
      signInNavigations += 1;
    }
  });

  await page.locator('#nav [data-panel="collections"]').click();
  await page.locator("#insCol").fill("products");
  await page.locator("#insJson").fill(JSON.stringify({
    id: "e2e-expired-session-write",
    name: "must not be written",
  }));
  await page.locator("#insSubmit").click();
  await page.waitForURL((url) => url.pathname.endsWith("/login.html"));
  await page.waitForTimeout(1_000);

  expect(writeAttempts).toBe(1);
  expect(signInNavigations).toBe(1);
});
