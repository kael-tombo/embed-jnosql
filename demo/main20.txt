package org.embeddedjnosql.db.demo.demos.durabilityrecovery;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo 20 — Durability: a B_TREE database persists documents and secondary
 * indexes; closing and reopening the same data directory recovers everything,
 * including data written just before shutdown.
 */
public class DurabilityDemo {

    public record Result(boolean survivedRestart, boolean indexRecovered,
                         long docCountAfterRestart, boolean lateWriteSurvived) {}

    public static Result runDemo(Path dataDir) {
        // Phase 1: write, index, close
        try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                .dataDir(dataDir)
                .build()) {
            var users = db.documentCollection("users");
            users.insert(new Document().id("u1").add("name", "Ada").add("age", 36));
            users.insert(new Document().id("u2").add("name", "Bob").add("age", 42));
            users.createIndex("age");
            // A last-minute write right before shutdown
            users.insert(new Document().id("u3").add("name", "Cara").add("age", 29));
        }

        // Phase 2: reopen the same directory
        try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                .dataDir(dataDir)
                .build()) {
            var users = db.documentCollection("users");
            long count = users.findAll().size();
            boolean ada = users.findById("u1") != null;
            boolean cara = users.findById("u3") != null;
            boolean idx = users.getIndex("age") != null;

            Result r = new Result(ada, idx, count, cara);
            if (!r.survivedRestart() || !r.indexRecovered() || r.docCountAfterRestart() != 3
                    || !r.lateWriteSurvived()) {
                throw new AssertionError("Unexpected durability results: " + r);
            }
            return r;
        }
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("embedjnosql-durability-demo");
        try {
            Result r = runDemo(dir);
            System.out.println("== Durability demo ==");
            System.out.println("data survived restart : " + r.survivedRestart());
            System.out.println("index recovered       : " + r.indexRecovered());
            System.out.println("docs after restart    : " + r.docCountAfterRestart());
            System.out.println("late write survived   : " + r.lateWriteSurvived());
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(Path dir) throws Exception {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
