package org.embeddedjnosql.db.demo.vertx;

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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
public class EcommerceVerticleTest {

    private static final int PORT = 18084;
    private WebClient client;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext testContext) {
        client = WebClient.create(vertx);
        vertx.deployVerticle(new EcommerceVerticle(PORT))
                .onSuccess(id -> testContext.completeNow())
                .onFailure(testContext::failNow);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void testGetAllProducts(Vertx vertx, VertxTestContext testContext) {
        client.get(PORT, "localhost", "/api/products")
                .send()
                .onSuccess(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonArray array = response.bodyAsJsonArray();
                        assertNotNull(array);
                        assertTrue(array.size() >= 5);
                    });
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    void testGetProductById(Vertx vertx, VertxTestContext testContext) {
        client.get(PORT, "localhost", "/api/products/prod-101")
                .send()
                .onSuccess(response -> {
                    testContext.verify(() -> {
                        assertEquals(200, response.statusCode());
                        JsonObject product = response.bodyAsJsonObject();
                        assertEquals("prod-101", product.getString("id"));
                        assertEquals("Developer Ultrabook 16", product.getString("name"));
                    });
                    testContext.completeNow();
                })
                .onFailure(testContext::failNow);
    }

    @Test
    void testCreateProductAndCache(Vertx vertx, VertxTestContext testContext) {
        JsonObject newProduct = new JsonObject()
                .put("id", "prod-vertx-1")
                .put("sku", "TECH-VTX-01")
                .put("name", "Reactive Systems in Java")
                .put("category", "Books")
                .put("price", 54.99);

        client.post(PORT, "localhost", "/api/products")
                .sendJsonObject(newProduct)
                .onSuccess(postResp -> {
                    testContext.verify(() -> {
                        assertEquals(201, postResp.statusCode());
                        assertEquals("prod-vertx-1", postResp.bodyAsJsonObject().getString("id"));
                    });

                    // Now check cached price in KV bucket
                    client.get(PORT, "localhost", "/api/products/prod-vertx-1/cached-price")
                            .send()
                            .onSuccess(cacheResp -> {
                                testContext.verify(() -> {
                                    assertEquals(200, cacheResp.statusCode());
                                    assertEquals(54.99, cacheResp.bodyAsJsonObject().getDouble("cachedPrice"), 0.001);
                                });
                                testContext.completeNow();
                            })
                            .onFailure(testContext::failNow);
                })
                .onFailure(testContext::failNow);
    }

    @Test
    void testPlaceOrderTransactional(Vertx vertx, VertxTestContext testContext) {
        JsonObject order = new JsonObject()
                .put("id", "ord-vertx-1")
                .put("orderNumber", "ORD-V-001")
                .put("customerId", "cust-001")
                .put("totalAmount", 3799.98)
                .put("items", new JsonArray().add(new JsonObject()
                        .put("productId", "prod-101")
                        .put("productName", "Developer Ultrabook 16")
                        .put("quantity", 2)
                        .put("unitPrice", 1899.99)));

        client.post(PORT, "localhost", "/api/orders")
                .sendJsonObject(order)
                .onSuccess(orderResp -> {
                    testContext.verify(() -> {
                        assertEquals(201, orderResp.statusCode());
                        assertEquals("CONFIRMED", orderResp.bodyAsJsonObject().getString("status"));
                    });

                    // Verify inventory deducted: 50 - 2 = 48
                    client.get(PORT, "localhost", "/api/orders/inventory/prod-101")
                            .send()
                            .onSuccess(invResp -> {
                                testContext.verify(() -> {
                                    assertEquals(200, invResp.statusCode());
                                    assertEquals(48, invResp.bodyAsJsonObject().getInteger("available"));
                                });
                                testContext.completeNow();
                            })
                            .onFailure(testContext::failNow);
                })
                .onFailure(testContext::failNow);
    }
}
