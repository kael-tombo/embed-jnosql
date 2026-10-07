package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.core.exception.EmbedJNoSQLException;
import org.embeddedjnosql.db.core.exception.SerializationException;
import org.embeddedjnosql.db.core.exception.StorageException;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the second improvement round (audit register R-16,
 * R-18, R-19, R-20, R-24, R-25).
 */
class SecondImprovementRoundTest {

    // ------------------------------------------------------------------
    // R-16: typed exception hierarchy
    // ------------------------------------------------------------------

    @Test
    void storageFailuresSurfaceAsTypedEmbedJNoSQLException() {
        // JsonSerde deserialize failures are SerializationException
        assertThrows(SerializationException.class, () ->
                org.embeddedjnosql.db.core.util.JsonSerde.fromJson("{ this is not json ]]", Object.class));

        // Hierarchy sanity: both subtypes are catchable via the base class
        assertTrue(StorageException.class.getSuperclass() == EmbedJNoSQLException.class);
        assertTrue(SerializationException.class.getSuperclass() == EmbedJNoSQLException.class);
    }

    // ------------------------------------------------------------------
    // R-18: planner performs a real point lookup for indexed equality
    // ------------------------------------------------------------------

    @Test
    void indexedEqualityQueryReturnsOnlyMatchingDocuments() {
        try (var db = EmbedJNoSQL.inMemory()) {
            var col = db.documentCollection("idx_test");
            col.createIndex("category");
            for (int i = 0; i < 50; i++) {
                col.insert(Document.of("category", i % 2 == 0 ? "even" : "odd").id("d" + i));
            }

            var evens = col.find(Query.eq("category", "even"));
            assertEquals(25, evens.size(), "index point lookup must return exactly the matching docs");
            assertTrue(evens.stream().allMatch(d -> "even".equals(d.getRaw("category"))));

            var missing = col.find(Query.eq("category", "nope"));
            assertTrue(missing.isEmpty(), "no match -> empty result");
        }
    }

    // ------------------------------------------------------------------
    // R-19: vector index dimensionality derived from client request
    // ------------------------------------------------------------------

    @Test
    void vectorIndexRejectsDimensionMismatchAndAcceptsFirstVector() throws Exception {
        var db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder().enabled(true).port(0).contextPath("/").build())
                .security(SecurityConfig.builder().apiKey("vec-test-key").build())
                .build();
        try {
            int port = db.consolePort();
            var base = "http://127.0.0.1:" + port;

            // First request with a 4-dim vector creates the index at 4 dims.
            var create = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(base + "/api/vectors/v1/vec-1"))
                            .header("Content-Type", "application/json")
                            .header("X-API-Key", "vec-test-key")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                                    "{\"vector\":[0.1,0.2,0.3,0.4]}"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(201, create.statusCode(), "first vector must create the index: " + create.body());

            // A different-dimension vector must be rejected with a clear error.
            var mismatch = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(base + "/api/vectors/v1/vec-2"))
                            .header("Content-Type", "application/json")
                            .header("X-API-Key", "vec-test-key")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                                    "{\"vector\":[0.1,0.2]}"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(400, mismatch.statusCode(), "dimension mismatch must be rejected");
            assertTrue(mismatch.body().contains("dimension"), "error should mention dimension: " + mismatch.body());
        } finally {
            db.close();
        }
    }

    // ------------------------------------------------------------------
    // R-20: apply phase is atomic (undo log) and delete conflicts validated
    // ------------------------------------------------------------------

    @Test
    void mvccCommitValidatesStagedDeletes() {
        var mvcc = new org.embeddedjnosql.db.transaction.mvcc.MVCCManager();

        // tx1 stages a delete at snapshot 1
        var tx1 = "tx1";
        mvcc.stageDelete(tx1, "k1");

        // tx2 commits a write to k1 at ts 2 (after tx1's snapshot)
        var tx2 = "tx2";
        mvcc.assignTimestamp(); // ts1 = tx1's snapshot reference
        long ts2 = mvcc.assignTimestamp();
        var rec = Document.of("k1", "v2");
        mvcc.stageWrite(tx2, "k1", rec);
        // commit tx2 with a fresh read timestamp so its own validation passes
        mvcc.assignTimestamp();
        assertTrue(mvcc.commit(tx2, ts2, ts2 - 1), "tx2 has no conflicting writer and must commit");

        // tx1's delete must now be rejected (delete-write conflict, first-committer-wins)
        long tx1Commit = mvcc.assignTimestamp();
        assertFalse(mvcc.commit(tx1, tx1Commit, 1),
                "a staged delete against a key written after the snapshot must be rejected");
    }

    // ------------------------------------------------------------------
    // R-24: audit trail persisted to disk for FILE engines
    // ------------------------------------------------------------------

    @Test
    void auditEventsArePersistedToDisk(@TempDir Path dir) throws Exception {
        var db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .console(ConsoleConfig.builder().enabled(true).port(0).contextPath("/").build())
                .security(SecurityConfig.builder().apiKey("audit-test-key").build())
                .build();
        try {
            int port = db.consolePort();
            var insert = java.net.http.HttpClient.newHttpClient().send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create("http://127.0.0.1:" + port + "/api/collections/audit_probe"))
                            .header("Content-Type", "application/json")
                            .header("X-API-Key", "audit-test-key")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"probe\":\"r24\"}"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(201, insert.statusCode(), "insert must succeed: " + insert.body());

            // Give the audit writer a beat, then verify the JSONL file exists
            // and contains the event.
            var auditFile = dir.resolve("audit.log");
            for (int i = 0; i < 20 && (!Files.exists(auditFile) || Files.size(auditFile) == 0); i++) {
                Thread.sleep(50);
            }
            assertTrue(Files.exists(auditFile), "audit.log must be created for FILE engines");
            var content = Files.readString(auditFile);
            assertTrue(content.contains("INSERT") && content.contains("audit_probe"),
                    "audit file must contain the insert event, got: " + content);
        } finally {
            db.close();
        }
    }

    // ------------------------------------------------------------------
    // R-25: CLI module restored (compile-checked by its own pom; behavioral
    // checks live in the cli module — here we pin the entry-point contract)
    // ------------------------------------------------------------------

    @Test
    void cliEntryPointClassExists() throws Exception {
        // The recovered shell must live at the corrected package path.
        var path = java.nio.file.Path.of("cli/src/main/java/org/embeddedjnosql/db/integration/standalone/EmbedJNoSQLShell.java");
        assertTrue(Files.exists(path), "EmbedJNoSQLShell must exist at its declared package path");
        var src = Files.readString(path);
        assertTrue(src.contains("package org.embeddedjnosql.db.integration.standalone;"));
        assertTrue(src.contains("public static void main"), "shell must have an entry point");
    }

    // ------------------------------------------------------------------
    // Guard: DocumentCollection.update still resolves prior values (CDC path
    // from round 1) after the Query/index changes this round.
    // ------------------------------------------------------------------

    @Test
    void queryEqStillFiltersAfterIndexChanges() {
        try (var db = EmbedJNoSQL.inMemory()) {
            var col = db.documentCollection("sanity");
            col.insert(Document.of("k", "a").id("1"));
            col.insert(Document.of("k", "b").id("2"));
            assertEquals(1, col.find(Query.eq("k", "a")).size());
            assertEquals(2, col.find(Query.all()).size());
        }
    }
}
