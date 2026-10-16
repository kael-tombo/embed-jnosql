import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type TestFixtures = {
  collection: string;
};

async function api(
  page: Page,
  path: string,
  method = "GET",
  body?: unknown,
): Promise<ApiResult> {
  return page.evaluate(
    async ({ path, method, body }) => {
      const headers: Record<string, string> = { "Content-Type": "application/json" };
      const csrf = sessionStorage.getItem("csrfToken");
      const session = sessionStorage.getItem("session");
      if (csrf) headers["X-CSRF-Token"] = csrf;
      if (session) headers.Authorization = "Bearer " + session;
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

function uniqueCollection(prefix = "e2e_collection"): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

async function cleanupCollection(page: Page, collection: string): Promise<void> {
  const path = `/collections/${encodeURIComponent(collection)}`;
  let listed = await api(page, path);
  if (listed.status === 404) return;
  expect(listed.status).toBe(200);
  expect((await api(page, `${path}/cleanup`, "POST")).status).toBe(200);
  listed = await api(page, path);
  expect(listed.status).toBe(200);
  for (const document of listed.data) {
    if (typeof document?.id === "string") {
      const deleted = await api(page, `${path}/${encodeURIComponent(document.id)}`, "DELETE");
      expect(deleted.status).toBe(204);
    }
  }
}

async function openCollections(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="collections"]').click();
  await expect(page.locator("#panel-collections")).toBeVisible();
}

async function insertThroughUi(
  page: Page,
  collection: string,
  document: Record<string, unknown>,
): Promise<number> {
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill(JSON.stringify(document, null, 2));
  const responsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST";
  });
  await page.locator("#insSubmit").click();
  const response = await responsePromise;
  if (response.ok()) {
    await expect(page.locator("#insStatus")).toHaveText(`saved ${document.id}`);
  }
  return response.status();
}

async function seedThroughUi(
  page: Page,
  collection: string,
  documents: Record<string, unknown>[],
): Promise<void> {
  for (const document of documents) {
    expect(await insertThroughUi(page, collection, document)).toBe(201);
  }
}

async function loadCollection(page: Page, collection: string): Promise<void> {
  await page.locator("#colSel").fill(collection);
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "GET",
  );
  await page.locator("#colLoad").click();
  expect((await responsePromise).status()).toBe(200);
}

async function readQueryResults(page: Page): Promise<string[]> {
  return page.locator("#qbOut td code").allTextContents();
}

async function runFilter(
  page: Page,
  collection: string,
  field: string,
  operator: string,
  value: unknown,
  expectedStatus = 200,
): Promise<void> {
  await page.locator("#qbClear").click();
  const row = page.locator("#qbRows .qb-row").first();
  await row.locator(".qb-field").fill(field);
  await row.locator(".qb-op").selectOption(operator);
  await row.locator(".qb-value").fill(JSON.stringify(value));
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(expectedStatus);
}

async function seedFilterDocuments(page: Page, collection: string): Promise<string[]> {
  const documents = [
    {
      id: `${collection}-alpha`,
      score: 10,
      label: "alpha",
      enabled: true,
      profile: { city: "Oslo" },
      createdAt: 1_700_000_000_000,
      optional: "present",
    },
    {
      id: `${collection}-beta`,
      score: 3,
      label: "beta",
      enabled: false,
      profile: { city: "Lima" },
      createdAt: 1_700_000_001_000,
    },
  ];
  await seedThroughUi(page, collection, documents);
  return documents.map((document) => String(document.id));
}

const test = base.extend<TestFixtures>({
  collection: async ({ page }, use) => {
    const name = uniqueCollection();
    await use(name);
    await cleanupCollection(page, name);
  },
});

test("New document focuses the collection insert editor", async ({ page }) => {
  await openCollections(page);
  await page.locator("#colNew").click();
  await expect(page.locator("#insCol")).toBeFocused();
});

test("inserting through the form reports the saved document ID", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-saved`;
  expect(await insertThroughUi(page, collection, { id, name: "Saved" })).toBe(201);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`)))
    .toMatchObject({ status: 200, data: { id, fields: { name: "Saved" } } });
});

