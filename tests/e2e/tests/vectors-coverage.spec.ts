import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type VectorNames = {
  index: string;
  firstId: string;
  secondId: string;
  thirdId: string;
  invalidId: string;
  unicodeId: string;
  createdIds: Set<string>;
};

type TestFixtures = {
  vectorNames: VectorNames;
  registerVector: (id: string) => void;
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
  vectorNames: async ({ page }, use) => {
    const names: VectorNames = {
      index: uniqueName("e2e_vectors"),
      firstId: uniqueName("first"),
      secondId: uniqueName("second"),
      thirdId: uniqueName("third"),
      invalidId: uniqueName("invalid"),
      unicodeId: `${uniqueName("document")} 雪`,
      createdIds: new Set<string>(),
    };

    await use(names);

    for (const id of names.createdIds) {
      const result = await api(
        page,
        `/vectors/${segment(names.index)}/${segment(id)}`,
        "DELETE",
      );
      expect(result.status).toBe(204);
    }
  },
  registerVector: async ({ vectorNames }, use) => {
    await use((id: string) => vectorNames.createdIds.add(id));
  },
});

async function openVectors(page: Page, index: string): Promise<void> {
  await page.locator('#nav [data-panel="vectors"]').click();
  await expect(page.locator("#panel-vectors .experimental")).toContainText("Experimental");
  await page.locator("#vecIdx").fill(index);
}

async function insertVector(
  page: Page,
  index: string,
  id: string,
  vector: unknown,
): Promise<import("@playwright/test").Response> {
  await page.locator("#vecAddId").fill(id);
  await page.locator("#vecAddData").fill(JSON.stringify(vector));
  const response = waitForApi(
    page,
    `/vectors/${segment(index)}/${segment(id)}`,
    "POST",
  );
  await page.locator("#vecAddBtn").click();
  return response;
}

async function infoThroughUi(
  page: Page,
  index: string,
): Promise<import("@playwright/test").Response> {
  const response = waitForApi(page, `/vectors/${segment(index)}/_`, "GET");
  await page.locator("#vecInfo").click();
  return response;
}

async function searchThroughUi(
  page: Page,
  index: string,
  vector: unknown,
): Promise<import("@playwright/test").Response> {
  await page.locator("#vecQuery").fill(JSON.stringify(vector));
  const response = waitForApi(page, `/vectors/${segment(index)}/search`, "POST");
  await page.locator("#vecSearch").click();
  return response;
}

