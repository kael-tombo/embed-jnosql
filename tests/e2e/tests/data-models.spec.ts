import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type DataNames = {
  kvBucket: string;
  kvKey: string;
  invalidKvKey: string;
  listBucket: string;
  listKey: string;
  setBucket: string;
  setKey: string;
  hashBucket: string;
  hashKey: string;
  columnFamily: string;
  columnRow: string;
  vectorIndex: string;
  vectorId: string;
  wrongDimensionId: string;
};

type TestFixtures = {
  dataNames: DataNames;
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

function waitForApi(
  page: Page,
  path: string,
  method: string,
): Promise<import("@playwright/test").Response> {
  const [expectedPath, expectedQuery] = path.split("?", 2);
  return page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api${expectedPath}`
      && (expectedQuery === undefined || new URL(response.url()).search === `?${expectedQuery}`)
      && response.request().method() === method,
  );
}

const test = base.extend<TestFixtures>({
  dataNames: async ({ page }, use) => {
    const names: DataNames = {
      kvBucket: uniqueName("e2e_kv"),
      kvKey: uniqueName("kept"),
      invalidKvKey: uniqueName("invalid"),
      listBucket: uniqueName("e2e_lists"),
      listKey: uniqueName("queue"),
      setBucket: uniqueName("e2e_sets"),
      setKey: uniqueName("members"),
      hashBucket: uniqueName("e2e_hashes"),
      hashKey: uniqueName("profile"),
      columnFamily: uniqueName("e2e_columns"),
      columnRow: uniqueName("row"),
      vectorIndex: uniqueName("e2e_vectors"),
      vectorId: uniqueName("stored"),
      wrongDimensionId: uniqueName("wrong_dims"),
    };

    await use(names);

    const deletions = [
      `/kv-meta/${encodeURIComponent(names.kvBucket)}/${encodeURIComponent(names.kvKey)}`,
      `/kv-meta/${encodeURIComponent(names.kvBucket)}/${encodeURIComponent(names.invalidKvKey)}`,
      `/kv/lists/${encodeURIComponent(names.listBucket)}/${encodeURIComponent(names.listKey)}`,
      `/kv/sets/${encodeURIComponent(names.setBucket)}/${encodeURIComponent(names.setKey)}`,
      `/kv/hashes/${encodeURIComponent(names.hashBucket)}/${encodeURIComponent(names.hashKey)}`,
      `/columns/${encodeURIComponent(names.columnFamily)}/${encodeURIComponent(names.columnRow)}`,
      `/vectors/${encodeURIComponent(names.vectorIndex)}/${encodeURIComponent(names.vectorId)}`,
      `/vectors/${encodeURIComponent(names.vectorIndex)}/${encodeURIComponent(names.wrongDimensionId)}`,
    ];

    for (const path of deletions) {
      const result = await api(page, path, "DELETE");
      expect([204, 404]).toContain(result.status);
    }
  },
});

test("browses an isolated KV bucket, writes a TTL key, and reports invalid TTL without changing state", async ({
  page,
  dataNames,
}) => {
  await page.locator('#nav [data-panel="kv"]').click();
  await page.locator("#kvBucket").fill(dataNames.kvBucket);

  const initialList = waitForApi(page, `/kv-meta/${dataNames.kvBucket}`, "GET");
  await page.locator("#kvBrowse").click();
  expect((await initialList).status()).toBe(200);
  await expect(page.locator("#kvBrowser")).toContainText(`Bucket "${dataNames.kvBucket}" is empty.`);
  expect((await api(page, `/kv-meta/${dataNames.kvBucket}`)).data).toMatchObject({
    bucket: dataNames.kvBucket,
    count: 0,
    keys: [],
  });
  expect((await api(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.kvKey}`,
  )).status).toBe(404);

  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(dataNames.kvKey);
  await page.locator("#kvNewValue").fill("engine-backed value");
  await page.locator("#kvNewTtl").fill("90");
  const saveResponse = waitForApi(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.kvKey}`,
    "PUT",
  );
  await page.locator("#kvNewSave").click();
  expect((await saveResponse).status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${dataNames.kvKey}`);

  const stored = await api(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.kvKey}`,
  );
  expect(stored.status).toBe(200);
  expect(stored.data).toMatchObject({
    key: dataNames.kvKey,
    value: "engine-backed value",
    hasTtl: true,
  });
  expect(stored.data.expiresAt).toBeGreaterThan(Date.now());
  await expect(page.locator("#kvBrowser")).toContainText("engine-backed value");
  await expect(page.locator("#kvBrowser")).toContainText(dataNames.kvKey);

  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(dataNames.invalidKvKey);
  await page.locator("#kvNewValue").fill("must not be saved");
  await page.locator("#kvNewTtl").fill("0");
  const invalidSave = waitForApi(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.invalidKvKey}`,
    "PUT",
  );
  await page.locator("#kvNewSave").click();
  expect((await invalidSave).status()).toBe(400);
  await expect(page.locator("#kvNewStatus")).toContainText("ttlSeconds must be positive");
  expect((await api(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.invalidKvKey}`,
  )).status).toBe(404);
  expect((await api(page, `/kv-meta/${dataNames.kvBucket}`)).data.count).toBe(1);

  await page.locator("#kvNewCancel").click();
  await page.locator("#kvPrefix").fill(uniqueName("no_match"));
  const emptyPrefix = waitForApi(page, `/kv-meta/${dataNames.kvBucket}`, "GET");
  await page.locator("#kvPrefixGo").click();
  expect((await emptyPrefix).status()).toBe(200);
  await expect(page.locator("#kvBrowser")).toContainText("starts with");
  expect((await api(
    page,
    `/kv-meta/${dataNames.kvBucket}?prefix=${encodeURIComponent(await page.locator("#kvPrefix").inputValue())}`,
  )).data).toMatchObject({ count: 0, keys: [] });

  await page.locator("#kvPrefix").fill("");
  const refreshAfterPrefix = waitForApi(page, `/kv-meta/${dataNames.kvBucket}`, "GET");
  await page.locator("#kvPrefixGo").click();
  expect((await refreshAfterPrefix).status()).toBe(200);
  await page.getByRole("button", { name: `Delete key ${dataNames.kvKey}` }).click();
  const deleteDialog = page.getByRole("dialog", { name: "Delete key" });
  await expect(deleteDialog).toBeVisible();
  const deleteResponse = waitForApi(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.kvKey}`,
    "DELETE",
  );
  await deleteDialog.getByRole("button", { name: "Delete key" }).click();
  expect((await deleteResponse).status()).toBe(204);
  expect((await api(
    page,
    `/kv-meta/${dataNames.kvBucket}/${dataNames.kvKey}`,
  )).status).toBe(404);
  await expect(page.locator("#kvBrowser")).not.toContainText(dataNames.kvKey);
});

