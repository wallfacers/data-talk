package com.datatalk.adapter.agents;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AgentsTemplateContractTest {

    private static final String BEGIN = "<!-- file-artifact-section:begin -->";
    private static final String END = "<!-- file-artifact-section:end -->";

    private String load() throws IOException {
        try (var in = new ClassPathResource("agents/AGENTS.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void template_contains_file_artifact_section_markers() throws IOException {
        String tpl = load();
        assertThat(tpl).contains(BEGIN);
        assertThat(tpl).contains(END);
    }

    @Test
    void template_markers_appear_in_correct_order() throws IOException {
        String tpl = load();
        int begin = tpl.indexOf(BEGIN);
        int end = tpl.indexOf(END);
        assertThat(begin).as("begin marker present").isGreaterThanOrEqualTo(0);
        assertThat(end).as("end marker present").isGreaterThan(begin);
    }

    @Test
    void template_contains_output_files_section_heading_between_markers() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("## Output Files & Artifacts");
    }

    @Test
    void template_contains_active_session_dir_placeholder_inside_section() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("{{ACTIVE_SESSION_DIR}}");
    }

    @Test
    void template_references_archive_tool_inside_section() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("datatalk_archive_artifact");
    }

    @Test
    void template_section_includes_default_promote_and_rules_subheaders() throws IOException {
        String tpl = load();
        String section = sectionBody(tpl);
        assertThat(section).contains("**Default (Temporary)**");
        assertThat(section).contains("**Promote to Archive Candidate**");
        assertThat(section).contains("**Rules**");
    }

    private static String sectionBody(String tpl) {
        int begin = tpl.indexOf(BEGIN);
        int end = tpl.indexOf(END);
        if (begin < 0 || end < 0 || end <= begin) {
            throw new AssertionError("file-artifact-section markers missing or out of order");
        }
        return tpl.substring(begin + BEGIN.length(), end);
    }
}
