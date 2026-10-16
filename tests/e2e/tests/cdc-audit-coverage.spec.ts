import { mkdtemp, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { expect, test as base } from "../fixtures/console";
import type { Page } from "@playwright/test";

type ApiResult = {
  status: number;
  data: any;
};

type ConnectorResources = {
  names: Set<string>;
  outputDir: string;
};

type CdcAuditFixtures = {
  collection: string;
  connectorResources: ConnectorResources;
  originalCdcEnabled: boolean;
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

async function cleanupCollection(page: Page, collection: string): Promise<void> {
  const collectionPath = `/collections/${encodeURIComponent(collection)}`;
  const listed = await api(page, collectionPath);
  if (listed.status === 404) return;
  expect(listed.status).toBe(200);
  for (const document of listed.data) {
    if (typeof document?.id !== "string") continue;
    const deleted = await api(
      page,
      `${collectionPath}/${encodeURIComponent(document.id)}`,
      "DELETE",
    );
    expect(deleted.status).toBe(204);
  }
}

const test = base.extend<CdcAuditFixtures>({
  collection: async ({ page }, use) => {
    const name = uniqueName("e2e_cdc_audit");
    await use(name);
    await cleanupCollection(page, name);
  },
  connectorResources: async ({ page }, use) => {
    const resources: ConnectorResources = {
      names: new Set<string>(),
      outputDir: await mkdtemp(path.join(os.tmpdir(), "embedjnosql-cdc-e2e-")),
    };
    await use(resources);
    for (const name of resources.names) {
      const result = await api(
        page,
        `/cdc/connectors/${encodeURIComponent(name)}`,
        "DELETE",
      );
      expect([200, 404]).toContain(result.status);
    }
    await rm(resources.outputDir, { recursive: true, force: true });
  },
  originalCdcEnabled: async ({ page }, use) => {
    const initial = await api(page, "/cdc");
    expect(initial.status).toBe(200);
    await use(initial.data.enabled);
    const current = await api(page, "/cdc");
    expect(current.status).toBe(200);
    if (current.data.enabled !== initial.data.enabled) {
      const action = initial.data.enabled ? "enable" : "disable";
      expect((await api(page, `/cdc/${action}`, "POST")).status).toBe(200);
    }
  },
});

async function openCdc(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="cdc"]').click();
  await expect(page.getByRole("heading", { name: "Change Data Capture" })).toBeVisible();
}

async function openAudit(page: Page): Promise<void> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/audit/logs"
      && response.request().method() === "GET",
  );
  await page.locator('#nav [data-panel="audit"]').click();
  await expect(page.getByRole("heading", { name: "Audit Trail" })).toBeVisible();
  expect((await responsePromise).status()).toBe(200);
  await expect(page.locator("#audTable tbody tr").first()).toBeVisible();
}

async function openCollections(page: Page): Promise<void> {
  await page.locator('#nav [data-panel="collections"]').click();
  await expect(page.locator("#panel-collections")).toBeVisible();
}

async function setCdcEnabledThroughUi(page: Page, enabled: boolean): Promise<void> {
  await openCdc(page);
  const current = (await api(page, "/cdc")).data.enabled;
  if (current !== enabled) {
    await page.locator("#cdcToggle").click();
    await expect(page.locator("#cdcState")).toHaveText(enabled ? "enabled" : "disabled");
  }
}

async function insertThroughUi(
  page: Page,
  collection: string,
  document: Record<string, unknown>,
): Promise<void> {
  await openCollections(page);
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

async function deleteThroughUi(
  page: Page,
  collection: string,
  documentId: string,
): Promise<void> {
  await openCollections(page);
  await page.locator("#colSel").fill(collection);
  const loadResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname === `/api/collections/${encodeURIComponent(collection)}`
      && response.request().method() === "GET",
  );
  await page.locator("#colLoad").click();
  expect((await loadResponse).status()).toBe(200);
  await page.locator(`#colTable [data-doc="${documentId}"]`).click();
  await page.locator("#docDel").click();
  const dialog = page.getByRole("dialog", { name: "Delete document" });
  await expect(dialog).toBeVisible();
  const deleteResponse = page.waitForResponse((response) =>
    new URL(response.url()).pathname
      === `/api/collections/${encodeURIComponent(collection)}/${encodeURIComponent(documentId)}`
      && response.request().method() === "DELETE",
  );
  await dialog.locator("#confirmAccept").click();
  expect((await deleteResponse).status()).toBe(204);
  await expect(page.locator("#toasts")).toContainText(`Deleted ${documentId}`);
}

