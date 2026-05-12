package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.*;

class OpenCodeBinaryResolverDataIngestionTest {

    @TempDir Path tmp;
    OpenCodeBinaryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OpenCodeBinaryResolver() {
            @Override
            InputStream openClasspathResource(String name) {
                if (!"opencode/skills/data-ingestion.tar.gz".equals(name)) return null;
                return makeMockTarGzInputStream();
            }
            @Override
            String readEmbeddedDataIngestionVersion() { return "1.0.0"; }
        };
    }

    @Test
    void extractsDataIngestionSkillOnFirstRun() throws Exception {
        resolver.ensureDataIngestionSkill(tmp);
        assertThat(Files.exists(tmp.resolve(".opencode/skills/data-ingestion/SKILL.md"))).isTrue();
        assertThat(Files.readString(tmp.resolve(".opencode/.data-ingestion-installed")).trim()).isEqualTo("1.0.0");
    }

    @Test
    void isIdempotentWhenMarkerMatchesEmbeddedVersion() throws Exception {
        resolver.ensureDataIngestionSkill(tmp);
        long firstMtime = Files.getLastModifiedTime(tmp.resolve(".opencode/skills/data-ingestion/SKILL.md")).toMillis();
        Thread.sleep(20);
        resolver.ensureDataIngestionSkill(tmp);
        long secondMtime = Files.getLastModifiedTime(tmp.resolve(".opencode/skills/data-ingestion/SKILL.md")).toMillis();
        assertThat(secondMtime).isEqualTo(firstMtime);
    }

    @Test
    void reExtractsWhenEmbeddedVersionDiffersFromMarker() throws Exception {
        resolver.ensureDataIngestionSkill(tmp);
        OpenCodeBinaryResolver v2 = new OpenCodeBinaryResolver() {
            @Override InputStream openClasspathResource(String n) {
                return "opencode/skills/data-ingestion.tar.gz".equals(n) ? makeMockTarGzInputStream("v2-content") : null;
            }
            @Override String readEmbeddedDataIngestionVersion() { return "2.0.0"; }
        };
        v2.ensureDataIngestionSkill(tmp);
        assertThat(Files.readString(tmp.resolve(".opencode/.data-ingestion-installed")).trim()).isEqualTo("2.0.0");
        assertThat(Files.readString(tmp.resolve(".opencode/skills/data-ingestion/SKILL.md"))).contains("v2-content");
    }

    @Test
    void noopWhenClasspathResourceMissing() throws Exception {
        OpenCodeBinaryResolver noSkill = new OpenCodeBinaryResolver() {
            @Override InputStream openClasspathResource(String n) { return null; }
            @Override String readEmbeddedDataIngestionVersion() { return ""; }
        };
        assertThatCode(() -> noSkill.ensureDataIngestionSkill(tmp)).doesNotThrowAnyException();
        assertThat(Files.exists(tmp.resolve(".opencode/skills/data-ingestion"))).isFalse();
    }

    private static InputStream makeMockTarGzInputStream() {
        return makeMockTarGzInputStream("# data-ingestion SKILL.md\nstub");
    }

    private static InputStream makeMockTarGzInputStream(String content) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                byte[] body = content.getBytes();
                byte[] header = new byte[512];
                System.arraycopy("SKILL.md".getBytes(), 0, header, 0, 8);
                String size = String.format("%011o ", body.length);
                System.arraycopy(size.getBytes(), 0, header, 124, size.length());
                gz.write(header);
                gz.write(body);
                int padding = (512 - (body.length % 512)) % 512;
                gz.write(new byte[padding]);
                gz.write(new byte[512 * 2]);
            }
            return new ByteArrayInputStream(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