test("loaded collection table displays scalar document fields", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-scalars`;
  await seedThroughUi(page, collection, [{ id, title: "Console guide", count: 12, active: true }]);
  await loadCollection(page, collection);
  await expect(page.locator("#colTable")).toContainText(id);
  await expect(page.locator("#colTable")).toContainText("Console guide");
  await expect(page.locator("#colTable")).toContainText("12");
  await expect(page.locator("#colTable")).toContainText("true");
});

test("document detail JSON preserves nested object values", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-nested-json`;
  await seedThroughUi(page, collection, [{ id, profile: { city: "Oslo", preferences: { alerts: true } } }]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await expect(page.locator("#docJson")).toContainText('"city": "Oslo"');
  await expect(page.locator("#docJson")).toContainText('"alerts": true');
});

test("tree view renders nested document fields", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-tree`;
  await seedThroughUi(page, collection, [{ id, profile: { city: "Lima", active: false } }]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await page.locator("#docViewTree").click();
  await expect(page.locator("#docDetail .json-tree")).toContainText("profile");
  await expect(page.locator("#docDetail .json-tree")).toContainText("Lima");
  await expect(page.locator("#docDetail .json-tree")).toContainText("false");
});

test("switching back to JSON retains the selected document", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-toggle`;
  await seedThroughUi(page, collection, [{ id, label: "toggle-target" }]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await page.locator("#docViewTree").click();
  await page.locator("#docViewJson").click();
  await expect(page.locator("#docViewJson")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#docJson")).toContainText("toggle-target");
});

test("Load into editor copies the selected document for editing", async ({ page, collection }) => {
  await openCollections(page);
  const document = { id: `${collection}-editor`, label: "editor-value" };
  await seedThroughUi(page, collection, [document]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${document.id}"]`).click();
  await page.locator("#docEdit").click();
  await expect(page.locator("#insCol")).toHaveValue(collection);
  await expect(page.locator("#insJson")).toHaveValue(JSON.stringify(document, null, 2));
});

test("known collection chip selects and loads its documents", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-chip`;
  await seedThroughUi(page, collection, [{ id, name: "chip record" }]);
  await page.locator(`#colPicker [data-col="${collection}"]`).click();
  await expect(page.locator("#colSel")).toHaveValue(collection);
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
  await expect(page.locator(`#colPicker [data-col="${collection}"]`)).toHaveAttribute("aria-pressed", "true");
});

test("collection picker refresh discovers a collection created before refresh", async ({ page, collection }) => {
  const id = `${collection}-picker`;
  await openCollections(page);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`, "POST", { id, marker: "picker" })).status)
    .toBe(201);
  const refreshPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/collections" && response.request().method() === "GET",
  );
  await page.locator("#colPickerRefresh").click();
  expect((await refreshPromise).status()).toBe(200);
  await expect(page.locator(`#colPicker [data-col="${collection}"]`)).toBeVisible();
});

test("Refresh reloads documents added after the collection was first loaded", async ({ page, collection }) => {
  await openCollections(page);
  const firstId = `${collection}-first`;
  const laterId = `${collection}-later`;
  await seedThroughUi(page, collection, [{ id: firstId, label: "first" }]);
  await loadCollection(page, collection);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`, "POST", {
    id: laterId,
    label: "added independently",
  })).status).toBe(201);
  const refreshPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "GET",
  );
  await page.locator("#colRefresh").click();
  expect((await refreshPromise).status()).toBe(200);
  await expect(page.locator(`#colTable [data-doc="${laterId}"]`)).toBeVisible();
});

test("empty collection drop reports that no documents need deletion", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [{ id: `${collection}-temporary` }]);
  await loadCollection(page, collection);
  const deleteResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/${encodeURIComponent(`${collection}-temporary`)}`
      && response.request().method() === "DELETE",
  );
  await page.locator("#colDrop").click();
  await page.getByRole("dialog", { name: "Delete every document" })
    .getByRole("button", { name: "Delete 1 document" }).click();
  expect((await deleteResponse).status()).toBe(204);
  await page.locator("#colDrop").click();
  await expect(page.locator("#toasts .toast").last()).toContainText("already empty");
});

test("blank collection load presents a validation message", async ({ page }) => {
  await openCollections(page);
  await page.locator("#colSel").fill("   ");
  await page.locator("#colLoad").click();
  await expect(page.locator("#toasts .toast").last()).toContainText("Enter a collection name");
});

test("inserting without a collection presents a validation message", async ({ page }) => {
  await openCollections(page);
  await page.locator("#insCol").fill(" ");
  await page.locator("#insJson").fill('{"id":"unsubmitted"}');
  await page.locator("#insSubmit").click();
  await expect(page.locator("#toasts .toast").last()).toContainText("Collection name required");
  await expect(page.locator("#insStatus")).toHaveText("");
});

test("JSON array root is saved as a generated-ID document", async ({ page, collection }) => {
  await openCollections(page);
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill("[]");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#insSubmit").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  await expect(page.locator("#insStatus")).toHaveText(/^saved .+/);
  const id = (await page.locator("#insStatus").innerText()).slice("saved ".length);
  expect(id).toBeTruthy();
  await loadCollection(page, collection);
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
});

test("cancelling drop-all preserves documents in the collection", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-cancel-drop`;
  await seedThroughUi(page, collection, [{ id, label: "keep me" }]);
  await loadCollection(page, collection);
  await page.locator("#colDrop").click();
  const dialog = page.getByRole("dialog", { name: "Delete every document" });
  await expect(dialog).toContainText(collection);
  await expect(dialog).toContainText("1 document");
  await dialog.getByRole("button", { name: "Cancel" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data).toHaveLength(1);
});