test.describe("Experimental vector flows (not a release-gate capability)", () => {
  test("reports an empty state for a missing experimental vector index", async ({
    page,
    vectorNames,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await infoThroughUi(page, vectorNames.index)).status()).toBe(404);
    await expect(page.locator("#vecStatus")).toHaveText("0 vecs (empty)");
  });

  test("an experimental search does not create a missing index", async ({ page, vectorNames }) => {
    await openVectors(page, vectorNames.index);
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(404);
    await expect(page.locator("#vecOut")).toContainText("Vector index not found");
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });

  test("invalid experimental vector JSON is rejected without a request", async ({ page, vectorNames }) => {
    await openVectors(page, vectorNames.index);
    const requests: string[] = [];
    const onInsert = (request: import("@playwright/test").Request) => {
      if (request.method() === "POST" && request.url().includes("/api/vectors/")) {
        requests.push(request.url());
      }
    };
    page.on("request", onInsert);
    await page.locator("#vecAddId").fill(vectorNames.firstId);
    await page.locator("#vecAddData").fill("{invalid");
    await page.locator("#vecAddBtn").click();
    await expect(page.locator("#vecAddStatus")).toHaveText("invalid JSON array");
    expect(requests).toHaveLength(0);
    page.off("request", onInsert);
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });

  test("an empty experimental vector array is rejected locally", async ({ page, vectorNames }) => {
    await openVectors(page, vectorNames.index);
    await page.locator("#vecAddId").fill(vectorNames.firstId);
    await page.locator("#vecAddData").fill("[]");
    await page.locator("#vecAddBtn").click();
    await expect(page.locator("#vecAddStatus")).toHaveText("must be non-empty array");
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });

  test("an experimental insert requires an index name", async ({ page }) => {
    await openVectors(page, "");
    await page.locator("#vecAddId").fill("candidate");
    await page.locator("#vecAddData").fill("[1,0,0]");
    await page.locator("#vecAddBtn").click();
    await expect(page.getByRole("alert").filter({ hasText: "Index name required" })).toBeVisible();
  });

  test("an experimental insert requires a vector ID", async ({ page, vectorNames }) => {
    await openVectors(page, vectorNames.index);
    await page.locator("#vecAddData").fill("[1,0,0]");
    await page.locator("#vecAddBtn").click();
    await expect(page.getByRole("alert").filter({ hasText: "Vector ID required" })).toBeVisible();
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });

  test("an experimental insert requires vector data", async ({ page, vectorNames }) => {
    await openVectors(page, vectorNames.index);
    await page.locator("#vecAddId").fill(vectorNames.firstId);
    await page.locator("#vecAddData").fill("");
    await page.locator("#vecAddBtn").click();
    await expect(page.getByRole("alert").filter({ hasText: "Vector data required" })).toBeVisible();
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });

  test("inserting the first experimental vector shows its saved status and dimensions", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.firstId);
    await expect(page.locator("#vecAddStatus")).toHaveText("saved");
    await expect(page.locator("#vecStatus")).toHaveText("1 vecs · 3d (experimental)");
  });

  test("adding a second experimental vector increments index size", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    for (const [id, vector] of [
      [vectorNames.firstId, [1, 0, 0]],
      [vectorNames.secondId, [0, 1, 0]],
    ] as const) {
      expect((await insertVector(page, vectorNames.index, id, vector)).status()).toBe(201);
      registerVector(id);
    }
    await expect(page.locator("#vecStatus")).toHaveText("2 vecs · 3d (experimental)");
  });

  test("an exact experimental query shows its matching vector ID", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.firstId);
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(200);
    await expect(page.locator("#vecOut")).toContainText(vectorNames.firstId);
  });

  test("experimental nearest-neighbor results rank the closest vector first", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    for (const [id, vector] of [
      [vectorNames.firstId, [0, 1, 0]],
      [vectorNames.secondId, [0.99, 0.01, 0]],
    ] as const) {
      expect((await insertVector(page, vectorNames.index, id, vector)).status()).toBe(201);
      registerVector(id);
    }
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(200);
    const rows = page.locator("#vecOut tbody tr");
    await expect(rows.first()).toContainText(vectorNames.secondId);
  });

  test("experimental search with k one returns one result", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    for (const [id, vector] of [
      [vectorNames.firstId, [1, 0, 0]],
      [vectorNames.secondId, [0, 1, 0]],
    ] as const) {
      await insertVector(page, vectorNames.index, id, vector);
      registerVector(id);
    }
    await page.locator("#vecK").fill("1");
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(200);
    await expect(page.locator("#vecOut tbody tr")).toHaveCount(1);
    await expect(page.locator("#vecOut tbody tr").first()).toContainText(vectorNames.firstId);
  });

  test("experimental search with k greater than index size returns available results", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    for (const [id, vector] of [
      [vectorNames.firstId, [1, 0, 0]],
      [vectorNames.secondId, [0, 1, 0]],
    ] as const) {
      await insertVector(page, vectorNames.index, id, vector);
      registerVector(id);
    }
    await page.locator("#vecK").fill("10");
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(200);
    await expect(page.locator("#vecOut tbody tr")).toHaveCount(2);
  });

  test("repeated experimental searches do not change index size", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.firstId);
    await searchThroughUi(page, vectorNames.index, [1, 0, 0]);
    await searchThroughUi(page, vectorNames.index, [1, 0, 0]);
    await expect(page.locator("#vecStatus")).toHaveText("1 vecs · 3d (experimental)");
  });

  test("wrong-dimension experimental insertion is rejected without changing index size", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.firstId);
    expect((await insertVector(page, vectorNames.index, vectorNames.invalidId, [1, 0])).status())
      .toBe(400);
    await expect(page.locator("#vecAddStatus")).toHaveText("failed");
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).data)
      .toMatchObject({ size: 1, dimensions: 3 });
  });

  test("wrong-dimension experimental search reports failure and preserves the index", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0]);
    registerVector(vectorNames.firstId);
    expect((await searchThroughUi(page, vectorNames.index, [1, 0])).status()).toBe(400);
    await expect(page.locator("#vecOut")).toContainText("Search failed");
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).data.size).toBe(1);
  });

  test("non-numeric experimental vector elements are rejected without insertion", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.firstId);
    expect((await insertVector(page, vectorNames.index, vectorNames.invalidId, [1, "x", 0])).status())
      .toBe(400);
    await expect(page.locator("#vecAddStatus")).toHaveText("failed");
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).data.size).toBe(1);
  });

  test("an experimental search rejects a non-positive k value", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    await insertVector(page, vectorNames.index, vectorNames.firstId, [1, 0, 0]);
    registerVector(vectorNames.firstId);
    await page.locator("#vecK").fill("-1");
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(400);
    await expect(page.locator("#vecOut")).toContainText("k must be a positive integer");
  });

  test("an experimental vector ID with non-ASCII characters can be searched", async ({
    page,
    vectorNames,
    registerVector,
  }) => {
    await openVectors(page, vectorNames.index);
    expect((await insertVector(page, vectorNames.index, vectorNames.unicodeId, [1, 0, 0])).status())
      .toBe(201);
    registerVector(vectorNames.unicodeId);
    expect((await searchThroughUi(page, vectorNames.index, [1, 0, 0])).status()).toBe(200);
    await expect(page.locator("#vecOut")).toContainText(vectorNames.unicodeId);
  });

  test("an empty experimental search input is rejected before sending a request", async ({
    page,
    vectorNames,
  }) => {
    await openVectors(page, vectorNames.index);
    await page.locator("#vecQuery").fill("");
    await page.locator("#vecSearch").click();
    await expect(page.getByRole("alert").filter({ hasText: "Vector must be a JSON array" }))
      .toBeVisible();
    expect((await api(page, `/vectors/${segment(vectorNames.index)}/_`)).status).toBe(404);
  });
});
