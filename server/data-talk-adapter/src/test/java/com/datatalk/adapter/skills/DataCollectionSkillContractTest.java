package com.datatalk.adapter.skills;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code skills/data-collection/SKILL.md}. Enforces the "❗ DO NOT" guidance
 * introduced to close BUG-0067 (scripts trying to install pymysql / connect to DB directly
 * instead of going through {@code POST /api/script-data/write}).
 *
 * <p>Without this gate the SKILL doc can silently drift and the agent loses the explicit
 * "no direct DB driver" rule, which was the root cause of the original failed import run.
 */
class DataCollectionSkillContractTest {

    @Test
    void skillDoc_containsDoNotSection_andForbidsDirectDriverUsage() throws IOException {
        String content = loadSkillDoc();

        assertThat(content)
            .as("DO NOT heading must be present so the agent sees the prohibitions before the tool surface")
            .contains("## ❗ DO NOT");

        // Each driver / direct-connect pattern that has been observed in the wild
        // must be explicitly named so the model cannot rationalize "but my specific
        // package is different."
        assertThat(content)
            .as("Python MySQL driver must be explicitly forbidden")
            .contains("pymysql");
        assertThat(content)
            .as("Python PostgreSQL driver must be explicitly forbidden")
            .contains("psycopg2");
        assertThat(content)
            .as("Node MySQL driver must be explicitly forbidden")
            .contains("mysql2");
        assertThat(content)
            .as("SQLAlchemy engine instantiation must be explicitly forbidden")
            .contains("create_engine");
    }

    @Test
    void skillDoc_pointsScriptsToTheWriteApi_notRawSql() throws IOException {
        String content = loadSkillDoc();

        assertThat(content)
            .as("The single allowed write channel must be advertised inside the DO NOT rationale")
            .contains("/api/script-data/write");

        assertThat(content)
            .as("Scripts must not be encouraged to emit raw INSERT / CREATE TABLE SQL")
            .contains("CREATE TABLE")
            .contains("INSERT");
    }

    @Test
    void skillDoc_mentionsIdentifierQuoterAsServerSideOwner() throws IOException {
        String content = loadSkillDoc();

        // Cross-references BUG-0066's fix: IdentifierQuoter owns dialect quoting end-to-end,
        // and the SKILL must not invite scripts to compete with it client-side.
        assertThat(content)
            .as("IdentifierQuoter must be named so future authors understand why client-side quoting is forbidden")
            .contains("IdentifierQuoter");
    }

    private static String loadSkillDoc() throws IOException {
        ClassPathResource resource = new ClassPathResource("skills/data-collection/SKILL.md");
        Path path = resource.getFile().toPath();
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
