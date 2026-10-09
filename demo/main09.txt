package org.embeddedjnosql.db.demo.demos.redisstylehashes;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.kv.HashBucket;

import java.util.Map;

/**
 * Demo 09 — Redis-style hashes: object-shaped field maps with HSETNX-style
 * first-write-wins, multi-field sets, full reads, and per-field deletion.
 */
public class RedisHashDemo {

    public record Result(Map<String, String> profile, int newFields, boolean cartCleared,
                         Map<String, String> afterDelete) {}

    public static Result runDemo(EmbedJNoSQL db) {
        HashBucket hashes = db.hashBucket("users");

        // 1. Write an object-shaped profile
        int added = hashes.hset("u:1", Map.of(
                "name", "Alice",
                "email", "alice@example.com",
                "tier", "vip"));

        // 2. Update one field (returns 0: field existed)
        int updated = hashes.hset("u:1", "tier", "platinum");

        // 3. Read the whole object back
        Map<String, String> profile = hashes.hgetall("u:1");

        // 4. Delete fields and verify
        long deleted = hashes.hdel("u:1", "email");
        Map<String, String> after = hashes.hgetall("u:1");

        Result r = new Result(profile, added, updated == 0 && deleted == 1, after);
        if (r.newFields() != 3 || !r.cartCleared()
                || !"platinum".equals(r.profile().get("tier"))
                || r.afterDelete().containsKey("email")) {
            throw new AssertionError("Unexpected hash results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Redis-style hash demo ==");
            System.out.println("profile          : " + r.profile());
            System.out.println("new fields       : " + r.newFields());
            System.out.println("update+delete ok : " + r.cartCleared());
            System.out.println("after hdel       : " + r.afterDelete());
        }
    }
}
