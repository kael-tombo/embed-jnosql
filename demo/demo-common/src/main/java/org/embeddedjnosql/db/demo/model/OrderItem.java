package org.embeddedjnosql.db.demo.model;

import java.util.Map;

public record OrderItem(
        String productId,
        String productName,
        int quantity,
        double unitPrice
) {
    public double subtotal() {
        return quantity * unitPrice;
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "productId", productId,
                "productName", productName,
                "quantity", quantity,
                "unitPrice", unitPrice
        );
    }

    public static OrderItem fromMap(Map<String, Object> map) {
        Number qtyNum = (Number) map.get("quantity");
        Number priceNum = (Number) map.get("unitPrice");
        return new OrderItem(
                (String) map.get("productId"),
                (String) map.get("productName"),
                qtyNum != null ? qtyNum.intValue() : 0,
                priceNum != null ? priceNum.doubleValue() : 0.0
        );
    }
}