test("confirming drop-all deletes documents but leaves the collection usable", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [
    { id: `${collection}-drop-a`, label: "a" },
    { id: `${collection}-drop-b`, label: "b" },
  ]);
  await loadCollection(page, collection);
  await page.locator("#colDrop").click();
  const dialog = page.getByRole("dialog", { name: "Delete every document" });
  const firstDelete = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/${encodeURIComponent(`${collection}-drop-a`)}`
      && response.request().method() === "DELETE",
  );
  await dialog.getByRole("button", { name: "Delete 2 documents" }).click();
  expect((await firstDelete).status()).toBe(204);
  await expect(page.locator("#colCount")).toHaveText("0 docs");
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data).toEqual([]);
});

const filterCases = [
  { name: "$ne excludes the selected score", field: "score", op: "$ne", value: 10, expected: "beta" },
  { name: "$gte includes the boundary score", field: "score", op: "$gte", value: 10, expected: "alpha" },
  { name: "$lt excludes the boundary score", field: "score", op: "$lt", value: 10, expected: "beta" },
  { name: "$lte includes the boundary score", field: "score", op: "$lte", value: 3, expected: "beta" },
  { name: "$in matches any listed scalar value", field: "label", op: "$in", value: ["alpha", "beta"], expected: ["alpha", "beta"] },
  { name: "$nin excludes listed scalar values", field: "label", op: "$nin", value: ["alpha"], expected: "beta" },
  { name: "$regex performs substring matching", field: "label", op: "$regex", value: "ph", expected: "alpha" },
  { name: "$exists true matches present fields", field: "optional", op: "$exists", value: true, expected: "alpha" },
  { name: "$exists false matches absent fields", field: "optional", op: "$exists", value: false, expected: "beta" },
];

for (const filterCase of filterCases) {
  test(`query builder ${filterCase.name}`, async ({ page, collection }) => {
    await openCollections(page);
    const ids = await seedFilterDocuments(page, collection);
    await loadCollection(page, collection);
    await runFilter(page, collection, filterCase.field, filterCase.op, filterCase.value);
    const expected = Array.isArray(filterCase.expected)
      ? filterCase.expected.map((suffix) => ids[suffix === "alpha" ? 0 : 1]).sort()
      : [ids[filterCase.expected === "alpha" ? 0 : 1]];
    expect((await readQueryResults(page)).sort()).toEqual(expected);
  });
}

test("query sorting orders score values ascending", async ({ page, collection }) => {
  await openCollections(page);
  const records = [
    { id: `${collection}-low`, score: 1 },
    { id: `${collection}-middle`, score: 5 },
    { id: `${collection}-high`, score: 9 },
  ];
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("asc");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual(records.map((record) => record.id));
});

test("query sorting orders score values descending", async ({ page, collection }) => {
  await openCollections(page);
  const records = [
    { id: `${collection}-low`, score: 1 },
    { id: `${collection}-middle`, score: 5 },
    { id: `${collection}-high`, score: 9 },
  ];
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("desc");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual(records.map((record) => record.id).reverse());
});

test("equal sort keys retain a consistent result order across repeated queries", async ({ page, collection }) => {
  await openCollections(page);
  const records = [
    { id: `${collection}-tie-a`, score: 7 },
    { id: `${collection}-tie-b`, score: 7 },
    { id: `${collection}-tie-c`, score: 7 },
  ];
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  const run = async () => {
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname
        === `/api/collections/${encodeURIComponent(collection)}/query`
        && response.request().method() === "POST",
    );
    await page.locator("#qbRun").click();
    expect((await responsePromise).status()).toBe(200);
    return readQueryResults(page);
  };
  const first = await run();
  expect([...first].sort()).toEqual(records.map((record) => record.id).sort());
  expect(await run()).toEqual(first);
});

test("limit of one advances to the next query page", async ({ page, collection }) => {
  await openCollections(page);
  const records = [1, 2, 3].map((score) => ({ id: `${collection}-${score}`, score }));
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("asc");
  await page.locator("#qbLimit").fill("1");
  const firstResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await firstResponse).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual([records[0].id]);
  await expect(page.locator("#qbNext")).toBeEnabled();
  const nextResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbNext").click();
  expect((await nextResponse).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual([records[1].id]);
});

test("pagination returns a partial final page", async ({ page, collection }) => {
  await openCollections(page);
  const records = [1, 2, 3].map((score) => ({ id: `${collection}-${score}`, score }));
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("asc");
  await page.locator("#qbLimit").fill("2");
  await page.locator("#qbOffset").fill("2");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual([records[2].id]);
  await expect(page.locator("#qbNext")).toBeDisabled();
  await expect(page.locator("#qbPageInfo")).toContainText("rows 3–3 of 3 matched");
});

test("pagination disables Next on an exact page boundary", async ({ page, collection }) => {
  await openCollections(page);
  const records = [1, 2, 3, 4].map((score) => ({ id: `${collection}-${score}`, score }));
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("asc");
  await page.locator("#qbLimit").fill("2");
  await page.locator("#qbRun").click();
  await expect(page.locator("#qbNext")).toBeEnabled();
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbNext").click();
  expect((await responsePromise).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual(records.slice(2).map((record) => record.id));
  await expect(page.locator("#qbNext")).toBeDisabled();
});

test("pagination Prev returns from the final page to the first page", async ({ page, collection }) => {
  await openCollections(page);
  const records = [1, 2, 3, 4].map((score) => ({ id: `${collection}-${score}`, score }));
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("asc");
  await page.locator("#qbLimit").fill("2");
  await page.locator("#qbOffset").fill("2");
  const lastResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await lastResponse).status()).toBe(200);
  await expect(page.locator("#qbPrev")).toBeEnabled();
  const previousResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbPrev").click();
  expect((await previousResponse).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual(records.slice(0, 2).map((record) => record.id));
  await expect(page.locator("#qbOffset")).toHaveValue("0");
});

test("an offset beyond the result set displays the no-match state", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [{ id: `${collection}-one`, score: 1 }]);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbLimit").fill("2");
  await page.locator("#qbOffset").fill("20");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  await expect(page.locator("#qbOut")).toContainText("No document matches this filter");
  await expect(page.locator("#qbStatus")).toHaveText("0 matches");
});

test("running an unfiltered query returns every document", async ({ page, collection }) => {
  await openCollections(page);
  const records = [
    { id: `${collection}-all-a`, value: "a" },
    { id: `${collection}-all-b`, value: "b" },
    { id: `${collection}-all-c`, value: "c" },
  ];
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  expect((await readQueryResults(page)).sort()).toEqual(records.map((record) => record.id).sort());
});

test("empty collection query shows a zero-match result", async ({ page, collection }) => {
  await openCollections(page);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`, "POST", {
    id: `${collection}-materialize`,
  })).status).toBe(201);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(`${collection}-materialize`)}`, "DELETE")).status)
    .toBe(204);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  await expect(page.locator("#qbStatus")).toHaveText("0 matches");
  await expect(page.locator("#qbOut")).toContainText("No document matches this filter");
});

test("query with no matching value displays the no-match explanation", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [{ id: `${collection}-query`, category: "books" }]);
  await loadCollection(page, collection);
  await runFilter(page, collection, "category", "$eq", "music");
  await expect(page.locator("#qbStatus")).toHaveText("0 matches");
  await expect(page.locator("#qbOut")).toContainText("No document matches this filter");
});

test("Clear resets query rows, results, and query status", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [{ id: `${collection}-clear`, score: 5 }]);
  await loadCollection(page, collection);
  await runFilter(page, collection, "score", "$gte", 5);
  await expect(page.locator("#qbOut")).toContainText(`${collection}-clear`);
  await page.locator("#qbClear").click();
  await expect(page.locator("#qbRows .qb-row")).toHaveCount(1);
  await expect(page.locator("#qbRows .qb-field").first()).toHaveValue("");
  await expect(page.locator("#qbOut")).toBeEmpty();
  await expect(page.locator("#qbStatus")).toHaveText("idle");
});

test("invalid regular expression shows a rejected-query error without changing data", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-regex-error`;
  await seedThroughUi(page, collection, [{ id, label: "unchanged" }]);
  await loadCollection(page, collection);
  await runFilter(page, collection, "label", "$regex", "[", 400);
  await expect(page.locator("#qbStatus")).toHaveText("failed");
  await expect(page.locator("#qbOut")).toContainText("Filter rejected");
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`)))
    .toMatchObject({ status: 200, data: { fields: { label: "unchanged" } } });
});

test("query builder filters a nested dotted field", async ({ page, collection }) => {
  await openCollections(page);
  const ids = await seedFilterDocuments(page, collection);
  await loadCollection(page, collection);
  await runFilter(page, collection, "profile.city", "$eq", "Oslo");
  expect(await readQueryResults(page)).toEqual([ids[0]]);
});

test("query builder filters numeric createdAt timestamp values", async ({ page, collection }) => {
  await openCollections(page);
  const ids = await seedFilterDocuments(page, collection);
  await loadCollection(page, collection);
  await runFilter(page, collection, "createdAt", "$gte", 1_700_000_001_000);
  expect(await readQueryResults(page)).toEqual([ids[1]]);
});

test("query sorting orders numeric timestamp fields chronologically", async ({ page, collection }) => {
  await openCollections(page);
  const records = [
    { id: `${collection}-later`, createdAt: 1_700_000_002_000 },
    { id: `${collection}-earlier`, createdAt: 1_700_000_001_000 },
  ];
  await seedThroughUi(page, collection, records);
  await loadCollection(page, collection);
  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("createdAt");
  await page.locator("#qbSortDir").selectOption("asc");
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbRun").click();
  expect((await responsePromise).status()).toBe(200);
  expect(await readQueryResults(page)).toEqual([records[1].id, records[0].id]);
});

async function setTtl(
  page: Page,
  collection: string,
  documentId: string,
  ttlSeconds: number,
): Promise<void> {
  const result = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/set-ttl`,
    "POST",
    { documentId, ttlSeconds },
  );
  expect(result.status).toBe(200);
  expect(result.data).toMatchObject({ success: true, updated: 1 });
}

