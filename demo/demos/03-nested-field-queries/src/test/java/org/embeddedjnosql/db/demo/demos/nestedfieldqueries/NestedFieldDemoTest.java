package org.embeddedjnosql.db.demo.demos.nestedfieldqueries;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NestedFieldDemoTest {

    @Test
    void dottedPathsMatchNestedValues() {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            NestedFieldDemo.Result r = NestedFieldDemo.runDemo(db);
            assertEquals(1, r.inNairobi());
            assertEquals(1, r.southern());
            assertEquals(2, r.withAddress());
        }
    }
}
