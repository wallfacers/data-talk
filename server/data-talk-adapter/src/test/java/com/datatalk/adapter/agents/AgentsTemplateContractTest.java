package com.datatalk.adapter.agents;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Post-refactor contract for AGENTS.md after the agents-md-skills-refactor change.
 *
 * <p>Pre-refactor, this class held 8 assertions tightly coupled to the
 * {@code <!-- file-artifact-section -->} markers and the {@code ## Data Ingestion}
 * section. Both sections were moved out of AGENTS.md (to the new
 * {@code artifacts-output} skill and the existing {@code data-ingestion} skill
 * respectively), so the historical assertions have been removed and replaced
 * with negative assertions plus the {@code data-ingestion} short-reference and
 * sample error-code presence in the owning skill file.</p>
 */
class AgentsTemplateContractTest {

    private static String loadAgentsMd() throws IOException {
        try (var in = new ClassPathResource("agents/AGENTS.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String loadSkillMd(String name) throws IOException {
        try (var in = new ClassPathResource("skills/" + name + "/SKILL.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void agentsTemplateDropsLegacyFileArtifactSectionMarkers() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .as("AGENTS.md must no longer contain the legacy file-artifact-section markers; rules moved to skills/artifacts-output/SKILL.md")
            .doesNotContain("<!-- file-artifact-section:begin -->")
            .doesNotContain("<!-- file-artifact-section:end -->")
            .doesNotContain("## Output Files & Artifacts");
    }

    @Test
    void artifactsOutputSkillDocumentsArchiveProtocol() throws IOException {
        String skill = loadSkillMd("artifacts-output");
        assertThat(skill)
            .as("skills/artifacts-output/SKILL.md must own the artifact archive / promote / rules contract")
            .contains("datatalk_archive_artifact")
            .contains("Default (Temporary)")
            .contains("Promote to Archive Candidate")
            .contains("Rules");
    }

    @Test
    void agentsTemplateRemovesInlineDataIngestionSectionButKeepsShortReference() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .as("AGENTS.md must drop the inline Data Ingestion section; routing goes through the skill reference")
            .doesNotContain("## Data Ingestion")
            .doesNotContain("INGESTION_SSRF_BLOCKED")
            .doesNotContain("INGESTION_PAYLOAD_TOO_LARGE")
            .doesNotContain("skills/data-ingestion/SKILL.md")
            .doesNotContain("skill:data-ingestion")
            .contains("skill:data-collection");
    }

    @Test
    void dataCollectionSkillRetainsScriptRunnerContract() throws IOException {
        // The data-collection skill replaced data-ingestion and owns the
        // script-runner contract (run/stop/list).
        String skill = loadSkillMd("data-collection");
        assertThat(skill)
            .as("skills/data-collection/SKILL.md must document the script-runner tools")
            .contains("datatalk_script_run")
            .contains("datatalk_script_stop")
            .contains("datatalk_script_list");
    }

    @Test
    void agentsTemplateContainsStageTabDigestPlaceholder() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl).contains("{{STAGE_TAB_DIGEST}}");
    }

    @Test
    void agentsTemplateContainsActiveSessionDirPlaceholder() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl).contains("{{ACTIVE_SESSION_DIR}}");
    }
}
