package org.embeddedjnosql.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for standalone launch-option validation.
 *
 * <p>Unrecognized options, missing values and unsupported engine names must fail
 * fast. Before this guard, {@code --storage IN_MEMORY} silently ran the FILE
 * engine and {@code --password …} silently left the console unauthenticated,
 * i.e. the server ran with defaults the operator did not choose.</p>
 */
class LaunchOptionValidationTest {

    private static final String[] DOCUMENTED_OPTIONS = {
            "--port", "--data-dir", "--engine", "--sync", "--async", "--flush-interval",
            "--api-key", "--ssl-port", "--ssl-keystore", "--ssl-keypass", "--help"
    };

    @Test
    void documentedOptionsAreAccepted() {
        for (String option : DOCUMENTED_OPTIONS) {
            assertDoesNotThrow(() -> EmbedJNoSQL.validateOption(option), option + " must be accepted");
        }
    }

    @Test
    void unknownOptionsAreRejectedWithAClearMessage() {
        var storage = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQL.validateOption("--storage"));
        assertTrue(storage.getMessage().contains("--storage"),
                () -> "message was: " + storage.getMessage());

        var password = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQL.validateOption("--password"));
        assertTrue(password.getMessage().contains("--password"),
                () -> "message was: " + password.getMessage());
    }

    @Test
    void missingValueForOptionIsReported() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQL.value("--port", new String[]{"--port"}, 1));
        assertTrue(error.getMessage().contains("Missing value for --port"),
                () -> "message was: " + error.getMessage());
    }

    @Test
    void nonNumericOptionValueIsReported() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQL.intValue("--port", new String[]{"--port", "abc"}, 1));
        assertTrue(error.getMessage().contains("Invalid number for --port: abc"),
                () -> "message was: " + error.getMessage());
    }

    @Test
    void numericOptionParsesValidValue() {
        assertEquals(8092, EmbedJNoSQL.intValue("--port", new String[]{"--port", "8092"}, 1));
    }

    @Test
    void engineNameIsNormalizedAndValidated() {
        assertEquals("IN_MEMORY", EmbedJNoSQL.engineValue("--engine", new String[]{"--engine", "in_memory"}, 1));
        assertEquals("B_TREE", EmbedJNoSQL.engineValue("--engine", new String[]{"--engine", "b_tree"}, 1));
        assertEquals("FILE", EmbedJNoSQL.engineValue("--engine", new String[]{"--engine", "file"}, 1));

        var error = assertThrows(IllegalArgumentException.class,
                () -> EmbedJNoSQL.engineValue("--engine", new String[]{"--engine", "mongo"}, 1));
        assertTrue(error.getMessage().contains("Unsupported engine: mongo"),
                () -> "message was: " + error.getMessage());
    }
}
