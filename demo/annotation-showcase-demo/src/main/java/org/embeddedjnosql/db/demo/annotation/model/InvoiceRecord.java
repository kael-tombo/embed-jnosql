package org.embeddedjnosql.db.demo.annotation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Invoice entity showcasing Hibernate annotations:
 * - {@link UuidGenerator} for automated primary key assignment
 * - {@link CreationTimestamp} and {@link UpdateTimestamp} for automated audit timestamps
 * - {@link Formula} for computed read-only derivations (e.g. 20% VAT total)
 * - JPA {@link Enumerated} and {@link Column} mappings
 */
@Entity
@Table(name = "invoices")
public class InvoiceRecord {

    @Id
    @UuidGenerator
    @Column(name = "invoice_id")
    private String invoiceId;

    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "amount")
    private Double amount;

    @Formula("amount * 1.20")
    @Column(name = "total_with_tax")
    private Double totalWithTax;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private InvoiceStatus status;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public InvoiceRecord() {}

    public InvoiceRecord(String customerId, Double amount, InvoiceStatus status) {
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public Double getTotalWithTax() {
        return totalWithTax;
    }

    public void setTotalWithTax(Double totalWithTax) {
        this.totalWithTax = totalWithTax;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "InvoiceRecord{" +
                "invoiceId='" + invoiceId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", amount=" + amount +
                ", totalWithTax=" + totalWithTax +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
