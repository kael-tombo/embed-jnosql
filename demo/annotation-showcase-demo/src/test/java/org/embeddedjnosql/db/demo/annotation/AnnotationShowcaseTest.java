package org.embeddedjnosql.db.demo.annotation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.annotation.model.CatalogProduct;
import org.embeddedjnosql.db.demo.annotation.model.CustomerAccount;
import org.embeddedjnosql.db.demo.annotation.model.InvoiceRecord;
import org.embeddedjnosql.db.demo.annotation.model.InvoiceStatus;
import org.embeddedjnosql.db.demo.annotation.repository.CatalogProductRepository;
import org.embeddedjnosql.db.demo.annotation.service.OrderInvoiceService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AnnotationShowcaseTest {

    @TempDir
    Path tempDir;

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed().dataDir(tempDir.resolve("demo_test_db")).buildConfig());
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void testEclipseJnosqlRepositoryFlow() {
        CatalogProductRepository repo = new CatalogProductRepository(db);

        repo.save(new CatalogProduct("P1", "SKU1", "Keyboard", "Peripherals", 75.0, 20));
        repo.save(new CatalogProduct("P2", "SKU2", "Mouse", "Peripherals", 45.0, 5));
        repo.save(new CatalogProduct("P3", "SKU3", "Monitor", "Displays", 300.0, 12));

        assertEquals(3, repo.count());

        List<CatalogProduct> peripherals = repo.findByCategory("Peripherals");
        assertEquals(2, peripherals.size());

        List<CatalogProduct> inStock = repo.findInStock(10);
        assertEquals(2, inStock.size());
        assertEquals("P1", inStock.get(0).getId());
    }

    @Test
    void testAnnotationDrivenDocumentPersistence() {
        OrderInvoiceService service = new OrderInvoiceService(db);

        CustomerAccount cust = service.registerCustomer("CUST-1", "John Doe", "john@test.com", "GOLD", 200.0);
        assertNotNull(cust);

        CustomerAccount foundCust = service.findCustomer("CUST-1");
        assertNotNull(foundCust);
        assertEquals("John Doe", foundCust.getFullName());

        InvoiceRecord inv = service.createInvoice("CUST-1", 100.0, InvoiceStatus.PAID);
        assertNotNull(inv.getInvoiceId(), "the @UuidGenerator annotation should populate the id");
        assertNotNull(inv.getCreatedAt(), "the @CreationTimestamp annotation should populate createdAt");
        assertNotNull(inv.getUpdatedAt(), "the @UpdateTimestamp annotation should populate updatedAt");
        assertEquals(120.0, inv.getTotalWithTax(), 0.001, "@Formula should compute amount * 1.20");

        List<InvoiceRecord> customerInvoices = service.findInvoicesByCustomer("CUST-1");
        assertEquals(1, customerInvoices.size());
        assertEquals(inv.getInvoiceId(), customerInvoices.get(0).getInvoiceId());
    }

    @Test
    void testDocumentAggregationAndEnrichment() {
        OrderInvoiceService service = new OrderInvoiceService(db);

        service.registerCustomer("C1", "Client One", "c1@test.com", "SILVER", 50.0);
        service.createInvoice("C1", 100.0, InvoiceStatus.PAID);
        service.createInvoice("C1", 200.0, InvoiceStatus.PAID);
        service.createInvoice("C1", 50.0, InvoiceStatus.PENDING);

        List<Map<String, Object>> summary = service.getInvoiceSummary();
        assertNotNull(summary);
        assertTrue(summary.size() >= 1, "at least one status group");
        // PAID totals 300, PENDING totals 50 → PAID is the first (descending) group.
        assertEquals(InvoiceStatus.PAID.name(), summary.get(0).get("status"));
        assertEquals(2L, ((Number) summary.get(0).get("count")).longValue());

        List<Map<String, Object>> details = service.getCustomerInvoiceDetails();
        assertNotNull(details);
        assertEquals(3, details.size());
        assertEquals("Client One", details.get(0).get("full_name"));
    }

    @Test
    void testFluentEntityQueryAndRangeFilter() {
        CatalogProductRepository repo = new CatalogProductRepository(db);

        repo.save(new CatalogProduct("P10", "SKU10", "LowEnd Mouse", "Peripherals", 20.0, 10));
        repo.save(new CatalogProduct("P11", "SKU11", "MidEnd Mouse", "Peripherals", 60.0, 15));
        repo.save(new CatalogProduct("P12", "SKU12", "Pro Gaming Mouse", "Peripherals", 120.0, 5));
        repo.save(new CatalogProduct("P13", "SKU13", "4K OLED Monitor", "Displays", 800.0, 8));

        // Native range filter
        List<CatalogProduct> midRange = repo.findByPriceBetween(50.0, 150.0);
        assertEquals(2, midRange.size());
        assertEquals("P11", midRange.get(0).getId());
        assertEquals("P12", midRange.get(1).getId());

        // Fluent entity builder query
        List<CatalogProduct> filtered = repo.findByFluentCategory("Peripherals", 70.0);
        assertEquals(2, filtered.size());
        assertEquals("LowEnd Mouse", filtered.get(0).getTitle());
        assertEquals("MidEnd Mouse", filtered.get(1).getTitle());
    }

    @Test
    void testInMemoryDatabaseLifecycle() throws Exception {
        try (EmbedJNoSQL inMem = EmbedJNoSQL.inMemory()) {
            CatalogProductRepository repo = new CatalogProductRepository(inMem);
            repo.save(new CatalogProduct("M1", "SKU-M1", "RAM 32GB", "Hardware", 150.0, 50));

            assertEquals(1, repo.count());
            var item = inMem.from(CatalogProduct.class).where("sku = ?", "SKU-M1").first();
            assertTrue(item.isPresent());
            assertEquals("RAM 32GB", item.get().getTitle());
        }
    }
}
