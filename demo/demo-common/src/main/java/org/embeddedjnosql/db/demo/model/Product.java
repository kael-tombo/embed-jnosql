package org.embeddedjnosql.db.demo.model;

import org.embeddedjnosql.db.nosql.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Product(
        String id,
        String sku,
        String name,
        String category,
        double price,
        List<String> tags,
        Map<String, Object> attributes
) {
    public Document toDocument() {
        return new Document()
                .id(id)
                .add("sku", sku)
                .add("name", name)
                .add("category", category)
                .add("price", price)
                .add("tags", tags != null ? tags : List.of())
                .add("attributes", attributes != null ? attributes : Map.of());
    }

    @SuppressWarnings("unchecked")
    public static Product fromDocument(Document doc) {
        if (doc == null) return null;
        Number priceNum = doc.get("price");
        return new Product(
                doc.id(),
                doc.get("sku"),
                doc.get("name"),
                doc.get("category"),
                priceNum != null ? priceNum.doubleValue() : 0.0,
                doc.get("tags") instanceof List<?> l ? (List<String>) l : List.of(),
                doc.get("attributes") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()
        );
    }
}
