package org.embeddedjnosql.db.spring.boot.ir;

import jakarta.persistence.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Entity and repository types for the Integration Review 36-Scenario test suite. */
public class IrFixtures {

    public enum Status { DRAFT, ACTIVE, DISCONTINUED }

    @Entity
    @Table(name = "ir_products")
    public static class Product {
        @Id public String id;
        public String name;
        @Column(name = "unit_price") public double price;
        public String category;
        public boolean active;
        @Enumerated(EnumType.STRING) public Status status;
        public Instant createdAt;
        @Transient public String computedLabel; // must NOT be stored

        public Product() {}
        public Product(String id, String name, double price, String category, boolean active,
                       Status status, Instant createdAt) {
            this.id = id; this.name = name; this.price = price; this.category = category;
            this.active = active; this.status = status; this.createdAt = createdAt;
        }
        public String getId() { return id; }
        public String getName() { return name; }
        public double getPrice() { return price; }
        public String getCategory() { return category; }
        public boolean isActive() { return active; }
        public Status getStatus() { return status; }
        public Instant getCreatedAt() { return createdAt; }
    }

    @Entity
    @Table(name = "ir_orders")
    public static class Order {
        @Id public String id;
        public String customerId;
        public double total;
        public boolean shipped;
        public LocalDate orderDate;

        public Order() {}
        public Order(String id, String customerId, double total, boolean shipped, LocalDate orderDate) {
            this.id = id; this.customerId = customerId; this.total = total;
            this.shipped = shipped; this.orderDate = orderDate;
        }
        public String getId() { return id; }
        public String getCustomerId() { return customerId; }
        public double getTotal() { return total; }
        public boolean isShipped() { return shipped; }
    }

    public interface ProductRepository extends JpaRepository<Product, String> {
        List<Product> findByCategory(String category);
        List<Product> findByPriceLessThan(double price);
        List<Product> findByActiveTrueOrderByPriceAsc();
        List<Product> findByNameContainingIgnoreCase(String fragment);
        long countByCategory(String category);
        boolean existsByName(String name);
        List<Product> findByStatusNot(Status status);
        List<Product> findFirst3ByActiveOrderByPriceDesc(boolean active);
        void deleteByCategory(String category);
    }

    public interface OrderRepository extends JpaRepository<Order, String> {
        List<Order> findByCustomerId(String customerId);
        List<Order> findByShippedFalse();
        List<Order> findByTotalGreaterThanEqual(double min);
    }
}
