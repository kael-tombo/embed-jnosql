import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type KvNames = {
  scalarBucket: string;
  scalarKey: string;
  encodedScalarBucket: string;
  encodedScalarKey: string;
  prefixedScalarKey: string;
  listBucket: string;
  listKey: string;
  encodedListKey: string;
  setBucket: string;
  setKey: string;
  hashBucket: string;
  hashKey: string;
};

type TestFixtures = {
  kvNames: KvNames;
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
  kvNames: async ({ page }, use) => {
    const names: KvNames = {
      scalarBucket: uniqueName("e2e_kv_scalar"),
      scalarKey: uniqueName("value"),
      encodedScalarBucket: `${uniqueName("e2e_kv_encoded")} 雪`,
      encodedScalarKey: "key 雪?&",
      prefixedScalarKey: `prefix_${uniqueName("value")}`,
      listBucket: uniqueName("e2e_kv_list"),
      listKey: uniqueName("queue"),
      encodedListKey: "key 雪?",
      setBucket: uniqueName("e2e_kv_set"),
      setKey: uniqueName("members"),
      hashBucket: uniqueName("e2e_kv_hash"),
      hashKey: uniqueName("record"),
    };

    await use(names);

    const deletions = [
      `/kv-meta/${segment(names.scalarBucket)}/${segment(names.scalarKey)}`,
      `/kv-meta/${segment(names.encodedScalarBucket)}/${segment(names.scalarKey)}`,
      `/kv-meta/${segment(names.scalarBucket)}/${segment(names.encodedScalarKey)}`,
      `/kv-meta/${segment(names.scalarBucket)}/${segment(names.prefixedScalarKey)}`,
      `/kv/lists/${segment(names.listBucket)}/${segment(names.listKey)}`,
      `/kv/lists/${segment(names.listBucket)}/${segment(names.encodedListKey)}`,
      `/kv/sets/${segment(names.setBucket)}/${segment(names.setKey)}`,
      `/kv/hashes/${segment(names.hashBucket)}/${segment(names.hashKey)}`,
    ];
    for (const path of deletions) {
      const result = await api(page, path, "DELETE");
      expect([204, 404]).toContain(result.status);
    }
  },
});

async function openKvTab(page: Page, tab: "KV browser" | "Lists" | "Sets" | "Hashes") {
  await page.locator('#nav [data-panel="kv"]').click();
  await page.getByRole("tab", { name: tab }).click();
}

async function browseBucket(page: Page, bucket: string) {
  await openKvTab(page, "KV browser");
  await page.locator("#kvBucket").fill(bucket);
  const response = waitForApi(page, `/kv-meta/${segment(bucket)}`, "GET");
  await page.locator("#kvBrowse").click();
  expect((await response).status()).toBe(200);
}

async function saveScalar(
  page: Page,
  bucket: string,
  key: string,
  value: string,
  ttl?: string,
) {
  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(key);
  await page.locator("#kvNewValue").fill(value);
  if (ttl !== undefined) await page.locator("#kvNewTtl").fill(ttl);
  const response = waitForApi(
    page,
    `/kv-meta/${segment(bucket)}/${segment(key)}`,
    "PUT",
  );
  await page.locator("#kvNewSave").click();
  return response;
}

async function fillStructureInputs(
  page: Page,
  kind: "list" | "set" | "hash",
  bucket: string,
  key: string,
) {
  const ids = kind === "list"
    ? ["#lsBucket", "#lsKey"]
    : kind === "set"
      ? ["#stBucket", "#stKey"]
      : ["#hsBucket", "#hsKey"];
  await page.locator(ids[0]).fill(bucket);
  await page.locator(ids[1]).fill(key);
}

async function clickStructureAction(
  page: Page,
  path: string,
  method: string,
  selector: string,
  status = 200,
) {
  const response = waitForApi(page, path, method);
  await page.locator(selector).click();
  expect((await response).status()).toBe(status);
}

test("round-trips scalar keys with reserved and non-ASCII characters", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  const saved = await saveScalar(page, kvNames.scalarBucket, kvNames.encodedScalarKey, "encoded key");
  expect(saved.status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.encodedScalarKey}`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.encodedScalarKey)}`,
  )).data.value).toBe("encoded key");
});

