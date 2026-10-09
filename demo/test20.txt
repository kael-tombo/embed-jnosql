package org.embeddedjnosql.db.demo.demos.durabilityrecovery;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DurabilityDemoTest {

    @Test
    void restartRecoversDocumentsAndIndexes(@TempDir Path dir) {
        var r = DurabilityDemo.runDemo(dir);
        assertTrue(r.survivedRestart());
        assertTrue(r.indexRecovered());
        assertEquals(3, r.docCountAfterRestart());
        assertTrue(r.lateWriteSurvived());
    }
}
