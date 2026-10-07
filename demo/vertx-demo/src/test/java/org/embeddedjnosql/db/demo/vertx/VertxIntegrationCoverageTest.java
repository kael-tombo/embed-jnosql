package org.embeddedjnosql.db.demo.vertx;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Framework-integration coverage for the Vert.x reactive stack: the event loop never
 * blocks (every handler funnels EmbedJNoSQL calls through {@code executeBlocking}),
 * futures compose, and the MVCC transaction behind {@code POST /api/orders} rolls back
 * atomically when stock runs out.
 */
@ExtendWith(VertxExtension.class)
public class VertxIntegrationCoverageTest {

    private static final int PORT = 18085;
    private static final String PREFIX = "vtx-" + System.nanoTime() + "-";

    private WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        client = WebClient.create(vertx);
        vertx.deployVerticle(new EcommerceVerticle(PORT))
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @AfterEach
    void tearDown(VertxTestContext testContext) {
        if (client != null) {
            client.close();
        }
        testContext.completeNow();
    }

    // ── Server-side behavior over the reactive web client ────────────────

    @Test
    void testCreateProductWithoutIdGeneratesServerId(Vertx vertx, VertxTestContext testContext) {
        JsonObject noId = new JsonObject()
                .put("sku", "TECH-VTX-SRV")
                .put("name", "Server Id Book")
                .put("category", "Books")
                .put("price", 21.5);

        client.post(PORT, "localhost", "/api/products")
                .sendJsonObject(noId)
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(201, resp.statusCode());
                    String generatedId = resp.bodyAsJsonObject().getString("id");
                    assertNotNull(generatedId, "server assigns an id when the client omits it");
                    assertTrue(generatedId.startsWith("prod-"), "generated id follows the prod- convention");
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testOrderPersistsAndIsListedAfterwards(Vertx vertx, VertxTestContext testContext) {
        JsonObject order = orderJson(PREFIX + "ord-persist", "ORD-P-1", "prod-101", 1);

        client.post(PORT, "localhost", "/api/orders")
                .sendJsonObject(order)
                .onSuccess(postResp -> client.get(PORT, "localhost", "/api/orders")
                        .send()
                        .onSuccess(listResp -> testContext.verify(() -> {
                            assertEquals(201, postResp.statusCode());
                            assertEquals(200, listResp.statusCode());
                            JsonArray orders = listResp.bodyAsJsonArray();
                            boolean found = false;
                            for (int i = 0; i < orders.size(); i++) {
                                if ((PREFIX + "ord-persist").equals(orders.getJsonObject(i).getString("id"))) {
                                    found = true;
                                }
                            }
                            assertTrue(found, "the confirmed order appears in the orders listing");
                            testContext.completeNow();
                        }))
                        .onFailure(testContext::failNow))
                .onFailure(testContext::failNow);
    }

    @Test
    void testConcurrentReadsAllReturnSeededCatalog(Vertx vertx, VertxTestContext testContext) {
        List<Future<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>>> reads =
                java.util.stream.IntStream.range(0, 8)
                        .mapToObj(i -> client.get(PORT, "localhost", "/api/products").send())
                        .toList();

        Future.all(reads)
                .onSuccess(composite -> testContext.verify(() -> {
                    for (int i = 0; i < reads.size(); i++) {
                        io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer> resp =
                                composite.resultAt(i);
                        assertEquals(200, resp.statusCode(), "read " + i + " must succeed");
                        assertTrue(resp.bodyAsJsonArray().size() >= 5,
                                "read " + i + " must see the seeded catalog");
                    }
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testInsufficientStockOrderRollsBackAtomically(Vertx vertx, VertxTestContext testContext) {
        JsonObject tooBig = orderJson(PREFIX + "ord-big", "ORD-BIG-1", "prod-101", 100000);

        client.post(PORT, "localhost", "/api/orders")
                .sendJsonObject(tooBig)
                .onSuccess(rejected -> client.get(PORT, "localhost", "/api/orders/inventory/prod-101")
                        .send()
                        .onSuccess(invResp -> testContext.verify(() -> {
                            assertEquals(400, rejected.statusCode(),
                                    "oversubscribed order is rejected: " + rejected.body());
                            assertEquals(50, invResp.bodyAsJsonObject().getInteger("available"),
                                    "stock is untouched after the rolled-back order");
                            testContext.completeNow();
                        }))
                        .onFailure(testContext::failNow))
                .onFailure(testContext::failNow);
    }

    @Test
    void testInventory404ForUnknownProduct(Vertx vertx, VertxTestContext testContext) {
        client.get(PORT, "localhost", "/api/orders/inventory/" + PREFIX + "ghost")
                .send()
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(404, resp.statusCode());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testUnknownProductRouteReturns404(Vertx vertx, VertxTestContext testContext) {
        client.get(PORT, "localhost", "/api/products/" + PREFIX + "ghost")
                .send()
                .onSuccess(resp -> testContext.verify(() -> {
                    assertEquals(404, resp.statusCode());
                    testContext.completeNow();
                }))
                .onFailure(testContext::failNow);
    }

    @Test
    void testConcurrentOrderPlacementSerializesStock(Vertx vertx, VertxTestContext testContext) {
        // prod-103 seeds 100 units; 4 concurrent orders of 10 each must all confirm
        // and leave exactly 60 behind — executeBlocking + MVCC serialize the stock reads.
        List<Future<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>>> placements =
                java.util.stream.IntStream.range(0, 4)
                        .mapToObj(i -> client.post(PORT, "localhost", "/api/orders")
                                .sendJsonObject(orderJson(
                                        PREFIX + "ord-race-" + i, "ORD-RACE-" + i, "prod-103", 10)))
                        .toList();

        Future.all(placements)
                .onSuccess(composite -> client.get(PORT, "localhost", "/api/orders/inventory/prod-103")
                        .send()
                        .onSuccess(invResp -> testContext.verify(() -> {
                            for (int i = 0; i < placements.size(); i++) {
                                assertEquals(201, composite.<io.vertx.ext.web.client.HttpResponse<io.vertx.core.buffer.Buffer>>resultAt(i).statusCode(),
                                        "racing order " + i + " must confirm");
                            }
                            assertEquals(60, invResp.bodyAsJsonObject().getInteger("available"),
                                    "stock decrements serialize exactly: 100 - 4x10 = 60");
                            testContext.completeNow();
                        }))
                        .onFailure(testContext::failNow))
                .onFailure(testContext::failNow);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static JsonObject orderJson(String id, String orderNumber, String productId, int quantity) {
        return new JsonObject()
                .put("id", id)
                .put("orderNumber", orderNumber)
                .put("customerId", "cust-001")
                .put("totalAmount", quantity * 1899.99)
                .put("items", new JsonArray().add(new JsonObject()
                        .put("productId", productId)
                        .put("productName", "Developer Ultrabook 16")
                        .put("quantity", quantity)
                        .put("unitPrice", 1899.99)));
    }
}
