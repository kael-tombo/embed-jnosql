import { unlink } from "node:fs/promises";
import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type BackupFixtures = {
  collection: string;
  createdBackups: Set<string>;
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

function uniqueCollection(): string {
  return `e2e_backup_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

async function cleanupCollection(page: Page, collection: string): Promise<void> {
  const collectionPath = `/collections/${encodeURIComponent(collection)}`;
  const result = await api(page, collectionPath);
  if (result.status === 404) return;
  expect(result.status).toBe(200);
  for (const document of result.data) {
    if (typeof document?.id === "string") {
      const deleted = await api(
        page,
        `${collectionPath}/${encodeURIComponent(document.id)}`,
        "DELETE",
      );
      expect(deleted.status).toBe(204);
    }
  }
}

const test = base.extend<BackupFixtures>({
  collection: async ({ page }, use) => {
    const name = uniqueCollection();
    await use(name);
    await cleanupCollection(page, name);
  },
  createdBackups: async ({ page }, use) => {
    const files = new Set<string>();
    await use(files);
    for (const file of files) await unlink(file);
  },
});

async function openBackup(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="backup"]').click();
  await expect(page.locator("#panel-backup")).toBeVisible();
}

async function insertDocument(
  page: Page,
  collection: string,
  document: Record<string, unknown>,
): Promise<void> {
  await page.locator('#nav [data-panel="collections"]').click();
  await page.locator("#insCol").fill(collection);
  await page.locator("#insJson").fill(JSON.stringify(document, null, 2));
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "POST",
  );
  await page.locator("#insSubmit").click();
  const response = await responsePromise;
  expect(response.status()).toBe(201);
  await expect(page.locator("#insStatus")).toHaveText(`saved ${document.id}`);
}

async function createBackup(
  page: Page,
  createdBackups: Set<string>,
): Promise<ApiResult> {
  await openBackup(page);
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/backup"
      && response.request().method() === "POST",
  );
  await page.locator("#bkCreate").click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const result = await response.json();
  createdBackups.add(result.file);
  await expect(page.locator("#bkStatus")).toContainText("backup →");
  await expect.poll(async () => {
    const state = await api(page, "/backup");
    return state.data.backups.some((backup: { file: string }) => backup.file === result.file);
  }).toBe(true);
  return { status: response.status(), data: result };
}

async function backupCount(page: Page): Promise<number> {
  return (await api(page, "/backup")).data.backups.length;
}

async function expectBackupCount(page: Page, count: number): Promise<void> {
  await expect.poll(() => backupCount(page)).toBe(count);
  await expect(page.locator("#bkInfo")).toContainText(`${count} existing snapshot`);
}

async function restoreBackup(page: Page, file: string): Promise<import("@playwright/test").Response> {
  await openBackup(page);
  await page.locator("#bkFile").fill(file);
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/backup/restore"
      && response.request().method() === "POST",
  );
  await page.locator("#bkRestore").click();
  const dialog = page.getByRole("dialog", { name: "Restore from backup" });
  await expect(dialog).toBeVisible();
  await dialog.locator("#confirmAccept").click();
  return responsePromise;
}

test.describe("Backup UI coverage", () => {
  test("the Backup and Restore panel opens with its heading", async ({ page }) => {
    await openBackup(page);
    await expect(page.getByRole("heading", { name: "Backup & Restore" })).toBeVisible();
  });

  test("backup status displays the active storage engine", async ({ page }) => {
    await openBackup(page);
    await expect(page.locator("#bkInfo")).toContainText("Engine");
    await expect(page.locator("#bkInfo")).not.toContainText("—");
  });

  test("backup status displays the backup directory", async ({ page }) => {
    await openBackup(page);
    await expect(page.locator("#bkInfo")).toContainText("Backup directory:");
  });

  test("backup status reports the current snapshot count", async ({ page }) => {
    await openBackup(page);
    await expectBackupCount(page, await backupCount(page));
  });

  test("refreshing backup status retains the current snapshot summary", async ({ page }) => {
    await openBackup(page);
    const currentCount = await backupCount(page);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/backup"
        && response.request().method() === "GET",
    );
    await page.locator("#bkRefresh").click();
    expect((await responsePromise).status()).toBe(200);
    await expectBackupCount(page, currentCount);
  });

  test("creating a snapshot with test data reports its document count", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-summary`, label: "summary" });
    const result = await createBackup(page, createdBackups);
    expect(result.data.documents).toBeGreaterThanOrEqual(1);
    expect(result.data.collections[collection]).toBe(1);
    await expect(page.locator("#toasts"))
      .toContainText(`Backup created · ${result.data.documents} document`);
  });

  test("creating a populated snapshot reports its document count", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-one`, label: "snapshot" });
    const result = await createBackup(page, createdBackups);
    expect(result.data.collections[collection]).toBe(1);
    await expect(page.locator("#toasts"))
      .toContainText(`Backup created · ${result.data.documents} document`);
  });

  test("populated snapshot status displays its saved file path", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-path`, label: "path" });
    const result = await createBackup(page, createdBackups);
    await expect(page.locator("#bkStatus")).toContainText(result.data.file);
  });

  test("a populated snapshot records its collection document count", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-count`, label: "count" });
    const result = await createBackup(page, createdBackups);
    expect(result.data.collections[collection]).toBe(1);
  });

  test("a newly created snapshot increases the visible snapshot count", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-refresh`, label: "refresh" });
    const before = await backupCount(page);
    await createBackup(page, createdBackups);
    await expectBackupCount(page, Math.min(before + 1, 10));
  });

  test("multiple snapshots are independently listed", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-multiple`, label: "multiple" });
    const before = await backupCount(page);
    const first = await createBackup(page, createdBackups);
    const second = await createBackup(page, createdBackups);
    expect(first.data.file).not.toBe(second.data.file);
    await expectBackupCount(page, Math.min(before + 2, 10));
    expect((await api(page, "/backup")).data.backups.map((backup: { file: string }) => backup.file))
      .toEqual(expect.arrayContaining([first.data.file, second.data.file]));
  });

  test("restoring with a blank path asks for a backup file", async ({ page }) => {
    await openBackup(page);
    await page.locator("#bkFile").fill("");
    await page.locator("#bkRestore").click();
    await expect(page.locator("#toasts"))
      .toContainText("Enter the path of the backup file to restore");
    await expect(page.locator("#confirmBackdrop")).toBeHidden();
  });

  test("restore confirmation names the requested backup path", async ({ page, createdBackups }) => {
    const result = await createBackup(page, createdBackups);
    await openBackup(page);
    await page.locator("#bkFile").fill(result.data.file);
    await page.locator("#bkRestore").click();
    const dialog = page.getByRole("dialog", { name: "Restore from backup" });
    await expect(dialog).toContainText(result.data.file);
    await dialog.locator("#confirmCancel").click();
  });

  test("restore confirmation explains that current data may be replaced", async ({
    page,
    createdBackups,
  }) => {
    const result = await createBackup(page, createdBackups);
    await openBackup(page);
    await page.locator("#bkFile").fill(result.data.file);
    await page.locator("#bkRestore").click();
    await expect(page.getByRole("dialog", { name: "Restore from backup" }))
      .toContainText("Documents written since the snapshot may be replaced or lost");
    await page.locator("#confirmCancel").click();
  });

  test("cancelling restore leaves the current data unchanged and sends no restore request", async ({
    page,
    collection,
    createdBackups,
  }) => {
    const document = { id: `${collection}-cancel`, label: "still here" };
    await insertDocument(page, collection, document);
    const result = await createBackup(page, createdBackups);
    await openBackup(page);
    await page.locator("#bkFile").fill(result.data.file);
    const restoreRequests: string[] = [];
    const onRequest = (request: import("@playwright/test").Request) => {
      if (new URL(request.url()).pathname === "/api/backup/restore") {
        restoreRequests.push(request.url());
      }
    };
    page.on("request", onRequest);
    await page.locator("#bkRestore").click();
    await page.locator("#confirmCancel").click();
    page.off("request", onRequest);
    expect(restoreRequests).toHaveLength(0);
    const restored = await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
    );
    expect(restored.status).toBe(200);
    expect(restored.data.fields).toEqual({ label: document.label });
  });

  test("Escape cancels restore before submitting it", async ({ page, createdBackups }) => {
    const result = await createBackup(page, createdBackups);
    await openBackup(page);
    await page.locator("#bkFile").fill(result.data.file);
    const requests: string[] = [];
    const onRequest = (request: import("@playwright/test").Request) => {
      if (new URL(request.url()).pathname === "/api/backup/restore") requests.push(request.url());
    };
    page.on("request", onRequest);
    await page.locator("#bkRestore").click();
    await page.keyboard.press("Escape");
    await expect(page.locator("#confirmBackdrop")).toBeHidden();
    page.off("request", onRequest);
    expect(requests).toHaveLength(0);
  });

  test("a missing backup path reports a recoverable restore error", async ({ page }) => {
    await openBackup(page);
    const response = await restoreBackup(page, `missing-${Date.now()}.json.gz`);
    expect(response.status()).toBe(404);
    await expect(page.locator("#bkStatus")).toHaveText("restore failed");
    await expect(page.locator("#bkInfo")).toContainText("Restore failed");
    await expect(page.locator("#bkInfo")).toContainText("Backup file not found");
  });

  test("a failed restore leaves existing test-owned documents intact", async ({
    page,
    collection,
  }) => {
    const document = { id: `${collection}-missing-restore`, label: "preserve" };
    await insertDocument(page, collection, document);
    await openBackup(page);
    const missingFile = `missing-${Date.now()}.json.gz`;
    const response = await restoreBackup(page, missingFile);
    expect(response.status()).toBe(404);
    const saved = await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
    );
    expect(saved.status).toBe(200);
    expect(saved.data.fields).toEqual({ label: document.label });
  });

  test("restoring a snapshot recovers a deleted test-owned document", async ({
    page,
    collection,
    createdBackups,
  }) => {
    const document = { id: `${collection}-restore`, label: "recover me" };
    await insertDocument(page, collection, document);
    const result = await createBackup(page, createdBackups);
    const deleted = await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
      "DELETE",
    );
    expect(deleted.status).toBe(204);
    const response = await restoreBackup(page, result.data.file);
    expect(response.status()).toBe(200);
    const recovered = await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
    );
    expect(recovered.status).toBe(200);
    expect(recovered.data.fields).toEqual({ label: document.label });
  });

  test("successful restore displays its confirmation", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-toast`, label: "toast" });
    const result = await createBackup(page, createdBackups);
    const response = await restoreBackup(page, result.data.file);
    expect(response.status()).toBe(200);
    await expect(page.locator("#toasts")).toContainText("Restore complete");
  });

  test("successful restore displays the restored file path", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-status`, label: "status" });
    const result = await createBackup(page, createdBackups);
    expect((await restoreBackup(page, result.data.file)).status()).toBe(200);
    await expect(page.locator("#bkStatus")).toContainText(`restored from ${result.data.file}`);
  });

  test("restoring an older snapshot preserves an unrelated current document", async ({
    page,
    collection,
    createdBackups,
  }) => {
    const original = { id: `${collection}-snapshot`, label: "snapshot data" };
    const unrelated = { id: `${collection}-unrelated`, label: "current data" };
    await insertDocument(page, collection, original);
    const result = await createBackup(page, createdBackups);
    await insertDocument(page, collection, unrelated);
    expect((await restoreBackup(page, result.data.file)).status()).toBe(200);
    for (const document of [original, unrelated]) {
      const saved = await api(
        page,
        `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
      );
      expect(saved.status).toBe(200);
      expect(saved.data.fields).toEqual({ label: document.label });
    }
  });

  test("a restored snapshot remains available in backup status", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-listed`, label: "listed" });
    const before = await backupCount(page);
    const result = await createBackup(page, createdBackups);
    expect((await restoreBackup(page, result.data.file)).status()).toBe(200);
    await expect.poll(async () => (await api(page, "/backup")).data.backups.some(
      (backup: { file: string }) => backup.file === result.data.file,
    )).toBe(true);
    await expectBackupCount(page, Math.min(before + 1, 10));
  });

  test("a new snapshot can be created after a restore", async ({
    page,
    collection,
    createdBackups,
  }) => {
    await insertDocument(page, collection, { id: `${collection}-again`, label: "again" });
    const before = await backupCount(page);
    const first = await createBackup(page, createdBackups);
    expect((await restoreBackup(page, first.data.file)).status()).toBe(200);
    const second = await createBackup(page, createdBackups);
    expect(second.data.file).not.toBe(first.data.file);
    await expectBackupCount(page, Math.min(before + 2, 10));
  });

  test("a restored document appears in the Collections panel", async ({
    page,
    collection,
    createdBackups,
  }) => {
    const document = { id: `${collection}-visible`, label: "visible after restore" };
    await insertDocument(page, collection, document);
    const result = await createBackup(page, createdBackups);
    expect((await api(
      page,
      `/collections/${encodeURIComponent(collection)}/${encodeURIComponent(document.id)}`,
      "DELETE",
    )).status).toBe(204);
    expect((await restoreBackup(page, result.data.file)).status()).toBe(200);

    await page.locator('#nav [data-panel="collections"]').click();
    await page.locator("#colSel").fill(collection);
    const loadResponse = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
        && response.request().method() === "GET",
    );
    await page.locator("#colLoad").click();
    expect((await loadResponse).status()).toBe(200);
    await expect(page.locator("#colTable")).toContainText(document.id);
    await expect(page.locator("#colTable")).toContainText(document.label);
  });
});
