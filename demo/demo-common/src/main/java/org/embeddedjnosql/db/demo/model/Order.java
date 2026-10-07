package org.embeddedjnosql.db.demo.model;

import org.embeddedjnosql.db.nosql.document.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record Order(
        String id,
        String orderNumber,
        String customerId,
        List<OrderItem> items,
        double totalAmount,
        String status,
        long createdAt
) {
    public Document toDocument() {
        List<Map<String, Object>> itemMaps = items != null ?
                items.stream().map(OrderItem::toMap).collect(Collectors.toList()) : List.of();

        return new Document()
                .id(id)
                .add("orderNumber", orderNumber)
                .add("customerId", customerId)
                .add("items", itemMaps)
                .add("totalAmount", totalAmount)
                .add("status", status)
                .add("createdAt", createdAt);
    }

    @SuppressWarnings("unchecked")
    public static Order fromDocument(Document doc) {
        if (doc == null) return null;
        Number totalNum = doc.get("totalAmount");
        Number createdNum = doc.get("createdAt");

        List<OrderItem> orderItems = List.of();
        Object rawItems = doc.get("items");
        if (rawItems instanceof List<?> list) {
            orderItems = list.stream()
                    .filter(obj -> obj instanceof Map)
                    .map(obj -> OrderItem.fromMap((Map<String, Object>) obj))
                    .collect(Collectors.toList());
        }

        return new Order(
                doc.id(),
                doc.get("orderNumber"),
                doc.get("customerId"),
                orderItems,
                totalNum != null ? totalNum.doubleValue() : 0.0,
                doc.get("status"),
                createdNum != null ? createdNum.longValue() : System.currentTimeMillis()
        );
    }
}
