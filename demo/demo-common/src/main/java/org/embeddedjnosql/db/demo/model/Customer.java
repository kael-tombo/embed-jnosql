package org.embeddedjnosql.db.demo.model;

import org.embeddedjnosql.db.nosql.document.Document;

public record Customer(
        String id,
        String name,
        String email,
        String tier
) {
    public Document toDocument() {
        return new Document()
                .id(id)
                .add("name", name)
                .add("email", email)
                .add("tier", tier);
    }

    public static Customer fromDocument(Document doc) {
        if (doc == null) return null;
        return new Customer(
                doc.id(),
                doc.get("name"),
                doc.get("email"),
                doc.get("tier")
        );
    }
}
