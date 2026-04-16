package com.datatalk.application.persistence;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
public class SecretVault {
    private static final byte VERSION = 0x01;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom rng = new SecureRandom();
    public SecretVault(byte[] master32) {
        if (master32 == null || master32.length != 32) {
            throw new IllegalArgumentException("master key must be 32 bytes (AES-256)");
        }
        this.key = new SecretKeySpec(master32, "AES");
    }
    public byte[] seal(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            rng.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + NONCE_BYTES + ct.length).put(VERSION).put(nonce).put(ct).array();
        } catch (Exception e) { throw new IllegalStateException("seal failed", e); }
    }
    public String open(byte[] envelope) {
        if (envelope == null || envelope.length < 1 + NONCE_BYTES + 16) {
            throw new IllegalStateException("invalid envelope");
        }
        if (envelope[0] != VERSION) { throw new IllegalStateException("unsupported envelope version"); }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            System.arraycopy(envelope, 1, nonce, 0, NONCE_BYTES);
            byte[] ct = new byte[envelope.length - 1 - NONCE_BYTES];
            System.arraycopy(envelope, 1 + NONCE_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("open failed", e); }
    }
}
