package org.embeddedjnosql.db.demo.demos.documentcrud;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.List;

/**
 * Demo 01 — Document CRUD: everything is one engine handle; documents are typed loosely ("schemaless")
 * and can be updated and deleted by id. Run with: mvn compile exec:java -Dexec.mainClass=...
 */
public class DocumentCrudDemo {

    public record Result(String id, boolean updated, boolean deleted) {}

    public static Result runDemo(EmbedJNoSQL db) {
        DocumentCollection users = db.documentCollection("users");

        // 1. Insert a schemaless document (the engine assigns a UUID id)
        Document doc = Document.of("name", "Ada")
                .add("role", "ADMIN")
                .add("tags", List.of("founder", "math"));
        Document saved = users.insert(doc);
        String id = saved.id();

        // 2. Read it back by id
        Document readBack = users.findById(id);
        if (readBack == null) throw new AssertionError("doc not found after insert");
        if (!"Ada".equals(readBack.get("name"))) {
            throw new AssertionError("expected name=Ada, got " + readBack.get("name"));
        }

        // 3. Update — replace the role, keep the name
        readBack.add("role", "COUNTESS");
        users.update(readBack);
        Document afterUpdate = users.findById(id);
        if (!"COUNTESS".equals(afterUpdate.get("role"))) {
            throw new AssertionError("update did not stick");
        }

        // 4. Delete
        boolean deleted = users.deleteById(id);

        return new Result(id, true, deleted);
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Document CRUD demo ==");
            System.out.println("id            : " + r.id());
            System.out.println("update applied : true");
            System.out.println("deleted        : " + r.deleted());
        }
    }
}
