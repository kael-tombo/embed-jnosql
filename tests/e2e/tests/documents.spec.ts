import { expect, test as base } from "../fixtures/console";
import type { Page, Request } from "@playwright/test";

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

function uniqueCollection(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

async function cleanupCollection(page: Page, collection: string): Promise<void> {
  const collectionPath = `/collections/${encodeURIComponent(collection)}`;
  const listed = await api(page, collectionPath);
  if (listed.status === 404) return;
  expect(listed.status).toBe(200);
  expect(Array.isArray(listed.data)).toBe(true);
  for (const document of listed.data) {
    if (typeof document?.id === "string") {
      const deleted = await api(page, `${collectionPath}/${encodeURIComponent(document.id)}`, "DELETE");
      expect(deleted.status).toBe(204);
    }
  }

  const indexes = await api(page, `/indexes/${encodeURIComponent(collection)}`);
  expect(indexes.status).toBe(200);
  if (indexes.data?.indexes) {
    for (const field of Object.keys(indexes.data.indexes)) {
      const removed = await api(
        page,
        `/indexes/${encodeURIComponent(collection)}?field=${encodeURIComponent(field)}`,
        "DELETE",
      );
      expect(removed.status).toBe(200);
    }
  }
  const schema = await api(page, `/schema/${encodeURIComponent(collection)}`, "DELETE");
  expect([200, 404]).toContain(schema.status);
}

async function insertThroughConsole(
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
  return (await responsePromise).status();
}

async function loadCollection(page: Page, collection: string): Promise<void> {
  await page.locator("#colSel").fill(collection);
  await page.locator("#colLoad").click();
}

const test = base.extend<TestFixtures>({
  collection: async ({ page }, use) => {
    const name = uniqueCollection("e2e_docs");
    await use(name);
    await cleanupCollection(page, name);
  },
});

test("shows seeded collections and distinguishes unloaded, empty, and missing collections", async ({
  page,
  collection,
}) => {
  await page.locator('#nav [data-panel="collections"]').click();
  await expect(page.locator("#colTable")).toContainText("No collection loaded.");

  const collections = await api(page, "/collections");
  expect(collections.status).toBe(200);
  const names = collections.data.collections.map((item: { name: string }) => item.name);
  expect(names).toEqual(expect.arrayContaining(["products", "users", "inventory"]));

  const seededUsers = await api(page, "/collections/users");
  expect(seededUsers.status).toBe(200);
  await loadCollection(page, "users");
  await expect(page.locator("#colCount")).toHaveText(`${seededUsers.data.length} docs`);
  const visibleUserIds = await page.locator("#colTable [data-doc]").allTextContents();
  expect(visibleUserIds).toEqual(seededUsers.data.map((document: { id: string }) => document.id));

  const emptyDocument = { id: `${collection}-empty`, marker: "empty-state" };
  expect(await insertThroughConsole(page, collection, emptyDocument)).toBe(201);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data)
    .toHaveLength(1);

  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${emptyDocument.id}"]`).click();
  await page.locator("#docDel").click();
  const dialog = page.getByRole("dialog", { name: "Delete document" });
  await expect(dialog).toBeVisible();
  await expect(dialog).toContainText(collection);

  const deleteResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/${encodeURIComponent(emptyDocument.id)}`
      && response.request().method() === "DELETE",
  );
  await dialog.getByRole("button", { name: "Delete document" }).click();
  expect((await deleteResponse).status()).toBe(204);

  const emptyState = await api(page, `/collections/${encodeURIComponent(collection)}`);
  expect(emptyState.status).toBe(200);
  expect(emptyState.data).toEqual([]);
  await expect(page.locator("#colCount")).toHaveText("0 docs");
  await expect(page.locator("#colTable [data-doc]")).toHaveCount(0);

  const missing = uniqueCollection("e2e_missing");
  await loadCollection(page, missing);
  await expect(page.getByRole("alert").filter({ hasText: `Collection not found: ${missing}` }))
    .toBeVisible();
  expect((await api(page, `/collections/${encodeURIComponent(missing)}`)).status).toBe(404);
  const afterMissingRead = await api(page, "/collections");
  expect(afterMissingRead.data.collections.map((item: { name: string }) => item.name))
    .not.toContain(missing);
});

test("creates, reads, replaces, and deletes documents while rejecting invalid JSON and checking duplicate IDs", async ({
  page,
  collection,
}) => {
  await page.locator('#nav [data-panel="collections"]').click();
  const id = `${collection}-record`;
  const original = {
    id,
    name: "Initial value",
    profile: { city: "Dublin", active: true },
  };

  expect(await insertThroughConsole(page, collection, original)).toBe(201);
  const stored = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
  );
  expect(stored.status).toBe(200);
  expect(stored.data).toMatchObject({ id, fields: { name: "Initial value", profile: original.profile } });

  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await expect(page.locator("#docJson")).toContainText("Initial value");
  await page.locator("#docViewTree").click();
  await expect(page.locator("#docViewTree")).toHaveAttribute("aria-pressed", "true");
  await expect(page.locator("#docDetail")).toContainText("Dublin");
  await page.locator("#docViewJson").click();
  await page.locator("#docEdit").click();

  const invalidPostRequests: string[] = [];
  const onInvalidRequest = (request: Request) => {
    if (request.method() === "POST"
      && new URL(request.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`) {
      invalidPostRequests.push(request.url());
    }
  };
  page.on("request", onInvalidRequest);
  await page.locator("#insJson").fill('{"id":');
  await page.locator("#insSubmit").click();
  await expect(page.locator("#insStatus")).toHaveText("invalid JSON");
  expect(invalidPostRequests).toHaveLength(0);
  page.off("request", onInvalidRequest);
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
  )).data).toMatchObject({ fields: { name: "Initial value", profile: original.profile } });

  const updated = {
    id,
    name: "Updated value",
    profile: { city: "Oslo", active: false },
  };
  await page.locator("#insJson").fill(JSON.stringify(updated, null, 2));
  const updateResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#insSubmit").click();
  expect((await updateResponse).status()).toBe(201);
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
  )).data).toMatchObject({ id, fields: { name: "Updated value", profile: updated.profile } });

  const duplicate = { id, name: "Duplicate ID replacement", sequence: 2 };
  expect(await insertThroughConsole(page, collection, duplicate)).toBe(201);
  const afterDuplicate = await api(page, `/collections/${encodeURIComponent(collection)}`);
  expect(afterDuplicate.data).toHaveLength(1);
  expect(afterDuplicate.data[0]).toMatchObject({ id, fields: { name: duplicate.name, sequence: 2 } });

  await loadCollection(page, collection);
  await page.locator(`#colTable [data-doc="${id}"]`).click();
  await page.locator("#docDel").click();
  const dialog = page.getByRole("dialog", { name: "Delete document" });
  const deleteResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`
      && response.request().method() === "DELETE",
  );
  await dialog.getByRole("button", { name: "Delete document" }).click();
  expect((await deleteResponse).status()).toBe(204);

  const afterDelete = await api(page, `/collections/${encodeURIComponent(collection)}`);
  expect(afterDelete.status).toBe(200);
  expect(afterDelete.data).toEqual([]);
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
  )).status).toBe(404);
});

test("reload preserves saved documents and the collection can be refreshed", async ({
  page,
  collection,
}) => {
  await page.locator('#nav [data-panel="collections"]').click();
  const document = { id: `${collection}-reload`, title: "Persisted across refresh" };
  expect(await insertThroughConsole(page, collection, document)).toBe(201);

  await page.reload();
  await expect(page.locator("#panel-collections")).toBeVisible();
  const persisted = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
  );
  expect(persisted.status).toBe(200);
  expect(persisted.data).toMatchObject({
    id: document.id,
    fields: { title: document.title },
  });

  await page.locator('#nav [data-panel="collections"]').click();
  await loadCollection(page, collection);
  await expect(page.locator(`#colTable [data-doc="${document.id}"]`)).toBeVisible();
});

