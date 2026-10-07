package org.embeddedjnosql.db.demo.model;

import java.util.List;
import java.util.Map;

public class SampleData {

    public static List<Product> products() {
        return List.of(
                new Product("prod-101", "TECH-LAPTOP-01", "Developer Ultrabook 16", "Laptops", 1899.99,
                        List.of("tech", "developer", "hardware"), Map.of("cpu", "M3 Max", "ram", "64GB", "storage", "2TB")),
                new Product("prod-102", "TECH-KEYB-02", "Mechanical Ergonomic Keyboard", "Accessories", 249.50,
                        List.of("ergonomic", "peripherals", "office"), Map.of("switches", "Brown Tactile", "connectivity", "Wireless")),
                new Product("prod-103", "TECH-MON-03", "4K Ultra-Wide Studio Monitor", "Displays", 899.00,
                        List.of("display", "studio", "office"), Map.of("resolution", "3840x2160", "refreshRate", "144Hz")),
                new Product("prod-104", "BOOK-NOSQL-04", "Enterprise Embedded NoSQL in Practice", "Books", 59.99,
                        List.of("education", "database", "java"), Map.of("pages", 480, "author", "A. Ratombotiana", "year", 2026)),
                new Product("prod-105", "AUDIO-NOISE-05", "Active Noise Cancelling Headphones", "Audio", 349.00,
                        List.of("audio", "wireless", "travel"), Map.of("batteryHours", 30, "codec", "LDAC/AAC"))
        );
    }

    public static List<Customer> customers() {
        return List.of(
                new Customer("cust-001", "Alice Martin", "alice@example.com", "VIP"),
                new Customer("cust-002", "Bob Spencer", "bob@example.com", "REGULAR"),
                new Customer("cust-003", "Charlie Davis", "charlie@example.com", "REGULAR")
        );
    }
}
