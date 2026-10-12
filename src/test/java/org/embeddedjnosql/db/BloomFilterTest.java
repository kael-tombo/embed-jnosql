package org.embeddedjnosql.db;

import org.embeddedjnosql.db.storage.spi.BloomFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BloomFilter correctness and robustness tests")
class BloomFilterTest {

    @Test
    @DisplayName("Basic add and mightContain functionality")
    void testBasicAddAndContain() {
        BloomFilter filter = new BloomFilter(0.01, 1000);

        for (int i = 0; i < 500; i++) {
            filter.add("key-" + i);
        }

        // All added elements MUST return true
        for (int i = 0; i < 500; i++) {
            assertTrue(filter.mightContain("key-" + i), "Filter must contain key-" + i);
        }

        // Elements not added should mostly return false
        int falsePositives = 0;
        for (int i = 500; i < 1500; i++) {
            if (filter.mightContain("key-" + i)) {
                falsePositives++;
            }
        }

        double observedFpRate = (double) falsePositives / 1000.0;
        assertTrue(observedFpRate < 0.05, "Observed FP rate should be low, got: " + observedFpRate);
    }

    @Test
    @DisplayName("Null input safety")
    void testNullSafety() {
        BloomFilter filter = new BloomFilter(0.01, 100);
        assertDoesNotThrow(() -> filter.add(null));
        assertFalse(filter.mightContain(null));
    }

    @Test
    @DisplayName("Constructor parameter bounds checking")
    void testConstructorValidation() {
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0.0, 100));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(1.0, 100));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-0.5, 100));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0.01, 0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0.01, -10));

        BloomFilter filter = new BloomFilter(0.05, 500);
        assertTrue(filter.bitSetSize() > 0);
        assertTrue(filter.hashCount() > 0);
    }

    @Test
    @DisplayName("Clear resets state")
    void testClear() {
        BloomFilter filter = new BloomFilter(0.01, 100);
        filter.add("item1");
        filter.add("item2");
        assertTrue(filter.mightContain("item1"));

        filter.clear();
        assertFalse(filter.mightContain("item1"));
        assertEquals(0.0, filter.expectedFalsePositiveRate());
    }

    @Test
    @DisplayName("Negative and Integer.MIN_VALUE hash codes never throw IndexOutOfBoundsException")
    void testNegativeHashHandling() {
        BloomFilter filter = new BloomFilter(0.01, 100);

        // Test with a synthetic object or crafted strings that have negative hashes
        String[] testStrings = {
                "",
                "polygenelubricants", // Known collision/special hash string
                "a",
                "\uFFFF\uD800\uDC00",
                "special-key-\u0000-null-byte"
        };

        for (String s : testStrings) {
            assertDoesNotThrow(() -> filter.add(s));
            assertTrue(filter.mightContain(s));
        }
    }
}
