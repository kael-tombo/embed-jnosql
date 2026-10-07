package org.junify.db.demo.spring;

import org.junify.db.JunifyDB;
import org.junify.db.demo.model.Order;
import org.junify.db.demo.model.OrderItem;
import org.junify.db.demo.model.Product;
import org.junify.db.console.http.JunifyDBServer;
import org.junify.db.spring.boot.JunifyDBTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EcommerceApplicationTest {

    @Autowired
    private JunifyDB db;

    @Autowired
    private JunifyDBTemplate template;

    @Autowired(required = false)
    private JunifyDBServer consoleServer;

    @Autowired
    private ProductService productService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private EcommerceController ecommerceController;

    @Test
    void testContextAndBeansInjected() {
        assertNotNull(db, "JunifyDB bean should be injected");
        assertNotNull(template, "JunifyDBTemplate bean should be injected");
        assertTrue(db.isOpen(), "Database should be open");
    }

    @Test
    void testAdminConsoleConfigurationAndAvailability() throws Exception {
        assertNotNull(consoleServer, "JunifyDBServer bean should be auto-configured and injected");
        assertEquals(9090, consoleServer.port(), "Admin console should bind to configured port 9090");

        String consoleUrl = db.consoleUrl();
        assertNotNull(consoleUrl, "Console URL should not be null");
        assertEquals("http://localhost:9090/junify-console/", consoleUrl, "Console URL should reflect configured context path and port");

        // Verify HTTP endpoint connectivity & security headers
        URI uri = URI.create(consoleUrl + "api/health");
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.connect();

        // Auth is enabled in application.yml, so anonymous request receives 401
        int status = conn.getResponseCode();
        assertEquals(401, status, "Unauthenticated health check should return 401 Unauthorized");
        assertEquals("nosniff", conn.getHeaderField("X-Content-Type-Options"), "Security headers must be present");
        assertEquals("DENY", conn.getHeaderField("X-Frame-Options"), "Security headers must be present");
        conn.disconnect();
    }

    @Test
    void testProductCrudAndQuery() {
        Product book = new Product(
                "prod-test-01",
                "BOOK-01",
                "Java Architecture Guide",
                "Books",
                45.00,
                List.of("java", "architecture"),
                Map.of("edition", 3)
        );

        productService.save(book);

        Optional<Product> fetched = productService.findById("prod-test-01");
        assertTrue(fetched.isPresent(), "Product should be found by ID");
        assertEquals("Java Architecture Guide", fetched.get().name());
        assertEquals("Books", fetched.get().category());

        List<Product> books = productService.findByCategory("Books");
        assertFalse(books.isEmpty(), "Category search should find books");
        assertTrue(books.stream().anyMatch(p -> "BOOK-01".equals(p.sku())));
    }

    @Test
    void testTransactionalOrderPlacement() {
        Order order = new Order(
                "ord-test-99",
                "ORD-99",
                "cust-001",
                List.of(
                        new OrderItem("prod-101", "Developer Ultrabook", 1, 1899.99),
                        new OrderItem("prod-102", "Mechanical Keyboard", 2, 249.50)
                ),
                0.0,
                "NEW",
                System.currentTimeMillis()
        );

        Order placed = orderService.placeOrder(order);
        assertNotNull(placed);
        assertEquals(2398.99, placed.totalAmount(), 0.01);
        assertEquals("CONFIRMED", placed.status());

        Order reloaded = orderService.getOrder("ord-test-99");
        assertNotNull(reloaded, "Order should be persisted in orders collection");
        assertEquals("cust-001", reloaded.customerId());
        assertEquals(2, reloaded.items().size());
    }

    @Test
    void testCategoryAndPriceRangeQuery() {
        productService.save(new Product("rng-p1", "SKU-RNG1", "Pro Keyboard", "Peripherals", 120.0, List.of("electronics"), Map.of()));
        productService.save(new Product("rng-p2", "SKU-RNG2", "Basic Mouse", "Peripherals", 25.0, List.of("electronics"), Map.of()));
        productService.save(new Product("rng-p3", "SKU-RNG3", "Wireless Mouse", "Peripherals", 65.0, List.of("electronics"), Map.of()));

        List<Product> matches = productService.findByCategoryAndPriceRange("Peripherals", 50.0, 150.0);
        assertEquals(2, matches.size());
        assertEquals("Wireless Mouse", matches.get(0).name());
        assertEquals("Pro Keyboard", matches.get(1).name());
    }

    @Test
    void testProductsRestControllerWithPriceRange() {
        @SuppressWarnings("unchecked")
        List<Product> products = ecommerceController.getAllProducts("Peripherals", 50.0, 150.0);
        assertNotNull(products);
        assertTrue(products.size() >= 2);
    }
}
