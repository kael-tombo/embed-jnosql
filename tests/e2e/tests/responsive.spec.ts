import { expect, test } from "../fixtures/console";

const panels = [
  { id: "overview", label: "Overview" },
  { id: "collections", label: "Collections" },
  { id: "kv", label: "Key-Value" },
  { id: "columns", label: "Column Family" },
  { id: "vectors", label: "Vectors" },
  { id: "schema", label: "Schema" },
  { id: "tx", label: "Transactions" },
  { id: "indexes", label: "Indexes" },
  { id: "backup", label: "Backup" },
  { id: "cdc", label: "CDC / Events" },
  { id: "audit", label: "Audit Trail" },
  { id: "server", label: "Server" },
] as const;

test("keeps all panels and topbar controls within supported viewport widths", async ({ page }) => {
  for (const width of [390, 760, 999, 1440]) {
    await page.setViewportSize({ width, height: 900 });

    for (const { id, label } of panels) {
      const navItem = page.getByRole("button", { name: label, exact: true });
      await navItem.click();

      await expect(page.locator(`#panel-${id}`)).toBeVisible();
      await expect(page.locator("#panelTitle")).toHaveText(label);
      await expect(navItem).toHaveAttribute("aria-current", "page");

      const layout = await page.evaluate(() => {
        const documentElement = document.documentElement;
        const topbar = document.querySelector<HTMLElement>(".topbar");
        if (!topbar) {
          throw new Error("Console topbar was not found.");
        }

        const topbarRect = topbar.getBoundingClientRect();
        const controls = Array.from(
          topbar.querySelectorAll<HTMLElement>(".topbar-chip, button"),
        ).map((control) => {
          const rect = control.getBoundingClientRect();
          return {
            label: control.textContent?.trim() || control.id,
            left: rect.left,
            right: rect.right,
            top: rect.top,
            bottom: rect.bottom,
            visible: rect.width > 0 && rect.height > 0,
          };
        });

        return {
          scrollWidth: documentElement.scrollWidth,
          clientWidth: documentElement.clientWidth,
          topbar: {
            left: topbarRect.left,
            right: topbarRect.right,
            top: topbarRect.top,
            bottom: topbarRect.bottom,
            scrollWidth: topbar.scrollWidth,
            clientWidth: topbar.clientWidth,
          },
          controls,
        };
      });

      expect(
        layout.scrollWidth,
        `Expected no document overflow at ${width}px on the ${id} panel`,
      ).toBe(layout.clientWidth);
      expect(
        layout.topbar.left,
        `Expected topbar to remain within the viewport at ${width}px`,
      ).toBeGreaterThanOrEqual(0);
      expect(
        layout.topbar.right,
        `Expected topbar to remain within the viewport at ${width}px`,
      ).toBeLessThanOrEqual(layout.clientWidth);
      expect(
        layout.topbar.scrollWidth,
        `Expected no topbar overflow at ${width}px on the ${id} panel`,
      ).toBeLessThanOrEqual(layout.topbar.clientWidth);

      for (const control of layout.controls) {
        expect(
          control.visible,
          `Expected topbar control "${control.label}" to be visible at ${width}px`,
        ).toBe(true);
        expect(
          control.left,
          `Expected topbar control "${control.label}" to remain within the topbar at ${width}px`,
        ).toBeGreaterThanOrEqual(layout.topbar.left);
        expect(
          control.right,
          `Expected topbar control "${control.label}" to remain within the topbar at ${width}px`,
        ).toBeLessThanOrEqual(layout.topbar.right);
        expect(
          control.top,
          `Expected topbar control "${control.label}" to remain within the topbar at ${width}px`,
        ).toBeGreaterThanOrEqual(layout.topbar.top);
        expect(
          control.bottom,
          `Expected topbar control "${control.label}" to remain within the topbar at ${width}px`,
        ).toBeLessThanOrEqual(layout.topbar.bottom);
        expect(
          control.left,
          `Expected topbar control "${control.label}" to remain within the viewport at ${width}px`,
        ).toBeGreaterThanOrEqual(0);
        expect(
          control.right,
          `Expected topbar control "${control.label}" to remain within the viewport at ${width}px`,
        ).toBeLessThanOrEqual(layout.clientWidth);
      }
    }
  }
});
