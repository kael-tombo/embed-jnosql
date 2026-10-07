package org.embeddedjnosql.db.demo.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Framework-integration coverage for the Quarkus extension (Ark CDI + RESTEasy Reactive +
 * SmallRye Config). Mirrors the Spring H2-parity suite: seeded-catalog CRUD, naming rules,
 * explicit MVCC transaction scopes around the HTTP layer, TTL/special-character edge cases,
 * and bean-level assertions on the extension's CDI producers.
 */
@QuarkusTest
public class ProductCatalogIntegrationTest {

    private static final String TAG = "qkat-" + System.nanoTime();
    private static final String PREFIX = "qk-" + System.nanoTime() + "-";

    @Inject
    EmbedJNoSQL db;

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String productJson(String id, double price, String nameSuffix) {
        return "{\n" +
                "  \"id\": \"" + id + "\",\n" +
                "  \"sku\": null,\n" +
                "  \"name\": \"" + nameSuffix + " Book\",\n" +
                "  \"price\": " + price + ",\n" +
                "  \"category\": \"" + nameSuffix + "\",\n" +
                "  \"tags\": [\"test\"],\n" +
                "  \"attributes\": {\"pages\": 234}\n" +
                "}";
    }

    // ── Catalog CRUD & naming rules ───────────────────────────────────────

    @Test
    public void testCatalogCrudRoundTripPreservesAllFields() {
        given().contentType(ContentType.JSON).body(productJson(PREFIX + "crud", 61.25, "Crud"))
                .when().post("/api/products")
                .then().statusCode(201).body("price", equalTo(61.25f));

        given().when().get("/api/products/" + PREFIX + "crud")
                .then().statusCode(200)
                .body("name", equalTo("Crud Book"))
                .body("price", equalTo(61.25f))
                .body("category", equalTo("Crud"))
                .body("attributes.pages", equalTo(234));
    }

    @Test
    public void testCatalogListIncludesProductsCreatedViaApi() {
        given().contentType(ContentType.JSON).body(productJson(PREFIX + "list", 8.5, "List"))
                .when().post("/api/products")
                .then().statusCode(201);

        Response response = given().when().get("/api/products").then().statusCode(200).extract().response();
        List<Map<String, Object>> products = response.jsonPath().getList("");
        assertTrue(products.size() >= 6, "at least the 5 seeded products plus the created one");
        String joined = products.stream().map(p -> String.valueOf(p.get("id"))).reduce("", (a, b) -> a + "," + b);
        assertTrue(joined.contains(PREFIX + "list"), "API-created product appears in the catalog list");
    }

    @Test
    public void testGetCollectionNamesIncludesSeededCollections() {
        given().contentType(ContentType.JSON).body(productJson(PREFIX + "naming", 2.0, "Naming"))
                .when().post("/api/products")
                .then().statusCode(201);

        given().when().get("/api/products/" + PREFIX + "naming")
                .then().statusCode(200).body("name", equalTo("Naming Book"));
        assertTrue(db.getCollectionNames().contains("products"));
    }

    // ── Transactions ─────────────────────────────────────────────────────

    @Test
    public void testExplicitTransactionCommitPersistsAndOrderDeductsStock() {
        String productId = PREFIX + "tx-commit";
        given().contentType(ContentType.JSON).body(productJson(productId, 10.0, "Tx"))
                .when().post("/api/products").then().statusCode(201);

        org.embeddedjnosql.db.transaction.mvcc.Transaction tx = db.beginTransaction();
        try {
            db.documentCollection("commit-check").insert(
                    new org.embeddedjnosql.db.nosql.document.Document().id(productId).add("ok", true));
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            fail("expected commit to succeed");
        }

        assertTrue(db.documentCollection("commit-check").findById(productId) != null,
                "committed collection entry is visible after the transaction");

        String orderJson = "{\n" +
                "  \"id\": \"" + PREFIX + "tx-ord-1\",\n" +
                "  \"orderNumber\": \"QKTX-1\",\n" +
                "  \"customerId\": \"cust-qk\",\n" +
                "  \"items\": [ {\"productId\": \"prod-102\", \"productName\": \"Python Handbook\", \"quantity\": 1, \"unitPrice\": 35.5} ],\n" +
                "  \"totalAmount\": 35.5\n" +
                "}";
        given().contentType(ContentType.JSON).body(orderJson)
                .when().post("/api/orders")
                .then().statusCode(201).body("status", equalTo("CONFIRMED"));

        given().when().get("/api/orders/inventory/prod-102")
                .then().statusCode(200).body("available", equalTo(29));
    }

