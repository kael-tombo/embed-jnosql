package org.embeddedjnosql.db.core.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom random;

    public EncryptionService(String base64Key) {
        var keyBytes = Base64.getDecoder().decode(base64Key);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.random = new SecureRandom();
    }

    public EncryptionService(byte[] keyBytes) {
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.random = new SecureRandom();
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            var cipher = Cipher.getInstance(ALGORITHM);
            var spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            var ciphertext = cipher.doFinal(plaintext.getBytes());
            var combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new org.embeddedjnosql.db.core.exception.EmbedJNoSQLException("Encryption failed", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            var combined = Base64.getDecoder().decode(encrypted);
            var iv = new byte[GCM_IV_LENGTH];
            var ciphertext = new byte[combined.length - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            var cipher = Cipher.getInstance(ALGORITHM);
            var spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            var plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext);
        } catch (Exception e) {
            throw new org.embeddedjnosql.db.core.exception.EmbedJNoSQLException("Decryption failed", e);
        }
    }

    public static String generateKey() {
        var key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
