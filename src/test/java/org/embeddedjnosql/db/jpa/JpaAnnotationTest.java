package org.embeddedjnosql.db.jpa;

import jakarta.persistence.*;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that EmbedJNoSQL's EntityMapper transparently handles
 * {@code jakarta.persistence.*} (JPA / Hibernate) annotations.
 *
 * <p>No JPA runtime is needed — EmbedJNoSQL detects annotations via reflection.
 */
@DisplayName("JPA / Hibernate Annotation Support")
class JpaAnnotationTest {

    // -----------------------------------------------------------------------
    // Sample JPA-annotated entities (inner classes)
    // -----------------------------------------------------------------------

    /** Basic entity using standard JPA annotations. */
    @jakarta.persistence.Entity(name = "products")
    static class Product {
        @jakarta.persistence.Id
        @GeneratedValue
        private String id;

        @jakarta.persistence.Column(name = "product_name")
        private String name;

        private double price;

        @jakarta.persistence.Transient
        private transient String internalCache;

        public Product() {}

        public Product(String name, double price) {
            this.name = name;
            this.price = price;
        }

        public String getId()    { return id; }
        public String getName()  { return name; }
        public double getPrice() { return price; }
        public String getInternalCache() { return internalCache; }
    }

    /** Entity using JPA @Entity without an explicit name → falls back to class simple name. */
    @jakarta.persistence.Entity
    static class Customer {
        @jakarta.persistence.Id
        private String customerId;

        private String email;

        public Customer() {}
        public Customer(String customerId, String email) {
            this.customerId = customerId;
            this.email = email;
        }

        public String getCustomerId() { return customerId; }
        public String getEmail()      { return email; }
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
    @DisplayName("JPA 1: @Entity(name) is used as the collection name")
    void testJpaEntityNameMapsToCollection() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("products"));

        Product p = template.insert(new Product("Widget", 9.99));
        assertNotNull(p, "insert must return the entity");

        List<Product> all = template.findAll(Product.class);
        assertEquals(1, all.size());
        assertEquals("Widget", all.get(0).getName());
    }

    @Test
    @DisplayName("JPA 2: @Id field is mapped as the document id; @GeneratedValue auto-assigns UUID")
    void testJpaIdAndGeneratedValue() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("products"));

        Product p = new Product("Gadget", 49.99);
        assertNull(p.getId(), "Id must be null before insert");

        template.insert(p);
        assertNotNull(p.getId(), "Id must be assigned after insert via @GeneratedValue");

        Optional<Product> found = template.find(Product.class, p.getId());
        assertTrue(found.isPresent());
        assertEquals("Gadget", found.get().getName());
    }

    @Test
    @DisplayName("JPA 3: @Column(name) overrides the document field name")
    void testJpaColumnNameOverride() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("products"));

        Product p = new Product("Doohickey", 19.99);
        template.insert(p);

        // The underlying document should use "product_name", not "name"
        var raw = db.documentCollection("products").findById(p.getId());
        assertNotNull(raw);
        assertNotNull(raw.get("product_name"),
                "Field must be stored under @Column(name=\"product_name\")");
        assertNull(raw.get("name"),
                "Original Java field name 'name' must NOT appear in the document");
    }

    @Test
    @DisplayName("JPA 4: @Transient field is excluded from the document")
    void testJpaTransientExcluded() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("products"));

        Product p = new Product("Thingamajig", 5.00);
        template.insert(p);

        var raw = db.documentCollection("products").findById(p.getId());
        assertNotNull(raw);
        assertNull(raw.get("internalCache"),
                "@Transient field must not be stored in the document");
    }

    @Test
    @DisplayName("JPA 5: @Entity without explicit name falls back to lower-case class name")
    void testJpaEntityDefaultName() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("customer"));

        Customer c = new Customer("C-001", "alice@example.com");
        template.insert(c);

        Optional<Customer> found = template.find(Customer.class, "C-001");
        assertTrue(found.isPresent());
        assertEquals("alice@example.com", found.get().getEmail());
    }

    @Test
    @DisplayName("JPA 6: update and deleteById work with JPA-annotated entities")
    void testJpaUpdateAndDelete() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("customer"));

        Customer c = new Customer("C-002", "bob@example.com");
        template.insert(c);

        // Update
        Customer updated = new Customer("C-002", "bob.updated@example.com");
        template.update(updated);

        Optional<Customer> found = template.find(Customer.class, "C-002");
        assertTrue(found.isPresent());
        assertEquals("bob.updated@example.com", found.get().getEmail());

        // Delete
        template.deleteById(Customer.class, "C-002");
        assertFalse(template.find(Customer.class, "C-002").isPresent());
    }
}