async function waitUntilExpired(page: Page, collection: string, id: string): Promise<void> {
  await expect.poll(async () => (
    await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
    )
  ).status, { timeout: 6_000 }).toBe(404);
}

test("Copy JSON places the selected document details on the clipboard", async ({ page, collection }) => {
  await openCollections(page);
  const document = { id: `${collection}-clipboard`, label: "copy target", active: true };
  await seedThroughUi(page, collection, [document]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${document.id}"]`).click();
  const stored = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
  );
  expect(stored.status).toBe(200);
  await page.context().grantPermissions(["clipboard-read", "clipboard-write"]);

  await page.locator("#docCopy").click();

  await expect(page.locator("#toasts .toast").last()).toContainText("Document JSON copied");
  const copiedText = await page.evaluate(() => navigator.clipboard.readText());
  expect(JSON.parse(copiedText)).toEqual(stored.data);
});

test("Clean expired preserves an unexpired document", async ({ page, collection }) => {
  await openCollections(page);
  const expiredId = `${collection}-expired-one`;
  const activeId = `${collection}-active`;
  await seedThroughUi(page, collection, [
    { id: expiredId, label: "expired" },
    { id: activeId, label: "active" },
  ]);
  await setTtl(page, collection, expiredId, 1);
  await setTtl(page, collection, activeId, 60);
  await waitUntilExpired(page, collection, expiredId);
  await loadCollection(page, collection);
  const cleanupPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colCleanup").click();
  expect((await cleanupPromise).status()).toBe(200);
  await expect(page.locator("#colCount")).toHaveText("1 docs");
  await expect(page.locator(`#colTable [data-doc="${activeId}"]`)).toBeVisible();
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(activeId)}`)).status)
    .toBe(200);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(expiredId)}`)).status)
    .toBe(404);
});

