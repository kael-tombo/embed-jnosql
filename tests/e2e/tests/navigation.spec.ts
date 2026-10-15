import { expect, test } from "../fixtures/console";

const panels = [
  { id: "overview", navigationName: "Overview", heading: "Overview" },
  { id: "collections", navigationName: "Collections", heading: "Collections" },
  { id: "kv", navigationName: "Key-Value", heading: "Key-Value & Redis Structures" },
  { id: "columns", navigationName: "Column Family", heading: "Wide-Column Families" },
  { id: "vectors", navigationName: "Vectors", heading: "Vector Index (HNSW)" },
  { id: "schema", navigationName: "Schema", heading: "Schema Validation" },
  { id: "tx", navigationName: "Transactions", heading: "Transactions" },
  { id: "indexes", navigationName: "Indexes", heading: "Secondary Indexes" },
  { id: "backup", navigationName: "Backup", heading: "Backup & Restore" },
  { id: "cdc", navigationName: "CDC / Events", heading: "Change Data Capture" },
  { id: "audit", navigationName: "Audit Trail", heading: "Audit Trail" },
  { id: "server", navigationName: "Server", heading: "Server" },
];

const numericShortcuts = [
  { key: "1", panelId: "overview" },
  { key: "3", panelId: "collections" },
  { key: "4", panelId: "kv" },
  { key: "5", panelId: "columns" },
  { key: "6", panelId: "vectors" },
  { key: "7", panelId: "schema" },
  { key: "8", panelId: "tx" },
  { key: "9", panelId: "indexes" },
  { key: "0", panelId: "backup" },
];

test("navigates all twelve panel routes and shows each panel heading", async ({ page }) => {
  const navigation = page.getByRole("navigation", { name: "Console navigation" });

  for (const panel of panels) {
    await navigation.getByRole("button", { name: panel.navigationName, exact: true }).click();
    await expect(page).toHaveURL(new RegExp(`#${panel.id}$`));
    await expect(page.locator(`#panel-${panel.id}`)).toBeVisible();
    await expect(page.locator(`#panel-${panel.id} h1`)).toHaveText(panel.heading);
    await expect(page.locator("#panelTitle")).toHaveText(panel.navigationName);
    await expect(
      navigation.getByRole("button", { name: panel.navigationName, exact: true }),
    ).toHaveAttribute("aria-current", "page");
  }
});

test("reload restores the deep-linked panel and its heading", async ({ page }) => {
  const panel = panels.find(({ id }) => id === "collections")!;

  await page.locator('#nav [data-panel="collections"]').click();
  await expect(page).toHaveURL(/#collections$/);
  await page.reload();

  await expect(page).toHaveURL(/#collections$/);
  await expect(page.locator("#panel-collections")).toBeVisible();
  await expect(page.locator("#panel-collections h1")).toHaveText(panel.heading);
  await expect(
    page.getByRole("navigation", { name: "Console navigation" })
      .getByRole("button", { name: panel.navigationName, exact: true }),
  ).toHaveAttribute("aria-current", "page");
});

test("numeric shortcuts navigate to their documented panels", async ({ page }) => {
  for (const shortcut of numericShortcuts) {
    await page.keyboard.press(shortcut.key);
    await expect(page).toHaveURL(new RegExp(`#${shortcut.panelId}$`));
    await expect(page.locator(`#panel-${shortcut.panelId}`)).toBeVisible();
  }
});

test("Help opens, closes, and restores focus to its trigger", async ({ page }) => {
  const helpButton = page.locator("#btnHelp");
  const dialog = page.getByRole("dialog", {
    name: "Help — keyboard shortcuts & data-model reference",
  });

  await helpButton.click();
  await expect(dialog).toBeVisible();
  await expect(page.locator("#helpClose")).toBeFocused();

  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(helpButton).toBeFocused();

  await helpButton.click();
  await page.getByRole("button", { name: "Close", exact: true }).click();
  await expect(dialog).toBeHidden();
  await expect(helpButton).toBeFocused();
});

test("keyboard tab order follows topbar and console navigation", async ({ page }) => {
  const orderedFocusTargets = [
    page.locator("#btnRefreshAll"),
    page.locator("#btnHelp"),
    page.locator("#btnLogout"),
    page.locator("#navSearch"),
    page.locator('#nav [data-panel="overview"]'),
    page.locator('#nav [data-panel="collections"]'),
  ];

  await page.locator("#btnTheme").focus();
  for (const target of orderedFocusTargets) {
    await page.keyboard.press("Tab");
    await expect(target).toBeFocused();
  }
});
