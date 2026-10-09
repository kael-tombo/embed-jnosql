package org.embeddedjnosql.db.jnosql;

import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.nosql.AttributeConverter;
import jakarta.nosql.Column;
import jakarta.nosql.Convert;
import jakarta.nosql.DiscriminatorColumn;
import jakarta.nosql.DiscriminatorValue;
import jakarta.nosql.Embeddable;
import jakarta.nosql.Entity;
import jakarta.nosql.Id;
import jakarta.nosql.Inheritance;
import jakarta.nosql.MappedSuperclass;
import jakarta.nosql.Template;
import org.eclipse.jnosql.communication.Settings;
import org.eclipse.jnosql.mapping.Database;
import org.eclipse.jnosql.mapping.DatabaseType;
import org.eclipse.jnosql.mapping.NoSQLRepository;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate;
import org.embeddedjnosql.db.adapter.jnosql.EmbedDBProducer;
import org.embeddedjnosql.db.adapter.jnosql.EmbedJNoSQLConfiguration;
import org.embeddedjnosql.db.adapter.jnosql.EmbedNoSQLRepository;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Eclipse JNoSQL & Jakarta NoSQL Full Integration Suite")
class EclipseJNoSQLFullIntegrationTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.inMemory();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    // =========================================================================
    // Test Entities
    // =========================================================================

    @Entity("books")
    static class Book {
        @Id
        private String isbn;

        @Column("book_title")
        private String title;

        @Column
        private String author;

        @Column
        private double price;

        public Book() {}

        public Book(String isbn, String title, String author, double price) {
            this.isbn = isbn;
            this.title = title;
            this.author = author;
            this.price = price;
        }

        public String getIsbn() { return isbn; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
    }

    // Entity with @Embeddable and @Convert
    @Embeddable
    static class Address {
        @Column("street_name")
        private String street;
        @Column("city_name")
        private String city;

        public Address() {}
        public Address(String street, String city) {
            this.street = street;
            this.city = city;
        }
        public String getStreet() { return street; }
        public String getCity() { return city; }
    }

    static class Money {
        private final double amount;
        private final String currency;
        public Money(double amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
        public double getAmount() { return amount; }
        public String getCurrency() { return currency; }
    }

    public static class MoneyConverter implements AttributeConverter<Money, String> {
        @Override
        public String convertToDatabaseColumn(Money attribute) {
            if (attribute == null) return null;
            return attribute.getCurrency() + ":" + attribute.getAmount();
        }

        @Override
        public Money convertToEntityAttribute(String dbData) {
            if (dbData == null || !dbData.contains(":")) return null;
            String[] parts = dbData.split(":", 2);
            return new Money(Double.parseDouble(parts[1]), parts[0]);
        }
    }

    @Entity("customers")
    static class Customer {
        @Id
        private String id;
        @Column("full_name")
        private String name;
        @Column
        private Address address;
        @Column
        @Convert(MoneyConverter.class)
        private Money balance;

        public Customer() {}
        public Customer(String id, String name, Address address, Money balance) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.balance = balance;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public Address getAddress() { return address; }
        public Money getBalance() { return balance; }
    }

    // Inheritance & Polymorphism
    @Entity("vehicles")
    @Inheritance
    @DiscriminatorColumn("v_type")
    static class Vehicle {
        @Id
        private String vin;
        @Column
        private String brand;

        public Vehicle() {}
        public Vehicle(String vin, String brand) {
            this.vin = vin;
            this.brand = brand;
        }
        public String getVin() { return vin; }
        public String getBrand() { return brand; }
    }

    @Entity("vehicles")
    @DiscriminatorValue("CAR")
    static class Car extends Vehicle {
        @Column("num_doors")
        private int doors;

        public Car() {}
        public Car(String vin, String brand, int doors) {
            super(vin, brand);
            this.doors = doors;
        }
        public int getDoors() { return doors; }
    }

    @Entity("vehicles")
    @DiscriminatorValue("TRUCK")
    static class Truck extends Vehicle {
        @Column("payload_ton")
        private double payload;

        public Truck() {}
        public Truck(String vin, String brand, double payload) {
            super(vin, brand);
            this.payload = payload;
        }
        public double getPayload() { return payload; }
    }

    // MappedSuperclass
    @MappedSuperclass
    static abstract class BaseAuditEntity {
        @Id
        private String id;
        @Column("created_by")
        private String createdBy;

        public BaseAuditEntity() {}
        public BaseAuditEntity(String id, String createdBy) {
            this.id = id;
            this.createdBy = createdBy;
        }
        public String getId() { return id; }
        public String getCreatedBy() { return createdBy; }
    }

    @Entity("audit_items")
    static class AuditItem extends BaseAuditEntity {
        @Column("item_name")
        private String name;

        public AuditItem() {}
        public AuditItem(String id, String createdBy, String name) {
            super(id, createdBy);
            this.name = name;
        }
        public String getName() { return name; }
    }

    // =========================================================================
    // Test 1: jakarta.nosql.Template CRUD & Duration TTL
    // =========================================================================

    @Test
    @DisplayName("Template: insert, insert with Duration, update, find, delete")
    void testJakartaTemplateCrud() {
        Template template = db.documentTemplate(Book.class);

        Book b1 = new Book("978-1", "Effective Java", "Joshua Bloch", 45.0);
        Book b2 = new Book("978-2", "Clean Code", "Robert C. Martin", 40.0);

        template.insert(b1);
        template.insert(b2, Duration.ofSeconds(300));

        Optional<Book> found = template.find(Book.class, "978-1");
        assertTrue(found.isPresent());
        assertEquals("Effective Java", found.get().getTitle());

        b1.setPrice(50.0);
        template.update(b1);
        Book updated = template.find(Book.class, "978-1").orElseThrow();
        assertEquals(50.0, updated.getPrice());

        // Batch insert & update
        Book b3 = new Book("978-3", "Refactoring", "Martin Fowler", 48.0);
        Book b4 = new Book("978-4", "Domain-Driven Design", "Eric Evans", 55.0);
        template.insert(List.of(b3, b4));

        assertTrue(template.find(Book.class, "978-3").isPresent());
        assertTrue(template.find(Book.class, "978-4").isPresent());

        // Delete
        template.delete(Book.class, "978-2");
        assertFalse(template.find(Book.class, "978-2").isPresent());
    }

    // =========================================================================
    // Test 2: QueryMapper Fluent Query API
    // =========================================================================

    @Test
    @DisplayName("QueryMapper: select with where, eq, gt, between, like, sort, skip, limit")
    void testQueryMapperSelect() {
        Template template = db.documentTemplate(Book.class);
        template.insert(new Book("B-1", "Design Patterns", "GoF", 55.0));
        template.insert(new Book("B-2", "Refactoring", "Martin Fowler", 45.0));
        template.insert(new Book("B-3", "Domain-Driven Design", "Eric Evans", 60.0));
        template.insert(new Book("B-4", "Patterns of Enterprise App Arch", "Martin Fowler", 65.0));

        // eq
        List<Book> fowler = template.select(Book.class)
                .where("author").eq("Martin Fowler")
                .result();
        assertEquals(2, fowler.size());

        // gt
        List<Book> expensive = template.select(Book.class)
                .where("price").gt(50.0)
                .result();
        assertEquals(3, expensive.size());

        // between
        List<Book> midPrice = template.select(Book.class)
                .where("price").between(45.0, 55.0)
                .result();
        assertEquals(2, midPrice.size());

        // like
        List<Book> patternBooks = template.select(Book.class)
                .where("book_title").like("%Patterns%")
                .result();
        assertEquals(2, patternBooks.size());

        // and with singleResult
        Optional<Book> ddd = template.select(Book.class)
                .where("author").eq("Eric Evans")
                .and("price").gte(60.0)
                .singleResult();
        assertTrue(ddd.isPresent());
        assertEquals("Domain-Driven Design", ddd.get().getTitle());

        // not condition
        long notFowlerCount = template.select(Book.class)
                .where("author").not().eq("Martin Fowler")
                .stream()
                .count();
        assertEquals(2, notFowlerCount);

        // order, skip, limit
        List<Book> sortedPaged = template.select(Book.class)
                .where("price").gt(40.0)
                .orderBy("price").desc()
                .skip(1)
                .limit(2)
                .result();
        assertEquals(2, sortedPaged.size());
        assertEquals("Domain-Driven Design", sortedPaged.get(0).getTitle());
        assertEquals("Design Patterns", sortedPaged.get(1).getTitle());
    }

    // =========================================================================
    // Test 3: QueryMapper Fluent Delete API
    // =========================================================================

    @Test
    @DisplayName("QueryMapper: delete with criteria execute")
    void testQueryMapperDelete() {
        Template template = db.documentTemplate(Book.class);
        template.insert(new Book("D-1", "Temp 1", "Anon", 10.0));
        template.insert(new Book("D-2", "Temp 2", "Anon", 12.0));
        template.insert(new Book("D-3", "Keep", "Known", 30.0));

        template.delete(Book.class)
                .where("author").eq("Anon")
                .execute();

        assertFalse(template.find(Book.class, "D-1").isPresent());
        assertFalse(template.find(Book.class, "D-2").isPresent());
        assertTrue(template.find(Book.class, "D-3").isPresent());
    }

    // =========================================================================
    // Test 4: NoSQLRepository (Eclipse JNoSQL Standard Repository)
    // =========================================================================

    @Test
    @DisplayName("NoSQLRepository: save, saveAll, insert, update, countBy, findByIdIn, deleteByIdIn")
    void testNoSqlRepository() {
        NoSQLRepository<Book, String> repo = db.noSqlRepository(Book.class);

        Book b1 = new Book("R-1", "Java Concurrency", "Brian Goetz", 50.0);
        Book b2 = new Book("R-2", "Java Generics", "Maurice Naftalin", 35.0);

        repo.save(b1);
        repo.save(b2);

        assertEquals(2, repo.countBy());
        assertTrue(repo.existsById("R-1"));

        // findByIdIn
        List<Book> multiFound = repo.findByIdIn(List.of("R-1", "R-2", "R-99")).toList();
        assertEquals(2, multiFound.size());

        // Update via save()
        b1.setPrice(55.0);
        repo.save(b1);
        assertEquals(55.0, repo.findById("R-1").orElseThrow().getPrice());
        assertEquals(2, repo.countBy());

        // deleteByIdIn
        repo.deleteByIdIn(List.of("R-1", "R-2"));
        assertEquals(0, repo.countBy());
    }

    // =========================================================================
    // Test 5: Jakarta Data Pagination via NoSQLRepository
    // =========================================================================

    @Test
    @DisplayName("NoSQLRepository: pagination and sorting with PageRequest and Order")
    void testNoSqlRepositoryPagination() {
        NoSQLRepository<Book, String> repo = db.noSqlRepository(Book.class);
        for (int i = 1; i <= 10; i++) {
            repo.insert(new Book("P-" + i, "Book " + i, "Author", i * 10.0));
        }

        assertEquals(10, repo.countBy());

        // Page 1, size 3, sorted by price descending
        Page<Book> page1 = repo.findAll(PageRequest.ofPage(1, 3, true), Order.by(Sort.desc("price")));
        assertEquals(3, page1.content().size());
        assertEquals(10, page1.totalElements());
        assertEquals(100.0, page1.content().get(0).getPrice());
        assertEquals(90.0, page1.content().get(1).getPrice());
        assertEquals(80.0, page1.content().get(2).getPrice());

        // Page 2
        Page<Book> page2 = repo.findAll(PageRequest.ofPage(2, 3, true), Order.by(Sort.desc("price")));
        assertEquals(3, page2.content().size());
        assertEquals(70.0, page2.content().get(0).getPrice());
    }

    // =========================================================================
    // Test 6: @Embeddable and @Convert (AttributeConverter)
    // =========================================================================

    @Test
    @DisplayName("Mapping: @Embeddable subdocument and @Convert AttributeConverter")
    void testEmbeddableAndConvert() {
        Template template = db.documentTemplate(Customer.class);

        Address addr = new Address("123 Java Lane", "Jakarta");
        Money money = new Money(250.75, "USD");
        Customer c1 = new Customer("C-100", "Alice", addr, money);

        template.insert(c1);

        Optional<Customer> found = template.find(Customer.class, "C-100");
        assertTrue(found.isPresent());
        Customer loaded = found.get();
        assertEquals("Alice", loaded.getName());

        assertNotNull(loaded.getAddress());
        assertEquals("123 Java Lane", loaded.getAddress().getStreet());
        assertEquals("Jakarta", loaded.getAddress().getCity());

        assertNotNull(loaded.getBalance());
        assertEquals(250.75, loaded.getBalance().getAmount());
        assertEquals("USD", loaded.getBalance().getCurrency());
    }

    // =========================================================================
    // Test 7: @Inheritance, @DiscriminatorColumn, @DiscriminatorValue
    // =========================================================================

    @Test
    @DisplayName("Mapping: @Inheritance and @DiscriminatorValue polymorphism")
    void testInheritancePolymorphism() {
        EntityMapper.registerEntitySubclass(Vehicle.class, Car.class);
        EntityMapper.registerEntitySubclass(Vehicle.class, Truck.class);

        Template template = db.documentTemplate(Vehicle.class);

        Car car = new Car("VIN-CAR-1", "Tesla", 4);
        Truck truck = new Truck("VIN-TRK-1", "Volvo", 18.5);

        template.insert(car);
        template.insert(truck);

        Optional<Vehicle> loadedCar = template.find(Vehicle.class, "VIN-CAR-1");
        assertTrue(loadedCar.isPresent());
        assertInstanceOf(Car.class, loadedCar.get());
        assertEquals(4, ((Car) loadedCar.get()).getDoors());
        assertEquals("Tesla", loadedCar.get().getBrand());

        Optional<Vehicle> loadedTruck = template.find(Vehicle.class, "VIN-TRK-1");
        assertTrue(loadedTruck.isPresent());
        assertInstanceOf(Truck.class, loadedTruck.get());
        assertEquals(18.5, ((Truck) loadedTruck.get()).getPayload());
    }

    // =========================================================================
    // Test 8: @MappedSuperclass Field Inheritance
    // =========================================================================

    @Test
    @DisplayName("Mapping: @MappedSuperclass persistent attributes inherited by subclass")
    void testMappedSuperclass() {
        Template template = db.documentTemplate(AuditItem.class);

        AuditItem item = new AuditItem("ITEM-1", "admin_user", "Server Config");
        template.insert(item);

        Optional<AuditItem> found = template.find(AuditItem.class, "ITEM-1");
        assertTrue(found.isPresent());
        assertEquals("admin_user", found.get().getCreatedBy());
        assertEquals("Server Config", found.get().getName());
    }

    // =========================================================================
    // Test 9: Eclipse JNoSQL Settings Configuration
    // =========================================================================

    @Test
    @DisplayName("Configuration: EmbedJNoSQLConfiguration from Eclipse JNoSQL Settings")
    void testSettingsConfiguration() {
        Settings settings = Settings.builder()
                .put(EmbedJNoSQLConfiguration.ENGINE_PROP, "IN_MEMORY")
                .put(EmbedJNoSQLConfiguration.AUTO_FLUSH_PROP, true)
                .put(EmbedJNoSQLConfiguration.QUERY_CACHE_PROP, true)
                .build();

        try (EmbedJNoSQL configuredDb = EmbedJNoSQLConfiguration.create(settings)) {
            assertNotNull(configuredDb);
            assertEquals(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY, configuredDb.config().storageEngine());
            assertTrue(configuredDb.config().autoFlush());
            assertTrue(configuredDb.config().queryCacheEnabled());
        }
    }

    // =========================================================================
    // Test 10: CDI Producer Wiring with @Database Qualifiers
    // =========================================================================

    @Test
    @DisplayName("CDI: EmbedDBProducer produces @Database qualified beans")
    void testCdiProducerWiring() {
        EmbedDBProducer producer = new EmbedDBProducer();
        EmbedJNoSQL producerDb = producer.produceEmbedJNoSQL();
        assertNotNull(producerDb);

        // Document qualified
        EclipseDocumentTemplate docTemplate = producer.produceDocumentTemplateQualified(producerDb);
        assertNotNull(docTemplate);

        Template jakartaTemplate = producer.produceJakartaTemplateQualified(producerDb);
        assertNotNull(jakartaTemplate);

        DocumentCollection collection = producer.produceDocumentCollectionQualified(producerDb);
        assertNotNull(collection);

        // Key-Value qualified
        KeyValueBucket kvBucket = producer.produceKeyValueBucketQualified(producerDb);
        assertNotNull(kvBucket);

        producer.close(producerDb);
    }
}
