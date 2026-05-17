package com.datatalk.adapter.actions.util;

import net.coobird.thumbnailator.Thumbnails;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

/**
 * Transparent image compression used by the {@code datatalk.file_read} MCP tool to
 * keep base64 payloads under model-side per-tool-result limits while preserving
 * legibility of typical UI screenshots.
 *
 * <p>See {@code openspec/changes/optimize-file-upload-image-and-latency/specs/file-read-image-pipeline/spec.md}
 * for the binding contract. Key rules:
 * <ul>
 *   <li>PNG / JPEG / WebP / BMP → resize maxEdge=1024 + JPEG q=0.75 → mime becomes {@code image/jpeg}</li>
 *   <li>GIF → passthrough (preserve animation), {@code skipReason="animated_passthrough"}</li>
 *   <li>≤ 25KB → passthrough, {@code skipReason="below_threshold"}</li>
 *   <li>any edge &gt; 16384 px or pixels &gt; 8192×8192 → passthrough WITHOUT decode,
 *       {@code skipReason="oversized_source"} (OOM / DoS safety gate)</li>
 *   <li>any {@link IOException} or runtime decode failure → passthrough,
 *       {@code skipReason="decode_failed"} (NEVER let the request fail end-to-end)</li>
 * </ul>
 *
 * <p>Mime types outside the known image set throw {@link IllegalArgumentException}
 * — callers must dispatch by {@code mimeType.startsWith("image/")} before invoking.
 */
public final class ImageCompressor {

    /**
     * Output of {@link #maybeCompress(Path, String)}.
     *
     * @param bytes      Final bytes to be base64-encoded — either the compressed JPEG
     *                   or the original file bytes when {@code applied=false}.
     * @param mimeType   Mime type matching {@code bytes}: {@code "image/jpeg"} on success,
     *                   or the original mime when passthrough.
     * @param applied    {@code true} iff resize + re-encode actually ran successfully.
     * @param skipReason {@code null} when {@code applied=true}; otherwise one of
     *                   {@code "below_threshold" | "oversized_source" | "decode_failed" | "animated_passthrough"}.
     * @param durationMs Wall-clock milliseconds spent inside this call (header peek +
     *                   resize + encode + I/O). Used for INFO log observability.
     */
    public record CompressionResult(
        byte[] bytes,
        String mimeType,
        boolean applied,
        String skipReason,
        long durationMs
    ) {}

    /**
     * Files ≤ this size skip compression — CPU not worth the savings.
     *
     * <p>The 25KB cap is derived from OpenCode's inline tool-output budget (~50KB before
     * it spills to disk + returns a "saved to file, use Task tool" stub that downstream
     * LLMs like qwen3-VL cannot resolve). With base64 expansion (×1.333) and the
     * surrounding JSON wrapper (~250 bytes), a raw image must stay under
     * {@code (50000 − 250) × 0.9 / 1.333 ≈ 33.5KB} to fit inline. We round down to
     * 25KB for headroom across model variants.
     *
     * <p>An earlier 50KB threshold let ~40KB PNG screenshots slip through as raw
     * passthrough, producing ~54KB base64 outputs that OpenCode truncated and the
     * model treated as unreadable binary.
     */
    static final long SIZE_THRESHOLD_BYTES = 25L * 1024L;
    /**
     * Target max edge for resize; aspect ratio preserved, never upscaled.
     *
     * <p>Initial implementation used 2048 (display-quality), but real-world UI
     * screenshots at 800–1280 wide ended up NOT being resized — and JPEG q=0.85
     * of full-resolution text-dense PNGs is consistently 1.5–2.5× LARGER than
     * the source PNG (measured: 824×569 PNG 40,678 byte → JPEG q=0.85 91,971 byte).
     * Lowered to 1024 to force a real pixel reduction on the typical screenshot
     * dimensions that drive {@link com.datatalk.adapter.actions.FileReadActionHandler}
     * payload size.
     */
    static final int MAX_EDGE = 1024;
    /** Refuse to decode if any single edge exceeds this (header-peeked). */
    static final int MAX_SOURCE_EDGE = 16384;
    /** Refuse to decode if total pixel count exceeds this (header-peeked). */
    static final long MAX_SOURCE_PIXELS = 8192L * 8192L;
    /**
     * JPEG quality for re-encode; balances screenshot text sharpness vs payload size.
     *
     * <p>Lowered from 0.85 to 0.75 in the 2026-05-18 follow-up after measuring that
     * q=0.85 of text-dense UI screenshots produces 30-40% larger output than q=0.75
     * with no perceptible OCR/legibility loss. Combined with {@link #MAX_EDGE}=1024,
     * keeps typical screenshot JPEG output ≤ 30KB → base64 ≤ 40KB → fits OpenCode's
     * ~50KB inline tool-output cap.
     */
    static final float JPEG_QUALITY = 0.75f;

