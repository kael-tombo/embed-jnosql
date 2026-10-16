import { expect, test } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

async function api(page: Page, path: string, method = "GET", body?: unknown): Promise<ApiResult> {
  return page.evaluate(
    async ({ path, method, body }) => {
      const headers: Record<string, string> = {};
      const csrf = sessionStorage.getItem("csrfToken");
      const session = sessionStorage.getItem("session");
      if (body !== undefined) headers["Content-Type"] = "application/json";
      if (csrf) headers["X-CSRF-Token"] = csrf;
      if (session) headers.Authorization = `Bearer ${session}`;
      const response = await fetch(`/api${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      let data: unknown = null;
      try {
        data = text ? JSON.parse(text) : null;
      } catch {
        data = text;
      }
      return { status: response.status, data };
    },
    { path, method, body },
  );
}

async function openOverview(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="overview"]').click();
  await expect(page.locator("#panel-overview")).toBeVisible();
}

async function openServer(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="server"]').click();
  await expect(page.getByRole("heading", { name: "Server" })).toBeVisible();
  await expect(page.locator("#srvHealth")).not.toBeEmpty();
  await expect(page.locator("#srvMetrics")).not.toBeEmpty();
}

async function overviewMetrics(page: Page): Promise<any> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/metrics"
      && response.request().method() === "GET",
  );
  await openOverview(page);
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  return response.json();
}

function kpiValue(page: Page, label: string) {
  return page.locator("#kpiRow .kpi-label")
    .filter({ hasText: new RegExp(`^${label}$`) })
    .locator("xpath=..")
    .locator(".kpi-value");
}

async function expectNumberKpi(page: Page, label: string, expected: number): Promise<void> {
  const text = await kpiValue(page, label).textContent();
  expect(Number((text ?? "").replaceAll(",", ""))).toBe(expected);
}

async function serverJson(page: Page, selector: string): Promise<any> {
  const text = await page.locator(selector).textContent();
  expect(text).toBeTruthy();
  return JSON.parse(text!);
}

test.describe("Overview and Server UI coverage", () => {
  test("Overview presents its live-counter and engine summary", async ({ page }) => {
    await openOverview(page);
    await expect(page.locator("#panel-overview h1")).toHaveText("Overview");
    await expect(page.locator("#panel-overview")).toContainText("Live counters, health, and engine info");
  });

  test("Overview reports the live database as online", async ({ page }) => {
    await openOverview(page);
    await expect(kpiValue(page, "Status")).toContainText("ONLINE");
    await expect(kpiValue(page, "Status")).toHaveClass(/ok/);
  });

  test("Overview total-operations KPI matches live metrics", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expectNumberKpi(page, "Total ops", metrics.totalOperations);
  });

  test("Overview operations-per-second KPI matches live metrics", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expectNumberKpi(page, "Ops / sec", Math.round(metrics.opsPerSecond ?? 0));
  });

  test("Overview inserts KPI matches live metrics", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expectNumberKpi(page, "Inserts", metrics.inserts);
  });

  test("Overview reads KPI matches live metrics", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expectNumberKpi(page, "Reads", metrics.reads);
  });

  test("Overview transaction-commits KPI matches live metrics", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expectNumberKpi(page, "Tx commits", metrics.transactionCommits);
  });

  test("Overview throughput badge displays the current operations rate", async ({ page }) => {
    const metrics = await overviewMetrics(page);
    await expect(page.locator("#sparkNow"))
      .toHaveText(`${Math.round(metrics.opsPerSecond ?? 0)} ops/s`);
  });

  test("Overview engine details match the health endpoint", async ({ page }) => {
    const healthPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/health"
        && response.request().method() === "GET",
    );
    await openOverview(page);
    const health = await (await healthPromise).json();
    await expect(page.locator("#engineInfo")).toContainText(health.engine);
  });

  test("Overview engine details report whether the database is open", async ({ page }) => {
    const healthPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/health"
        && response.request().method() === "GET",
    );
    await openOverview(page);
    const health = await (await healthPromise).json();
    await expect(page.locator("#engineInfo")).toContainText(health.open ? "yes" : "no");
  });

  test("Overview engine details display the active thread count", async ({ page }) => {
    const healthPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/health"
        && response.request().method() === "GET",
    );
    await openOverview(page);
    const health = await (await healthPromise).json();
    await expect(page.locator("#engineInfo")).toContainText("Threads");
    await expect(page.locator("#engineInfo")).toContainText(String(health.threads.active));
  });

  test("Overview collection summary reflects the live collection list", async ({ page }) => {
    const response = await api(page, "/collections");
    expect(response.status).toBe(200);
    await openOverview(page);
    await expect(page.locator("#ovCollections")).toContainText("Collection");
    await expect(page.locator("#ovCollections")).toContainText("Docs");
    for (const collection of response.data.collections) {
      await expect(page.locator("#ovCollections")).toContainText(collection.name);
    }
  });

  test("Overview audit preview displays operation and resource columns", async ({ page }) => {
    const eventsPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/audit/logs"
        && response.request().method() === "GET",
    );
    await openOverview(page);
    const response = await eventsPromise;
    expect(response.status()).toBe(200);
    const events = (await response.json()).events;
    if (events.length) {
      await expect(page.locator("#ovAudit")).toContainText("Time");
      await expect(page.locator("#ovAudit")).toContainText("Op");
      await expect(page.locator("#ovAudit")).toContainText("Resource");
      await expect(page.locator("#ovAudit tbody tr")).toHaveCount(Math.min(events.length, 8));
    } else {
      await expect(page.locator("#ovAudit")).toContainText("No mutations recorded yet.");
    }
  });

  test("Overview storage summary reflects engine, mode, and WAL status", async ({ page }) => {
    const result = await api(page, "/storage/status");
    expect(result.status).toBe(200);
    await openOverview(page);
    await expect(page.locator("#ovStorage")).toContainText(result.data.engine);
    await expect(page.locator("#ovStorage")).toContainText(result.data.storageMode);
    await expect(page.locator("#ovStorage")).toContainText("WAL");
    await expect(page.locator("#ovStorage")).toContainText(result.data.durability);
  });

  test("refreshing Overview storage requests and displays current storage status", async ({
    page,
  }) => {
    await openOverview(page);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/storage/status"
        && response.request().method() === "GET",
    );
    await page.locator("#ovStorageRefresh").click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const status = await response.json();
    await expect(page.locator("#ovStorage")).toContainText(status.storageMode);
  });

  test("the Server panel identifies health, metrics, and connection information", async ({
    page,
  }) => {
    await openServer(page);
    await expect(page.locator("#panel-server")).toContainText("Health");
    await expect(page.locator("#panel-server")).toContainText("JVM metrics");
    await expect(page.locator("#panel-server")).toContainText("connection info");
  });

  test("Server health JSON reports an OK status", async ({ page }) => {
    await openServer(page);
    expect((await serverJson(page, "#srvHealth")).status).toBe("ok");
  });

  test("Server health JSON includes the current product version", async ({ page }) => {
    await openServer(page);
    const health = await serverJson(page, "#srvHealth");
    expect(health.version).toMatch(/^\d+\.\d+\.\d+/);
  });

  test("Server health JSON reports positive uptime", async ({ page }) => {
    await openServer(page);
    expect((await serverJson(page, "#srvHealth")).uptime).toBeGreaterThan(0);
  });

  test("Server health JSON includes memory and thread telemetry", async ({ page }) => {
    await openServer(page);
    const health = await serverJson(page, "#srvHealth");
    expect(health.memory.used).toBeGreaterThan(0);
    expect(health.memory.max).toBeGreaterThan(0);
    expect(health.threads.active).toBeGreaterThan(0);
    expect(health.threads.daemon).toBeGreaterThan(0);
  });

  test("Server metrics JSON matches the live total-operations counter", async ({ page }) => {
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/metrics"
        && response.request().method() === "GET",
    );
    await openServer(page);
    const metrics = await (await responsePromise).json();
    const displayed = await serverJson(page, "#srvMetrics");
    expect(displayed.totalOperations).toBe(metrics.totalOperations);
  });

  test("Server metrics JSON includes read, insert, and commit counters", async ({ page }) => {
    await openServer(page);
    const metrics = await serverJson(page, "#srvMetrics");
    expect(metrics.reads).toEqual(expect.any(Number));
    expect(metrics.inserts).toEqual(expect.any(Number));
    expect(metrics.transactionCommits).toEqual(expect.any(Number));
  });

  test("refreshing Server health sends a health request", async ({ page }) => {
    await openServer(page);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/health"
        && response.request().method() === "GET",
    );
    await page.locator("#srvRefresh").click();
    expect((await responsePromise).status()).toBe(200);
  });

  test("refreshing Server metrics sends a metrics request", async ({ page }) => {
    await openServer(page);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/metrics"
        && response.request().method() === "GET",
    );
    await page.locator("#srvRefresh").click();
    expect((await responsePromise).status()).toBe(200);
  });

  test("refreshed Server health and metrics remain valid JSON", async ({ page }) => {
    await openServer(page);
    const healthPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/health"
        && response.request().method() === "GET",
    );
    const metricsPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/metrics"
        && response.request().method() === "GET",
    );
    await page.locator("#srvRefresh").click();
    expect((await healthPromise).status()).toBe(200);
    expect((await metricsPromise).status()).toBe(200);
    expect((await serverJson(page, "#srvHealth")).status).toBe("ok");
    expect((await serverJson(page, "#srvMetrics")).totalOperations).toEqual(expect.any(Number));
  });
});
