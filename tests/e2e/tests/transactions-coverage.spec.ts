import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type TransactionFixtures = {
  startedTransactions: Set<number>;
};

async function api(
  page: Page,
  method = "GET",
  body?: unknown,
): Promise<ApiResult> {
  return page.evaluate(
    async ({ method, body }) => {
      const headers: Record<string, string> = {};
      const csrf = sessionStorage.getItem("csrfToken");
      const session = sessionStorage.getItem("session");
      if (body !== undefined) headers["Content-Type"] = "application/json";
      if (csrf) headers["X-CSRF-Token"] = csrf;
      if (session) headers.Authorization = `Bearer ${session}`;

      const response = await fetch("/api/transactions", {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      return { status: response.status, data: await response.json() };
    },
    { method, body },
  );
}

const test = base.extend<TransactionFixtures>({
  startedTransactions: async ({ page }, use) => {
    const ids = new Set<number>();
    await use(ids);

    const active = await api(page);
    for (const id of ids) {
      if (active.data.ids.includes(id)) {
        const result = await api(page, "POST", { action: "rollback", transactionId: id });
        expect([200, 404]).toContain(result.status);
      }
    }
  },
});

async function openTransactions(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="tx"]').click();
  await expect(page.getByRole("heading", { name: "Transactions" })).toBeVisible();
}

async function beginTransaction(
  page: Page,
  startedTransactions: Set<number>,
): Promise<number> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/transactions"
      && response.request().method() === "POST",
  );
  await page.locator("#txBegin").click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const result = await response.json();
  startedTransactions.add(result.transactionId);
  await expect(page.locator("#toasts")).toContainText(`Started tx #${result.transactionId}`);
  return result.transactionId;
}

async function finishTransaction(
  page: Page,
  id: number,
  action: "commit" | "rollback",
): Promise<void> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/transactions"
      && response.request().method() === "POST",
  );
  await page.locator(
    `#txTable [data-tx="${id}"][data-act="${action}"]`,
  ).click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  await expect(page.locator("#toasts")).toContainText(
    action === "commit" ? "Transaction committed" : "Transaction rolled back",
  );
}

async function expectActiveCount(page: Page, count: number): Promise<void> {
  await expect(page.locator("#txCount")).toHaveText(`${count} active`);
}

