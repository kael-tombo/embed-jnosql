import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type ColumnNames = {
  family: string;
  row: string;
  secondRow: string;
  encodedFamily: string;
  encodedRow: string;
};

type TestFixtures = {
  columnNames: ColumnNames;
};

async function api(
  page: Page,
  path: string,
  method = "GET",
  body?: unknown,
): Promise<ApiResult> {
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

function uniqueName(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

function segment(value: string): string {
  return encodeURIComponent(value);
}

function waitForApi(
  page: Page,
  path: string,
  method: string,
): Promise<import("@playwright/test").Response> {
  return page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api${path}`
      && response.request().method() === method,
  );
}

const test = base.extend<TestFixtures>({
  columnNames: async ({ page }, use) => {
    const names: ColumnNames = {
      family: uniqueName("e2e_columns"),
      row: uniqueName("row"),
      secondRow: uniqueName("row"),
      encodedFamily: `${uniqueName("e2e_columns_encoded")} 雪`,
      encodedRow: "row 雪",
    };

    await use(names);

    for (const [family, row] of [
      [names.family, names.row],
      [names.family, names.secondRow],
      [names.encodedFamily, names.encodedRow],
    ]) {
      const result = await api(
        page,
        `/columns/${segment(family)}/${segment(row)}`,
        "DELETE",
      );
      expect([204, 404]).toContain(result.status);
    }
  },
});

async function openColumns(page: Page, family: string, row?: string): Promise<void> {
  await page.locator('#nav [data-panel="columns"]').click();
  await page.locator("#cfFam").fill(family);
  if (row !== undefined) await page.locator("#cfRow").fill(row);
}

async function writeRow(
  page: Page,
  family: string,
  row: string,
  columns: unknown,
): Promise<import("@playwright/test").Response> {
  await page.locator("#cfPutRow").fill(row);
  await page.locator("#cfPutCols").fill(JSON.stringify(columns));
  const response = waitForApi(
    page,
    `/columns/${segment(family)}/${segment(row)}`,
    "PUT",
  );
  await page.locator("#cfPutGo").click();
  return response;
}

async function readRow(
  page: Page,
  family: string,
  row: string,
): Promise<import("@playwright/test").Response> {
  const response = waitForApi(
    page,
    `/columns/${segment(family)}/${segment(row)}`,
    "GET",
  );
  await page.locator("#cfGet").click();
  return response;
}

async function readFamilyStats(
  page: Page,
  family: string,
): Promise<import("@playwright/test").Response> {
  const response = waitForApi(page, `/columns/${segment(family)}/stats`, "GET");
  await page.locator("#cfStats").click();
  return response;
}

test("fetching a missing row displays the not-found state", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  expect((await readRow(page, columnNames.family, columnNames.row)).status()).toBe(404);
  await expect(page.locator("#cfOut")).toContainText("Row not found");
});

test("writing one column displays the saved value in the row inspector", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  expect((await writeRow(page, columnNames.family, columnNames.row, { temperature: "21C" })).status())
    .toBe(201);
  await expect(page.locator("#cfOut")).toContainText("21C");
});

test("writing several columns creates one row with every supplied value", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  const columns = { cpu: "42", memory: "68", region: "west" };
  expect((await writeRow(page, columnNames.family, columnNames.row, columns)).status()).toBe(201);
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual(columns);
  await expect(page.locator("#cfOut")).toContainText('"memory": "68"');
});

test("writing a second column preserves the first column", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { first: "one" });
  expect((await writeRow(page, columnNames.family, columnNames.row, { second: "two" })).status())
    .toBe(201);
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ first: "one", second: "two" });
});

test("overwriting a column updates its value", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { status: "queued" });
  expect((await writeRow(page, columnNames.family, columnNames.row, { status: "complete" })).status())
    .toBe(201);
  await expect(page.locator("#cfOut")).toContainText('"status": "complete"');
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ status: "complete" });
});

test("writing an empty string keeps the column present", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  expect((await writeRow(page, columnNames.family, columnNames.row, { note: "" })).status()).toBe(201);
  await expect(page.locator("#cfOut")).toContainText('"note": ""');
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ note: "" });
});

test("writing a numeric zero keeps its numeric value", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { retries: 0 });
  await expect(page.locator("#cfOut")).toContainText('"retries": 0');
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ retries: 0 });
});

test("writing a false boolean keeps its boolean value", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { enabled: false });
  await expect(page.locator("#cfOut")).toContainText('"enabled": false');
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ enabled: false });
});

test("writing a nested JSON value preserves its structure", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  const columns = { metadata: { active: true, tags: ["blue", "green"] } };
  await writeRow(page, columnNames.family, columnNames.row, columns);
  await expect(page.locator("#cfOut")).toContainText('"tags": [');
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual(columns);
});

test("writing non-ASCII column names and values preserves them", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { "温度": "二十一度 🌡️" });
  await expect(page.locator("#cfOut")).toContainText("二十一度 🌡️");
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ "温度": "二十一度 🌡️" });
});

test("encoded family and row names can be written and fetched", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.encodedFamily, columnNames.encodedRow);
  expect((await writeRow(
    page,
    columnNames.encodedFamily,
    columnNames.encodedRow,
    { "node name": "東京" },
  )).status()).toBe(201);
  expect((await readRow(page, columnNames.encodedFamily, columnNames.encodedRow)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText("東京");
});

test("a per-column TTL value is displayed after a UI write", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  const response = await writeRow(page, columnNames.family, columnNames.row, {
    expiring: { value: "short-lived", ttlSeconds: 90 },
  });
  expect(response.status()).toBe(201);
  await expect(page.locator("#cfOut")).toContainText("short-lived");
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}/ttl/expiring`,
  )).data).toMatchObject({ hasTtl: true, column: "expiring" });
});

