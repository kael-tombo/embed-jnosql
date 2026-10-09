package org.embeddedjnosql.db.demo.demos.keyvaluebuckets;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demo 06 — Key-Value buckets: Redis-style string store with TTL, atomic
 * counters, and atomic batch put/get. This is the model for session stores,
 * feature flags, rate-limit counters and price caches.
 */
public class KeyValueDemo {

    public record Result(String sessionUser, long pageViews, long inventoryLeft,
                         Map<String, String> batchResult, boolean expiredKeyGone) {}

    public static Result runDemo(EmbedJNoSQL db) {
        KeyValueBucket kv = db.keyValueBucket("app_kv");

        // 1. Basic put/get
        kv.put("session:u1", "alice");
        String user = kv.get("session:u1");

        // 2. Atomic counters
        kv.put("stats:pageviews", "0");
        kv.increment("stats:pageviews");
        kv.increment("stats:pageviews", 4);
        long views = kv.get("stats:pageviews") != null ? Long.parseLong(kv.get("stats:pageviews")) : -1;

        // 3. Decrement: stock tracking
        kv.put("stock:sku-1", "10");
        kv.decrement("stock:sku-1");
        kv.decrement("stock:sku-1");
        long left = Long.parseLong(kv.get("stock:sku-1"));

        // 4. Atomic batch write + multi-get
        Map<String, String> flags = new LinkedHashMap<>();
        flags.put("flag:new-ui", "on");
        flags.put("flag:beta-api", "off");
        kv.putAll(flags);
        Map<String, String> readBack = kv.getAll(java.util.List.of("flag:new-ui", "flag:beta-api"));

        // 5. TTL: a one-shot token that expires on read
        kv.put("otp:1234", "9911", Duration.ofMillis(50));
        try { Thread.sleep(80); } catch (InterruptedException ignored) {}
        boolean gone = kv.get("otp:1234") == null;

        Result r = new Result(user, views, left, readBack, gone);
        if (!"alice".equals(r.sessionUser()) || r.pageViews() != 5 || r.inventoryLeft() != 8
                || r.batchResult().size() != 2 || !r.expiredKeyGone()) {
            throw new AssertionError("Unexpected KV results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Key-value demo ==");
            System.out.println("session user    : " + r.sessionUser());
            System.out.println("page views      : " + r.pageViews());
            System.out.println("inventory left  : " + r.inventoryLeft());
            System.out.println("batch flags     : " + r.batchResult());
            System.out.println("expired otp gone: " + r.expiredKeyGone());
        }
    }
}
