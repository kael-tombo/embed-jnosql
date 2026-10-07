package org.embeddedjnosql.db;

import org.embeddedjnosql.db.core.crypto.EncryptionService;
import org.embeddedjnosql.db.core.util.ChecksumUtil;
import org.embeddedjnosql.db.core.util.RetryWithBackoff;
import org.embeddedjnosql.db.index.hnsw.VectorIndex;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentAggregation;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.document.QueryExplain;
import org.embeddedjnosql.db.security.PasswordPolicy;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage extension tests for core utilities, security policies, aggregation,
 * explain plans, and crypto service.
 */
class UtilityClassTest {

    // ==========================================
    // ChecksumUtil Tests
    // ==========================================
    @Test
    void testChecksumUtilCalculationAndVerification() {
        byte[] data = "Hello, EmbedJNoSQL!".getBytes();
        long checksum = ChecksumUtil.calculate(data);
        assertTrue(checksum != 0);
        assertTrue(ChecksumUtil.verify(data, checksum));
        assertFalse(ChecksumUtil.verify(data, checksum + 1));

        String strData = "Hello, String Checksum!";
        long strChecksum = ChecksumUtil.calculate(strData);
        assertTrue(ChecksumUtil.verify(strData, strChecksum));
        assertFalse(ChecksumUtil.verify(strData, strChecksum + 99));
    }

    @Test
    void testChecksumUtilPackAndUnpack() throws Exception {
        byte[] data = "Mission Critical Database Record".getBytes();
        byte[] packed = ChecksumUtil.pack(data);
        assertEquals(4 + data.length + 8, packed.length);

        byte[] unpacked = ChecksumUtil.unpack(packed);
        assertArrayEquals(data, unpacked);
    }

    @Test
    void testChecksumUtilUnpackErrors() {
        // Too small
        assertThrows(ChecksumUtil.ChecksumException.class, () -> ChecksumUtil.unpack(new byte[10]));

        // Invalid length header
        byte[] invalidHeader = new byte[16];
        invalidHeader[0] = 0; invalidHeader[1] = 0; invalidHeader[2] = 0; invalidHeader[3] = 100; // expects 100 bytes data
        assertThrows(ChecksumUtil.ChecksumException.class, () -> ChecksumUtil.unpack(invalidHeader));

        // Corrupted data (checksum mismatch)
        byte[] data = "Valid Data".getBytes();
        byte[] packed = ChecksumUtil.pack(data);
        packed[4] = (byte) (packed[4] ^ 0xFF); // flip a bit in data
        assertThrows(ChecksumUtil.ChecksumException.class, () -> ChecksumUtil.unpack(packed));
    }

    // ==========================================
    // RetryWithBackoff Tests
    // ==========================================
    @Test
    void testRetryWithBackoffSuccessFirstAttempt() {
        RetryWithBackoff<String> retry = new RetryWithBackoff<>(3, 1, 10, 1.5, false);
        String res = retry.execute(() -> "success");
        assertEquals("success", res);
    }

    @Test
    void testRetryWithBackoffSuccessAfterRetries() {
        RetryWithBackoff<Integer> retry = new RetryWithBackoff<>(4, 1, 5, 1.2, true);
        AtomicInteger count = new AtomicInteger(0);
        int val = retry.execute(() -> {
            if (count.incrementAndGet() < 3) {
                throw new IllegalStateException("temporary failure");
            }
            return 42;
        });
        assertEquals(42, val);
        assertEquals(3, count.get());
    }

    @Test
    void testRetryWithBackoffExhaustion() {
        RetryWithBackoff<String> retry = new RetryWithBackoff<>(2, 1, 5, 2.0, false);
        List<RetryWithBackoff.RetryContext> contexts = new ArrayList<>();

        assertThrows(RetryWithBackoff.RetryExhaustedException.class, () ->
            retry.execute(() -> {
                throw new RuntimeException("permanent failure");
            }, contexts::add)
        );
        assertEquals(2, contexts.size());
        assertEquals(1, contexts.get(0).attempt());
        assertEquals(2, contexts.get(1).attempt());
    }

    @Test
    void testRetryWithBackoffRunnable() {
        RetryWithBackoff<Void> retry = new RetryWithBackoff<>();
        AtomicInteger runs = new AtomicInteger(0);
        retry.executeRunnable(runs::incrementAndGet);
        assertEquals(1, runs.get());

        RetryWithBackoff<Void> failingRetry = new RetryWithBackoff<>(2, 1, 5, 2.0, false);
        List<RetryWithBackoff.RetryContext> contexts = new ArrayList<>();
        assertThrows(RetryWithBackoff.RetryExhaustedException.class, () ->
            failingRetry.executeRunnable(() -> {
                throw new RuntimeException("runnable fail");
            }, contexts::add)
        );
        assertEquals(2, contexts.size());
    }

