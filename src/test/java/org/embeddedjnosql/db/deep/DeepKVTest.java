package org.embeddedjnosql.db.deep;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub-Agent B — Deep Key-Value, List, Set, Hash Tests.
 *
 * Covers: KV put/get/exists/delete/overwrite, List push/pop/range/trim/lrem/lindex,
 * Set sadd/srem/scard/inter/union/diff/spop/srandmember, Hash hset/hget/hdel/hincrby/hkeys/hvals/hmget.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class DeepKVTest {

    private static EmbedJNoSQL db;

    @BeforeAll
    static void init() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterAll
    static void teardown() {
        if (db != null && db.isOpen()) db.close();
    }

    // =======================================================================
    // KEY-VALUE BUCKET
    // =======================================================================

    @Test @Order(1)
    void kv_putAndGetSimpleString() {
        KeyValueBucket kv = db.keyValueBucket("kv_deep");
        kv.put("greeting", "hello");
        assertEquals("hello", kv.get("greeting"));
    }

    @Test @Order(2)
    void kv_overwriteExistingKey() {
        KeyValueBucket kv = db.keyValueBucket("kv_overwrite");
        kv.put("color", "red");
        kv.put("color", "blue");
        assertEquals("blue", kv.get("color"), "Second put should overwrite first");
    }

    @Test @Order(3)
    void kv_getMissingKeyReturnsNull() {
        KeyValueBucket kv = db.keyValueBucket("kv_missing");
        assertNull(kv.get("no_such_key_xyz"));
    }

    @Test @Order(4)
    void kv_existsTrueAndFalse() {
        KeyValueBucket kv = db.keyValueBucket("kv_exists");
        kv.put("present", "yes");
        assertTrue(kv.exists("present"));
        assertFalse(kv.exists("absent"));
    }

    @Test @Order(5)
    void kv_deleteRemovesKey() {
        KeyValueBucket kv = db.keyValueBucket("kv_del");
        kv.put("temp", "value");
        kv.delete("temp");
        assertNull(kv.get("temp"));
        assertFalse(kv.exists("temp"));
    }

    @Test @Order(6)
    void kv_deleteNonExistentDoesNotThrow() {
        KeyValueBucket kv = db.keyValueBucket("kv_del2");
        assertDoesNotThrow(() -> kv.delete("phantom_key"));
    }

    @Test @Order(7)
    void kv_100KeysIsolated() {
        KeyValueBucket kv = db.keyValueBucket("kv_100");
        for (int i = 0; i < 100; i++) kv.put("key_" + i, "val_" + i);
        for (int i = 0; i < 100; i++) {
            assertEquals("val_" + i, kv.get("key_" + i), "Key key_" + i + " mismatch");
        }
    }

    @Test @Order(8)
    void kv_specialCharsInKey() {
        KeyValueBucket kv = db.keyValueBucket("kv_special");
        String key = "user:1:session#abc!@%";
        kv.put(key, "token-xyz");
        assertEquals("token-xyz", kv.get(key));
    }

    @Test @Order(9)
    void kv_longValue() {
        KeyValueBucket kv = db.keyValueBucket("kv_longval");
        String bigVal = "X".repeat(50_000);
        kv.put("big", bigVal);
        assertEquals(50_000, kv.get("big").length());
    }

    @Test @Order(10)
    void kv_unicodeKeyAndValue() {
        KeyValueBucket kv = db.keyValueBucket("kv_unicode");
        kv.put("日本語", "日本語の値");
        assertEquals("日本語の値", kv.get("日本語"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "bb", "ccc", "dddd", "eeeee"})
    @Order(11)
    void kv_parameterizedPutGet(String key) {
        KeyValueBucket kv = db.keyValueBucket("kv_param");
        kv.put(key, "val_" + key);
        assertEquals("val_" + key, kv.get(key));
    }

    @Test @Order(12)
    void kv_concurrentWrites() throws Exception {
        KeyValueBucket kv = db.keyValueBucket("kv_concurrent");
        int threads = 10;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 50; i++) {
                    kv.put("t" + tid + "_k" + i, "v" + i);
                }
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        // Spot-check
        for (int t = 0; t < threads; t++) {
            assertEquals("v0", kv.get("t" + t + "_k0"), "Concurrent KV put must not lose data");
        }
    }

    @Test @Order(13)
    void kv_bucketsAreIsolated() {
        KeyValueBucket kvA = db.keyValueBucket("kv_iso_a");
        KeyValueBucket kvB = db.keyValueBucket("kv_iso_b");
        kvA.put("shared_key", "from_a");
        kvB.put("shared_key", "from_b");
        assertEquals("from_a", kvA.get("shared_key"));
        assertEquals("from_b", kvB.get("shared_key"));
    }

    // =======================================================================
    // LIST BUCKET
    // =======================================================================

    @Test @Order(20)
    void list_rpushAndLrange() {
        ListBucket lb = db.listBucket("lb_basic");
        lb.rpush("q", "a", "b", "c");
        assertEquals(List.of("a", "b", "c"), lb.lrange("q", 0, -1));
    }

    @Test @Order(21)
    void list_lpushPrependsToFront() {
        ListBucket lb = db.listBucket("lb_lpush");
        lb.rpush("lp", "x");
        lb.lpush("lp", "z");
        assertEquals("z", lb.lrange("lp", 0, 0).get(0));
    }

    @Test @Order(22)
    void list_lpopAndRpop() {
        ListBucket lb = db.listBucket("lb_pop");
        lb.rpush("pop", "1", "2", "3");
        assertEquals("1", lb.lpop("pop"));
        assertEquals("3", lb.rpop("pop"));
        assertEquals(List.of("2"), lb.lrange("pop", 0, -1));
    }

    @Test @Order(23)
    void list_llen() {
        ListBucket lb = db.listBucket("lb_len");
        lb.rpush("len_list", "a", "b", "c", "d");
        assertEquals(4, lb.llen("len_list"));
    }

    @Test @Order(24)
    void list_ltrimKeepsOnlyRange() {
        ListBucket lb = db.listBucket("lb_ltrim");
        lb.rpush("trim_list", "a", "b", "c", "d", "e");
        lb.ltrim("trim_list", 1, 3);
        assertEquals(List.of("b", "c", "d"), lb.lrange("trim_list", 0, -1));
    }

    @Test @Order(25)
    void list_lrem() {
        ListBucket lb = db.listBucket("lb_lrem");
        lb.rpush("rem_list", "x", "y", "x", "z", "x");
        long removed = lb.lrem("rem_list", 2, "x");
        assertEquals(2, removed);
        var remaining = lb.lrange("rem_list", 0, -1);
        assertEquals(1, remaining.stream().filter("x"::equals).count());
    }

    @Test @Order(26)
    void list_lindex() {
        ListBucket lb = db.listBucket("lb_lindex");
        lb.rpush("idx_list", "first", "second", "third");
        assertEquals("first",  lb.lindex("idx_list", 0));
        assertEquals("second", lb.lindex("idx_list", 1));
        assertEquals("third",  lb.lindex("idx_list", 2));
    }

    @Test @Order(27)
    void list_stats() {
        ListBucket lb = db.listBucket("lb_stats");
        lb.rpush("stat_key", "a", "b");
        var stats = lb.stats();
        assertNotNull(stats);
        assertFalse(stats.isEmpty());
    }

    @Test @Order(28)
    void list_1000Elements() {
        ListBucket lb = db.listBucket("lb_1000");
        for (int i = 0; i < 1000; i++) lb.rpush("big_list", "item_" + i);
        assertEquals(1000, lb.llen("big_list"));
        assertEquals("item_0",   lb.lrange("big_list", 0, 0).get(0));
        assertEquals("item_999", lb.lrange("big_list", 999, 999).get(0));
    }

    // =======================================================================
    // SET BUCKET
    // =======================================================================

    @Test @Order(30)
    void set_saddAndSmembers() {
        SetBucket sb = db.setBucket("sb_basic");
        sb.sadd("colors", "red", "green", "blue");
        var members = sb.smembers("colors");
        assertTrue(members.containsAll(Set.of("red", "green", "blue")));
    }

    @Test @Order(31)
    void set_saddIsDeduplicated() {
        SetBucket sb = db.setBucket("sb_dedup");
        sb.sadd("nums", "1", "2", "1", "3", "2");
        assertEquals(3, sb.scard("nums"), "Sets must deduplicate");
    }

    @Test @Order(32)
    void set_srem() {
        SetBucket sb = db.setBucket("sb_srem");
        sb.sadd("fruits", "apple", "banana", "cherry");
        sb.srem("fruits", "banana");
        assertFalse(sb.sismember("fruits", "banana"));
        assertTrue(sb.sismember("fruits", "apple"));
    }

    @Test @Order(33)
    void set_scard() {
        SetBucket sb = db.setBucket("sb_card");
        sb.sadd("letters", "a", "b", "c", "d");
        assertEquals(4, sb.scard("letters"));
    }

    @Test @Order(34)
    void set_sismember() {
        SetBucket sb = db.setBucket("sb_member");
        sb.sadd("set1", "alpha", "beta");
        assertTrue(sb.sismember("set1", "alpha"));
        assertFalse(sb.sismember("set1", "gamma"));
    }

    @Test @Order(35)
    void set_intersection() {
        SetBucket sb = db.setBucket("sb_inter");
        sb.sadd("sA", "1", "2", "3");
        sb.sadd("sB", "2", "3", "4");
        var inter = sb.sinter("sA", "sB");
        assertEquals(Set.of("2", "3"), inter);
    }

    @Test @Order(36)
    void set_union() {
        SetBucket sb = db.setBucket("sb_union");
        sb.sadd("uA", "a", "b");
        sb.sadd("uB", "b", "c");
        var union = sb.sunion("uA", "uB");
        assertEquals(Set.of("a", "b", "c"), union);
    }

    @Test @Order(37)
    void set_difference() {
        SetBucket sb = db.setBucket("sb_diff");
        sb.sadd("dA", "x", "y", "z");
        sb.sadd("dB", "y", "z");
        var diff = sb.sdiff("dA", "dB");
        assertEquals(Set.of("x"), diff);
    }

    @Test @Order(38)
    void set_spopRemovesRandomMember() {
        SetBucket sb = db.setBucket("sb_spop");
        sb.sadd("pop_set", "m1", "m2", "m3");
        String popped = sb.spop("pop_set");
        assertNotNull(popped);
        assertEquals(2, sb.scard("pop_set"));
        assertFalse(sb.sismember("pop_set", popped));
    }

    @Test @Order(39)
    void set_srandmemberNotRemoved() {
        SetBucket sb = db.setBucket("sb_rand");
        sb.sadd("rand_set", "r1", "r2", "r3");
        String rand = sb.srandmember("rand_set");
        assertNotNull(rand);
        assertEquals(3, sb.scard("rand_set"), "srandmember must not remove the element");
    }

    // =======================================================================
    // HASH BUCKET
    // =======================================================================

    @Test @Order(40)
    void hash_hsetAndHget() {
        HashBucket hb = db.hashBucket("hb_basic");
        hb.hset("u1", "name", "Alice");
        hb.hset("u1", "age", "30");
        assertEquals("Alice", hb.hget("u1", "name"));
        assertEquals("30",    hb.hget("u1", "age"));
    }

    @Test @Order(41)
    void hash_hgetall() {
        HashBucket hb = db.hashBucket("hb_getall");
        hb.hset("h1", "f1", "v1");
        hb.hset("h1", "f2", "v2");
        var all = hb.hgetall("h1");
        assertEquals("v1", all.get("f1"));
        assertEquals("v2", all.get("f2"));
    }

    @Test @Order(42)
    void hash_hdel() {
        HashBucket hb = db.hashBucket("hb_hdel");
        hb.hset("hd1", "field", "val");
        hb.hdel("hd1", "field");
        assertNull(hb.hget("hd1", "field"));
    }

    @Test @Order(43)
    void hash_hexists() {
        HashBucket hb = db.hashBucket("hb_hexists");
        hb.hset("he1", "f", "v");
        assertTrue(hb.hexists("he1", "f"));
        assertFalse(hb.hexists("he1", "missing"));
    }

    @Test @Order(44)
    void hash_hincrby() {
        HashBucket hb = db.hashBucket("hb_incr");
        hb.hset("counter", "views", "10");
        long v1 = hb.hincrby("counter", "views", 5);
        assertEquals(15, v1);
        long v2 = hb.hincrby("counter", "views", -3);
        assertEquals(12, v2);
    }

    @Test @Order(45)
    void hash_hincrbyfloat() {
        HashBucket hb = db.hashBucket("hb_incrf");
        hb.hset("rate", "value", "1.5");
        String result = hb.hincrbyfloat("rate", "value", 0.5);
        assertNotNull(result);
        double parsed = Double.parseDouble(result);
        assertEquals(2.0, parsed, 0.001);
    }

    @Test @Order(46)
    void hash_hkeys() {
        HashBucket hb = db.hashBucket("hb_hkeys");
        hb.hset("hk", "k1", "v1");
        hb.hset("hk", "k2", "v2");
        hb.hset("hk", "k3", "v3");
        var keys = hb.hkeys("hk");
        assertEquals(3, keys.size());
        assertTrue(keys.containsAll(List.of("k1", "k2", "k3")));
    }

    @Test @Order(47)
    void hash_hvals() {
        HashBucket hb = db.hashBucket("hb_hvals");
        hb.hset("hv", "a", "1");
        hb.hset("hv", "b", "2");
        var vals = hb.hvals("hv");
        assertEquals(2, vals.size());
        assertTrue(vals.containsAll(List.of("1", "2")));
    }

    @Test @Order(48)
    void hash_hmget() {
        HashBucket hb = db.hashBucket("hb_hmget");
        hb.hset("hmg", "x", "10");
        hb.hset("hmg", "y", "20");
        hb.hset("hmg", "z", "30");
        var result = hb.hmget("hmg", "x", "z");
        assertEquals("10", result.get("x"));
        assertEquals("30", result.get("z"));
    }

    @Test @Order(49)
    void hash_hlen() {
        HashBucket hb = db.hashBucket("hb_hlen");
        hb.hset("hl", "f1", "v1");
        hb.hset("hl", "f2", "v2");
        hb.hset("hl", "f3", "v3");
        assertEquals(3, hb.hlen("hl"));
    }

    @Test @Order(50)
    void hash_hsetMultipleFieldsAtOnce() {
        HashBucket hb = db.hashBucket("hb_multi");
        Map<String, String> fields = Map.of("a", "1", "b", "2", "c", "3");
        int added = hb.hset("mhash", fields);
        assertEquals(3, added);
        assertEquals("1", hb.hget("mhash", "a"));
        assertEquals("2", hb.hget("mhash", "b"));
    }

    @Test @Order(51)
    void hash_concurrentIncrements() throws Exception {
        HashBucket hb = db.hashBucket("hb_concurrent");
        hb.hset("shared", "count", "0");
        int threads = 10;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 10; i++) hb.hincrby("shared", "count", 1);
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        long finalCount = Long.parseLong(hb.hget("shared", "count"));
        assertEquals(100, finalCount, "All concurrent increments must be applied");
    }
}