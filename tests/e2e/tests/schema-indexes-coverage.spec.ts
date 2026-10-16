import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type TestFixtures = {
  collection: string;
  otherCollection: string;
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

function uniqueCollection(prefix: string): string {
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

async function cleanupCollection(page: Page, collection: string): Promise<void> {
  const collectionPath = `/collections/${encodeURIComponent(collection)}`;
  const listed = await api(page, collectionPath);
  expect([200, 404]).toContain(listed.status);
  if (listed.status === 200 && Array.isArray(listed.data)) {
    for (const document of listed.data) {
      if (typeof document?.id === "string") {
        const removed = await api(
          page,
          `${collectionPath}/${encodeURIComponent(document.id)}`,
          "DELETE",
        );
        expect([204, 404]).toContain(removed.status);
      }
    }
  }

  const indexes = await api(page, `/indexes/${encodeURIComponent(collection)}`);
  expect([200, 404]).toContain(indexes.status);
  if (indexes.status === 200 && indexes.data?.indexes) {
    for (const field of Object.keys(indexes.data.indexes)) {
      const removed = await api(
        page,
        `/indexes/${encodeURIComponent(collection)}?field=${encodeURIComponent(field)}`,
        "DELETE",
      );
      expect([200, 404]).toContain(removed.status);
    }
  }
  expect((await api(page, `/schema/${encodeURIComponent(collection)}`, "DELETE")).status)
    .toBe(200);
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
  collection: async ({ page }, use) => {
    const name = uniqueCollection("e2e_schema_coverage");
    await use(name);
    await cleanupCollection(page, name);
  },
  otherCollection: async ({ page }, use) => {
    const name = uniqueCollection("e2e_index_other");
    await use(name);
    await cleanupCollection(page, name);
  },
});

async function registerSchema(
  page: Page,
  collection: string,
  fields: unknown,
  strict = false,
): Promise<import("@playwright/test").Response> {
  await page.locator('#nav [data-panel="schema"]').click();
  await page.locator("#schCol").fill(collection);
  await page.locator("#schFields").fill(JSON.stringify(fields));
  if (strict) await page.locator("#schStrict").check();
  else await page.locator("#schStrict").uncheck();
  const response = waitForApi(page, `/schema/${encodeURIComponent(collection)}`, "POST");
  await page.locator("#schSave").click();
  return response;
}

async function insertDocument(
  page: Page,
  collection: string,
  document: Record<string, unknown>,
): Promise<import("@playwright/test").Response> {
  await page.locator('#nav [data-panel="collections"]').click();
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill(JSON.stringify(document));
  const response = waitForApi(page, `/collections/${encodeURIComponent(collection)}`, "POST");
  await page.locator("#insSubmit").click();
  return response;
}

async function listSchemas(page: Page): Promise<import("@playwright/test").Response> {
  await page.locator('#nav [data-panel="schema"]').click();
  const response = waitForApi(page, "/schema", "GET");
  await page.locator("#schRefresh").click();
  return response;
}

async function listIndexes(
  page: Page,
  collection: string,
): Promise<import("@playwright/test").Response> {
  await page.locator('#nav [data-panel="indexes"]').click();
  await page.locator("#idxCol").fill(collection);
  const response = waitForApi(page, `/indexes/${encodeURIComponent(collection)}`, "GET");
  await page.locator("#idxList").click();
  return response;
}

async function createIndex(
  page: Page,
  collection: string,
  field: string,
): Promise<import("@playwright/test").Response> {
  await page.locator('#nav [data-panel="indexes"]').click();
  await page.locator("#idxCol").fill(collection);
  await page.locator("#idxField").fill(field);
  const response = waitForApi(page, `/indexes/${encodeURIComponent(collection)}`, "POST");
  await page.locator("#idxCreate").click();
  return response;
}

test.describe("Schema UI coverage", () => {
  test("an empty schema list explains that unconfigured collections accept documents", async ({
    page,
  }) => {
    expect((await listSchemas(page)).status()).toBe(200);
    await expect(page.locator("#schList"))
      .toContainText("No schemas registered — collections accept any valid document.");
  });

  test("registering a schema displays its collection in the list", async ({ page, collection }) => {
    expect((await registerSchema(page, collection, [{ name: "name", type: "String" }])).status())
      .toBe(201);
    await expect(page.getByRole("status").filter({ hasText: `Schema registered for ${collection}` }))
      .toBeVisible();
    await expect(page.locator("#schList")).toContainText(collection);
  });

  test("strict mode is reflected in the registered schema", async ({ page, collection }) => {
    await registerSchema(page, collection, [{ name: "name", type: "String" }], true);
    expect((await api(page, `/schema/${encodeURIComponent(collection)}`)).data.strict).toBe(true);
    await expect(page.locator("#schStrict")).toBeChecked();
  });

  test("registering a replacement schema updates its field definition", async ({ page, collection }) => {
    await registerSchema(page, collection, [{ name: "oldField", type: "String" }]);
    await registerSchema(page, collection, [{ name: "newField", type: "Boolean" }]);
    const schema = await api(page, `/schema/${encodeURIComponent(collection)}`);
    expect(schema.data.fields).toEqual([
      { name: "newField", type: "Boolean", required: false },
    ]);
  });

  test("an empty field list accepts a valid document", async ({ page, collection }) => {
    await registerSchema(page, collection, []);
    expect((await insertDocument(page, collection, { id: `${collection}-empty-schema`, value: 1 }))
      .status()).toBe(201);
    await expect(page.locator("#insStatus")).toContainText("saved");
  });

  test("an optional schema field may be omitted", async ({ page, collection }) => {
    await registerSchema(page, collection, [{ name: "nickname", type: "String" }]);
    expect((await insertDocument(page, collection, { id: `${collection}-optional` })).status())
      .toBe(201);
  });

  test("a missing required schema field is rejected without insertion", async ({ page, collection }) => {
    await registerSchema(page, collection, [{ name: "email", type: "String", required: true }]);
    const id = `${collection}-missing`;
    expect((await insertDocument(page, collection, { id })).status()).toBe(400);
    await expect(page.locator("#insStatus")).toContainText("Schema validation failed");
    expect((await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
    )).status).toBe(404);
  });

  const acceptedTypes: Array<{ type: string; value: unknown }> = [
    { type: "String", value: "sensor" },
    { type: "Integer", value: 42 },
    { type: "Long", value: 9_000_000_000 },
    { type: "Double", value: 3.5 },
    { type: "Boolean", value: true },
    { type: "Map", value: { enabled: true } },
    { type: "List", value: ["red", "blue"] },
  ];

  for (const { type, value } of acceptedTypes) {
    test(`schema UI accepts a valid ${type} field value`, async ({ page, collection }) => {
      await registerSchema(page, collection, [{ name: "payload", type, required: true }]);
      const id = `${collection}-valid-${type.toLowerCase()}`;
      expect((await insertDocument(page, collection, { id, payload: value })).status()).toBe(201);
      const record = await api(
        page,
        `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
      );
      expect(record.status).toBe(200);
      expect(record.data.fields.payload).toEqual(value);
    });
  }

  const invalidTypes: Array<{ type: string; value: unknown }> = [
    { type: "String", value: 42 },
    { type: "Integer", value: "42" },
    { type: "Boolean", value: "true" },
    { type: "Map", value: ["not", "a", "map"] },
    { type: "List", value: { not: "a list" } },
  ];

  for (const { type, value } of invalidTypes) {
    test(`schema UI rejects an incompatible ${type} value without mutation`, async ({
      page,
      collection,
    }) => {
      await registerSchema(page, collection, [{ name: "payload", type, required: true }]);
      const id = `${collection}-invalid-${type.toLowerCase()}`;
      expect((await insertDocument(page, collection, { id, payload: value })).status()).toBe(400);
      await expect(page.locator("#insStatus")).toContainText("Schema validation failed");
      expect((await api(
        page,
        `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(id)}`,
      )).status).toBe(404);
    });
  }

  test("strict schemas reject an unknown field and preserve registered data", async ({
    page,
    collection,
  }) => {
    await registerSchema(page, collection, [{ name: "known", type: "String" }], true);
    const id = `${collection}-extra`;
    expect((await insertDocument(page, collection, { id, known: "yes", extra: "no" })).status())
      .toBe(400);
    await expect(page.locator("#insStatus")).toContainText("Schema validation failed");
    expect((await api(page, `/collections/${encodeURIComponent(collection)}`)).data).toHaveLength(0);
  });

  test("non-strict schemas allow additional document fields", async ({ page, collection }) => {
    await registerSchema(page, collection, [{ name: "known", type: "String" }]);
    expect((await insertDocument(page, collection, {
      id: `${collection}-extra-allowed`,
      known: "allowed",
      additional: 7,
    })).status()).toBe(201);
  });

  test("schema refresh shows multiple registered collections", async ({
    page,
    collection,
    otherCollection,
  }) => {
    await registerSchema(page, collection, []);
    await registerSchema(page, otherCollection, []);
    await listSchemas(page);
    await expect(page.locator("#schList")).toContainText(collection);
    await expect(page.locator("#schList")).toContainText(otherCollection);
  });

  test("registering a schema for a non-ASCII collection name succeeds", async ({ page }) => {
    const collection = `${uniqueCollection("e2e_schema")} 雪`;
    await registerSchema(page, collection, [{ name: "label", type: "String" }]);
    expect((await api(page, `/schema/${encodeURIComponent(collection)}`)).status).toBe(200);
    expect((await api(page, `/schema/${encodeURIComponent(collection)}`, "DELETE")).status)
      .toBe(200);
  });

  test("a blank schema collection name is rejected without a request", async ({ page }) => {
    await page.locator('#nav [data-panel="schema"]').click();
    await page.locator("#schCol").fill("");
    await page.locator("#schFields").fill("[]");
    const requests: string[] = [];
    const onRequest = (request: import("@playwright/test").Request) => {
      if (request.method() === "POST" && new URL(request.url()).pathname.startsWith("/api/schema/")) {
        requests.push(request.url());
      }
    };
    page.on("request", onRequest);
    await page.locator("#schSave").click();
    await expect(page.getByRole("alert").filter({ hasText: "Collection name required" }))
      .toBeVisible();
    expect(requests).toHaveLength(0);
    page.off("request", onRequest);
  });

  test("malformed schema JSON is rejected without replacing the registered schema", async ({
    page,
    collection,
  }) => {
    await registerSchema(page, collection, [{ name: "stable", type: "String" }]);
    const before = await api(page, `/schema/${encodeURIComponent(collection)}`);
    await page.locator("#schFields").fill('[{"name":');
    await page.locator("#schSave").click();
    await expect(page.getByRole("alert").filter({ hasText: "Fields must be valid JSON" }))
      .toBeVisible();
    expect((await api(page, `/schema/${encodeURIComponent(collection)}`)).data)
      .toMatchObject(before.data);
  });

});

test.describe("Indexes UI coverage", () => {
  test("listing indexes on an empty collection shows the empty state", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, value: "present" });
    expect((await listIndexes(page, collection)).status()).toBe(200);
    await expect(page.locator("#idxOut")).toContainText("No indexes on this collection.");
  });

  test("creating a string index lists its field name", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, category: "hardware" });
    expect((await createIndex(page, collection, "category")).status()).toBe(201);
    await expect(page.locator("#idxStatus")).toHaveText(`created ${collection}.category`);
    await expect(page.locator("#idxOut")).toContainText("category");
  });

  test("creating a numeric-field index displays index statistics", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, score: 9 });
    await createIndex(page, collection, "score");
    await expect(page.locator("#idxOut")).toContainText("score");
    await expect(page.locator("#idxOut")).toContainText("Unique values");
    await expect(page.locator("#idxOut")).toContainText("Entries");
    expect(Object.keys((await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes))
      .toContain("score");
  });

  test("a dotted field index is listed under its exact field path", async ({ page, collection }) => {
    await insertDocument(page, collection, {
      id: `${collection}-seed`,
      profile: { city: "Dublin" },
    });
    await createIndex(page, collection, "profile.city");
    await expect(page.locator("#idxOut")).toContainText("profile.city");
  });

  test("several indexes on one collection are listed independently", async ({ page, collection }) => {
    await insertDocument(page, collection, {
      id: `${collection}-seed`,
      category: "hardware",
      region: "west",
    });
    await createIndex(page, collection, "category");
    await createIndex(page, collection, "region");
    const indexes = (await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes;
    expect(Object.keys(indexes).sort()).toEqual(["category", "region"]);
    await expect(page.locator("#idxOut")).toContainText("category");
    await expect(page.locator("#idxOut")).toContainText("region");
  });

  test("an empty index field is rejected without creating an index", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, category: "hardware" });
    await page.locator('#nav [data-panel="indexes"]').click();
    await page.locator("#idxCol").fill(collection);
    await page.locator("#idxField").fill("");
    await page.locator("#idxCreate").click();
    await expect(page.getByRole("alert").filter({ hasText: "Collection and field required" }))
      .toBeVisible();
    expect((await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes).toEqual({});
  });

  test("a whitespace-only index field is rejected", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed` });
    await page.locator('#nav [data-panel="indexes"]').click();
    await page.locator("#idxCol").fill(collection);
    await page.locator("#idxField").fill("   ");
    await page.locator("#idxCreate").click();
    await expect(page.getByRole("alert").filter({ hasText: "Collection and field required" }))
      .toBeVisible();
    expect((await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes).toEqual({});
  });

  test("an empty collection name is rejected before creating an index", async ({ page }) => {
    await page.locator('#nav [data-panel="indexes"]').click();
    await page.locator("#idxCol").fill("");
    await page.locator("#idxField").fill("category");
    await page.locator("#idxCreate").click();
    await expect(page.getByRole("alert").filter({ hasText: "Collection and field required" }))
      .toBeVisible();
  });

  test("listing a missing collection reports an error without creating it", async ({ page, collection }) => {
    await page.locator('#nav [data-panel="indexes"]').click();
    await page.locator("#idxCol").fill(collection);
    await page.locator("#idxList").click();
    await expect(page.getByRole("alert").filter({ hasText: "Collection not found" })).toBeVisible();
    expect((await api(page, `/indexes/${encodeURIComponent(collection)}`)).status).toBe(404);
    const collections = (await api(page, "/collections")).data.collections;
    expect(collections.map((entry: { name: string }) => entry.name)).not.toContain(collection);
  });

  test("creating an index leaves existing collection documents intact", async ({ page, collection }) => {
    const document = { id: `${collection}-preserve`, category: "hardware", marker: "keep" };
    await insertDocument(page, collection, document);
    await createIndex(page, collection, "category");
    expect((await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
    )).data.fields).toEqual({ category: "hardware", marker: "keep" });
  });

  test("refreshing the index list retains the created index", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, owner: "ops" });
    await createIndex(page, collection, "owner");
    expect((await listIndexes(page, collection)).status()).toBe(200);
    await expect(page.locator("#idxOut")).toContainText("owner");
  });

  test("recreating an index on the same field leaves one listed field", async ({ page, collection }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, code: "A-1" });
    await createIndex(page, collection, "code");
    await createIndex(page, collection, "code");
    const indexes = (await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes;
    expect(Object.keys(indexes)).toEqual(["code"]);
  });

  test("indexes created on one collection are not listed for another", async ({
    page,
    collection,
    otherCollection,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-seed`, category: "hardware" });
    await insertDocument(page, otherCollection, { id: `${otherCollection}-seed`, category: "books" });
    await createIndex(page, collection, "category");
    expect((await listIndexes(page, otherCollection)).status()).toBe(200);
    await expect(page.locator("#idxOut")).toContainText("No indexes on this collection.");
    expect((await api(page, `/indexes/${encodeURIComponent(otherCollection)}`)).data.indexes)
      .toEqual({});
  });

  test("an index field with spaces is registered and displayed intact", async ({ page, collection }) => {
    const field = "display name";
    await insertDocument(page, collection, { id: `${collection}-seed`, [field]: "Ada" });
    await createIndex(page, collection, field);
    await expect(page.locator("#idxOut")).toContainText(field);
    expect(Object.keys((await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes))
      .toContain(field);
  });

  test("an index remains listed after the collection receives a second document", async ({
    page,
    collection,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-one`, category: "hardware" });
    await createIndex(page, collection, "category");
    await insertDocument(page, collection, { id: `${collection}-two`, category: "books" });
    await listIndexes(page, collection);
    const indexes = (await api(page, `/indexes/${encodeURIComponent(collection)}`)).data.indexes;
    expect(Object.keys(indexes)).toContain("category");
    const documents = (await api(page, `/collections/${encodeURIComponent(collection)}`)).data;
    expect(documents.map((document: { id: string }) => document.id)).toEqual(
      expect.arrayContaining([`${collection}-one`, `${collection}-two`]),
    );
    await expect(page.locator("#idxOut")).toContainText("category");
  });
});