    // ==========================================
    // DocumentAggregation Tests
    // ==========================================
    @Test
    void testDocumentAggregationMetrics() {
        Document d1 = new Document().add("dept", "ENG").add("salary", 100).add("rank", 1);
        Document d2 = new Document().add("dept", "ENG").add("salary", 200).add("rank", 2);
        Document d3 = new Document().add("dept", "HR").add("salary", 150).add("rank", 3);
        List<Document> docs = List.of(d1, d2, d3);

        assertEquals(3, DocumentAggregation.count(docs));
        assertEquals(1, DocumentAggregation.count(Query.eq("dept", "HR"), docs));

        Optional<Document> minDoc = DocumentAggregation.min(docs, "salary");
        assertTrue(minDoc.isPresent());
        assertEquals(100, minDoc.get().<Integer>get("salary"));

        Optional<Document> maxDoc = DocumentAggregation.max(docs, "salary");
        assertTrue(maxDoc.isPresent());
        assertEquals(200, maxDoc.get().<Integer>get("salary"));

        assertEquals(450.0, DocumentAggregation.sum(docs, "salary"));
        assertEquals(150.0, DocumentAggregation.avg(docs, "salary"));
        assertEquals(0.0, DocumentAggregation.avg(Collections.emptyList(), "salary"));

        // Group by
        Map<Object, Long> deptCounts = DocumentAggregation.groupBy(docs, "dept");
        assertEquals(2L, deptCounts.get("ENG"));
        assertEquals(1L, deptCounts.get("HR"));

        Map<Object, List<Document>> deptDocs = DocumentAggregation.groupByDocuments(docs, "dept");
        assertEquals(2, deptDocs.get("ENG").size());

        Map<String, Long> customGroup = DocumentAggregation.groupBy(docs, "dept", doc -> doc.<String>get("dept").toLowerCase());
        assertEquals(2L, customGroup.get("eng"));

        // Distinct
        assertEquals(2, DocumentAggregation.distinct(docs, "dept").size());
        assertEquals(2, DocumentAggregation.distinctValues(docs, "dept").size());

        // First / Last / Limit / Skip
        assertEquals(d1, DocumentAggregation.first(docs).orElse(null));
        assertEquals(d3, DocumentAggregation.last(docs).orElse(null));
        assertTrue(DocumentAggregation.first(Collections.emptyList()).isEmpty());
        assertTrue(DocumentAggregation.last(Collections.emptyList()).isEmpty());

        assertEquals(2, DocumentAggregation.limit(docs, 2).size());
        assertEquals(1, DocumentAggregation.skip(docs, 2).size());

        // Order by
        List<Document> asc = DocumentAggregation.orderBy(docs, "salary", Query.SortOrder.ASC);
        assertEquals(d1, asc.get(0));
        assertEquals(d2, asc.get(2));

        List<Document> desc = DocumentAggregation.orderBy(docs, "salary", Query.SortOrder.DESC);
        assertEquals(d2, desc.get(0));
        assertEquals(d1, desc.get(2));

        // AggregationResult
        DocumentAggregation.AggregationResult ar = new DocumentAggregation.AggregationResult();
        ar.add("total", 450.0).add("count", 3);
        assertEquals(450.0, ar.get("total"));
        assertEquals(2, ar.toMap().size());
        assertTrue(ar.toString().contains("450.0"));
    }

    // ==========================================
    // PasswordPolicy Tests
    // ==========================================
    @Test
    void testPasswordPolicyValidation() {
        // Null or empty
        var resNull = PasswordPolicy.validate(null);
        assertFalse(resNull.valid());
        assertTrue(resNull.violations().contains("Password is required"));

        var resEmpty = PasswordPolicy.validate("");
        assertFalse(resEmpty.valid());

        // Too short (< 12)
        var resShort = PasswordPolicy.validate("Ab1!short");
        assertFalse(resShort.valid());
        assertTrue(resShort.violations().stream().anyMatch(v -> v.contains("at least 12")));

        // Too long (> 128)
        var resLong = PasswordPolicy.validate("A1!" + "a".repeat(130));
        assertFalse(resLong.valid());
        assertTrue(resLong.violations().stream().anyMatch(v -> v.contains("must not exceed 128")));

        // Missing lowercase
        var resNoLower = PasswordPolicy.validate("ALLCAPS123456!@#");
        assertFalse(resNoLower.valid());
        assertTrue(resNoLower.violations().stream().anyMatch(v -> v.contains("lowercase")));

        // Missing uppercase
        var resNoUpper = PasswordPolicy.validate("alllower123456!@#");
        assertFalse(resNoUpper.valid());
        assertTrue(resNoUpper.violations().stream().anyMatch(v -> v.contains("uppercase")));

        // Missing number
        var resNoNum = PasswordPolicy.validate("AllLettersNoNum!@#");
        assertFalse(resNoNum.valid());
        assertTrue(resNoNum.violations().stream().anyMatch(v -> v.contains("number")));

        // Missing special
        var resNoSpec = PasswordPolicy.validate("LettersAndNumbers123456");
        assertFalse(resNoSpec.valid());
        assertTrue(resNoSpec.violations().stream().anyMatch(v -> v.contains("special character")));

        // Common password
        var resCommon = PasswordPolicy.validate("Admin123456789!@#");
        assertFalse(resCommon.valid());
        assertTrue(resCommon.violations().stream().anyMatch(v -> v.contains("too common")));

        // Sequential characters
        var resSeq = PasswordPolicy.validate("XyZ_abc_987654321!");
        assertFalse(resSeq.valid());
        assertTrue(resSeq.violations().stream().anyMatch(v -> v.contains("sequential")));

        // Repeated characters
        var resRep = PasswordPolicy.validate("ZZZZZZZZZZ_1234!Aa");
        assertFalse(resRep.valid());
        assertTrue(resRep.violations().stream().anyMatch(v -> v.contains("repeated")));

        // Valid strong password
        var resValid = PasswordPolicy.validate("K7#mP9$vL2@qW5!z");
        assertTrue(resValid.valid());
        assertTrue(resValid.violations().isEmpty());
    }

