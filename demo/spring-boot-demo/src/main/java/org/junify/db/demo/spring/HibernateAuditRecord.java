package org.junify.db.demo.spring;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.NaturalId;
import org.junify.db.nosql.document.Document;

/**
 * A small audit-trail entity proving two Hibernate behaviors JunifyDB honors with no JPA runtime:
 * {@code @Immutable} (updates must throw) and {@code @NaturalId} (an alternate business key the
 * mapper records via {@link org.junify.db.adapter.jnosql.EntityMapper#isNaturalId}).
 */
@Entity
@Immutable
class HibernateAuditRecord {

    @Id
    private String id;

    private String action;

    @NaturalId
    private String ticketNumber;

    public HibernateAuditRecord() {}

    HibernateAuditRecord(String id, String action, String ticketNumber) {
        this.id = id;
        this.action = action;
        this.ticketNumber = ticketNumber;
    }

    public String getId() { return id; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getTicketNumber() { return ticketNumber; }
    public void setTicketNumber(String ticketNumber) { this.ticketNumber = ticketNumber; }

    public Document snapshot() {
        return new Document()
                .id(id)
                .add("action", action)
                .add("ticketNumber", ticketNumber);
    }
}