test("creates a scalar string without a TTL", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "plain text")).status())
    .toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data).toMatchObject({ key: kvNames.scalarKey, value: "plain text", hasTtl: false });
});

test("stores an empty scalar value as a present key", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "")).status()).toBe(201);
  const stored = await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  );
  expect(stored.status).toBe(200);
  expect(stored.data).toMatchObject({ key: kvNames.scalarKey, value: "", hasTtl: false });
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
});

test("preserves leading and trailing spaces in a scalar value", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "  padded value  "))
    .status()).toBe(201);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data.value).toBe("  padded value  ");
});

test("stores non-ASCII scalar values", async ({ page, kvNames }) => {
  const value = "こんにちは 🌌 café";
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, value)).status())
    .toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data.value).toBe(value);
});

test("replaces an existing scalar key through a second save", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "first")).status())
    .toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  await expect(page.locator("#kvBrowser")).toContainText("first");
  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(kvNames.scalarKey);
  await page.locator("#kvNewValue").fill("replacement");
  const update = waitForApi(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
    "PUT",
  );
  await page.locator("#kvNewSave").click();
  expect((await update).status()).toBe(201);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data.value).toBe("replacement");
});

test("rejects an empty scalar key name without sending a write", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill("   ");
  await page.locator("#kvNewValue").fill("not saved");
  const writes: string[] = [];
  const onRequest = (request: import("@playwright/test").Request) => {
    if (request.method() === "PUT" && request.url().includes("/api/kv-meta/")) writes.push(request.url());
  };
  page.on("request", onRequest);
  await page.locator("#kvNewSave").click();
  await expect(page.locator("#kvNewStatus")).toHaveText("key name required");
  expect(writes).toHaveLength(0);
  page.off("request", onRequest);
  expect((await api(page, `/kv-meta/${segment(kvNames.scalarBucket)}`)).data.count).toBe(0);
});

test("round-trips scalar values in a bucket with reserved and non-ASCII characters", async ({
  page,
  kvNames,
}) => {
  await browseBucket(page, kvNames.encodedScalarBucket);
  const saved = await saveScalar(page, kvNames.encodedScalarBucket, kvNames.scalarKey, "encoded bucket");
  expect(saved.status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.encodedScalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data.value).toBe("encoded bucket");
});

test("rejects a negative TTL without changing an existing scalar value", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "safe")).status())
    .toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  await expect(page.locator("#kvBrowser")).toContainText("safe");
  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(kvNames.scalarKey);
  await page.locator("#kvNewValue").fill("must not replace");
  await page.locator("#kvNewTtl").fill("-2");
  const response = waitForApi(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
    "PUT",
  );
  await page.locator("#kvNewSave").click();
  expect((await response).status()).toBe(400);
  await expect(page.locator("#kvNewStatus")).toContainText("ttlSeconds must be positive");
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data.value).toBe("safe");
});

test("replacing a scalar with a longer TTL extends its expiry", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "temporary", "30"))
    .status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  const original = await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  );
  expect(original.status).toBe(200);
  expect(original.data.hasTtl).toBe(true);

  const updated = await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "extended", "120");
  expect((await updated).status()).toBe(201);
  const extended = await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  );
  expect(extended.data.value).toBe("extended");
  expect(extended.data.expiresAt).toBeGreaterThan(original.data.expiresAt);
});

test("shows an active TTL key in the scalar browser", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "alive", "120"))
    .status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  await expect(page.locator("#kvBrowser")).toContainText(kvNames.scalarKey);
  await expect(page.locator("#kvBrowser")).toContainText("alive");
  await expect(page.locator("#kvBrowser")).toContainText("active");
  expect((await api(page, `/kv-meta/${segment(kvNames.scalarBucket)}`)).data.keys[0].hasTtl)
    .toBe(true);
});

