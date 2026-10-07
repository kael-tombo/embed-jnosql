package org.embeddedjnosql.db.demo.annotation.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Customer account entity modeled using standard JPA (Jakarta Persistence) annotations.
 */
@Entity
@Table(name = "customer_accounts")
public class CustomerAccount {

    @Id
    @Column(name = "customer_id")
    private String customerId;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "loyalty_tier")
    private String loyaltyTier;

    @Column(name = "credit_balance")
    private Double creditBalance;

    public CustomerAccount() {}

    public CustomerAccount(String customerId, String fullName, String email, String loyaltyTier, Double creditBalance) {
        this.customerId = customerId;
        this.fullName = fullName;
        this.email = email;
        this.loyaltyTier = loyaltyTier;
        this.creditBalance = creditBalance;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getLoyaltyTier() {
        return loyaltyTier;
    }

    public void setLoyaltyTier(String loyaltyTier) {
        this.loyaltyTier = loyaltyTier;
    }

    public Double getCreditBalance() {
        return creditBalance;
    }

    public void setCreditBalance(Double creditBalance) {
        this.creditBalance = creditBalance;
    }

    @Override
    public String toString() {
        return "CustomerAccount{" +
                "customerId='" + customerId + '\'' +
                ", fullName='" + fullName + '\'' +
                ", email='" + email + '\'' +
                ", loyaltyTier='" + loyaltyTier + '\'' +
                ", creditBalance=" + creditBalance +
                '}';
    }
}