test("pushes, ranges, and pops a list while distinguishing an empty list from a missing-input error", async ({
  page,
  dataNames,
}) => {
  await page.locator('#nav [data-panel="kv"]').click();
  await page.getByRole("tab", { name: "Lists" }).click();
  await page.locator("#lsBucket").fill(dataNames.listBucket);
  await page.locator("#lsKey").fill(dataNames.listKey);

  const initialRange = waitForApi(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}/lrange`,
    "GET",
  );
  await page.locator("#lsRange").click();
  expect((await initialRange).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText('"values": []');
  expect((await api(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}`,
  )).data.values).toEqual([]);

  await page.locator("#lsBucket").fill("");
  const requests: string[] = [];
  const onRequest = (request: import("@playwright/test").Request) => {
    if (request.url().includes(`/api/kv/lists/${dataNames.listBucket}/${dataNames.listKey}`)) {
      requests.push(request.url());
    }
  };
  page.on("request", onRequest);
  await page.locator("#lsRange").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect(requests).toHaveLength(0);
  page.off("request", onRequest);
  await page.locator("#lsBucket").fill(dataNames.listBucket);

  await page.locator("#lsVals").fill("one,two,three");
  const pushed = waitForApi(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}/rpush`,
    "POST",
  );
  await page.locator("#lsPush").click();
  expect((await pushed).status()).toBe(200);

  const range = waitForApi(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}/lrange`,
    "GET",
  );
  await page.locator("#lsRange").click();
  expect((await range).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText('"values": [');
  const stored = await api(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}`,
  );
  expect(stored.status).toBe(200);
  expect(stored.data.values).toEqual(["one", "two", "three"]);

  const popped = waitForApi(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}/rpop`,
    "POST",
  );
  await page.locator("#lsPop").click();
  expect((await popped).status()).toBe(200);
  const afterPop = await api(
    page,
    `/kv/lists/${dataNames.listBucket}/${dataNames.listKey}`,
  );
  expect(afterPop.data.values).toEqual(["one", "two"]);
});

