package org.embeddedjnosql.db.demo.quarkus;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
public class ProductResourceTest {

    @Test
    public void testGetAllProducts() {
        given()
                .when().get("/api/products")
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(5));
    }

    @Test
    public void testGetProductById() {
        given()
                .when().get("/api/products/prod-101")
                .then()
                .statusCode(200)
                .body("id", equalTo("prod-101"))
                .body("name", equalTo("Developer Ultrabook 16"));
    }

    @Test
    public void testCreateProductAndCache() {
        String newProductJson = "{\n" +
                "  \"id\": \"prod-quarkus-test\",\n" +
                "  \"sku\": \"TECH-QUARK-01\",\n" +
                "  \"name\": \"Quarkus in Action\",\n" +
                "  \"price\": 45.99,\n" +
                "  \"category\": \"Books\",\n" +
                "  \"tags\": [\"quarkus\", \"java\"],\n" +
                "  \"attributes\": {\"pages\": 350}\n" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(newProductJson)
                .when().post("/api/products")
                .then()
                .statusCode(201)
                .body("id", equalTo("prod-quarkus-test"));

        // Verify cached price via KV bucket
        given()
                .when().get("/api/products/prod-quarkus-test/cached-price")
                .then()
                .statusCode(200)
                .body("cachedPrice", equalTo(45.99f));
    }

    @Test
    public void testPlaceOrderTransactional() {
        String orderJson = "{\n" +
                "  \"id\": \"ord-quarkus-1\",\n" +
                "  \"orderNumber\": \"ORD-Q-001\",\n" +
                "  \"customerId\": \"cust-001\",\n" +
                "  \"items\": [\n" +
                "    {\"productId\": \"prod-101\", \"productName\": \"Developer Ultrabook 16\", \"quantity\": 2, \"unitPrice\": 1899.99}\n" +
                "  ],\n" +
                "  \"totalAmount\": 3799.98\n" +
                "}";

        given()
                .contentType(ContentType.JSON)
                .body(orderJson)
                .when().post("/api/orders")
                .then()
                .statusCode(201)
                .body("status", equalTo("CONFIRMED"));

        // Verify inventory deducted: 50 - 2 = 48
        given()
                .when().get("/api/orders/inventory/prod-101")
                .then()
                .statusCode(200)
                .body("available", equalTo(48));
    }
}
