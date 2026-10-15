import { expect, test } from "../fixtures/console";

test("does not overflow at supported viewport widths", async ({ page }) => {
  for (const width of [390, 760, 999, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    const dimensions = await page.evaluate(() => ({
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    }));

    expect(
      dimensions.scrollWidth === dimensions.clientWidth,
      `Expected no horizontal document overflow at ${width}px, got ` +
        `scrollWidth=${dimensions.scrollWidth} and clientWidth=${dimensions.clientWidth}`,
    ).toBe(true);
  }
});
