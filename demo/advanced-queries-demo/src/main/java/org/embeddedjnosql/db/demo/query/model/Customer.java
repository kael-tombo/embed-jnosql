package org.embeddedjnosql.db.demo.query.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customers")
public class Customer {
    @Id
    private String id;
    private String name;
    private String email;
    private String tier; // BRONZE, SILVER, GOLD, PLATINUM
    private double lifetimeSpend;

    public Customer() {}

    public Customer(String id, String name, String email, String tier, double lifetimeSpend) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.tier = tier;
        this.lifetimeSpend = lifetimeSpend;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public double getLifetimeSpend() { return lifetimeSpend; }
    public void setLifetimeSpend(double lifetimeSpend) { this.lifetimeSpend = lifetimeSpend; }
}