async function loadCdcEvents(page: Page): Promise<any[]> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/cdc/events"
      && response.request().method() === "GET",
  );
  await page.locator("#cdcEvents").click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  return (await response.json()).events;
}

async function loadAudit(page: Page): Promise<void> {
  const responsePromise = page.waitForResponse((response) =>
    new URL(response.url()).pathname === "/api/audit/logs"
      && response.request().method() === "GET",
  );
  await page.locator("#audRefresh").click();
  expect((await responsePromise).status()).toBe(200);
}

async function auditEventsFor(page: Page, resource: string): Promise<any[]> {
  const result = await api(
    page,
    `/audit/logs?resource=${encodeURIComponent(resource)}`,
  );
  expect(result.status).toBe(200);
  return result.data.events;
}

test.describe("CDC UI coverage", () => {
  test("the CDC panel opens with its heading and event-stream hint", async ({ page }) => {
    await openCdc(page);
    await expect(page.locator("#panel-cdc")).toContainText("Event log, connectors, and enable/disable");
  });

  test("CDC status initially reports the enabled processor", async ({ page }) => {
    await openCdc(page);
    await expect(page.locator("#cdcState")).toHaveText("enabled");
  });

  test("CDC status badge styling matches its enabled state", async ({ page }) => {
    await openCdc(page);
    await expect(page.locator("#cdcState")).toHaveClass(/ok/);
  });

  test("CDC status KPIs match the returned subscriber and connector counts", async ({ page }) => {
    await openCdc(page);
    const status = await api(page, "/cdc");
    expect(status.status).toBe(200);
    await expect(page.locator("#cdcStatus")).toContainText("Subscribers");
    await expect(page.locator("#cdcStatus")).toContainText(String(status.data.subscribers));
    await expect(page.locator("#cdcStatus")).toContainText("Events in log");
    await expect(page.locator("#cdcStatus")).toContainText(String(status.data.eventsInLog));
    await expect(page.locator("#cdcStatus")).toContainText("File connectors");
    await expect(page.locator("#cdcStatus")).toContainText(String(status.data.fileConnectors.length));
    await expect(page.locator("#cdcStatus")).toContainText("Kafka connectors");
    await expect(page.locator("#cdcStatus")).toContainText(String(status.data.kafkaConnectors.length));
  });

  test("refreshing CDC status requests the current processor state", async ({ page }) => {
    await openCdc(page);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/cdc"
        && response.request().method() === "GET",
    );
    await page.locator("#cdcRefresh").click();
    expect((await responsePromise).status()).toBe(200);
    await expect(page.locator("#cdcState")).toHaveText("enabled");
  });

  test("an empty connector list explains that none are configured", async ({ page }) => {
    await openCdc(page);
    await expect(page.locator("#cdcConnectors")).toContainText("No connectors configured.");
  });

  test("adding a file connector displays its name and type", async ({
    page,
    connectorResources,
  }) => {
    await openCdc(page);
    const name = uniqueName("file_sink");
    connectorResources.names.add(name);
    await page.locator("#cdcName").fill(name);
    await page.locator("#cdcType").selectOption("file");
    await page.locator("#cdcParam").fill(connectorResources.outputDir);
    const responsePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/cdc/connectors/${encodeURIComponent(name)}`
        && response.request().method() === "POST",
    );
    await page.locator("#cdcAdd").click();
    expect((await responsePromise).status()).toBe(201);
    await expect(page.locator("#toasts")).toContainText(`Connector ${name} added`);
    const row = page.locator("#cdcConnectors tbody tr").filter({ hasText: name });
    await expect(row).toContainText("file");
  });

  test("a file connector is counted as a subscriber in CDC status", async ({
    page,
    connectorResources,
  }) => {
    await openCdc(page);
    const name = uniqueName("file_subscriber");
    connectorResources.names.add(name);
    await page.locator("#cdcName").fill(name);
    await page.locator("#cdcType").selectOption("file");
    await page.locator("#cdcParam").fill(connectorResources.outputDir);
    const addPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/cdc/connectors/${encodeURIComponent(name)}`
        && response.request().method() === "POST",
    );
    await page.locator("#cdcAdd").click();
    expect((await addPromise).status()).toBe(201);
    await expect.poll(async () => (await api(page, "/cdc")).data.fileConnectors)
      .toEqual(expect.arrayContaining([name]));
    const status = await api(page, "/cdc");
    expect(status.data.subscribers).toBe(status.data.fileConnectors.length);
    const refreshPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/cdc"
        && response.request().method() === "GET",
    );
    await page.locator("#cdcRefresh").click();
    expect((await refreshPromise).status()).toBe(200);
    await expect(page.locator("#cdcStatus")).toContainText(String(status.data.subscribers));
  });

  test("refreshing the connector panel retains the configured file connector", async ({
    page,
    connectorResources,
  }) => {
    await openCdc(page);
    const name = uniqueName("file_refresh");
    connectorResources.names.add(name);
    await page.locator("#cdcName").fill(name);
    await page.locator("#cdcType").selectOption("file");
    await page.locator("#cdcParam").fill(connectorResources.outputDir);
    const addPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/cdc/connectors/${encodeURIComponent(name)}`
        && response.request().method() === "POST",
    );
    await page.locator("#cdcAdd").click();
    expect((await addPromise).status()).toBe(201);
    await expect(page.locator("#cdcConnectors")).toContainText(name);
    const refreshPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === "/api/cdc"
        && response.request().method() === "GET",
    );
    await page.locator("#cdcRefresh").click();
    expect((await refreshPromise).status()).toBe(200);
    await expect(page.locator("#cdcConnectors")).toContainText(name);
  });

  test("removing a file connector removes it from the visible list", async ({
    page,
    connectorResources,
  }) => {
    await openCdc(page);
    const name = uniqueName("file_remove");
    connectorResources.names.add(name);
    await page.locator("#cdcName").fill(name);
    await page.locator("#cdcType").selectOption("file");
    await page.locator("#cdcParam").fill(connectorResources.outputDir);
    const addPromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/cdc/connectors/${encodeURIComponent(name)}`
        && response.request().method() === "POST",
    );
    await page.locator("#cdcAdd").click();
    expect((await addPromise).status()).toBe(201);
    const row = page.locator("#cdcConnectors tbody tr").filter({ hasText: name });
    await expect(row).toBeVisible();
    const removePromise = page.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/cdc/connectors/${encodeURIComponent(name)}`
        && response.request().method() === "DELETE",
    );
    await row.locator("[data-cdc]").click();
    expect((await removePromise).status()).toBe(200);
    await expect(page.locator("#cdcConnectors")).not.toContainText(name);
  });

  test("connecting with a blank name shows validation and sends no request", async ({ page }) => {
    await openCdc(page);
    const requests: string[] = [];
    const onRequest = (request: import("@playwright/test").Request) => {
      if (new URL(request.url()).pathname.startsWith("/api/cdc/connectors/")) {
        requests.push(request.url());
      }
    };
    page.on("request", onRequest);
    await page.locator("#cdcName").fill("");
    await page.locator("#cdcAdd").click();
    await expect(page.locator("#toasts")).toContainText("Connector name required");
    page.off("request", onRequest);
    expect(requests).toHaveLength(0);
  });

  test("loading CDC events renders the event table headings", async ({ page }) => {
    await openCdc(page);
    await loadCdcEvents(page);
    await expect(page.locator("#cdcTable")).toContainText("Time");
    await expect(page.locator("#cdcTable")).toContainText("Type");
    await expect(page.locator("#cdcTable")).toContainText("Collection");
    await expect(page.locator("#cdcTable")).toContainText("Key");
  });

  test("a console document insert appears in the CDC event log", async ({ page, collection }) => {
    await setCdcEnabledThroughUi(page, true);
    const id = `${collection}-insert`;
    await insertThroughUi(page, collection, { id, action: "cdc-insert" });
    await openCdc(page);
    const events = await loadCdcEvents(page);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ eventType: "INSERT", collection, key: id }),
    ]));
    const row = page.locator("#cdcTable tbody tr").filter({ hasText: id });
    await expect(row).toContainText("INSERT");
    await expect(row).toContainText(collection);
  });

  test("a console document delete appears in the CDC event log", async ({ page, collection }) => {
    await setCdcEnabledThroughUi(page, true);
    const id = `${collection}-delete`;
    await insertThroughUi(page, collection, { id, action: "cdc-delete" });
    await deleteThroughUi(page, collection, id);
    await openCdc(page);
    const events = await loadCdcEvents(page);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ eventType: "DELETE", collection, key: id }),
    ]));
    const rows = page.locator("#cdcTable tbody tr").filter({ hasText: id });
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(1)).toContainText("DELETE");
  });

  test("CDC event rows preserve insert-before-delete order for one document", async ({
    page,
    collection,
  }) => {
    await setCdcEnabledThroughUi(page, true);
    const id = `${collection}-ordered`;
    await insertThroughUi(page, collection, { id, action: "ordered-events" });
    await deleteThroughUi(page, collection, id);
    await openCdc(page);
    await loadCdcEvents(page);
    const rows = page.locator("#cdcTable tbody tr").filter({ hasText: id });
    await expect(rows).toHaveCount(2);
    await expect(rows.nth(0)).toContainText("INSERT");
    await expect(rows.nth(1)).toContainText("DELETE");
  });

  test("toggling CDC disables event capture and updates its state badge", async ({
    page,
    originalCdcEnabled,
  }) => {
    await setCdcEnabledThroughUi(page, true);
    await page.locator("#cdcToggle").click();
    await expect(page.locator("#cdcState")).toHaveText("disabled");
    await expect(page.locator("#cdcState")).toHaveClass(/err/);
    expect(originalCdcEnabled).toBe(true);
  });

  test("a console insert made while CDC is disabled is not added to the event log", async ({
    page,
    collection,
    originalCdcEnabled: _originalCdcEnabled,
  }) => {
    await setCdcEnabledThroughUi(page, false);
    const id = `${collection}-disabled`;
    await insertThroughUi(page, collection, { id, action: "cdc-disabled" });
    const events = await api(page, "/cdc/events");
    expect(events.status).toBe(200);
    expect(events.data.events).not.toEqual(
      expect.arrayContaining([expect.objectContaining({ collection, key: id })]),
    );
  });
});

test.describe("Audit Trail UI coverage", () => {
  test("the Audit Trail panel opens with its heading and retention hint", async ({ page }) => {
    await openAudit(page);
    await expect(page.locator("#panel-audit")).toContainText("Recent mutating operations");
    await expect(page.locator("#panel-audit")).toContainText("bounded in-memory log");
  });

  test("the Audit Trail table displays operation and resource columns", async ({ page }) => {
    await openAudit(page);
    await expect(page.locator("#audTable")).toContainText("Operation");
    await expect(page.locator("#audTable")).toContainText("Resource");
    await expect(page.locator("#audTable")).toContainText("Document");
    await expect(page.locator("#audTable")).toContainText("Status");
    await expect(page.locator("#audTable")).toContainText("Client");
  });

  test("refreshing Audit Trail requests the current event log", async ({ page }) => {
    await openAudit(page);
    await loadAudit(page);
  });

  test("a console insert is recorded as an INSERT operation", async ({ page, collection }) => {
    const id = `${collection}-operation`;
    await insertThroughUi(page, collection, { id, action: "audit-operation" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ operation: "INSERT", documentId: id }),
    ]));
  });

  test("an insert audit event identifies its collection resource", async ({ page, collection }) => {
    const id = `${collection}-resource`;
    await insertThroughUi(page, collection, { id, action: "audit-resource" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ operation: "INSERT", resource: collection, documentId: id }),
    ]));
  });

  test("an insert audit event displays its document identifier", async ({ page, collection }) => {
    const id = `${collection}-document`;
    await insertThroughUi(page, collection, { id, action: "audit-document" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ operation: "INSERT", documentId: id }),
    ]));
  });

  test("a console insert has SUCCESS status and Audit Trail renders success rows", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-status`;
    await insertThroughUi(page, collection, { id, action: "audit-status" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({
        operation: "INSERT",
        resource: collection,
        documentId: id,
        status: "SUCCESS",
      }),
    ]));
    await expect(page.locator("#audTable tbody tr").first().locator("td").nth(4))
      .toHaveText("SUCCESS");
  });

  test("an insert audit event includes the requesting client address", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-client`;
    await insertThroughUi(page, collection, { id, action: "audit-client" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({
        operation: "INSERT",
        documentId: id,
        clientIp: expect.stringMatching(/\S/),
      }),
    ]));
    const client = page.locator("#audTable tbody tr").first().locator("td").nth(5);
    await expect(client).not.toBeEmpty();
    await expect(client).not.toHaveText("—");
  });

  test("refreshing Audit Trail preserves visible rows and keeps recent inserts recorded", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-refresh`;
    await insertThroughUi(page, collection, { id, action: "audit-refresh" });
    await openAudit(page);
    const visibleRows = page.locator("#audTable tbody tr");
    await expect(visibleRows.first()).toBeVisible();
    await loadAudit(page);
    await expect(visibleRows.first()).toBeVisible();
    const events = await auditEventsFor(page, collection);
    expect(events).toEqual(expect.arrayContaining([
      expect.objectContaining({ operation: "INSERT", documentId: id }),
    ]));
  });

  test("separate console inserts create separate audit rows", async ({ page, collection }) => {
    const first = `${collection}-first`;
    const second = `${collection}-second`;
    await insertThroughUi(page, collection, { id: first, action: "audit-first" });
    await insertThroughUi(page, collection, { id: second, action: "audit-second" });
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events.filter((event) => [first, second].includes(event.documentId))).toEqual([
      expect.objectContaining({ operation: "INSERT", documentId: first }),
      expect.objectContaining({ operation: "INSERT", documentId: second }),
    ]);
  });

  test("a console document delete is recorded as a DELETE operation", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-delete`;
    await insertThroughUi(page, collection, { id, action: "audit-delete" });
    await deleteThroughUi(page, collection, id);
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events.filter((event) => event.documentId === id).map((event) => event.operation))
      .toEqual(["INSERT", "DELETE"]);
    expect(events.find((event) => event.documentId === id && event.operation === "DELETE"))
      .toEqual(expect.objectContaining({ status: "SUCCESS" }));
  });

  test("a delete audit event retains the collection resource name", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-delete-resource`;
    await insertThroughUi(page, collection, { id, action: "audit-delete-resource" });
    await deleteThroughUi(page, collection, id);
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events.filter((event) => event.documentId === id)).toEqual([
      expect.objectContaining({ operation: "INSERT", resource: collection }),
      expect.objectContaining({ operation: "DELETE", resource: collection }),
    ]);
  });

  test("audit rows preserve insert-before-delete order for one document", async ({
    page,
    collection,
  }) => {
    const id = `${collection}-ordered`;
    await insertThroughUi(page, collection, { id, action: "audit-ordered" });
    await deleteThroughUi(page, collection, id);
    await openAudit(page);
    const events = await auditEventsFor(page, collection);
    expect(events.filter((event) => event.documentId === id).map((event) => event.operation))
      .toEqual(["INSERT", "DELETE"]);
  });
});
