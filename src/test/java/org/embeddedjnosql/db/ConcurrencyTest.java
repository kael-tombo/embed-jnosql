package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTest {

    private EmbedJNoSQL db;
    private DocumentCollection collection;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        collection = db.documentCollection("concurrent");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void concurrentInserts() throws InterruptedException {
        var executor = Executors.newFixedThreadPool(10);
        var latch = new CountDownLatch(10);
        var errors = new AtomicInteger(0);

        for (int t = 0; t < 10; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        collection.insert(Document.of("thread", threadId).add("seq", i));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errors.get());
        assertEquals(1000, collection.count());
    }

    @Test
    void concurrentReadsAndWrites() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            collection.insert(Document.of("key", "item-" + i).add("value", i));
        }

        var executor = Executors.newFixedThreadPool(5);
        var latch = new CountDownLatch(5);
        var errors = new AtomicInteger(0);

        for (int t = 0; t < 5; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        if (i % 2 == 0) {
                            collection.insert(Document.of("writer", threadId).add("op", i));
                        } else {
                            collection.find(org.embeddedjnosql.db.nosql.document.Query.eq("key", "item-0"));
                        }
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errors.get());
        assertTrue(collection.count() >= 10);
    }

    @Test
    void concurrentKeyValueOps() throws InterruptedException {
        var executor = Executors.newFixedThreadPool(10);
        var latch = new CountDownLatch(10);
        var bucket = db.keyValueBucket("concurrent-kv");
        var errors = new AtomicInteger(0);

        for (int t = 0; t < 10; t++) {
            int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        bucket.increment("counter-" + threadId);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        assertEquals(0, errors.get());
        for (int t = 0; t < 10; t++) {
            assertEquals("100", bucket.get("counter-" + t));
        }
    }
}