test("Clean expired removes multiple expired documents and reports the count", async ({ page, collection }) => {
  await openCollections(page);
  const ids = [`${collection}-expired-a`, `${collection}-expired-b`];
  await seedThroughUi(page, collection, ids.map((id) => ({ id })));
  for (const id of ids) await setTtl(page, collection, id, 1);
  for (const id of ids) await waitUntilExpired(page, collection, id);
  await loadCollection(page, collection);
  const cleanupPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colCleanup").click();
  expect((await cleanupPromise).status()).toBe(200);
  await expect(page.locator("#toasts .toast").last()).toContainText("Removed 2 expired docs");
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data).toEqual([]);
});

test("Clean expired reports zero when every document is active", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-active-only`;
  await seedThroughUi(page, collection, [{ id, label: "active" }]);
  await setTtl(page, collection, id, 60);
  await loadCollection(page, collection);
  const cleanupPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colCleanup").click();
  expect((await cleanupPromise).status()).toBe(200);
  await expect(page.locator("#toasts .toast").last()).toContainText("Removed 0 expired docs");
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
});

test("Clean expired is safe to run against an empty collection", async ({ page, collection }) => {
  await openCollections(page);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`, "POST", {
    id: `${collection}-materialize-empty`,
  })).status).toBe(201);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(`${collection}-materialize-empty`)}`, "DELETE")).status)
    .toBe(204);
  await loadCollection(page, collection);
  const cleanupPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colCleanup").click();
  expect((await cleanupPromise).status()).toBe(200);
  await expect(page.locator("#toasts .toast").last()).toContainText("Removed 0 expired docs");
  await expect(page.locator("#colCount")).toHaveText("0 docs");
});

test("expired documents disappear from the loaded collection before cleanup", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-expires-from-view`;
  await seedThroughUi(page, collection, [{ id, label: "expires" }]);
  await loadCollection(page, collection);
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
  await setTtl(page, collection, id, 1);
  await waitUntilExpired(page, collection, id);
  const refreshPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "GET",
  );
  await page.locator("#colRefresh").click();
  expect((await refreshPromise).status()).toBe(200);
  await expect(page.locator("#colCount")).toHaveText("0 docs");
  await expect(page.locator("#colTable [data-doc]")).toHaveCount(0);
  const cleanupPromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colCleanup").click();
  expect((await cleanupPromise).status()).toBe(200);
});

