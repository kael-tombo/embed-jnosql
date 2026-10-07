package org.embeddedjnosql.db.demo.micronaut;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.model.Order;
import org.embeddedjnosql.db.demo.model.OrderItem;
import org.embeddedjnosql.db.demo.model.Product;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Framework-integration coverage for the Micronaut integration module (APT bean graph +
 * {@code embedjnosql.*} property binding + {@code @SerdeImport} serialization) and the
 * MVCC transaction contract exercised through the Micronaut-managed database.
 */
@MicronautTest
public class MicronautIntegrationCoverageTest {

    private static final long RUN = System.nanoTime();

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    ApplicationContext applicationContext;

    @Inject
    EmbedJNoSQL db;

    @Inject
    JsonMapper jsonMapper;

    // ── APT bean graph from the integration module ────────────────────────

    @Test
    void testEmbedJNoSQLBeanResolvesThroughMicronautAptGraph() {
        EmbedJNoSQL bean = applicationContext.getBean(EmbedJNoSQL.class);
        assertNotNull(bean, "EmbedJNoSQL bean resolves through the integration module's APT metadata");
        assertTrue(bean.isOpen());

        ProductController controller = applicationContext.getBean(ProductController.class);
        List<Product> catalog = controller.getAllProducts();
        assertTrue(catalog.size() >= 5, "seeded catalog is served through the injected controller");
    }

    @Test
    void testEmbedjnosqlPropertiesBindFromApplicationYml() {
        assertEquals(Optional.of("IN_MEMORY"),
                applicationContext.getEnvironment().getProperty("embedjnosql.engine", String.class));
        assertEquals(Optional.of("target/micronaut-data"),
                applicationContext.getEnvironment().getProperty("embedjnosql.data-dir", String.class));
        assertEquals(Optional.of(true),
                applicationContext.getEnvironment().getProperty("embedjnosql.auto-flush", Boolean.class));
        assertEquals(Optional.of(1000),
                applicationContext.getEnvironment().getProperty("embedjnosql.flush-interval-ms", Integer.class));
    }

    @Test
    void testSerdeImportRoundTripsProductRecord() throws IOException {
        Product source = new Product("serde-" + RUN, "SKU-SERDE", "Serde Book", "Test",
                12.5, List.of("micronaut", "serde"), Map.of("pages", 42));

        String json = jsonMapper.writeValueAsString(source);
        Product decoded = jsonMapper.readValue(json, Product.class);

        assertEquals(source.id(), decoded.id());
        assertEquals(source.sku(), decoded.sku());
        assertEquals(source.name(), decoded.name());
        assertEquals(source.price(), decoded.price(), 0.001);
        assertEquals(source.tags(), decoded.tags());
        assertEquals(source.attributes(), decoded.attributes());
    }

    // ── HTTP consistency and error edges ──────────────────────────────────

    @Test
    void testCatalogIsConsistentAcrossRepeatedReads() {
        HttpResponse<List<Product>> first = client.toBlocking()
                .exchange(HttpRequest.GET("/api/products"), Argument.listOf(Product.class));
        HttpResponse<List<Product>> second = client.toBlocking()
                .exchange(HttpRequest.GET("/api/products"), Argument.listOf(Product.class));

        assertEquals(HttpStatus.OK, first.getStatus());
        assertEquals(HttpStatus.OK, second.getStatus());

        Product seededFirst = first.body().stream()
                .filter(p -> "prod-101".equals(p.id())).findFirst().orElseThrow();
        Product seededSecond = second.body().stream()
                .filter(p -> "prod-101".equals(p.id())).findFirst().orElseThrow();
        assertEquals(seededFirst.name(), seededSecond.name(), "repeated reads return the same product data");
        assertEquals(seededFirst.price(), seededSecond.price(), 0.001);
    }

    @Test
    void testMissingProductYields404WithNoBody() {
        io.micronaut.http.client.exceptions.HttpClientResponseException thrown = assertThrows(
                io.micronaut.http.client.exceptions.HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.GET("/api/products/prod-missing-" + RUN), Product.class),
                "the blocking client raises the 404 as HttpClientResponseException");
        assertEquals(HttpStatus.NOT_FOUND, thrown.getStatus(),
                "the exception status is the server's 404");
    }

    @Test
    void testInsufficientStockYields400AndKeepsInventoryIntact() {
        Order tooBig = new Order("ord-micro-big-" + RUN, "ORD-BIG-" + RUN, "cust-001",
                List.of(new OrderItem("prod-102", "Python Handbook", 50, 35.5)),
                1775.0, "PENDING", System.currentTimeMillis());

        io.micronaut.http.client.exceptions.HttpClientResponseException thrown = assertThrows(
                io.micronaut.http.client.exceptions.HttpClientResponseException.class,
                () -> client.toBlocking().exchange(
                        HttpRequest.POST("/api/orders", tooBig),
                        Argument.mapOf(String.class, Object.class)),
                "oversubscribed order is rejected by the server");
        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus(),
                "the rejection maps to a 400: " + thrown.getMessage());

        HttpResponse<Map<String, Object>> inventory = client.toBlocking()
                .exchange(HttpRequest.GET("/api/orders/inventory/prod-102"),
                        Argument.mapOf(String.class, Object.class));
        assertEquals(30, ((Number) inventory.body().get("available")).intValue(),
                "a rejected order must not touch stock");
    }

    // ── MVCC transaction contract on the Micronaut-managed database ──────

    @Test
    void testExplicitTransactionCommitsAtomicallyAcrossCollections() {
        var tx = db.beginTransaction();
        try {
            var colA = tx.documentCollection("micro-tx-a");
            var colB = tx.documentCollection("micro-tx-b");
            colA.insert(new org.embeddedjnosql.db.nosql.document.Document()
                    .id("mta-" + RUN).add("v", 1));
            colB.insert(new org.embeddedjnosql.db.nosql.document.Document()
                    .id("mtb-" + RUN).add("v", 2));
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }

        assertTrue(db.documentCollection("micro-tx-a").findById("mta-" + RUN) != null,
                "first staged write is visible after commit");
        assertTrue(db.documentCollection("micro-tx-b").findById("mtb-" + RUN) != null,
                "second staged write is visible after commit");
    }
}