    private static final String MIME_PNG = "image/png";
    private static final String MIME_JPEG = "image/jpeg";
    private static final String MIME_WEBP = "image/webp";
    private static final String MIME_BMP = "image/bmp";
    private static final String MIME_GIF = "image/gif";

    private ImageCompressor() {}

    /**
     * Apply the compression policy defined by {@link ImageCompressor} to the given file.
     *
     * <p>This method NEVER throws for image inputs — any decode / I/O failure is
     * captured as {@code skipReason="decode_failed"} with the original bytes returned.
     * The only checked exception is {@link IllegalArgumentException} for non-image
     * mime types, which is a programmer error (caller should pre-filter).
     *
     * @param file     absolute path to the source image on disk
     * @param mimeType caller-supplied mime; routing decision is purely mime-driven
     * @return {@link CompressionResult} — always non-null
     * @throws IllegalArgumentException if {@code mimeType} is not a recognised image type
     */
    public static CompressionResult maybeCompress(Path file, String mimeType) {
        long startNanos = System.nanoTime();
        if (mimeType == null) {
            throw new IllegalArgumentException("mimeType must not be null");
        }

        // Branch 1: GIF → passthrough to preserve animation frames
        if (MIME_GIF.equals(mimeType)) {
            return passthrough(file, mimeType, "animated_passthrough", startNanos);
        }

        // Whitelist gate: anything outside the known compressible set is a caller bug.
        if (!isCompressibleMime(mimeType)) {
            throw new IllegalArgumentException("Unsupported image mime type: " + mimeType);
        }

        // Branch 2: small files → not worth the CPU
        long fileSize;
        try {
            fileSize = Files.size(file);
        } catch (IOException e) {
            return passthrough(file, mimeType, "decode_failed", startNanos);
        }
        if (fileSize <= SIZE_THRESHOLD_BYTES) {
            return passthrough(file, mimeType, "below_threshold", startNanos);
        }

        // Branch 3: read original bytes once (used both for header peek and as
        // passthrough fallback), so we avoid re-reading the file on the failure paths.
        byte[] originalBytes;
        try {
            originalBytes = Files.readAllBytes(file);
        } catch (IOException e) {
            return passthrough(file, mimeType, "decode_failed", startNanos);
        }

        // Branch 4: header-only dimension check — refuse to decode oversized sources
        // (prevents ImageIO from allocating multi-hundred-MB BufferedImages).
        Dimensions peeked = peekDimensions(originalBytes);
        if (peeked != null && peeked.exceedsLimits()) {
            return new CompressionResult(originalBytes, mimeType, false, "oversized_source",
                durationMs(startNanos));
        }

        // Branch 5: compress — any failure falls back to original bytes.
        try (ByteArrayInputStream in = new ByteArrayInputStream(originalBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(originalBytes.length, 1 << 20))) {
            Thumbnails.of(in)
                .size(MAX_EDGE, MAX_EDGE)
                .outputQuality(JPEG_QUALITY)
                .outputFormat("jpg")
                .toOutputStream(out);
            byte[] compressed = out.toByteArray();
            if (compressed.length == 0) {
                // Defensive: should not happen, but treat empty output as decode failure
                // rather than emitting an invalid data URI.
                return new CompressionResult(originalBytes, mimeType, false, "decode_failed",
                    durationMs(startNanos));
            }
            return new CompressionResult(compressed, MIME_JPEG, true, null, durationMs(startNanos));
        } catch (IOException | RuntimeException e) {
            return new CompressionResult(originalBytes, mimeType, false, "decode_failed",
                durationMs(startNanos));
        }
    }

    private static boolean isCompressibleMime(String mime) {
        return MIME_PNG.equals(mime) || MIME_JPEG.equals(mime)
            || MIME_WEBP.equals(mime) || MIME_BMP.equals(mime);
    }

    private static CompressionResult passthrough(Path file, String mimeType, String reason, long startNanos) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            // Even reading the file failed — surface a decode_failed with empty payload.
            return new CompressionResult(new byte[0], mimeType, false, "decode_failed", durationMs(startNanos));
        }
        return new CompressionResult(bytes, mimeType, false, reason, durationMs(startNanos));
    }

    private static long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record Dimensions(int width, int height) {
        boolean exceedsLimits() {
            if (width > MAX_SOURCE_EDGE || height > MAX_SOURCE_EDGE) {
                return true;
            }
            long pixels = (long) width * (long) height;
            return pixels > MAX_SOURCE_PIXELS;
        }
    }

    /**
     * Peek width / height from the image header without decoding the pixel data.
     * Returns {@code null} if no ImageIO reader can parse the bytes — in that case
     * the caller proceeds to the compress branch, which will surface the failure
     * as {@code decode_failed} via the Thumbnailator try / catch.
     */
    private static Dimensions peekDimensions(byte[] bytes) {
        try (ByteArrayInputStream raw = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(raw)) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                return new Dimensions(w, h);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
