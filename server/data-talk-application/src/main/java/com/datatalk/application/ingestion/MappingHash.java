package com.datatalk.application.ingestion;

import com.datatalk.domain.ingestion.IngestionMapping;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Computes a deterministic SHA-256 hash over an {@link IngestionMapping}.
 * The hash covers mappingId and every column's sourcePath, targetName, type, skip, and nullable.
 * sampleValues are intentionally excluded so the hash remains stable across re-inference.
 */
public final class MappingHash {

    private MappingHash() {}

    public static String compute(IngestionMapping mapping) {
        if (mapping == null) return "";
        MessageDigest md = sha256();
        updateUtf8(md, nullable(mapping.mappingId()));
        if (mapping.columns() != null) {
            for (var col : mapping.columns()) {
                updateUtf8(md, "\0");
                updateUtf8(md, nullable(col.sourcePath()));
                updateUtf8(md, nullable(col.targetName()));
                updateUtf8(md, col.type() != null ? col.type().name() : "");
                updateUtf8(md, col.skip() ? "1" : "0");
                updateUtf8(md, col.nullable() ? "1" : "0");
            }
        }
        return hexEncode(md.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void updateUtf8(MessageDigest md, String s) {
        md.update(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String nullable(String s) {
        return s != null ? s : "";
    }

    private static String hexEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
