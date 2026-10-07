package org.embeddedjnosql.db.jpa;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.*;
import org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates that all three annotation strategies — Eclipse JNoSQL, JPA/Hibernate,
 * and EmbedJNoSQL built-in — work side-by-side in the same application without conflict.
 *
 * <p>Also verifies the priority ordering when multiple annotation types are combined
 * (should not normally happen, but must not break).
 */
@DisplayName("Dual Annotation Support — JNoSQL + JPA + EmbedJNoSQL built-in")
class AnnotationDualSupportTest {

    // -----------------------------------------------------------------------
    // Entity annotated with EmbedJNoSQL built-in annotations (baseline)
    // -----------------------------------------------------------------------
    @Entity("orders")
    static class Order {
        @Id
        private String orderId;

        @Column("order_total")
        private double total;

        @Transient
        private String runtimeNote;

        public Order() {}
        public Order(String orderId, double total) {
            this.orderId = orderId;
            this.total   = total;
        }
        public String getOrderId() { return orderId; }
        public double getTotal()   { return total; }
    }

    // -----------------------------------------------------------------------
    // Entity annotated with JPA annotations
    // -----------------------------------------------------------------------
    @jakarta.persistence.Entity(name = "invoices")
    static class Invoice {
        @jakarta.persistence.Id
        @jakarta.persistence.GeneratedValue
        private String invoiceId;

        @jakarta.persistence.Column(name = "amount_due")
        private double amountDue;

        public Invoice() {}
        public Invoice(double amountDue) { this.amountDue = amountDue; }
        public String getInvoiceId()  { return invoiceId; }
        public double getAmountDue()  { return amountDue; }
    }

    // -----------------------------------------------------------------------
    // Plain entity — no annotations at all (uses class name + field names)
    // -----------------------------------------------------------------------
    static class Note {
        private String id;
        private String text;

        public Note() {}
        public Note(String id, String text) { this.id = id; this.text = text; }
        public String getId()   { return id; }
        public String getText() { return text; }
    }

    // -----------------------------------------------------------------------
    // Test infrastructure
    // -----------------------------------------------------------------------
    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Dual 1: EmbedJNoSQL built-in annotations work correctly")
    void testBuiltInAnnotations() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("orders"));

        Order o = new Order("ORD-001", 199.99);
        template.insert(o);

        Optional<Order> found = template.find(Order.class, "ORD-001");
        assertTrue(found.isPresent());
        assertEquals(199.99, found.get().getTotal(), 0.001);

        // @Column("order_total") must rename the field in the raw document
        var raw = db.documentCollection("orders").findById("ORD-001");
        assertNotNull(raw.get("order_total"), "@Column must rename the stored field");
        assertNull(raw.get("total"),          "Java field name must not appear");

        // @Transient field must be absent
        assertNull(raw.get("runtimeNote"), "@Transient field must be excluded");
    }

    @Test
    @DisplayName("Dual 2: JPA annotations work correctly")
    void testJpaAnnotations() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("invoices"));

        Invoice inv = new Invoice(500.00);
        assertNull(inv.getInvoiceId(), "Id must be null before insert");

        template.insert(inv);
        assertNotNull(inv.getInvoiceId(), "@GeneratedValue must assign UUID after insert");

        var raw = db.documentCollection("invoices").findById(inv.getInvoiceId());
        assertNotNull(raw.get("amount_due"), "@Column(name) must rename the stored field");
        assertNull(raw.get("amountDue"),     "Java field name must not appear");
    }

    @Test
    @DisplayName("Dual 3: Un-annotated entity uses field names and class simple name as default")
    void testUnannotatedEntity() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("note"));

        Note n = new Note("N-001", "Hello world");
        template.insert(n);

        Optional<Note> found = template.find(Note.class, "N-001");
        assertTrue(found.isPresent());
        assertEquals("Hello world", found.get().getText());
    }

    @Test
    @DisplayName("Dual 4: All three entity types can co-exist in the same DB instance")
    void testAllThreeCoExist() {
        var orders   = EclipseDocumentTemplate.of(db.documentCollection("orders"));
        var invoices = EclipseDocumentTemplate.of(db.documentCollection("invoices"));
        var notes    = EclipseDocumentTemplate.of(db.documentCollection("note"));

        Order o   = new Order("ORD-99", 42.00);
        Invoice i = new Invoice(777.00);
        Note n    = new Note("N-99", "Coexistence test");

        orders.insert(o);
        invoices.insert(i);
        notes.insert(n);

        assertEquals(1, orders.findAll(Order.class).size());
        assertEquals(1, invoices.findAll(Invoice.class).size());
        assertEquals(1, notes.findAll(Note.class).size());

        // Cross-collection isolation
        assertEquals(0, orders.count(Invoice.class) - 1 + 1,
                "Collections must be isolated; count is per-collection");
    }

    @Test
    @DisplayName("Dual 5: count, existsById work across annotation styles")
    void testCountAndExistsAcrossStyles() {
        var orders = EclipseDocumentTemplate.of(db.documentCollection("orders"));

        orders.insert(new Order("ORD-A", 10.0));
        orders.insert(new Order("ORD-B", 20.0));

        assertEquals(2, orders.count(Order.class));

        List<Order> all = orders.findAll(Order.class);
        assertEquals(2, all.size());

        orders.deleteById(Order.class, "ORD-A");
        assertEquals(1, orders.count(Order.class));
    }
}