test("adds, reads, and removes set members and verifies the empty set state", async ({
  page,
  dataNames,
}) => {
  await page.locator('#nav [data-panel="kv"]').click();
  await page.getByRole("tab", { name: "Sets" }).click();
  await page.locator("#stBucket").fill(dataNames.setBucket);
  await page.locator("#stKey").fill(dataNames.setKey);

  const initialMembers = waitForApi(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/smembers`,
    "GET",
  );
  await page.locator("#stMembers").click();
  expect((await initialMembers).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText('"members": []');
  expect((await api(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}`,
  )).data.members).toEqual([]);

  await page.locator("#stVals").fill("red,green,blue");
  const add = waitForApi(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/sadd`,
    "POST",
  );
  await page.locator("#stAdd").click();
  expect((await add).status()).toBe(200);
  const listed = waitForApi(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/smembers`,
    "GET",
  );
  await page.locator("#stMembers").click();
  expect((await listed).status()).toBe(200);
  expect((await api(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}`,
  )).data.members).toEqual(expect.arrayContaining(["red", "green", "blue"]));

  const membership = await api(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/sismember?member=green`,
  );
  expect(membership.status).toBe(200);
  expect(membership.data).toMatchObject({
    operation: "sismember",
    member: "green",
    exists: true,
  });

  await page.locator("#stVals").fill("green");
  const remove = waitForApi(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/srem`,
    "POST",
  );
  await page.locator("#stRem").click();
  expect((await remove).status()).toBe(200);
  const afterRemove = await api(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}`,
  );
  expect(afterRemove.data.members).toEqual(expect.arrayContaining(["red", "blue"]));
  expect(afterRemove.data.members).not.toContain("green");
  const removedMembership = await api(
    page,
    `/kv/sets/${dataNames.setBucket}/${dataNames.setKey}/sismember?member=green`,
  );
  expect(removedMembership.data.exists).toBe(false);
});

test("writes and reads a hash field and keeps missing-field validation distinct from empty state", async ({
  page,
  dataNames,
}) => {
  await page.locator('#nav [data-panel="kv"]').click();
  await page.getByRole("tab", { name: "Hashes" }).click();
  await page.locator("#hsBucket").fill(dataNames.hashBucket);
  await page.locator("#hsKey").fill(dataNames.hashKey);

  const initialHash = waitForApi(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}/hgetall`,
    "GET",
  );
  await page.locator("#hsAll").click();
  expect((await initialHash).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText('"fields": {}');
  expect((await api(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}`,
  )).data.fields).toEqual({});

  const mutationRequests: string[] = [];
  const onRequest = (request: import("@playwright/test").Request) => {
    if (request.method() === "POST"
      && request.url().includes(`/api/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}/hset`)) {
      mutationRequests.push(request.url());
    }
  };
  page.on("request", onRequest);
  await page.locator("#hsField").fill("");
  await page.locator("#hsSet").click();
  await expect(page.getByRole("alert").filter({ hasText: "Field required" })).toBeVisible();
  expect(mutationRequests).toHaveLength(0);
  page.off("request", onRequest);

  await page.locator("#hsField").fill("displayName");
  await page.locator("#hsVal").fill("Ada Lovelace");
  const set = waitForApi(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}/hset`,
    "POST",
  );
  await page.locator("#hsSet").click();
  expect((await set).status()).toBe(200);
  const get = waitForApi(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}/hget?field=displayName`,
    "GET",
  );
  await page.locator("#hsGet").click();
  expect((await get).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText("Ada Lovelace");
  expect((await api(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}`,
  )).data.fields).toMatchObject({ displayName: "Ada Lovelace" });

  const deleteField = await api(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}/hdel`,
    "POST",
    { fields: ["displayName"] },
  );
  expect(deleteField.status).toBe(200);
  expect(deleteField.data.deleted).toBe(1);
  const afterDeleteField = await api(
    page,
    `/kv/hashes/${dataNames.hashBucket}/${dataNames.hashKey}`,
  );
  expect(afterDeleteField.data.fields).toEqual({});
});