test("TTL on one column leaves an unexpired sibling visible", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, {
    active: "persistent",
    expiring: { value: "temporary", ttlSeconds: 90 },
  });
  await expect(page.locator("#cfOut")).toContainText("persistent");
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toMatchObject({ active: "persistent", expiring: "temporary" });
});

test("family stats show zero rows for a fresh family", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family);
  expect((await readFamilyStats(page, columnNames.family)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText('"rowCount": 0');
});

test("family stats count a row written through the UI", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { value: "present" });
  expect((await readFamilyStats(page, columnNames.family)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText('"rowCount": 1');
});

test("family stats update when a second row is written", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { value: "first" });
  await writeRow(page, columnNames.family, columnNames.secondRow, { value: "second" });
  expect((await readFamilyStats(page, columnNames.family)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText('"rowCount": 2');
});

test("writing a row updates the inspector's selected row key", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family);
  await writeRow(page, columnNames.family, columnNames.row, { value: "selected" });
  await expect(page.locator("#cfRow")).toHaveValue(columnNames.row);
  await expect(page.locator("#cfOut")).toContainText("selected");
});

test("a row remains readable after reloading the console", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { persisted: "after-reload" });
  await page.reload();
  await openColumns(page, columnNames.family, columnNames.row);
  expect((await readRow(page, columnNames.family, columnNames.row)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText("after-reload");
});

test("replacing one value preserves the other row values", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { state: "old", owner: "ops" });
  await writeRow(page, columnNames.family, columnNames.row, { state: "new" });
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ state: "new", owner: "ops" });
});

test("JSON string values containing quotes remain intact", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  const value = 'operator said "ready"';
  await writeRow(page, columnNames.family, columnNames.row, { message: value });
  await expect(page.locator("#cfOut")).toContainText(JSON.stringify(value));
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ message: value });
});

test("a long column value is not truncated in the inspector", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  const value = "column-value-".repeat(128);
  await writeRow(page, columnNames.family, columnNames.row, { payload: value });
  await expect(page.locator("#cfOut")).toContainText(value);
});

test("an empty object write leaves the row in the not-found state", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  expect((await writeRow(page, columnNames.family, columnNames.row, {})).status()).toBe(201);
  expect((await readRow(page, columnNames.family, columnNames.row)).status()).toBe(404);
  await expect(page.locator("#cfOut")).toContainText("Row not found");
});

test("writing another row does not change the first row", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { value: "first-row" });
  await writeRow(page, columnNames.family, columnNames.secondRow, { value: "second-row" });
  await page.locator("#cfRow").fill(columnNames.row);
  expect((await readRow(page, columnNames.family, columnNames.row)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText("first-row");
  await expect(page.locator("#cfOut")).not.toContainText("second-row");
});

test("a rejected JSON edit leaves an existing row unchanged", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { stable: "original" });
  await page.locator("#cfPutRow").fill(columnNames.row);
  await page.locator("#cfPutCols").fill('{"broken":');
  await page.locator("#cfPutGo").click();
  await expect(page.getByRole("alert").filter({ hasText: "Columns must be valid JSON" }))
    .toBeVisible();
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ stable: "original" });
});

test("writing with a blank family is rejected without creating a row", async ({ page, columnNames }) => {
  await openColumns(page, "");
  await page.locator("#cfPutRow").fill(columnNames.row);
  await page.locator("#cfPutCols").fill('{"value":"not-written"}');
  await page.locator("#cfPutGo").click();
  await expect(page.getByRole("alert").filter({ hasText: "Family and Row required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).status).toBe(404);
});

test("writing with a blank row is rejected without creating the family row", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family);
  await page.locator("#cfPutRow").fill("");
  await page.locator("#cfPutCols").fill('{"value":"not-written"}');
  await page.locator("#cfPutGo").click();
  await expect(page.getByRole("alert").filter({ hasText: "Family and Row required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).status).toBe(404);
});

test("a row with mixed permanent and expiring values keeps both TTL settings separate", async ({
  page,
  columnNames,
}) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, {
    permanent: "no expiry",
    temporary: { value: "expires later", ttlSeconds: 90 },
  });
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}/ttl/temporary`,
  )).data.hasTtl).toBe(true);
  await expect(page.locator("#cfOut")).toContainText("expires later");
});

test("refreshing family stats does not change the row contents", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { retained: "unchanged" });
  await readFamilyStats(page, columnNames.family);
  await page.locator("#cfRow").fill(columnNames.row);
  await readRow(page, columnNames.family, columnNames.row);
  await expect(page.locator("#cfOut")).toContainText("unchanged");
  expect((await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns).toEqual({ retained: "unchanged" });
});

test("writing the same value twice does not duplicate the column", async ({ page, columnNames }) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { stable: "same" });
  await writeRow(page, columnNames.family, columnNames.row, { stable: "same" });
  const columns = (await api(
    page,
    `/columns/${segment(columnNames.family)}/${segment(columnNames.row)}`,
  )).data.columns;
  expect(columns).toEqual({ stable: "same" });
  expect(Object.keys(columns)).toHaveLength(1);
});

test("the inspector shows the selected family's row after changing row keys", async ({
  page,
  columnNames,
}) => {
  await openColumns(page, columnNames.family, columnNames.row);
  await writeRow(page, columnNames.family, columnNames.row, { value: "first" });
  await writeRow(page, columnNames.family, columnNames.secondRow, { value: "second" });
  await page.locator("#cfRow").fill(columnNames.secondRow);
  expect((await readRow(page, columnNames.family, columnNames.secondRow)).status()).toBe(200);
  await expect(page.locator("#cfOut")).toContainText("second");
  await expect(page.locator("#cfOut")).not.toContainText("first");
});