test("filters scalar keys to a matching prefix", async ({ page, kvNames }) => {
  const key = kvNames.prefixedScalarKey;
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, key, "filtered")).status()).toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${key}`);
  await expect(page.locator("#kvBrowser")).toContainText(key);
  await page.locator("#kvPrefix").fill("prefix_");
  const response = waitForApi(page, `/kv-meta/${segment(kvNames.scalarBucket)}`, "GET");
  await page.locator("#kvPrefixGo").click();
  expect((await response).status()).toBe(200);
  await expect(page.locator("#kvBrowser")).toContainText(key);
  await expect(page.locator("#kvBrowseCount")).toContainText("1 key");
});

test("reports an empty result when a scalar prefix matches no key", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  await page.locator("#kvPrefix").fill("absent_prefix_");
  const response = waitForApi(page, `/kv-meta/${segment(kvNames.scalarBucket)}`, "GET");
  await page.locator("#kvPrefixGo").click();
  expect((await response).status()).toBe(200);
  await expect(page.locator("#kvBrowser"))
    .toContainText(`No key in "${kvNames.scalarBucket}" starts with "absent_prefix_".`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}?prefix=absent_prefix_`,
  )).data).toMatchObject({ count: 0, keys: [] });
});

test("returns from the new-key form to the empty bucket when cancelled", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  await page.locator("#kvNewKey").click();
  await page.locator("#kvNewName").fill(kvNames.scalarKey);
  await page.locator("#kvNewValue").fill("discarded");
  await page.locator("#kvNewCancel").click();
  await expect(page.locator("#kvBrowser"))
    .toContainText(`Bucket "${kvNames.scalarBucket}" is empty.`);
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).status).toBe(404);
});

test("repeats a scalar browser read without changing the stored value", async ({ page, kvNames }) => {
  await browseBucket(page, kvNames.scalarBucket);
  expect((await saveScalar(page, kvNames.scalarBucket, kvNames.scalarKey, "stable")).status())
    .toBe(201);
  await expect(page.locator("#kvNewStatus")).toHaveText(`saved ${kvNames.scalarKey}`);
  await expect(page.locator("#kvBrowser")).toContainText("stable");
  const first = await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  );
  const response = waitForApi(page, `/kv-meta/${segment(kvNames.scalarBucket)}`, "GET");
  await page.locator("#kvBrowse").click();
  expect((await response).status()).toBe(200);
  await expect(page.locator("#kvBrowser")).toContainText("stable");
  expect((await api(
    page,
    `/kv-meta/${segment(kvNames.scalarBucket)}/${segment(kvNames.scalarKey)}`,
  )).data).toEqual(first.data);
});

test("round-trips list values using a key with reserved and non-ASCII characters", async ({
  page,
  kvNames,
}) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.encodedListKey);
  await page.locator("#lsVals").fill("first,second");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.encodedListKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.encodedListKey)}/lrange`,
    "GET",
    "#lsRange",
  );
  await expect(page.locator("#kvOut")).toContainText("first");
  await expect(page.locator("#kvOut")).toContainText("second");
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.encodedListKey)}`,
  )).data.values).toEqual(["first", "second"]);
});

test("blocks a list read when the bucket is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", "", kvNames.listKey);
  await page.locator("#lsRange").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual([]);
});

test("blocks a list read when the key is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, "");
  await page.locator("#lsRange").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(page, `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`))
    .data.values).toEqual([]);
});

test("preserves a long value in a list without truncation", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  const longValue = "list-value-".repeat(128);
  await page.locator("#lsVals").fill(longValue);
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  const stored = await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  );
  expect(stored.data.values).toEqual([longValue]);
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/lrange`,
    "GET",
    "#lsRange",
  );
  await expect(page.locator("#kvOut")).toContainText(longValue);
});

test("repeated list pushes append rather than replace prior values", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await page.locator("#lsVals").fill("first");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  await page.locator("#lsVals").fill("second,third");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual(["first", "second", "third"]);
});

test("trims list items and ignores empty comma-separated entries", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await page.locator("#lsVals").fill(" one, , two ,, ");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual(["one", "two"]);
});

test("pops the final list value and leaves earlier values intact", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await page.locator("#lsVals").fill("left,middle,right");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpop`,
    "POST",
    "#lsPop",
  );
  await expect(page.locator("#kvOut")).toContainText('"value": "right"');
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual(["left", "middle"]);
});

