package org.junify.db.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junify.db.config.JunifyDBConfig.StorageEngineType;

/**
 * Regression coverage for benchmark-runner option validation.
 *
 * <p>The runner previously ignored unrecognized options, fell back to
 * {@code IN_MEMORY} for any unknown {@code --engine} value, and accepted any
 * {@code --workload} string — a typo matched no workload and the run finished
 * reporting zero benchmarks as if it had succeeded.</p>
 */
class BenchmarkOptionValidationTest {

    @Test
    void parsesDocumentedOptions() {
        var options = BenchmarkRunner.parseArgs(new String[]{
                "--ops", "100", "--threads", "4", "--engine", "FILE", "--workload", "kv"});

        assertEquals(100, options.ops);
        assertEquals(4, options.threads);
        assertEquals(StorageEngineType.FILE, options.engine);
        assertEquals("kv", options.workload);
    }

    @Test
    void defaultsRemainUnchanged() {
        var options = BenchmarkRunner.parseArgs(new String[]{});

        assertEquals(10000, options.ops);
        assertEquals(1, options.threads);
        assertEquals(StorageEngineType.IN_MEMORY, options.engine);
        assertEquals("all", options.workload);
    }

    @Test
    void unknownOptionIsRejected() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.parseArgs(new String[]{"--storage", "FILE"}));
        assertTrue(error.getMessage().contains("Unknown option: --storage"),
                () -> "message was: " + error.getMessage());
    }

    @Test
    void unsupportedEngineIsRejected() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.parseArgs(new String[]{"--engine", "mongo"}));
        assertTrue(error.getMessage().contains("Unsupported engine: mongo"),
                () -> "message was: " + error.getMessage());
    }

    @Test
    void unknownWorkloadIsRejected() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.parseArgs(new String[]{"--workload", "kv-reads"}));
        assertTrue(error.getMessage().contains("Unknown workload: kv-reads"),
                () -> "message was: " + error.getMessage());
    }

    @Test
    void combinedWorkloadsAreAccepted() {
        assertEquals("document,kv",
                BenchmarkRunner.parseArgs(new String[]{"--workload", "document,kv"}).workload);
    }

    @Test
    void missingAndNonNumericValuesAreReported() {
        var missing = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.parseArgs(new String[]{"--ops"}));
        assertTrue(missing.getMessage().contains("Missing value for --ops"), missing::getMessage);

        var invalid = assertThrows(IllegalArgumentException.class,
                () -> BenchmarkRunner.parseArgs(new String[]{"--threads", "many"}));
        assertTrue(invalid.getMessage().contains("Invalid number for --threads: many"), invalid::getMessage);
    }
}
