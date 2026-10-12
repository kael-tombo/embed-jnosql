package org.embeddedjnosql.db.storage.spi;

import java.util.BitSet;

/**
 * Probabilistic data structure for fast set membership testing in LSM-Tree storage.
 */
public class BloomFilter {

    private final BitSet bitSet;
    private final int bitSetSize;
    private final int hashCount;

    public BloomFilter(double falsePositiveRate, int expectedElements) {
        if (falsePositiveRate <= 0.0 || falsePositiveRate >= 1.0) {
            throw new IllegalArgumentException("False positive rate must be between 0 and 1 exclusive: " + falsePositiveRate);
        }
        if (expectedElements <= 0) {
            throw new IllegalArgumentException("Expected elements must be positive: " + expectedElements);
        }
        this.bitSetSize = Math.max(64, optimalBitSetSize(expectedElements, falsePositiveRate));
        this.hashCount = optimalHashCount(bitSetSize, expectedElements);
        this.bitSet = new BitSet(bitSetSize);
    }

    public void add(String element) {
        if (element == null) return;
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            bitSet.set(Math.floorMod(hash, bitSetSize));
        }
    }

    public boolean mightContain(String element) {
        if (element == null) return false;
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            if (!bitSet.get(Math.floorMod(hash, bitSetSize))) {
                return false;
            }
        }
        return true;
    }

    private int[] getHashes(String element) {
        int[] hashes = new int[hashCount];
        int hash1 = element.hashCode();
        // Murmur3-inspired 32-bit avalanche mix for independent second hash
        int hash2 = mix32(hash1);
        if (hash2 == 0) {
            hash2 = 0x9e3779b9;
        }

        for (int i = 0; i < hashCount; i++) {
            hashes[i] = hash1 + i * hash2;
        }
        return hashes;
    }

    private static int mix32(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private int optimalBitSetSize(int n, double p) {
        return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
    }

    private int optimalHashCount(int m, int n) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    public double expectedFalsePositiveRate() {
        return Math.pow((double) bitSet.cardinality() / bitSetSize, hashCount);
    }

    public int bitSetSize() {
        return bitSetSize;
    }

    public int hashCount() {
        return hashCount;
    }

    public void clear() {
        bitSet.clear();
    }
}