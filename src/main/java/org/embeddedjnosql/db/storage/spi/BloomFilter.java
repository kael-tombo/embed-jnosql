package org.embeddedjnosql.db.storage.spi;

import java.util.BitSet;
import java.util.Random;

public class BloomFilter {

    private final BitSet bitSet;
    private final int bitSetSize;
    private final int hashCount;
    private final Random random;

    public BloomFilter(double falsePositiveRate, int expectedElements) {
        this.bitSetSize = optimalBitSetSize(expectedElements, falsePositiveRate);
        this.hashCount = optimalHashCount(bitSetSize, expectedElements);
        this.bitSet = new BitSet(bitSetSize);
        this.random = new Random();
    }

    public void add(String element) {
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            bitSet.set(Math.abs(hash % bitSetSize));
        }
    }

    public boolean mightContain(String element) {
        int[] hashes = getHashes(element);
        for (int hash : hashes) {
            if (!bitSet.get(Math.abs(hash % bitSetSize))) {
                return false;
            }
        }
        return true;
    }

    private int[] getHashes(String element) {
        int[] hashes = new int[hashCount];
        int hash1 = element.hashCode();
        int hash2 = hash1 >>> 16;
        
        for (int i = 0; i < hashCount; i++) {
            hashes[i] = hash1 + i * hash2;
        }
        return hashes;
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

    public void clear() {
        bitSet.clear();
    }
}