test("sets document TTL and cleans up the expired document", async ({ page, collection }) => {
  await page.locator('#nav [data-panel="collections"]').click();
  const document = { id: `${collection}-ttl`, name: "Short-lived document" };
  expect(await insertThroughConsole(page, collection, document)).toBe(201);

  const ttlResult = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/set-ttl`,
    "POST",
    { documentId: document.id, ttlSeconds: 1 },
  );
  expect(ttlResult.status).toBe(200);
  expect(ttlResult.data).toMatchObject({ success: true, updated: 1 });
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
  )).status).toBe(200);

  await expect.poll(async () => (
    await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
    )
  ).status, { timeout: 5_000 }).toBe(404);

  const cleanupResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/cleanup`
      && response.request().method() === "POST",
  );
  await page.locator("#colSel").fill(collection);
  await page.locator("#colCleanup").click();
  const cleanup = await cleanupResponse;
  expect(cleanup.status()).toBe(200);
  expect(await cleanup.json()).toEqual({ deleted: 1 });
  await expect(page.locator("#toasts .toast").last())
    .toContainText("Removed 1 expired docs");

  const remaining = await api(page, `/collections/${encodeURIComponent(collection)}`);
  expect(remaining.status).toBe(200);
  expect(remaining.data).toEqual([]);
});

