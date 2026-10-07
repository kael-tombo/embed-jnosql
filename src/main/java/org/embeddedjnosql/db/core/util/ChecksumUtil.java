package org.embeddedjnosql.db.core.util;

import java.util.zip.CRC32;

/**
 * CRC32 checksum utility for data integrity verification.
 * 
 * Usage:
 * ```java
 * long checksum = ChecksumUtil.calculate(data);
 * boolean valid = ChecksumUtil.verify(data, checksum);
 * ```
 */
public class ChecksumUtil {

    /**
     * Calculate CRC32 checksum for a byte array.
     */
    public static long calculate(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data, 0, data.length);
        return crc.getValue();
    }

    /**
     * Calculate CRC32 checksum for a string (UTF-8 encoded).
     */
    public static long calculate(String data) {
        return calculate(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Verify data against expected checksum.
     * 
     * @param data Data to verify
     * @param expectedChecksum Expected CRC32 value
     * @return true if checksum matches
     */
    public static boolean verify(byte[] data, long expectedChecksum) {
        return calculate(data) == expectedChecksum;
    }

    /**
     * Verify string data against expected checksum.
     */
    public static boolean verify(String data, long expectedChecksum) {
        return verify(data.getBytes(java.nio.charset.StandardCharsets.UTF_8), expectedChecksum);
    }

    /**
     * Create a checksummed payload (data + checksum).
     * Format: [4 bytes length][data][8 bytes CRC32]
     */
    public static byte[] pack(byte[] data) {
        byte[] packed = new byte[4 + data.length + 8];
        
        // Write length (4 bytes, big-endian)
        int len = data.length;
        packed[0] = (byte) ((len >> 24) & 0xFF);
        packed[1] = (byte) ((len >> 16) & 0xFF);
        packed[2] = (byte) ((len >> 8) & 0xFF);
        packed[3] = (byte) (len & 0xFF);
        
        // Write data
        System.arraycopy(data, 0, packed, 4, data.length);
        
        // Write checksum (8 bytes, big-endian)
        long checksum = calculate(data);
        int offset = 4 + data.length;
        packed[offset] = (byte) ((checksum >> 56) & 0xFF);
        packed[offset + 1] = (byte) ((checksum >> 48) & 0xFF);
        packed[offset + 2] = (byte) ((checksum >> 40) & 0xFF);
        packed[offset + 3] = (byte) ((checksum >> 32) & 0xFF);
        packed[offset + 4] = (byte) ((checksum >> 24) & 0xFF);
        packed[offset + 5] = (byte) ((checksum >> 16) & 0xFF);
        packed[offset + 6] = (byte) ((checksum >> 8) & 0xFF);
        packed[offset + 7] = (byte) (checksum & 0xFF);
        
        return packed;
    }

    /**
     * Unpack and verify a checksummed payload.
     * 
     * @param packed Packed data [4 bytes length][data][8 bytes CRC32]
     * @return Original data if checksum valid
     * @throws ChecksumException if checksum verification fails
     */
    public static byte[] unpack(byte[] packed) throws ChecksumException {
        if (packed.length < 12) {
            throw new ChecksumException("Packed data too small: " + packed.length);
        }

        // Read length
        int len = ((packed[0] & 0xFF) << 24) |
                  ((packed[1] & 0xFF) << 16) |
                  ((packed[2] & 0xFF) << 8) |
                  (packed[3] & 0xFF);

        if (packed.length != 4 + len + 8) {
            throw new ChecksumException("Invalid packed data length");
        }

        // Extract data
        byte[] data = new byte[len];
        System.arraycopy(packed, 4, data, 0, len);

        // Read stored checksum
        int offset = 4 + len;
        long storedChecksum = ((packed[offset] & 0xFFL) << 56) |
                              ((packed[offset + 1] & 0xFFL) << 48) |
                              ((packed[offset + 2] & 0xFFL) << 40) |
                              ((packed[offset + 3] & 0xFFL) << 32) |
                              ((packed[offset + 4] & 0xFFL) << 24) |
                              ((packed[offset + 5] & 0xFFL) << 16) |
                              ((packed[offset + 6] & 0xFFL) << 8) |
                              (packed[offset + 7] & 0xFFL);

        // Verify checksum
        long calculatedChecksum = calculate(data);
        if (calculatedChecksum != storedChecksum) {
            throw new ChecksumException(
                "Checksum mismatch: expected " + storedChecksum + ", got " + calculatedChecksum);
        }

        return data;
    }

    /**
     * Exception thrown when checksum verification fails.
     */
    public static class ChecksumException extends Exception {
        public ChecksumException(String message) {
            super(message);
        }
    }
}