test("writes a wide-column row, reads a range and TTL, and rejects invalid input without writing", async ({
  page,
  dataNames,
}) => {
  await page.locator('#nav [data-panel="columns"]').click();
  await page.locator("#cfFam").fill(dataNames.columnFamily);
  await page.locator("#cfRow").fill(dataNames.columnRow);

  const missingRow = await api(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}`,
  );
  expect(missingRow.status).toBe(404);
  const fetchMissing = waitForApi(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}`,
    "GET",
  );
  await page.locator("#cfGet").click();
  expect((await fetchMissing).status()).toBe(404);
  await expect(page.locator("#cfOut")).toContainText("Row not found");

  const writes: string[] = [];
  const onWrite = (request: import("@playwright/test").Request) => {
    if (["POST", "PUT"].includes(request.method())
      && request.url().includes(`/api/columns/${dataNames.columnFamily}/${dataNames.columnRow}`)) {
      writes.push(request.url());
    }
  };
  page.on("request", onWrite);
  await page.locator("#cfPutRow").fill(dataNames.columnRow);
  await page.locator("#cfPutCols").fill('{"column":');
  await page.locator("#cfPutGo").click();
  await expect(page.getByRole("alert").filter({ hasText: "Columns must be valid JSON" }))
    .toBeVisible();
  expect(writes).toHaveLength(0);
  await page.locator("#cfPutRow").fill("");
  await page.locator("#cfPutCols").fill('{"a":"1"}');
  await page.locator("#cfPutGo").click();
  await expect(page.getByRole("alert").filter({ hasText: "Family and Row required" }))
    .toBeVisible();
  expect(writes).toHaveLength(0);
  page.off("request", onWrite);

  await page.locator("#cfPutRow").fill(dataNames.columnRow);
  await page.locator("#cfPutCols").fill(JSON.stringify({
    alpha: "A",
    beta: "B",
    gamma: "C",
    expiring: { value: "short-lived", ttlSeconds: 90 },
  }));
  const write = waitForApi(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}`,
    "PUT",
  );
  await page.locator("#cfPutGo").click();
  expect((await write).status()).toBe(201);

  const stored = await api(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}`,
  );
  expect(stored.status).toBe(200);
  expect(stored.data.columns).toMatchObject({
    alpha: "A",
    beta: "B",
    gamma: "C",
    expiring: "short-lived",
  });
  await expect(page.locator("#cfOut")).toContainText("short-lived");

  const statsResponse = waitForApi(page, `/columns/${dataNames.columnFamily}/stats`, "GET");
  await page.locator("#cfStats").click();
  expect((await statsResponse).status()).toBe(200);
  const stats = await api(page, `/columns/${dataNames.columnFamily}/stats`);
  expect(stats.status).toBe(200);
  expect(stats.data).toMatchObject({ familyName: dataNames.columnFamily });
  expect(stats.data.rowCount).toBe(1);

  const range = await api(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}/get-range?limit=2&offset=1`,
  );
  expect(range.status).toBe(200);
  expect(range.data).toMatchObject({
    rowKey: dataNames.columnRow,
    limit: 2,
    offset: 1,
    columnsReturned: 2,
  });
  expect(Object.keys(range.data.data)).toHaveLength(2);
  const pastEnd = await api(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}/get-range?limit=2&offset=10`,
  );
  expect(pastEnd.status).toBe(200);
  expect(pastEnd.data).toMatchObject({ columnsReturned: 0, data: {} });

  const ttl = await api(
    page,
    `/columns/${dataNames.columnFamily}/${dataNames.columnRow}/ttl/expiring`,
  );
  expect(ttl.status).toBe(200);
  expect(ttl.data).toMatchObject({ rowKey: dataNames.columnRow, column: "expiring", hasTtl: true });
  expect(ttl.data.remainingTtlSeconds).toBeGreaterThan(0);
});