test("filters with $eq, $gt, and combined conditions, then sorts and pages against engine results", async ({
  page,
  collection,
}) => {
  await page.locator('#nav [data-panel="collections"]').click();
  const records = [
    { id: `${collection}-a`, group: "red", score: 1 },
    { id: `${collection}-b`, group: "red", score: 3 },
    { id: `${collection}-c`, group: "blue", score: 4 },
    { id: `${collection}-d`, group: "blue", score: 5 },
  ];
  for (const document of records) {
    expect(await insertThroughConsole(page, collection, document)).toBe(201);
  }
  await loadCollection(page, collection);

  const rows = page.locator("#qbRows .qb-row");
  const firstRow = rows.first();
  const runQueryAndCompare = async (payload: Record<string, unknown>) => {
    const uiResponsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname
        === `/api/collections/${encodeURIComponent(collection)}/query`
        && response.request().method() === "POST",
    );
    await page.locator("#qbRun").click();
    const uiResponse = await uiResponsePromise;
    expect(uiResponse.status()).toBe(200);

    const expected = await api(
      page,
      `/collections/${encodeURIComponent(collection)}/query`,
      "POST",
      payload,
    );
    expect(expected.status).toBe(200);
    const expectedIds = expected.data.map((document: { id: string }) => document.id);
    await expect.poll(async () => page.locator("#qbOut td code").allTextContents())
      .toEqual(expectedIds);
    return expectedIds;
  };

  await firstRow.locator(".qb-field").fill("group");
  await firstRow.locator(".qb-op").selectOption("$eq");
  await firstRow.locator(".qb-value").fill('"red"');
  const eqIds = await runQueryAndCompare({ group: { $eq: "red" } });
  await expect(page.locator("#qbStatus")).toContainText("2 match");
  expect([...eqIds].sort()).toEqual([records[0].id, records[1].id].sort());

  await page.locator("#qbClear").click();
  const greaterThanRow = rows.first();
  await greaterThanRow.locator(".qb-field").fill("score");
  await greaterThanRow.locator(".qb-op").selectOption("$gt");
  await greaterThanRow.locator(".qb-value").fill("3");
  const gtIds = await runQueryAndCompare({ score: { $gt: 3 } });
  expect([...gtIds].sort()).toEqual([records[2].id, records[3].id].sort());

  await page.locator("#qbClear").click();
  const combinedRows = page.locator("#qbRows .qb-row");
  await combinedRows.nth(0).locator(".qb-field").fill("group");
  await combinedRows.nth(0).locator(".qb-op").selectOption("$eq");
  await combinedRows.nth(0).locator(".qb-value").fill('"red"');
  await page.locator("#qbAddRow").click();
  await combinedRows.nth(1).locator(".qb-field").fill("score");
  await combinedRows.nth(1).locator(".qb-op").selectOption("$gt");
  await combinedRows.nth(1).locator(".qb-value").fill("1");
  const combinedIds = await runQueryAndCompare({
    group: { $eq: "red" },
    score: { $gt: 1 },
  });
  expect(combinedIds).toEqual([records[1].id]);

  await page.locator("#qbClear").click();
  await page.locator("#qbSortField").fill("score");
  await page.locator("#qbSortDir").selectOption("desc");
  await page.locator("#qbLimit").fill("2");
  await page.locator("#qbOffset").fill("0");
  const runSortedPage = async (offset: number) => {
    await page.locator("#qbOffset").fill(String(offset));
    return runQueryAndCompare({
      sortField: "score",
      sortDir: "desc",
      limit: 2,
      offset,
    });
  };

  expect(await runSortedPage(0)).toEqual([records[3].id, records[2].id]);
  await expect(page.locator("#qbPrev")).toBeDisabled();
  await expect(page.locator("#qbNext")).toBeEnabled();
  const nextPageResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbNext").click();
  expect((await nextPageResponse).status()).toBe(200);
  expect(await page.locator("#qbOffset")).toHaveValue("2");
  await expect.poll(async () => page.locator("#qbOut td code").allTextContents())
    .toEqual([records[1].id, records[0].id]);
  await expect(page.locator("#qbPrev")).toBeEnabled();
  await expect(page.locator("#qbNext")).toBeDisabled();
  const previousPageResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/query`
      && response.request().method() === "POST",
  );
  await page.locator("#qbPrev").click();
  expect((await previousPageResponse).status()).toBe(200);
  expect(await page.locator("#qbOffset")).toHaveValue("0");
  expect(await runSortedPage(0)).toEqual([records[3].id, records[2].id]);

  await page.locator("#qbClear").click();
  const malformedValueRow = page.locator("#qbRows .qb-row").first();
  await malformedValueRow.locator(".qb-field").fill("score");
  await malformedValueRow.locator(".qb-op").selectOption("$eq");
  await malformedValueRow.locator(".qb-value").fill("not-json");
  await expect(page.locator("#qbPreview")).toContainText('"$eq": "not-json"');
  await page.locator("#qbRun").click();
  await expect(page.locator("#qbStatus")).toHaveText("0 matches");
  await expect(page.locator("#qbOut")).toContainText("No document matches this filter");
});
