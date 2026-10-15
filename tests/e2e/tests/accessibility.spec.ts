import AxeBuilder from "@axe-core/playwright";
import { expect, test } from "../fixtures/console";
import type { Page, TestInfo } from "@playwright/test";

async function scanAccessibility(page: Page, testInfo: TestInfo): Promise<void> {
  const results = await new AxeBuilder({ page }).analyze();
  const violations = results.violations.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    description: violation.description,
    help: violation.help,
    nodes: violation.nodes.map((node) => ({
      target: node.target,
      failureSummary: node.failureSummary,
    })),
  }));
  const severeViolations = violations.filter(
    (violation) => violation.impact === "critical" || violation.impact === "serious",
  );
  const lesserViolations = violations.filter(
    (violation) => violation.impact !== "critical" && violation.impact !== "serious",
  );

  if (lesserViolations.length > 0) {
    const report = JSON.stringify(lesserViolations, null, 2);
    const summary = lesserViolations
      .map((violation) => {
        const targets = violation.nodes.flatMap((node) => node.target).join(", ");
        return `${violation.id} (${violation.impact}) at ${targets}`;
      })
      .join("; ");
    console.warn(`Lesser axe violations in ${testInfo.title}: ${summary}`);
    await testInfo.attach("lesser-axe-violations.json", {
      body: report,
      contentType: "application/json",
    });
  }

  const severeSummary = severeViolations.map((violation) => ({
    id: violation.id,
    impact: violation.impact,
    help: violation.help,
    targets: violation.nodes.flatMap((node) => node.target),
  }));
  expect(
    severeSummary,
    `Critical and serious axe violations:\n${JSON.stringify(severeSummary, null, 2)}`,
  ).toEqual([]);
}

test("authenticated Overview has no critical or serious axe violations", async ({
  page,
}, testInfo) => {
  await scanAccessibility(page, testInfo);
});

test("authenticated Collections has no critical or serious axe violations", async ({
  page,
}, testInfo) => {
  await page.locator('#nav [data-panel="collections"]').click();
  await scanAccessibility(page, testInfo);
});

test("Help dialog has no critical or serious axe violations", async ({ page }, testInfo) => {
  await page.locator("#btnHelp").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await scanAccessibility(page, testInfo);
});

test("confirmation dialog has no critical or serious axe violations", async ({
  page,
}, testInfo) => {
  await page.locator('#nav [data-panel="collections"]').click();
  await page.locator("#colSel").fill("products");
  await page.locator("#colLoad").click();
  await expect(page.locator("#colCount")).not.toBeEmpty();
  await page.locator("#colDrop").click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await scanAccessibility(page, testInfo);
});

test("Sign In has no critical or serious axe violations", async ({
  browser,
  baseURL,
}, testInfo) => {
  const context = await browser.newContext({ baseURL });
  try {
    const page = await context.newPage();
    await page.goto("/login.html");
    await scanAccessibility(page, testInfo);
  } finally {
    await context.close();
  }
});
