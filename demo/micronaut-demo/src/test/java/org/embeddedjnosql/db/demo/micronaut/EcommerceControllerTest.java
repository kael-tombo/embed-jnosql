package org.embeddedjnosql.db.demo.micronaut;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.demo.model.Product;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
public class EcommerceControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testGetAllProducts() {
        var request = HttpRequest.GET("/api/products");
        HttpResponse<List<Product>> response = client.toBlocking().exchange(request, Argument.listOf(Product.class));

        assertEquals(HttpStatus.OK, response.getStatus());
        List<Product> products = response.body();
        assertNotNull(products);
        assertTrue(products.size() >= 5);
    }

    @Test
    void testGetProductById() {
        var request = HttpRequest.GET("/api/products/prod-101");
        HttpResponse<Product> response = client.toBlocking().exchange(request, Product.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        Product product = response.body();
        assertNotNull(product);
        assertEquals("prod-101", product.id());
        assertEquals("Developer Ultrabook 16", product.name());
    }

    @Test
    void testCreateProductAndCache() {
        Product newProduct = new Product(
                "prod-micro-1",
                "TECH-MIC-01",
                "Micronaut Microservice Blueprint",
                "Books",
                39.99,
                List.of("micronaut", "java"),
                Map.of("pages", 280)
        );

        var postReq = HttpRequest.POST("/api/products", newProduct);
        HttpResponse<Product> postResp = client.toBlocking().exchange(postReq, Product.class);

        assertEquals(HttpStatus.CREATED, postResp.getStatus());
        assertEquals("prod-micro-1", postResp.body().id());

        // Check cached price
        var cacheReq = HttpRequest.GET("/api/products/prod-micro-1/cached-price");
        HttpResponse<Map> cacheResp = client.toBlocking().exchange(cacheReq, Map.class);
        assertEquals(HttpStatus.OK, cacheResp.getStatus());
        assertEquals(39.99, ((Number) cacheResp.body().get("cachedPrice")).doubleValue(), 0.001);
    }

    @Test
    void testPlaceOrderTransactional() {
        Order order = new Order(
                "ord-micro-1",
                "ORD-M-001",
                "cust-001",
                List.of(new OrderItem("prod-101", "Developer Ultrabook 16", 2, 1899.99)),
                3799.98,
                "PENDING",
                System.currentTimeMillis()
        );

        var orderReq = HttpRequest.POST("/api/orders", order);
        HttpResponse<Order> orderResp = client.toBlocking().exchange(orderReq, Order.class);

        assertEquals(HttpStatus.CREATED, orderResp.getStatus());
        assertEquals("CONFIRMED", orderResp.body().status());

        // Check stock: 50 - 2 = 48
        var invReq = HttpRequest.GET("/api/orders/inventory/prod-101");
        HttpResponse<Map> invResp = client.toBlocking().exchange(invReq, Map.class);
        assertEquals(HttpStatus.OK, invResp.getStatus());
        assertEquals(48, ((Number) invResp.body().get("available")).intValue());
    }
}
