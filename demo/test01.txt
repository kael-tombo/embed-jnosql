package org.embeddedjnosql.db.demo.demos.documentcrud;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DocumentCrudDemoTest {

    @Test
    void insertReadUpdateDeleteRoundTrip() {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            DocumentCrudDemo.Result r = DocumentCrudDemo.runDemo(db);
            assertNotNull(r.id());
            assertTrue(r.deleted(), "deleteById must report the document was removed");
        }
    }

    @Test
    void deletedDocumentIsGone() {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            DocumentCrudDemo.Result r = DocumentCrudDemo.runDemo(db);
            assertNull(db.documentCollection("users").findById(r.id()),
                    "after deleteById the document must not be readable");
        }
    }
}
