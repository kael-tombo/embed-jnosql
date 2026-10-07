package org.embeddedjnosql.db.demo.stress.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lightweight domain record used as the payload for load and stress testing.
 * Designed to exercise both NoSQL document storage and key-value operations.
 *
 * @param id        Unique record identifier
 * @param threadId  ID of the thread that produced this record
 * @param payload   Synthetic content simulating a variable-size message body
 * @param category  Workload category (READ_HEAVY, WRITE_HEAVY, MIXED, QUERY)
 * @param timestamp Creation epoch millis
 */
public record StressRecord(
        String id,
        String threadId,
        String payload,
        String category,
        long timestamp
) {

    /** Generates a record with a random UUID, bound to the given thread and category. */
    public static StressRecord generate(String threadId, String category, int payloadSize) {
        String id = "SR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String payload = "X".repeat(Math.max(1, payloadSize));
        return new StressRecord(id, threadId, payload, category, System.currentTimeMillis());
    }

    /** Converts to a plain Map suitable for EmbedJNoSQL document insertion. */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("id", id);
        m.put("threadId", threadId);
        m.put("payload", payload);
        m.put("category", category);
        m.put("timestamp", timestamp);
        return m;
    }
}
