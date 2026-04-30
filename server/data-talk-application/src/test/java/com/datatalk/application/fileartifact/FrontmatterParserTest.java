package com.datatalk.application.fileartifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FrontmatterParserTest {

    @TempDir
    Path tmp;

    @Test
    void markdownWithFrontmatterExtractsLowercaseKeys() throws Exception {
        Path file = tmp.resolve("doc.md");
        Files.writeString(file, """
                ---
                artifact: true
                kind: er_diagram
                title: Orders
                summary: Three tables
                ---

                # body
                """);

        Map<String, String> parsed = FrontmatterParser.parse(file);

        assertThat(parsed)
                .containsEntry("artifact", "true")
                .containsEntry("kind", "er_diagram")
                .containsEntry("title", "Orders")
                .containsEntry("summary", "Three tables");
        assertThat(FrontmatterParser.isArtifactDeclared(parsed)).isTrue();
    }

    @Test
    void markdownWithoutLeadingFenceReturnsEmptyMap() throws Exception {
        Path file = tmp.resolve("plain.md");
        Files.writeString(file, "# no frontmatter\n");

        assertThat(FrontmatterParser.parse(file)).isEmpty();
    }

    @Test
    void markdownWithUnclosedFenceReturnsEmptyMap() throws Exception {
        Path file = tmp.resolve("broken.md");
        Files.writeString(file, "---\nartifact: true\n");

        assertThat(FrontmatterParser.parse(file)).isEmpty();
    }

    @Test
    void sqlFencedFrontmatterExtractsKeys() throws Exception {
        Path file = tmp.resolve("script.sql");
        Files.writeString(file, """
                -- ---
                -- artifact: yes
                -- kind: sql_script
                -- title: Backfill
                -- ---
                SELECT 1;
                """);

        Map<String, String> parsed = FrontmatterParser.parse(file);

        assertThat(parsed)
                .containsEntry("artifact", "yes")
                .containsEntry("kind", "sql_script")
                .containsEntry("title", "Backfill");
        assertThat(FrontmatterParser.isArtifactDeclared(parsed)).isTrue();
    }

    @Test
    void sqlPlainLeadingCommentBlockExtractsKeys() throws Exception {
        Path file = tmp.resolve("script.sql");
        Files.writeString(file, """
                -- artifact: 1
                -- kind: sql_script

                SELECT 1;
                """);

        Map<String, String> parsed = FrontmatterParser.parse(file);

        assertThat(parsed)
                .containsEntry("artifact", "1")
                .containsEntry("kind", "sql_script");
        assertThat(FrontmatterParser.isArtifactDeclared(parsed)).isTrue();
    }

    @Test
    void sqlPlainLeadingCommentBlockAllowsLeadingBlankLines() throws Exception {
        Path file = tmp.resolve("script.sql");
        Files.writeString(file, """

                -- artifact: true
                -- kind: sql_script

                SELECT 1;
                """);

        assertThat(FrontmatterParser.parse(file))
                .containsEntry("artifact", "true")
                .containsEntry("kind", "sql_script");
    }

    @Test
    void unsupportedExtensionsReturnEmptyMap() throws Exception {
        Path file = tmp.resolve("data.csv");
        Files.writeString(file, "id,value\n1,2\n");

        assertThat(FrontmatterParser.parse(file)).isEmpty();
    }

    @Test
    void closingFencePastFirst8KbIsIgnored() throws Exception {
        Path file = tmp.resolve("large.md");
        StringBuilder body = new StringBuilder("---\nartifact: true\nfiller: ");
        body.append("x".repeat(10_000));
        body.append("\n---\n");
        Files.writeString(file, body);

        assertThat(FrontmatterParser.parse(file)).isEmpty();
    }

    @Test
    void quotedValuesHaveOuterQuotesStripped() throws Exception {
        Path file = tmp.resolve("doc.txt");
        Files.writeString(file, """
                ---
                title: "Quoted"
                summary: 'Single quoted'
                ---
                """);

        assertThat(FrontmatterParser.parse(file))
                .containsEntry("title", "Quoted")
                .containsEntry("summary", "Single quoted");
    }

    @Test
    void artifactTruthinessRecognizesTrueYesAndOneOnly() {
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "true"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "yes"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "1"))).isTrue();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of("artifact", "false"))).isFalse();
        assertThat(FrontmatterParser.isArtifactDeclared(Map.of())).isFalse();
    }
}
