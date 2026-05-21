package com.datatalk.adapter.actions.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ImageCompressor}. All fixtures are generated programmatically
 * via {@link ImageIO} into JUnit's {@link TempDir} — no on-disk fixture files are
 * committed to the repo, which keeps the test suite hermetic and cross-platform.
 *
 * <p>WebP coverage note: ImageIO has no built-in WebP plugin, so we cannot generate
 * a real WebP fixture from BufferedImage. The webp test feeds PNG bytes labelled
 * with {@code mimeType="image/webp"} to verify the mime-routing branch does not throw
 * (Thumbnailator decodes by content, not by the caller-supplied mime).
 */
class ImageCompressorTest {

    @TempDir
    Path tempDir;

    @Test
    void png_largeScreenshot_isCompressedToJpeg() throws Exception {
        // 4K-sized fixture so the resize maxEdge=2048 actually has work to do
        // (a 1920×1080 source would stay full-size after resize and the only
        // saving would come from JPEG q=0.85 of mosaic content, which can grow
        // for non-photographic edges).
        Path png = writeBusyPng(3840, 2160, "fixture-3840x2160.png");
        long originalBytes = Files.size(png);
        assertThat(originalBytes)
            .as("fixture must be > 50KB to trigger compression")
            .isGreaterThan(ImageCompressor.SIZE_THRESHOLD_BYTES);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(png, "image/png");

        assertThat(result.applied()).isTrue();
        assertThat(result.skipReason()).isNull();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.bytes().length).isLessThan((int) originalBytes);
        assertThat(result.bytes().length).isGreaterThan(0);
        // JPEG magic bytes: FF D8 FF
        assertThat(result.bytes()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result.bytes()[1] & 0xFF).isEqualTo(0xD8);
        assertThat(result.bytes()[2] & 0xFF).isEqualTo(0xFF);
    }

    @Test
    void jpeg_largeScreenshot_isResizedAndKeptAsJpeg() throws Exception {
        Path jpeg = writeBusyJpeg(1920, 1080, "fixture-1920x1080.jpg");
        long originalBytes = Files.size(jpeg);
        // We need the original to be > 50KB so the compress branch actually runs.
        assertThat(originalBytes).isGreaterThan(ImageCompressor.SIZE_THRESHOLD_BYTES);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(jpeg, "image/jpeg");

        assertThat(result.applied()).isTrue();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        // After resize maxEdge=2048 a 1920×1080 source is unchanged dimensionally, but
        // re-encode at q=0.85 of an already-q=0.95 jpeg can grow slightly; both directions
        // are acceptable per spec. Just assert we got real JPEG bytes back.
        assertThat(result.bytes().length).isGreaterThan(0);
        assertThat(result.bytes()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result.bytes()[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void webp_mimeRouting_doesNotThrow() throws Exception {
        // Synthesise PNG bytes but tag them as WebP — verifies the mime routing branch
        // accepts WebP without throwing. Thumbnailator decodes by content (sees PNG)
        // and re-encodes as JPEG, so the result is applied=true.
        Path fakeWebp = writeBusyPng(1280, 720, "fake.webp");
        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(fakeWebp, "image/webp");

        // Either applied OR skipReason set — must not throw and must be a coherent result.
        assertThat(result).isNotNull();
        if (result.applied()) {
            assertThat(result.mimeType()).isEqualTo("image/jpeg");
            assertThat(result.skipReason()).isNull();
        } else {
            assertThat(result.skipReason()).isNotNull();
        }
    }

    @Test
    void bmp_largeImage_isCompressedToJpeg() throws Exception {
        Path bmp = writeBusyBmp(1024, 768, "fixture.bmp");
        long originalBytes = Files.size(bmp);
        assertThat(originalBytes).isGreaterThan(ImageCompressor.SIZE_THRESHOLD_BYTES);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(bmp, "image/bmp");

        assertThat(result.applied()).isTrue();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(result.bytes().length).isLessThan((int) originalBytes);
    }

    @Test
    void gif_isPassthroughToPreserveAnimation() throws Exception {
        Path gif = writeBusyGif(1024, 768, "fixture.gif");
        long originalBytes = Files.size(gif);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(gif, "image/gif");

        assertThat(result.applied()).isFalse();
        assertThat(result.skipReason()).isEqualTo("animated_passthrough");
        assertThat(result.mimeType()).isEqualTo("image/gif");
        assertThat(result.bytes().length).isEqualTo((int) originalBytes);
    }

    @Test
    void corruptPng_fallsBackToOriginalBytes() throws Exception {
        // Valid PNG 8-byte magic + an IHDR-shaped chunk, then garbage — Thumbnailator
        // will throw IOException during decode and we must fall back to original.
        byte[] corruptBytes = new byte[200];
        // PNG magic
        corruptBytes[0] = (byte) 0x89;
        corruptBytes[1] = 'P';
        corruptBytes[2] = 'N';
        corruptBytes[3] = 'G';
        corruptBytes[4] = 0x0D;
        corruptBytes[5] = 0x0A;
        corruptBytes[6] = 0x1A;
        corruptBytes[7] = 0x0A;
        // Fill the rest with random garbage above the 50KB threshold? No — keep it small
        // enough to be below SIZE_THRESHOLD would trigger below_threshold instead.
        // We need to exceed 50KB to land in the compress branch.
        byte[] padded = new byte[(int) (ImageCompressor.SIZE_THRESHOLD_BYTES + 1024)];
        System.arraycopy(corruptBytes, 0, padded, 0, corruptBytes.length);
        // Fill remainder with deterministic noise
        Random rnd = new Random(42);
        for (int i = corruptBytes.length; i < padded.length; i++) {
            padded[i] = (byte) rnd.nextInt(256);
        }
        Path file = tempDir.resolve("corrupt.png");
        Files.write(file, padded);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(file, "image/png");

        assertThat(result.applied()).isFalse();
        assertThat(result.skipReason()).isEqualTo("decode_failed");
        // Falls back to original bytes with original mime
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.bytes()).isEqualTo(padded);
    }

    @Test
    void smallPng_belowThreshold_skipsCompression() throws Exception {
        // A 100×100 solid PNG is well under 50KB; expect skip with reason=below_threshold.
        Path png = writeSolidPng(100, 100, Color.WHITE, "small.png");
        long originalBytes = Files.size(png);
        assertThat(originalBytes)
            .as("small fixture must be ≤ threshold")
            .isLessThanOrEqualTo(ImageCompressor.SIZE_THRESHOLD_BYTES);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(png, "image/png");

        assertThat(result.applied()).isFalse();
        assertThat(result.skipReason()).isEqualTo("below_threshold");
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.bytes().length).isEqualTo((int) originalBytes);
    }

    @Test
    void mediumPng_inOpenCodeTruncationDangerZone_isCompressed() throws Exception {
        // Regression: BUG-0058 follow-up. A ~40KB PNG screenshot used to fall under the
        // old 50KB SIZE_THRESHOLD_BYTES → passed through as raw PNG → ~54KB base64 →
        // exceeded OpenCode's ~50KB inline tool-output cap → model received a truncation
        // stub instead of the image. After lowering the threshold to 25KB, any image
        // that would otherwise blow the inline cap MUST enter the compress branch.
        //
        // Fixture: realistic UI screenshot 1600×1000 — chosen LARGER than MAX_EDGE=1024
        // so the resize branch actually runs (the user-reported regression case was
        // 824×569 which would skip resize; we deliberately go bigger to exercise the
        // full resize+re-encode path under test). Pure mosaic fixtures (paintBusy)
        // would NOT be a valid test here — JPEG cannot compress mosaic block edges
        // efficiently and would produce >300KB output, which is a fixture pathology.
        Path png = writeRealisticScreenshot(1600, 1000, "screenshot-1600x1000.png");
        long originalBytes = Files.size(png);
        assertThat(originalBytes)
            .as("fixture must exceed new 25KB threshold to enter compress branch")
            .isGreaterThan(ImageCompressor.SIZE_THRESHOLD_BYTES);

        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(png, "image/png");

        assertThat(result.applied()).isTrue();
        assertThat(result.skipReason()).isNull();
        assertThat(result.mimeType()).isEqualTo("image/jpeg");

        // Key regression assertion: base64-encoded output MUST stay under OpenCode's
        // inline tool-output cap (~50KB). We give ~10KB headroom for the surrounding
        // JSON wrapper that FileReadActionHandler adds (fileId / metadata fields).
        int base64Len = Base64.getEncoder().encodeToString(result.bytes()).length();
        assertThat(base64Len)
            .as("base64 output MUST stay under ~40KB to avoid OpenCode tool-output truncation")
            .isLessThan(40 * 1024);
    }

    @Test
    void oversizedPng_rejectsDecodeQuickly() throws Exception {
        // 18000×300 = > 16384 on one edge → must skip decode. Total pixels are small
        // (5.4M) so memory stays low — we're testing the safety gate latency, not OOM.
        // Uses busy pattern so PNG size exceeds the 50KB threshold and we don't
        // short-circuit on below_threshold before reaching the dimension check.
        Path png = writeBusyPng(18000, 300, "huge.png");
        long originalBytes = Files.size(png);
        // Sanity: must exceed threshold to reach the dimension-check branch (small
        // files short-circuit earlier).
        assertThat(originalBytes).isGreaterThan(ImageCompressor.SIZE_THRESHOLD_BYTES);

        long startMs = System.currentTimeMillis();
        ImageCompressor.CompressionResult result = ImageCompressor.maybeCompress(png, "image/png");
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertThat(result.applied()).isFalse();
        assertThat(result.skipReason()).isEqualTo("oversized_source");
        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(result.bytes().length).isEqualTo((int) originalBytes);
        // Spec: "请求 SHALL 在 1 秒内返回" — give CI generous slack but still well
        // under a full decode of an 18000-wide image.
        assertThat(elapsedMs)
            .as("oversized-source gate should return within ~1s (header peek only)")
            .isLessThan(1000L);
    }

    // ---------------------------------------------------------------------------------
    // Fixture helpers — generate PNG / JPEG / BMP / GIF on disk via ImageIO.
    // We paint busy patterns (text + noise) so JPEG re-encode produces meaningfully
    // different bytes than the source PNG/BMP.
    // ---------------------------------------------------------------------------------

    private Path writeBusyPng(int width, int height, String name) throws IOException {
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private Path writeBusyJpeg(int width, int height, String name) throws IOException {
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "jpg", out.toFile());
        return out;
    }

    private Path writeBusyBmp(int width, int height, String name) throws IOException {
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "bmp", out.toFile());
        return out;
    }

    private Path writeBusyGif(int width, int height, String name) throws IOException {
        // GIF is indexed-color, so we use TYPE_INT_ARGB then let ImageIO quantise.
        BufferedImage img = paintBusy(width, height);
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "gif", out.toFile());
        return out;
    }

    /**
     * Paint a realistic UI-screenshot-like image: mostly solid background,
     * a few colored "cards" with text, and many lines of body text. This represents
     * the typical user upload — JPEG q=0.85 compresses such content well (smooth
     * fills + sharp text), unlike the {@link #paintBusy} mosaic which is a JPEG
     * worst-case pattern.
     *
     * <p>Used by {@link #mediumPng_inOpenCodeTruncationDangerZone_isCompressed}.
     */
    private Path writeRealisticScreenshot(int width, int height, String name) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            // Anti-aliasing on — matches real OS-level text rendering. Aliased text
            // is a JPEG worst case because sharp 1-pixel edges become high-frequency
            // DCT coefficients that q=0.75 cannot compress; AA text has smooth
            // sub-pixel gradients that JPEG handles well.
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Layer 1: solid light background (UI panel)
            g.setColor(new Color(245, 245, 248));
            g.fillRect(0, 0, width, height);
            // Layer 2: a few colored cards across the top
            for (int i = 0; i < 4; i++) {
                int x = 20 + i * (width / 5);
                int y = 50;
                int w = width / 6;
                int h = 80;
                g.setColor(new Color(80 + i * 30, 100, 200 - i * 20));
                g.fillRect(x, y, w, h);
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
                g.drawString("Card " + i, x + 10, y + 30);
            }
            // Layer 3: 10 text lines simulating UI content. Sparse-on-purpose:
            // a real screenshot has whitespace and color regions, not wall-to-wall
            // text. With aa text + this density, the resulting PNG lands around
            // 30KB (above 25KB threshold) and the post-resize+JPEG q=0.75 base64
            // output is ~33KB (well within the 40KB safety budget for OpenCode cap).
            g.setColor(new Color(40, 40, 40));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            for (int line = 0; line < 10; line++) {
                g.drawString("Line " + line + ": realistic UI screenshot text content",
                        30, 180 + line * 30);
            }
        } finally {
            g.dispose();
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    private Path writeSolidPng(int width, int height, Color fill, String name) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(fill);
            g.fillRect(0, 0, width, height);
        } finally {
            g.dispose();
        }
        Path out = tempDir.resolve(name);
        ImageIO.write(img, "png", out.toFile());
        return out;
    }

    /**
     * Paint a busy "photo-like" image:
     *  - 16×16 mosaic of random colours — gives PNG enough block-level variety to
     *    push the file past the 50KB compression threshold without exploding into
     *    per-pixel noise that JPEG's DCT cannot represent compactly.
     *  - A few overlaid text lines — large edge transitions that JPEG handles well.
     *
     * Earlier revisions used pixel-dense random noise (~width*height/40 spots),
     * which made JPEG q=0.85 re-encode produce LARGER bytes than the source PNG
     * because every spot inflated the high-frequency DCT bands. The 16×16 mosaic
     * keeps the fixture realistic without that pathology.
     */
    private static BufferedImage paintBusy(int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            Random rnd = new Random(width * 31L + height);
            // 64×64 blocks: each JPEG 8×8 DCT block almost always lands inside a
            // single mosaic cell, so q=0.85 keeps only the DC coefficient + few
            // ACs → very small JPEG output. 16×16 blocks straddled DCT boundaries
            // and inflated high-frequency bands, making JPEG > PNG.
            int block = 64;
            for (int y = 0; y < height; y += block) {
                for (int x = 0; x < width; x += block) {
                    g.setColor(new Color(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256)));
                    g.fillRect(x, y, block, block);
                }
            }
            // Overlay a few text lines — JPEG compresses large-edge text much
            // more efficiently than per-pixel noise.
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            int lines = Math.min(8, height / 40);
            for (int line = 0; line < lines; line++) {
                g.drawString("DataTalk fixture " + width + "x" + height + " line " + line,
                        50, 50 + line * 40);
            }
        } finally {
            g.dispose();
        }
        return img;
    }
}