test.describe("Transactions UI coverage", () => {
  test("an empty transaction list reports zero active transactions", async ({ page }) => {
    await openTransactions(page);
    await expectActiveCount(page, 0);
    await expect(page.locator("#txTable")).toContainText("No data.");
  });

  test("the panel explains that it manages transaction lifecycle", async ({ page }) => {
    await openTransactions(page);
    await expect(page.getByText("this panel manages transaction", { exact: false }))
      .toBeVisible();
  });

  test("the scope notice explains console writes bypass panel transactions", async ({ page }) => {
    await openTransactions(page);
    await expect(page.getByText("not governed by a transaction begun here", { exact: false }))
      .toBeVisible();
  });

  test("beginning a transaction displays its started confirmation", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
  });

  test("beginning a transaction updates the active count", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await expectActiveCount(page, 1);
  });

  test("an active transaction displays its numeric handle", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable code")).toHaveText(String(id));
  });

  test("an active transaction displays its ACTIVE status", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable .badge.ok")).toHaveText("ACTIVE");
  });

  test("a new transaction displays zero operations", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable tbody tr td").nth(2)).toHaveText("0");
  });

  test("a new transaction displays its snapshot isolation level", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable tbody tr td").nth(3)).toHaveText("SNAPSHOT");
  });

  test("an active transaction displays its creation time", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable tbody tr td").nth(4)).not.toHaveText("—");
  });

  test("an active transaction offers a commit action", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await expect(page.locator(`#txTable [data-tx="${id}"][data-act="commit"]`))
      .toBeVisible();
  });

  test("an active transaction offers a rollback action", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await expect(page.locator(`#txTable [data-tx="${id}"][data-act="rollback"]`))
      .toBeVisible();
  });

  test("refreshing the panel keeps an active transaction listed", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await page.locator('#nav [data-panel="tx"]').click();
    await expect(page.locator("#txTable code")).toHaveText(String(id));
  });

  test("reloading the page keeps an active transaction listed", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await page.reload();
    await openTransactions(page);
    await expect(page.locator("#txTable code")).toHaveText(String(id));
  });

  test("committing a transaction displays a committed confirmation", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "commit");
  });

  test("committing a transaction removes it from the active count", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "commit");
    await expectActiveCount(page, 0);
  });

  test("committing a transaction removes its terminal action controls", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "commit");
    await expect(page.locator(`#txTable [data-tx="${id}"]`)).toHaveCount(0);
  });

  test("rolling back a transaction displays a rolled-back confirmation", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "rollback");
  });

  test("rolling back a transaction removes it from the active count", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "rollback");
    await expectActiveCount(page, 0);
  });

  test("rolling back a transaction removes its terminal action controls", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const id = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, id, "rollback");
    await expect(page.locator(`#txTable [data-tx="${id}"]`)).toHaveCount(0);
  });

  test("beginning two transactions updates the active count to two", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    await beginTransaction(page, startedTransactions);
    await beginTransaction(page, startedTransactions);
    await expectActiveCount(page, 2);
  });

  test("two active transactions have distinct visible handles", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const firstId = await beginTransaction(page, startedTransactions);
    const secondId = await beginTransaction(page, startedTransactions);
    expect(secondId).not.toBe(firstId);
    expect(await page.locator("#txTable code").allTextContents()).toEqual(
      expect.arrayContaining([String(firstId), String(secondId)]),
    );
  });

  test("each active transaction has its own action buttons", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const firstId = await beginTransaction(page, startedTransactions);
    const secondId = await beginTransaction(page, startedTransactions);
    await expect(page.locator(`#txTable [data-tx="${firstId}"]`)).toHaveCount(2);
    await expect(page.locator(`#txTable [data-tx="${secondId}"]`)).toHaveCount(2);
  });

  test("committing one transaction leaves the other active", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const firstId = await beginTransaction(page, startedTransactions);
    const secondId = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, firstId, "commit");
    await expectActiveCount(page, 1);
    await expect(page.locator("#txTable code")).toHaveText(String(secondId));
  });

  test("rolling back one transaction leaves the other active", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const firstId = await beginTransaction(page, startedTransactions);
    const secondId = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, firstId, "rollback");
    await expectActiveCount(page, 1);
    await expect(page.locator("#txTable code")).toHaveText(String(secondId));
  });

  test("three active transactions are counted and listed independently", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const ids = [
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
    ];
    await expectActiveCount(page, 3);
    expect(await page.locator("#txTable code").allTextContents()).toEqual(
      expect.arrayContaining(ids.map(String)),
    );
  });

  test("committing three active transactions clears the list", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const ids = [
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
    ];
    for (const id of ids) await finishTransaction(page, id, "commit");
    await expectActiveCount(page, 0);
  });

  test("rolling back three active transactions clears the list", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const ids = [
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
      await beginTransaction(page, startedTransactions),
    ];
    for (const id of ids) await finishTransaction(page, id, "rollback");
    await expectActiveCount(page, 0);
  });

  test("a new transaction can start after a commit", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const committedId = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, committedId, "commit");
    const nextId = await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable code")).toHaveText(String(nextId));
  });

  test("a new transaction can start after a rollback", async ({
    page,
    startedTransactions,
  }) => {
    await openTransactions(page);
    const rolledBackId = await beginTransaction(page, startedTransactions);
    await finishTransaction(page, rolledBackId, "rollback");
    const nextId = await beginTransaction(page, startedTransactions);
    await expect(page.locator("#txTable code")).toHaveText(String(nextId));
  });
});