test("popping a one-item list leaves an empty list", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await page.locator("#lsVals").fill("only");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpop`,
    "POST",
    "#lsPop",
  );
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual([]);
  await page.locator("#lsRange").click();
  await expect(page.locator("#kvOut")).toContainText('"values": []');
});

test("popping an empty list reports no value and preserves the empty state", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpop`,
    "POST",
    "#lsPop",
  );
  await expect(page.locator("#kvOut")).toContainText('"value": null');
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual([]);
});

test("repeats a list read without mutating its values", async ({ page, kvNames }) => {
  await openKvTab(page, "Lists");
  await fillStructureInputs(page, "list", kvNames.listBucket, kvNames.listKey);
  await page.locator("#lsVals").fill("repeat,read");
  await clickStructureAction(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/rpush`,
    "POST",
    "#lsPush",
  );
  const rangePath = `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}/lrange`;
  await clickStructureAction(page, rangePath, "GET", "#lsRange");
  await expect(page.locator("#kvOut")).toContainText('"values": [');
  await clickStructureAction(page, rangePath, "GET", "#lsRange");
  expect((await api(
    page,
    `/kv/lists/${segment(kvNames.listBucket)}/${segment(kvNames.listKey)}`,
  )).data.values).toEqual(["repeat", "read"]);
});

test("preserves non-ASCII set members in the member list", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  const members = ["café", "雪", "🌌"];
  await page.locator("#stVals").fill(members.join(","));
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/smembers`,
    "GET",
    "#stMembers",
  );
  await expect(page.locator("#kvOut")).toContainText("café");
  await expect(page.locator("#kvOut")).toContainText("雪");
  await expect(page.locator("#kvOut")).toContainText("🌌");
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data).toMatchObject({ members: expect.arrayContaining(members), cardinality: 3 });
});

test("blocks a set add when the bucket is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", "", kvNames.setKey);
  await page.locator("#stVals").fill("not-written");
  await page.locator("#stAdd").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members).toEqual([]);
});

test("blocks a set add when the key is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, "");
  await page.locator("#stVals").fill("not-written");
  await page.locator("#stAdd").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members).toEqual([]);
});

test("removes several selected set members while preserving the others", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill("keep,remove-a,also-keep,remove-b");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  await page.locator("#stVals").fill("remove-a,remove-b");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/srem`,
    "POST",
    "#stRem",
  );
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members).toEqual(expect.arrayContaining(["keep", "also-keep"]));
  const remaining = (await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members;
  expect(remaining).not.toContain("remove-a");
  expect(remaining).not.toContain("remove-b");
});

test("deduplicates repeated members in one set addition", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill("same,same,other");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  const stored = await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  );
  expect(stored.data.cardinality).toBe(2);
  expect(stored.data.members).toEqual(expect.arrayContaining(["same", "other"]));
});

test("repeatedly adding an existing set member does not increase cardinality", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill("existing");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  await page.locator("#stVals").fill("existing");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data).toMatchObject({ cardinality: 1, members: ["existing"] });
});

test("removes only the selected set member", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill("keep,remove,also-keep");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  await page.locator("#stVals").fill("remove");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/srem`,
    "POST",
    "#stRem",
  );
  const stored = await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  );
  expect(stored.data.members).toEqual(expect.arrayContaining(["keep", "also-keep"]));
  expect(stored.data.members).not.toContain("remove");
});

