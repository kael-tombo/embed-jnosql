package org.embeddedjnosql.db.integration.standalone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for CLI shell argument handling: unsupported options are
 * rejected instead of ignored, and the positional data directory keeps its
 * documented default.
 */
class EmbedJNoSQLShellArgsTest {

    @Test
    void defaultsToDataDirectory() {
        assertEquals("data", EmbedJNoSQLShell.parseDataDir(new String[]{}));
    }

    @Test
    void usesPositionalDataDirectory() {
        assertEquals("./mydata", EmbedJNoSQLShell.parseDataDir(new String[]{"./mydata"}));
    }

    @Test
    void rejectsUnknownOptions() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQLShell.parseDataDir(new String[]{"--port", "9000"}));
        assertTrue(error.getMessage().contains("--port"), () -> "message was: " + error.getMessage());
    }

    @Test
    void rejectsExtraPositionalArguments() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQLShell.parseDataDir(new String[]{"first", "second"}));
        assertTrue(error.getMessage().contains("Unexpected extra argument: second"),
                () -> "message was: " + error.getMessage());
    }
}
