package org.junify.db.demo.spring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Transient;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.annotations.UpdateTimestamp;
import org.junify.db.nosql.document.Document;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * An order entity carrying the Hibernate/JPA annotation surface JunifyDB maps without any JPA
 * runtime or relational database: collection renaming ({@code @Table}), UUID id generation
 * ({@code @UuidGenerator}), lifecycle callbacks ({@code @PrePersist}/{@code @PostLoad}),
 * auto timestamps ({@code @CreationTimestamp}/{@code @UpdateTimestamp}), a derived field
 * ({@code @Formula}), column aliasing ({@code @Column(name=...)}), enum-as-string persistence
 * ({@code @Enumerated}), a non-persisted field ({@code @Transient}) and a legacy
 * {@code java.util.Date} round trip.
 */
@Entity
@jakarta.persistence.Table(name = "hibernate_orders")
class HibernateOrder {

    enum Priority {LOW, MEDIUM, HIGH}

    @jakarta.persistence.Id
    @UuidGenerator
    private String id;

    private String reference;

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

    @Column(name = "order_email")
    private String email;

    @Transient
    private String validationToken;

    @Column(name = "approved")
    private boolean approved;

    private boolean prePersisted;
    private boolean postLoaded;

    public HibernateOrder() {}

    /** A fully populated example order — detaches the tests from fixture noise. */
    static HibernateOrder of(String reference) {
        var order = new HibernateOrder();
        order.reference = reference;
        order.priority = Priority.MEDIUM;
        order.basePrice = 100.0;
        order.taxRate = 15.0;
        order.email = reference.toLowerCase() + "@junifydb.example";
        order.validationToken = UUID.randomUUID().toString();
        order.auditDate = new Date();
        return order;
    }

    /** Legacy {@code java.util.Date} field proving pre-{@code java.time} types round trip. */
    private Date auditDate;

    /** Live, typed view of the stored document (formula is recomputed, never trusted from storage). */
    static HibernateOrder fromSnapshot(Document doc) {
        var order = new HibernateOrder();
        order.reloadFrom(doc);
        return order;
    }

    public String getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
    public double getTaxRate() { return taxRate; }
    public void setTaxRate(double taxRate) { this.taxRate = taxRate; }
    public double getTotalCost() { return totalCost; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getValidationToken() { return validationToken; }
    public void setValidationToken(String validationToken) { this.validationToken = validationToken; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public boolean isPrePersisted() { return prePersisted; }
    public boolean isPostLoaded() { return postLoaded; }
    public Date getAuditDate() { return auditDate; }
    public void setAuditDate(Date auditDate) { this.auditDate = auditDate; }

    @jakarta.persistence.PrePersist
    void onPrePersist() { this.prePersisted = true; }

    @jakarta.persistence.PostLoad
    void onPostLoad() { this.postLoaded = true; }

    /** A generic-typed storage value proving arbitrary types survive the mapping. */
    private Double partialPrice;

    public Double getPartialPrice() { return partialPrice; }
    public void setPartialPrice(Double partialPrice) { this.partialPrice = partialPrice; }

    /** Snapshot of this instance, exchangeable detached↔flushed through raw storage. */
    Document snapshot() {
        return new Document()
                .id(id)
                .add("reference", reference)
                .add("priority", priority.name())   // @Enumerated(STRING): stored as the name
                .add("createdAt", createdAt != null ? createdAt.toString() : null)
                .add("updatedAt", updatedAt != null ? updatedAt.toString() : null)
                .add("basePrice", basePrice)
                .add("taxRate", taxRate)
                .add("totalCost", 0.0)              // recomputed on load; never trusted from storage
                .add("order_email", email)          // @Column alias
                .add("approved", approved)
                .add("auditDate", auditDate);       // legacy java.util.Date round trip
    }

    void reloadFrom(Document doc) {
        // read through getRaw (Object) rather than the generic get(): valueOf/instanceof-style
        // contexts otherwise infer a hidden unchecked cast (e.g. String -> char[])
        this.id = doc.getId();
        this.reference = (String) doc.getRaw("reference");
        Object rawPriority = doc.getRaw("priority");
        this.priority = rawPriority == null ? null : Priority.valueOf(rawPriority.toString());
        this.createdAt = parseInstant(doc.getRaw("createdAt"));
        this.updatedAt = parseInstant(doc.getRaw("updatedAt"));
        this.basePrice = asDouble(doc.getRaw("basePrice"));
        this.taxRate = asDouble(doc.getRaw("taxRate"));
        this.email = (String) doc.getRaw("order_email");
        Object rawApproved = doc.getRaw("approved");
        this.approved = rawApproved instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(rawApproved));
        Object rawDate = doc.getRaw("auditDate");
        this.auditDate = rawDate instanceof Date d ? d
                : rawDate == null ? null : Date.from(Instant.parse(rawDate.toString()));
    }

    private static Instant parseInstant(Object raw) {
        return raw == null ? null : Instant.parse(raw.toString());
    }

    private static double asDouble(Object o) {
        if (o instanceof Number n) return n.doubleValue();
        return o == null ? 0.0 : Double.parseDouble(o.toString());
    }
}