    @Test
    void testPasswordPolicyStrengthScoring() {
        assertEquals("weak", PasswordPolicy.getStrengthLevel("short"));
        assertEquals("fair", PasswordPolicy.getStrengthLevel("MediumPw"));
        assertEquals("good", PasswordPolicy.getStrengthLevel("LongerPasswordWithoutNumbers"));
        assertTrue(PasswordPolicy.calculateStrength("K7#mP9$vL2@qW5!z") >= 80);
        assertEquals("strong", PasswordPolicy.getStrengthLevel("K7#mP9$vL2@qW5!z"));
    }

    // ==========================================
    // QueryExplain Tests
    // ==========================================
    @Test
    void testQueryExplainBuilderAndMap() {
        Query query = Query.eq("status", "active");
        QueryExplain explain = QueryExplain.explain(query)
                .collection("orders")
                .estimatedCost(42)
                .estimatedResults(15)
                .executionPlan("INDEX_SCAN")
                .indexUsed(List.of("status_idx"))
                .analysisTimeMs(3)
                .build();

        assertEquals(query, explain.getQuery());
        assertEquals("orders", explain.getCollection());
        assertEquals(42, explain.getEstimatedCost());
        assertEquals(15, explain.getEstimatedResults());
        assertEquals("INDEX_SCAN", explain.getExecutionPlan());
        assertEquals(List.of("status_idx"), explain.getIndexUsed());
        assertEquals(3, explain.getAnalysisTimeMs());

        Map<String, Object> map = explain.toMap();
        assertEquals("orders", map.get("collection"));
        assertEquals(List.of("status_idx"), map.get("indexUsed"));
        assertTrue(explain.toString().contains("orders"));

        // Explain without index
        QueryExplain noIdx = QueryExplain.explain(query).build();
        assertEquals("NONE (full scan)", noIdx.toMap().get("indexUsed"));
    }

    // ==========================================
    // EncryptionService Tests
    // ==========================================
    @Test
    void testEncryptionServiceRoundtrip() {
        String key = EncryptionService.generateKey();
        assertNotNull(key);

        EncryptionService service = new EncryptionService(key);
        String secret = "SuperSecretDatabaseToken123!";
        String cipher = service.encrypt(secret);
        assertNotEquals(secret, cipher);

        String decrypted = service.decrypt(cipher);
        assertEquals(secret, decrypted);

        // Byte array constructor
        byte[] rawKey = Base64.getDecoder().decode(key);
        EncryptionService service2 = new EncryptionService(rawKey);
        assertEquals(secret, service2.decrypt(cipher));

        // Corrupted ciphertext throws exception
        assertThrows(RuntimeException.class, () -> service.decrypt("corruptedPayload"));
    }

    // ==========================================
    // VectorIndex Tests
    // ==========================================
    @Test
    void testVectorIndexOperations() {
        VectorIndex index = new VectorIndex(3, 16, 200);

        index.add("doc1", new float[]{1.0f, 0.0f, 0.0f});
        index.add("doc2", new float[]{0.0f, 1.0f, 0.0f});
        index.add("doc3", new float[]{0.9f, 0.1f, 0.0f});

        assertEquals(3, index.size());

        // Dimension mismatch
        assertThrows(IllegalArgumentException.class, () -> index.add("err", new float[]{1.0f, 0.0f}));
        assertThrows(IllegalArgumentException.class, () -> index.search(new float[]{1.0f}, 1));

        // Cosine similarity search: closest to [1, 0, 0] should be doc1 then doc3
        List<String> results = index.search(new float[]{1.0f, 0.0f, 0.0f}, 2);
        assertEquals(2, results.size());
        assertEquals("doc1", results.get(0));
        assertEquals("doc3", results.get(1));

        // Euclidean distance search
        List<String> euclidean = index.searchEuclidean(new float[]{0.0f, 1.0f, 0.0f}, 1);
        assertEquals(1, euclidean.size());
        assertEquals("doc2", euclidean.get(0));

        // Remove
        index.remove("doc1");
        assertEquals(2, index.size());
    }
}