test("removing a missing set member leaves existing members unchanged", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill("present");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  await page.locator("#stVals").fill("absent");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/srem`,
    "POST",
    "#stRem",
  );
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members).toEqual(["present"]);
});

test("trims set members and ignores empty comma-separated entries", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill(" one, , two ,, ");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.members).toEqual(expect.arrayContaining(["one", "two"]));
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data.cardinality).toBe(2);
});

test("adding an empty set input leaves the set empty", async ({ page, kvNames }) => {
  await openKvTab(page, "Sets");
  await fillStructureInputs(page, "set", kvNames.setBucket, kvNames.setKey);
  await page.locator("#stVals").fill(" , , ");
  await clickStructureAction(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}/sadd`,
    "POST",
    "#stAdd",
  );
  expect((await api(
    page,
    `/kv/sets/${segment(kvNames.setBucket)}/${segment(kvNames.setKey)}`,
  )).data).toMatchObject({ members: [], cardinality: 0 });
  await page.locator("#stMembers").click();
  await expect(page.locator("#kvOut")).toContainText('"members": []');
});

test("reads a hash field containing non-ASCII characters", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  const field = "profile雪";
  await page.locator("#hsField").fill(field);
  await page.locator("#hsVal").fill("Dublin 🏰");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  const get = waitForApi(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hget`,
    "GET",
  );
  await page.locator("#hsGet").click();
  expect((await get).status()).toBe(200);
  await expect(page.locator("#kvOut")).toContainText("Dublin 🏰");
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ [field]: "Dublin 🏰" });
});

test("blocks a hash write when the bucket is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", "", kvNames.hashKey);
  await page.locator("#hsField").fill("name");
  await page.locator("#hsVal").fill("Ada");
  await page.locator("#hsSet").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({});
});

test("blocks a hash write when the key is blank", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, "");
  await page.locator("#hsField").fill("name");
  await page.locator("#hsVal").fill("Ada");
  await page.locator("#hsSet").click();
  await expect(page.getByRole("alert").filter({ hasText: "Bucket and Key required" }))
    .toBeVisible();
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({});
});

test("rejects a blank hash field name without changing hash state", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("   ");
  await page.locator("#hsVal").fill("not saved");
  await page.locator("#hsSet").click();
  await expect(page.getByRole("alert").filter({ hasText: "Field required" })).toBeVisible();
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({});
});

test("overwriting one hash field preserves unrelated fields", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("retained");
  await page.locator("#hsVal").fill("unchanged");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsField").fill("status");
  await page.locator("#hsVal").fill("draft");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsVal").fill("published");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsAll").click();
  await expect(page.locator("#kvOut")).toContainText("unchanged");
  await expect(page.locator("#kvOut")).toContainText("published");
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ retained: "unchanged", status: "published" });
});

test("stores an empty hash field value", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("emptyValue");
  await page.locator("#hsVal").fill("");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toMatchObject({ emptyValue: "" });
});

test("overwrites an existing hash field on a repeated write", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("status");
  await page.locator("#hsVal").fill("draft");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsVal").fill("published");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsGet").click();
  await expect(page.locator("#kvOut")).toContainText("published");
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ status: "published" });
});

test("keeps separately written hash fields together", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("first");
  await page.locator("#hsVal").fill("one");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsField").fill("second");
  await page.locator("#hsVal").fill("two");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsAll").click();
  await expect(page.locator("#kvOut")).toContainText("first");
  await expect(page.locator("#kvOut")).toContainText("second");
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ first: "one", second: "two" });
});

test("reads an empty hash field value through the visible controls", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("emptyValue");
  await page.locator("#hsVal").fill("");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsGet").click();
  await expect(page.locator("#kvOut")).toContainText('"value": ""');
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ emptyValue: "" });
});

test("repeats a hash read without changing stored fields", async ({ page, kvNames }) => {
  await openKvTab(page, "Hashes");
  await fillStructureInputs(page, "hash", kvNames.hashBucket, kvNames.hashKey);
  await page.locator("#hsField").fill("repeatable");
  await page.locator("#hsVal").fill("value");
  await clickStructureAction(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}/hset`,
    "POST",
    "#hsSet",
  );
  await page.locator("#hsGet").click();
  await expect(page.locator("#kvOut")).toContainText("value");
  await page.locator("#hsGet").click();
  await expect(page.locator("#kvOut")).toContainText("value");
  expect((await api(
    page,
    `/kv/hashes/${segment(kvNames.hashBucket)}/${segment(kvNames.hashKey)}`,
  )).data.fields).toEqual({ repeatable: "value" });
});