test.describe("Experimental vector flows (not a release-gate capability)", () => {
  test("reports a missing index, inserts and searches a vector, and rejects invalid and wrong-dimension vectors", async ({
    page,
    dataNames,
  }) => {
    await page.locator('#nav [data-panel="vectors"]').click();
    await expect(page.locator("#panel-vectors .experimental")).toContainText("Experimental");
    await page.locator("#vecIdx").fill(dataNames.vectorIndex);

    const missingInfo = waitForApi(page, `/vectors/${dataNames.vectorIndex}/_`, "GET");
    await page.locator("#vecInfo").click();
    expect((await missingInfo).status()).toBe(404);
    await expect(page.locator("#vecStatus")).toHaveText("0 vecs (empty)");

    await page.locator("#vecQuery").fill("[0.1,0.2,0.3]");
    const missingSearch = waitForApi(page, `/vectors/${dataNames.vectorIndex}/search`, "POST");
    await page.locator("#vecSearch").click();
    expect((await missingSearch).status()).toBe(404);
    await expect(page.locator("#vecOut")).toContainText("Vector index not found");
    expect((await api(page, `/vectors/${dataNames.vectorIndex}/_`)).status).toBe(404);

    const requests: string[] = [];
    const onInsert = (request: import("@playwright/test").Request) => {
      if (request.method() === "POST"
        && request.url().includes(`/api/vectors/${dataNames.vectorIndex}/${dataNames.vectorId}`)) {
        requests.push(request.url());
      }
    };
    page.on("request", onInsert);
    await page.locator("#vecAddId").fill(dataNames.vectorId);
    await page.locator("#vecAddData").fill("{invalid");
    await page.locator("#vecAddBtn").click();
    await expect(page.locator("#vecAddStatus")).toHaveText("invalid JSON array");
    await page.locator("#vecAddData").fill("[]");
    await page.locator("#vecAddBtn").click();
    await expect(page.locator("#vecAddStatus")).toHaveText("must be non-empty array");
    expect(requests).toHaveLength(0);
    page.off("request", onInsert);
    expect((await api(page, `/vectors/${dataNames.vectorIndex}/_`)).status).toBe(404);

    await page.locator("#vecAddData").fill("[0.1,0.2,0.3]");
    const insert = waitForApi(
      page,
      `/vectors/${dataNames.vectorIndex}/${dataNames.vectorId}`,
      "POST",
    );
    await page.locator("#vecAddBtn").click();
    expect((await insert).status()).toBe(201);
    await expect(page.locator("#vecAddStatus")).toHaveText("saved");

    const info = await api(page, `/vectors/${dataNames.vectorIndex}/_`);
    expect(info.status).toBe(200);
    expect(info.data).toMatchObject({ index: dataNames.vectorIndex, size: 1, dimensions: 3 });
    await expect(page.locator("#vecStatus")).toHaveText("1 vecs · 3d (experimental)");

    await page.locator("#vecQuery").fill("[0.1,0.2,0.3]");
    const search = waitForApi(page, `/vectors/${dataNames.vectorIndex}/search`, "POST");
    await page.locator("#vecSearch").click();
    expect((await search).status()).toBe(200);
    await expect(page.locator("#vecOut")).toContainText(dataNames.vectorId);
    const matching = await api(page, `/vectors/${dataNames.vectorIndex}/search`, "POST", {
      vector: [0.1, 0.2, 0.3],
      k: 5,
    });
    expect(matching.data.results.map((item: any) => typeof item === "string" ? item : item.id))
      .toContain(dataNames.vectorId);

    await page.locator("#vecQuery").fill("[0.1,0.2]");
    const wrongDimensionSearch = waitForApi(
      page,
      `/vectors/${dataNames.vectorIndex}/search`,
      "POST",
    );
    await page.locator("#vecSearch").click();
    expect((await wrongDimensionSearch).status()).toBe(400);
    await expect(page.locator("#vecOut")).toContainText("Search failed");

    await page.locator("#vecAddId").fill(dataNames.wrongDimensionId);
    await page.locator("#vecAddData").fill("[0.1,0.2]");
    const wrongDimensionInsert = waitForApi(
      page,
      `/vectors/${dataNames.vectorIndex}/${dataNames.wrongDimensionId}`,
      "POST",
    );
    await page.locator("#vecAddBtn").click();
    expect((await wrongDimensionInsert).status()).toBe(400);
    await expect(page.locator("#vecAddStatus")).toHaveText("failed");
    const afterRejection = await api(page, `/vectors/${dataNames.vectorIndex}/_`);
    expect(afterRejection.data).toMatchObject({ size: 1, dimensions: 3 });
    expect((await api(page, `/vectors/${dataNames.vectorIndex}/search`, "POST", {
      vector: [0.1, 0.2, 0.3],
      k: 5,
    })).data.results.map((item: any) => typeof item === "string" ? item : item.id))
      .not.toContain(dataNames.wrongDimensionId);
  });
});
