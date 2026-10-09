package org.embeddedjnosql.db.demo.demos.nativequery;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeQueryDemoTest {

    @Test
    void predicatesSortAndPageTogether() {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            NativeQueryDemo.Result r = NativeQueryDemo.runDemo(db);
            assertEquals(2, r.shippedCount());
            assertEquals(2, r.topPageCount());
            assertEquals(525.25, r.maxTotal(), 0.0001);
        }
    }
}
