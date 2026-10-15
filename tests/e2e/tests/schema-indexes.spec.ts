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
  const listed = await api(page, collectionPath).catch(() => null);
  if (listed?.status === 200 && Array.isArray(listed.data)) {
    for (const document of listed.data) {
      if (typeof document?.id === "string") {
        await api(page, `${collectionPath}/${encodeURIComponent(document.id)}`, "DELETE");
      }
    }
  }

  const indexes = await api(page, `/indexes/${encodeURIComponent(collection)}`).catch(() => null);
  if (indexes?.status === 200 && indexes.data?.indexes) {
    for (const field of Object.keys(indexes.data.indexes)) {
      await api(
        page,
        `/indexes/${encodeURIComponent(collection)}?field=${encodeURIComponent(field)}`,
        "DELETE",
      );
    }
  }
  await api(page, `/schema/${encodeURIComponent(collection)}`, "DELETE").catch(() => null);
}

async function insertThroughConsole(
  page: Page,
  collection: string,
  document: Record<string, unknown>,
): Promise<number> {
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill(JSON.stringify(document, null, 2));
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#insSubmit").click();
  return (await responsePromise).status();
}

const test = base.extend<TestFixtures>({
  collection: async ({ page }, use) => {
    const name = uniqueCollection("e2e_schema");
    await use(name);
    await cleanupCollection(page, name);
  },
});

test("registers a schema, accepts valid documents, rejects invalid data, and preserves state after invalid schema input", async ({
  page,
  collection,
}) => {
  const schema = {
    fields: [
      { name: "name", type: "String", required: true },
      { name: "rank", type: "Integer", required: true },
    ],
    strict: true,
  };

  await page.locator('#nav [data-panel="schema"]').click();
  await page.locator("#schCol").fill(collection);
  await page.locator("#schFields").fill(JSON.stringify(schema.fields, null, 2));
  await page.locator("#schStrict").check();
  const registerResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/schema/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#schSave").click();
  expect((await registerResponse).status()).toBe(201);
  await expect(
    page.getByRole("status").filter({ hasText: `Schema registered for ${collection}` }),
  ).toBeVisible();

  const registeredSchema = await api(page, `/schema/${encodeURIComponent(collection)}`);
  expect(registeredSchema.status).toBe(200);
  expect(registeredSchema.data).toMatchObject({
    collectionName: collection,
    strict: true,
    fields: [
      { name: "name", type: "String", required: true },
      { name: "rank", type: "Integer", required: true },
    ],
  });
  await expect(page.locator("#schList")).toContainText(collection);

  await page.locator('#nav [data-panel="collections"]').click();
  const accepted = { id: `${collection}-accepted`, name: "valid", rank: 7 };
  expect(await insertThroughConsole(page, collection, accepted)).toBe(201);
  const acceptedRecord = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(accepted.id)}`,
  );
  expect(acceptedRecord.status).toBe(200);
  expect(acceptedRecord.data).toMatchObject({
    id: accepted.id,
    fields: { name: accepted.name, rank: accepted.rank },
  });

  const rejected = { id: `${collection}-rejected`, name: "missing required rank" };
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill(JSON.stringify(rejected));
  const rejectedResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#insSubmit").click();
  expect((await rejectedResponse).status()).toBe(400);
  await expect(page.locator("#insStatus")).toContainText("Schema validation failed");
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(rejected.id)}`,
  )).status).toBe(404);
  const afterRejectedInsert = await api(page, `/collections/${encodeURIComponent(collection)}`);
  expect(afterRejectedInsert.data).toHaveLength(1);
  expect(afterRejectedInsert.data[0].id).toBe(accepted.id);

  await page.locator('#nav [data-panel="schema"]').click();
  await page.locator("#schCol").fill(collection);
  await page.locator("#schFields").fill('[{"name":');
  const invalidSchemaRequests: string[] = [];
  const onSchemaRequest = (request: Request) => {
    if (request.method() === "POST"
      && new URL(request.url()).pathname === `/api/schema/${encodeURIComponent(collection)}`) {
      invalidSchemaRequests.push(request.url());
    }
  };
  page.on("request", onSchemaRequest);
  await page.locator("#schSave").click();
  await expect(page.getByRole("alert").filter({ hasText: "Fields must be valid JSON" }))
    .toBeVisible();
  expect(invalidSchemaRequests).toHaveLength(0);
  page.off("request", onSchemaRequest);
  expect((await api(page, `/schema/${encodeURIComponent(collection)}`)).data)
    .toMatchObject(registeredSchema.data);
  expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data)
    .toHaveLength(1);
});

test("inspects and creates a field index and rejects an empty field without changing engine state", async ({
  page,
  collection,
}) => {
  const document = { id: `${collection}-row`, category: "hardware", marker: "preserve" };
  await page.locator('#nav [data-panel="collections"]').click();
  expect(await insertThroughConsole(page, collection, document)).toBe(201);

  await page.locator('#nav [data-panel="indexes"]').click();
  await page.locator("#idxCol").fill(collection);
  const listResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/indexes/${encodeURIComponent(collection)}`
      && response.request().method() === "GET",
  );
  await page.locator("#idxList").click();
  expect((await listResponse).status()).toBe(200);
  const beforeCreate = await api(page, `/indexes/${encodeURIComponent(collection)}`);
  expect(beforeCreate.status).toBe(200);
  expect(beforeCreate.data.indexes).toEqual({});
  await expect(page.locator("#idxOut")).toContainText("No indexes on this collection.");

  const invalidIndexRequests: string[] = [];
  const onIndexRequest = (request: Request) => {
    if (request.method() === "POST"
      && new URL(request.url()).pathname === `/api/indexes/${encodeURIComponent(collection)}`) {
      invalidIndexRequests.push(request.url());
    }
  };
  page.on("request", onIndexRequest);
  await page.locator("#idxField").fill("");
  await page.locator("#idxCreate").click();
  await expect(page.getByRole("alert").filter({ hasText: "Collection and field required" }))
    .toBeVisible();
  expect(invalidIndexRequests).toHaveLength(0);
  page.off("request", onIndexRequest);
  expect((await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes).toEqual({});
  expect((await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
  )).data).toMatchObject({ fields: { category: document.category, marker: document.marker } });

  await page.locator("#idxField").fill("category");
  const createResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/indexes/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#idxCreate").click();
  expect((await createResponse).status()).toBe(201);
  await expect(page.locator("#idxStatus")).toHaveText(`created ${collection}.category`);

  const createdIndex = await api(page, `/indexes/${encodeURIComponent(collection)}`);
  expect(createdIndex.status).toBe(200);
  expect(Object.keys(createdIndex.data.indexes)).toContain("category");
  await expect(page.locator("#idxOut")).toContainText("category");
  const recordAfterIndex = await api(
    page,
    `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
  );
  expect(recordAfterIndex.status).toBe(200);
  expect(recordAfterIndex.data).toMatchObject({
    id: document.id,
    fields: { category: document.category, marker: document.marker },
  });
});
