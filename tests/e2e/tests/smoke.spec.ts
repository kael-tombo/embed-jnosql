import { expect, test } from "../fixtures/console";

test("loads the authenticated console overview", async ({ page }) => {
  await expect(page).toHaveTitle(/EmbedJNoSQL Console/);
  await expect(page.locator("#chipHealthText")).toHaveText("Healthy");

  const collections = page.locator("#ovCollections");
  await expect(collections).toContainText("products");
  await expect(collections).toContainText("users");
  await expect(collections).toContainText("inventory");
});