test("picker refresh followed by chip selection loads a newly inserted document", async ({ page, collection }) => {
  const id = `${collection}-record`;
  await openCollections(page);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`, "POST", {
    id,
    title: "created before picker refresh",
  })).status).toBe(201);
  await page.locator("#colPickerRefresh").click();
  const chip = page.locator(`#colPicker [data-col="${collection}"]`);
  await expect(chip).toBeVisible();
  await chip.click();
  await expect(page.locator("#colSel")).toHaveValue(collection);
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
});

test("document deletion cancellation keeps the selected record", async ({ page, collection }) => {
  await openCollections(page);
  const id = `${collection}-cancel-delete`;
  await seedThroughUi(page, collection, [{ id, label: "preserved" }]);
  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await page.locator("#docDel").click();
  const dialog = page.getByRole("dialog", { name: "Delete document" });
  await expect(dialog).toContainText(id);
  await dialog.getByRole("button", { name: "Cancel" }).click();
  await expect(page.locator(`#colTable [data-doc="${id}"]`)).toBeVisible();
  expect((await api(page, `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`)).status)
    .toBe(200);
});

test("query builder matches boolean values parsed from JSON", async ({ page, collection }) => {
  await openCollections(page);
  await seedThroughUi(page, collection, [
    { id: `${collection}-true`, enabled: true },
    { id: `${collection}-false`, enabled: false },
  ]);
  await loadCollection(page, collection);
  await runFilter(page, collection, "enabled", "$eq", true);
  expect(await readQueryResults(page)).toEqual([`${collection}-true`]);
});
