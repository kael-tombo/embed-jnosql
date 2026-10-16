import { expect, test } from "../fixtures/console";

const deepLinks = [
  { id: "kv", heading: "Key-Value & Redis Structures", panelTitle: "Key-Value" },
  { id: "columns", heading: "Wide-Column Families", panelTitle: "Column Family" },
  { id: "vectors", heading: "Vector Index (HNSW)", panelTitle: "Vectors" },
  { id: "schema", heading: "Schema Validation", panelTitle: "Schema" },
  { id: "tx", heading: "Transactions", panelTitle: "Transactions" },
  { id: "backup", heading: "Backup & Restore", panelTitle: "Backup" },
  { id: "cdc", heading: "Change Data Capture", panelTitle: "CDC / Events" },
  { id: "audit", heading: "Audit Trail", panelTitle: "Audit Trail" },
  { id: "server", heading: "Server", panelTitle: "Server" },
] as const;

test.describe("Navigation filter coverage", () => {
  test("filtering by a full panel label leaves only that navigation item visible", async ({
    page,
  }) => {
    await page.locator("#navSearch").fill("Column Family");
    await expect(page.locator('#nav [data-panel="columns"]')).toBeVisible();
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(1);
  });

  test("navigation filtering is case-insensitive", async ({ page }) => {
    await page.locator("#navSearch").fill("aUdIt");
    await expect(page.locator('#nav [data-panel="audit"]')).toBeVisible();
    await expect(page.locator('#nav [data-panel="cdc"]')).toBeHidden();
  });

  test("navigation filtering trims leading and trailing whitespace", async ({ page }) => {
    await page.locator("#navSearch").fill("  server  ");
    await expect(page.locator('#nav [data-panel="server"]')).toBeVisible();
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(1);
  });

  test("a navigation filter with no match hides every panel and group", async ({ page }) => {
    await page.locator("#navSearch").fill("no-such-console-panel");
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(0);
    await expect(page.locator("#nav .nav-group:visible")).toHaveCount(0);
  });

  test("clearing the navigation filter restores all twelve panel buttons", async ({ page }) => {
    const search = page.locator("#navSearch");
    await search.fill("backup");
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(1);
    await search.fill("");
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(12);
  });

  test("Escape clears and blurs the navigation filter", async ({ page }) => {
    const search = page.locator("#navSearch");
    await search.fill("transactions");
    await search.press("Escape");
    await expect(search).toHaveValue("");
    await expect(search).not.toBeFocused();
    await expect(page.locator("#nav .nav-item:visible")).toHaveCount(12);
  });

  test("filtering navigation does not change the active panel", async ({ page }) => {
    await page.locator('#nav [data-panel="collections"]').click();
    await page.locator("#navSearch").fill("server");
    await expect(page).toHaveURL(/#collections$/);
    await expect(page.locator("#panelTitle")).toHaveText("Collections");
    await expect(page.locator("#panel-collections")).toBeVisible();
  });

  test("navigation filtering keeps only groups containing a matching panel", async ({ page }) => {
    await page.locator("#navSearch").fill("cdc");
    await expect(page.locator("#nav .nav-group:visible")).toHaveCount(1);
    await expect(page.locator("#nav .nav-group:visible")).toHaveText("Operations");
  });
});

test.describe("Panel deep-link coverage", () => {
  for (const panel of deepLinks) {
    test(`reloading a direct #${panel.id} link restores its panel`, async ({ page }) => {
      await page.goto(`/#${panel.id}`);
      await page.reload();
      await expect(page).toHaveURL(new RegExp(`#${panel.id}$`));
      await expect(page.locator(`#panel-${panel.id}`)).toBeVisible();
      await expect(page.locator(`#panel-${panel.id} h1`)).toHaveText(panel.heading);
      await expect(page.locator("#panelTitle")).toHaveText(panel.panelTitle);
    });
  }
});

test.describe("Keyboard interaction coverage", () => {
  test("numeric panel shortcuts are ignored while typing in navigation search", async ({
    page,
  }) => {
    const search = page.locator("#navSearch");
    await search.fill("");
    await search.focus();
    await page.keyboard.press("4");
    await expect(search).toHaveValue("4");
    await expect(page).toHaveURL(/#overview$/);
    await expect(page.locator("#panel-overview")).toBeVisible();
  });

  test("a modified numeric shortcut does not switch panels", async ({ page }) => {
    await page.keyboard.press("Alt+3");
    await expect(page).toHaveURL(/#overview$/);
    await expect(page.locator("#panel-overview")).toBeVisible();
  });

  test("an unmapped keyboard key leaves the selected panel unchanged", async ({ page }) => {
    await page.locator('#nav [data-panel="server"]').click();
    await page.keyboard.press("x");
    await expect(page).toHaveURL(/#server$/);
    await expect(page.locator("#panel-server")).toBeVisible();
  });
});

test.describe("Unauthenticated API coverage", () => {
  const protectedPaths = [
    { path: "/api/metrics", name: "metrics" },
    { path: "/api/cdc", name: "CDC status" },
    { path: "/api/audit/logs", name: "Audit Trail" },
  ] as const;

  for (const endpoint of protectedPaths) {
    test(`an anonymous request to ${endpoint.name} is rejected`, async ({ anonymousPage }) => {
      const status = await anonymousPage.evaluate(async (url) =>
        (await fetch(url, { credentials: "omit" })).status,
      endpoint.path);
      expect(status).toBe(401);
    });
  }
});

test.describe("Responsive navigation coverage", () => {
  test("the navigation rail collapses its text labels at 900 pixels", async ({ page }) => {
    await page.setViewportSize({ width: 900, height: 800 });
    await expect(page.locator("#nav .nav-label").first()).toBeHidden();
    await expect(page.locator('#nav [data-panel="collections"]'))
      .toHaveAttribute("aria-label", "Collections");
  });

  test("the navigation rail shows its text labels above 900 pixels", async ({ page }) => {
    await page.setViewportSize({ width: 901, height: 800 });
    await expect(page.locator("#nav .nav-label").first()).toBeVisible();
  });

  test("the topbar wraps its controls at the 1100-pixel breakpoint", async ({ page }) => {
    await page.setViewportSize({ width: 1100, height: 800 });
    expect(await page.locator(".topbar").evaluate((element) =>
      getComputedStyle(element).flexWrap,
    )).toBe("wrap");
  });

  test("the topbar keeps its default no-wrap layout above 1100 pixels", async ({ page }) => {
    await page.setViewportSize({ width: 1101, height: 800 });
    expect(await page.locator(".topbar").evaluate((element) =>
      getComputedStyle(element).flexWrap,
    )).toBe("nowrap");
  });
});

test.describe("Accessibility and connectivity coverage", () => {
  test("the skip-to-main-content link is exposed when focused", async ({ page }) => {
    const skipLink = page.getByRole("link", { name: "Skip to main content" });
    await skipLink.focus();
    await expect(skipLink).toBeVisible();
    await expect(skipLink).toHaveAttribute("href", "#main");
  });

  test("breadcrumbs expose the active panel and its navigation group", async ({ page }) => {
    await page.locator('#nav [data-panel="cdc"]').click();
    const currentCrumbs = page.locator('#crumbs [aria-current="page"]');
    await expect(currentCrumbs).toHaveText(["Operations", "CDC / Events"]);
  });

  test("collapsed navigation buttons retain accessible panel names", async ({ page }) => {
    await page.setViewportSize({ width: 900, height: 800 });
    await expect(page.getByRole("navigation", { name: "Console navigation" }))
      .toBeVisible();
    await expect(page.getByRole("button", { name: "Audit Trail", exact: true }))
      .toBeVisible();
  });

  test("an offline browser event displays offline status and feedback", async ({ page }) => {
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));
    await expect(page.locator("#chipHealthText")).toHaveText("Offline");
    await expect(page.locator("#toasts")).toContainText("Network offline");
  });

  test("an online browser event announces recovery and refreshes health", async ({ page }) => {
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await expect(page.locator("#toasts")).toContainText("Network connection restored");
    await expect(page.locator("#chipHealthText")).toHaveText("Healthy");
  });
});

test.describe("User-visible request error coverage", () => {
  test("Overview displays a health error instead of its dashboard after refresh fails", async ({
    page,
  }) => {
    await page.route("**/api/health", (route) => route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ error: "simulated health outage" }),
    }));
    await page.locator("#btnRefreshAll").click();
    await expect(page.locator("#kpiRow")).toContainText("Overview unavailable");
  });

  test("Server displays the health endpoint failure in its health payload", async ({ page }) => {
    await page.route("**/api/health", (route) => route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ error: "simulated health outage" }),
    }));
    await page.locator('#nav [data-panel="server"]').click();
    await expect.poll(async () => page.locator("#srvHealth").textContent())
      .toContain("error");
  });

  test("Server displays a metrics endpoint failure in its metrics payload", async ({ page }) => {
    await page.route("**/api/metrics", (route) => route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ error: "simulated metrics outage" }),
    }));
    await page.locator('#nav [data-panel="server"]').click();
    await expect.poll(async () => page.locator("#srvMetrics").textContent())
      .toContain("error");
  });
});
