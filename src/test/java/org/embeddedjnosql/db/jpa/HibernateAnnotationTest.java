package org.embeddedjnosql.db.jpa;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Hibernate & Extended JPA Annotation Support")
class HibernateAnnotationTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    public enum Priority {
        LOW, MEDIUM, HIGH
    }

    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "audit_records")
    static class AuditRecord {
        @jakarta.persistence.Id
        @UuidGenerator
        private String id;

        private String action;

        @Enumerated(EnumType.STRING)
        private Priority priority;

        @CreationTimestamp
        private Instant createdAt;

        @UpdateTimestamp
        private Instant updatedAt;

        private double basePrice;
        private double taxRate;

        @Formula("basePrice + taxRate")
        private double totalCost;

        private boolean prePersisted = false;
        private boolean postLoaded = false;

        public AuditRecord() {}

        public AuditRecord(String action, Priority priority, double basePrice, double taxRate) {
            this.action = action;
            this.priority = priority;
            this.basePrice = basePrice;
            this.taxRate = taxRate;
        }

        @PrePersist
        public void onPrePersist() {
            this.prePersisted = true;
        }

        @PostLoad
        public void onPostLoad() {
            this.postLoaded = true;
        }

        public String getId() { return id; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Priority getPriority() { return priority; }
        public Instant getCreatedAt() { return createdAt; }
        public Instant getUpdatedAt() { return updatedAt; }
        public double getBasePrice() { return basePrice; }
        public double getTaxRate() { return taxRate; }
        public double getTotalCost() { return totalCost; }
        public boolean isPrePersisted() { return prePersisted; }
        public boolean isPostLoaded() { return postLoaded; }
    }

    @jakarta.persistence.Entity
    @Immutable
    static class LogEntry {
        @jakarta.persistence.Id
        private String id;
        private String message;

        public LogEntry() {}
        public LogEntry(String id, String message) {
            this.id = id;
            this.message = message;
        }

        public String getId() { return id; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    @Test
    @DisplayName("Hibernate: @Table overrides collection name")
    void testTableAnnotationOverride() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("audit_records"));
        AuditRecord record = new AuditRecord("LOGIN", Priority.HIGH, 100.0, 20.0);

        template.insert(record);

        // Verify it was stored in 'audit_records' collection
        assertEquals(1, db.documentCollection("audit_records").count());
        assertTrue(template.find(AuditRecord.class, record.getId()).isPresent());
    }

    @Test
    @DisplayName("Hibernate: @UuidGenerator assigns UUID if null")
    void testUuidGenerator() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("audit_records"));
        AuditRecord record = new AuditRecord("EXPORT", Priority.LOW, 50.0, 5.0);
        assertNull(record.getId());

        template.insert(record);

        assertNotNull(record.getId(), "@UuidGenerator should populate id");
        assertEquals(36, record.getId().length());
    }

    @Test
    @DisplayName("Hibernate: @CreationTimestamp and @UpdateTimestamp populate automatically")
    void testTimestamps() throws InterruptedException {
        var template = EclipseDocumentTemplate.of(db.documentCollection("audit_records"));
        AuditRecord record = new AuditRecord("UPDATE_DOC", Priority.MEDIUM, 80.0, 8.0);

        template.insert(record);

        Instant created = record.getCreatedAt();
        Instant updated = record.getUpdatedAt();
        assertNotNull(created, "@CreationTimestamp must be set");
        assertNotNull(updated, "@UpdateTimestamp must be set");

        Thread.sleep(10);
        record.setAction("UPDATE_DOC_V2");
        template.update(record);

        AuditRecord reloaded = template.find(AuditRecord.class, record.getId()).orElseThrow();
        assertEquals("UPDATE_DOC_V2", reloaded.getAction());
        assertNotNull(reloaded.getCreatedAt());
        assertNotNull(reloaded.getUpdatedAt());
        assertTrue(!reloaded.getUpdatedAt().isBefore(created), "Updated timestamp must be >= creation timestamp");
    }

    @Test
    @DisplayName("Hibernate: @Formula evaluates derived value on load")
    void testFormula() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("audit_records"));
        AuditRecord record = new AuditRecord("CALCULATE", Priority.MEDIUM, 100.0, 15.0);

        template.insert(record);

        AuditRecord loaded = template.find(AuditRecord.class, record.getId()).orElseThrow();
        assertEquals(115.0, loaded.getTotalCost(), 0.001, "@Formula must evaluate basePrice + taxRate");
    }

    @Test
    @DisplayName("Hibernate: @Immutable entity throws on update")
    void testImmutableEntity() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("logentry"));
        LogEntry entry = new LogEntry("LOG-1", "System initialized");
        template.insert(entry);

        entry.setMessage("Hacked message");
        assertThrows(IllegalStateException.class, () -> template.update(entry),
                "Updating an @Immutable entity must throw IllegalStateException");
    }

    @Test
    @DisplayName("JPA: @PrePersist and @PostLoad lifecycle callbacks")
    void testLifecycleCallbacks() {
        var template = EclipseDocumentTemplate.of(db.documentCollection("audit_records"));
        AuditRecord record = new AuditRecord("LIFECYCLE", Priority.HIGH, 10.0, 1.0);

        assertFalse(record.isPrePersisted());
        template.insert(record);
        assertTrue(record.isPrePersisted(), "@PrePersist must be invoked before persistence");

        AuditRecord loaded = template.find(AuditRecord.class, record.getId()).orElseThrow();
        assertTrue(loaded.isPostLoaded(), "@PostLoad must be invoked upon loading");
    }
}
