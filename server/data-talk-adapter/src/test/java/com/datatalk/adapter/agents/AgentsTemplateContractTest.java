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

    @Test
    void agentsTemplateContainsAllFivePlaceholders() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .contains("{{STAGE_TAB_DIGEST}}")
            .contains("{{ACTIVE_SESSION_DIR}}")
            .contains("{{SEMANTIC_MODEL_DIGEST}}")
            .contains("{{ACTIVE_CONNECTION_SUMMARY}}")
            .contains("{{RECENT_FAILED_QUERIES_DIGEST}}");
    }

    @Test
    void agentsTemplateContainsPreActionExplorationProtocolSection() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .contains("## Pre-Action Exploration Protocol")
            .contains("get_data_context")
            .contains("schema_search")
            .contains("read_schema")
            .contains("execute_sql")
            .contains("3 times")
            .contains("question")
            .containsIgnoringCase("good")
            .containsIgnoringCase("bad");
    }

    @Test
    void agentsTemplateTriggerGateRoutesUnfamiliarTableToExploringData() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .contains("skill:exploring-data");
    }

    @Test
    void agentsTemplateSkillIndexIncludesExploringData() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .contains("- skill:exploring-data");
    }

    @Test
    void agentsTemplateRegisteredActionsIncludeSchemaSearchAndQueryHistory() throws IOException {
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .contains("`datatalk_schema_search`")
            .contains("`datatalk_query_history`");
    }

    @Test
    void agentsTemplateForbidsSkillFilePathReferences() throws IOException {
        // BUG-0040: any literal "skills/<name>/SKILL.md" / ".opencode/skills/" / "~/.agents/skills/"
        // triggers LLM hallucination — must be absent from the skeleton at all times.
        String tpl = loadAgentsMd();
        assertThat(tpl)
            .doesNotContainPattern("skills/[a-z0-9-]+/SKILL\\.md")
            .doesNotContain(".opencode/skills/")
            .doesNotContain("~/.agents/skills/");
    }

    @Test
    void agentsTemplateBodyStaysWithin350NonBlankLines() throws IOException {
        String tpl = loadAgentsMd();
        long nonBlank = tpl.lines().filter(l -> !l.isBlank()).count();
        assertThat(nonBlank)
            .as("AGENTS.md skeleton non-blank line budget; expand skills if this grows.")
            .isLessThanOrEqualTo(350L);
    }

    @Test
    void exploringDataSkillIsPresentAndDocumentsProtocol() throws IOException {
        String skill = loadSkillMd("exploring-data");
        assertThat(skill)
            .as("skills/exploring-data/SKILL.md must own the Pre-Action Exploration Protocol")
            .contains("name: exploring-data")
            .contains("Pre-Action Exploration Protocol")
            .contains("datatalk_schema_search")
            .contains("datatalk_read_schema")
            .contains("question")
            .contains("3 times")
            .contains("[[sql-execution]]")
            .contains("[[connection-management]]")
            .contains("[[query-editor-workflow]]")
            .doesNotContainPattern("skills/[a-z0-9-]+/SKILL\\.md")
            .doesNotContain(".opencode/skills/");
    }
}
