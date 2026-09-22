package org.junify.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for R-33: a document TTL must survive the storage round-trip.
 *
 * <p>{@code JsonSerde.fromJson}'s custom {@code Document} path rebuilt only {@code id} and
 * {@code fields}, so {@code expiresAt} was dropped on every read: the API reported
 * {@code expiresAt: null}, {@code isExpired()} was always false, and documents never
 * expired even though the expiry was written to disk.</p>
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class DocumentTtlPersistenceTest {

    @Test
    void ttlSurvivesTheStorageRoundTripAndExpires() throws Exception {
        try (var db = JunifyDB.embed().build()) {
            var collection = db.documentCollection("ttl-round-trip");

            var longLived = collection.insert(org.junify.db.nosql.document.Document.of("name", "keep"), 600);
            var stored = collection.findById(longLived.id());
            assertNotNull(stored, "document should be readable");
            assertNotNull(stored.getExpiresAt(), "expiresAt must survive the storage round-trip");
            assertFalse(stored.isExpired(), "a 600s TTL must not be expired yet");

            var shortLived = collection.insert(org.junify.db.nosql.document.Document.of("name", "expire"), 1);
            var expiring = collection.findById(shortLived.id());
            assertNotNull(expiring, "document should be readable right after insert");
            assertNotNull(expiring.getExpiresAt(), "expiresAt must survive the storage round-trip");
            assertEquals("expire", expiring.get("name"), "fields survive the round-trip too");

            Thread.sleep(1200);

            // R-51 (2026-09-22): an expired document now reads as ABSENT — the
            // same semantics the KV engine always had — instead of coming back
            // with expired:true. The physical sweep still removes the row.
            assertNull(collection.findById(shortLived.id()),
                    "an expired document must read as absent (R-51)");

            assertEquals(1, collection.cleanupExpired(), "cleanupExpired should remove the expired document");
            assertNull(collection.findById(shortLived.id()), "expired document must be gone after cleanup");
            assertNotNull(collection.findById(longLived.id()), "the long-lived document must remain");

            var ttlStats = collection.ttlStats();
            assertEquals(1L, ttlStats.get("withTtl"), "ttl stats must see the remaining TTL: " + ttlStats);
        }
    }

    @Test
    void setTtlIsVisibleOnReadBack() throws Exception {
        try (var db = JunifyDB.embed().build()) {
            var collection = db.documentCollection("ttl-set");
            var doc = collection.insert(org.junify.db.nosql.document.Document.of("name", "row"));

            assertEquals(1, collection.setTtl(doc.id(), 300));

            var reloaded = collection.findById(doc.id());
            assertNotNull(reloaded);
            assertNotNull(reloaded.getExpiresAt(), "setTtl must be visible on the next read");
            assertFalse(reloaded.isExpired());

            assertEquals(1, collection.setTtl(doc.id(), 0), "ttl 0 clears the expiry");
            assertNull(collection.findById(doc.id()).getExpiresAt(), "cleared TTL must read back as null");
        }
    }
}