    @Test
    public void testExplicitTransactionRollbackDiscardsStagedWrites() {
        String productId = PREFIX + "tx-rollback";
        var seedCol = db.documentCollection("rollback-check");
        seedCol.insert(new org.embeddedjnosql.db.nosql.document.Document().id(productId).add("v", 1));

        org.embeddedjnosql.db.transaction.mvcc.Transaction tx = db.beginTransaction();
        try {
            var txCol = tx.documentCollection("rollback-check");
            txCol.deleteById(productId);
        } finally {
            tx.rollback();
        }

        assertNotNull(seedCol.findById(productId),
                "uncommitted delete is discarded when the transaction rolls back");

        String orderJson = "{\n" +
                "  \"id\": \"" + PREFIX + "tx-ord-2\",\n" +
                "  \"orderNumber\": \"QKTX-2\",\n" +
                "  \"customerId\": \"cust-qk\",\n" +
                "  \"items\": [ {\"productId\": \"prod-101\", \"productName\": \"x\", \"quantity\": 100000, \"unitPrice\": 1} ],\n" +
                "  \"totalAmount\": 100000\n" +
                "}";
        given().contentType(ContentType.JSON).body(orderJson)
                .when().post("/api/orders")
                .then().statusCode(400).body(containsString("Insufficient stock"));
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    public void testDocumentTtlInsertReportsExpiryMetadata() {
        var col = db.documentCollection("ttl-check");
        var doc = new org.embeddedjnosql.db.nosql.document.Document()
                .id(PREFIX + "ttl-doc").add("m", 1);
        col.insert(doc, 600);

        var stored = col.findById(doc.id());
        assertNotNull(stored, "a TTL'd document is readable before its deadline");
        assertNotNull(stored.getExpiresAt(), "a TTL'd document reports its expiry");
        Object withTtl = col.ttlStats().get("withTtl");
        assertTrue(withTtl instanceof Number && ((Number) withTtl).longValue() >= 1,
                "ttlStats must report the TTL'd document: " + col.ttlStats());
    }

    @Test
    public void testSpecialCharacterAndUnicodeIdRoundTrip() {
        // Storage-level round trip with URL-hostile characters:
        String exoticId = PREFIX + "space&id=v2-100%";
        var namingCol = db.documentCollection("naming-check");
        namingCol.insert(new org.embeddedjnosql.db.nosql.document.Document()
                .id(exoticId).add("v", 1));
        assertNotNull(namingCol.findById(exoticId),
                "URL-hostile ids round-trip at the storage level");

        // HTTP-level round trip with whitespace and non-ASCII characters:
        String httpId = PREFIX + "id with spaces \u00fcn\u00efcode";
        given().contentType(ContentType.JSON).body(productJson(httpId, 13.0, "Edges"))
                .when().post("/api/products")
                .then().statusCode(201);

        given().when().get("/api/products/" + httpId)
                .then().statusCode(200)
                .body("name", equalTo("Edges Book"))
                .body("price", equalTo(13.0f))
                .body("sku", nullValue());
    }

    // ── CDI wiring of the extension ───────────────────────────────────────

    @Test
    public void testExtensionProducesOpenDatabaseWithManagedCollections() {
        assertNotNull(db, "EmbedJNoSQL injected from the extension's CDI producer");
        assertTrue(db.isOpen());
        assertTrue(db.getCollectionNames().contains("products"),
                "the seeded products collection exists in the extension-managed database");
    }

    @Test
    public void testSmallRyeConfigResolvesEmbedJNoSQLProperties() {
        org.eclipse.microprofile.config.Config config =
                org.eclipse.microprofile.config.ConfigProvider.getConfig();
        assertEquals("IN_MEMORY", config.getValue("embedjnosql.engine", String.class),
                "embedjnosql.engine resolves through SmallRye Config");
        assertEquals("true", config.getValue("embedjnosql.auto-flush", String.class));
    }
}
