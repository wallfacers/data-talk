package com.datatalk.adapter.agents;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural contract for the AGENTS.md skeleton + 11 new SKILL.md resources
 * introduced by the agents-md-skills-refactor change. Verifies the spec
 * Requirements in {@code openspec/changes/agents-md-skills-refactor/specs/
 * agent-skill-routing/spec.md}.
 */
class SkillRoutingContractTest {

    private static final List<String> REQUIRED_AGENTS_MD_HEADINGS = List.of(
        "## Identity & Hard Constraints",
        "## Intent Routing Gate",
        "## Context Model",
        "## Registered Actions",
        "## Trigger Gate",
        "## Skill Index"
    );

    /** 11 new skills introduced by this change (excludes pre-existing bezel). */
    private static final List<String> NEW_SKILL_NAMES = List.of(
        "sql-execution",
        "query-editor-workflow",
        "ui-contract",
        "tab-management",
        "er-tabs",
        "concurrency-contract",
        "charts-and-dashboards",
        "artifacts-output",
        "connection-management",
        "sql-error-diagnostics",
        "database-dialects"
    );

    /** Skills added after the refactor that must be covered by syncSkill registration. */
    private static final List<String> POST_REFACTOR_SKILL_NAMES = List.of(
        "data-collection",
        "skill-creator",
        "semantic-model-usage",
        "file-upload-routing"
    );

    /** Pre-existing skills that the syncSkill registration block must still cover. */
    private static final List<String> EXISTING_SKILL_NAMES = List.of("bezel");

    /**
     * Keyword → owning-skill map from
     * {@code openspec/changes/agents-md-skills-refactor/keyword-ownership.md}.
     * Each keyword must (a) appear ≥ 1 time in its owning skill (first complete
     * definition), and (b) in any other new SKILL.md only as a short reference
     * accompanied by {@code [[owning-skill]]}.
     */
    private static final Map<String, String> KEYWORD_OWNERSHIP = new LinkedHashMap<>();

    static {
        KEYWORD_OWNERSHIP.put("READ-ONLY", "sql-execution");
        KEYWORD_OWNERSHIP.put("confirm=true", "connection-management");
        KEYWORD_OWNERSHIP.put("confirmationToken", "connection-management");
        KEYWORD_OWNERSHIP.put("apply_text_edits", "ui-contract");
        KEYWORD_OWNERSHIP.put("truncated", "sql-execution");
        KEYWORD_OWNERSHIP.put("set_data_context", "connection-management");
        KEYWORD_OWNERSHIP.put("er_inspector", "er-tabs");
        KEYWORD_OWNERSHIP.put("er_designer", "er-tabs");
        KEYWORD_OWNERSHIP.put("expectedVersion", "concurrency-contract");
        KEYWORD_OWNERSHIP.put("supersedes", "artifacts-output");
        KEYWORD_OWNERSHIP.put("information_schema", "sql-execution");
        KEYWORD_OWNERSHIP.put("use xxx", "connection-management");
    }

    // ---------- Requirement: AGENTS.md 骨架体积上限 ----------

    @Test
    void agentsMdNonEmptyLinesAtMost350() throws IOException {
        String agentsMd = loadAgentsMd();
        long nonEmpty = Arrays.stream(agentsMd.split("\n"))
            .map(String::strip)
            .filter(s -> !s.isEmpty() && !s.startsWith("<!--"))
            .count();
        assertThat(nonEmpty)
            .as("AGENTS.md non-empty / non-comment lines must be ≤ 350 (got %d)", nonEmpty)
            .isLessThanOrEqualTo(350L);
    }

    @Test
    void agentsMdContainsBothPlaceholders() throws IOException {
        String agentsMd = loadAgentsMd();
        assertThat(agentsMd).contains("{{STAGE_TAB_DIGEST}}");
        assertThat(agentsMd).contains("{{ACTIVE_SESSION_DIR}}");
    }

    @Test
    void agentsMdContainsSixRequiredH2Headings() throws IOException {
        String agentsMd = loadAgentsMd();
        for (String heading : REQUIRED_AGENTS_MD_HEADINGS) {
            assertThat(agentsMd)
                .as("AGENTS.md must contain heading: %s", heading)
                .contains(heading);
        }
    }

    // ---------- Requirement: Trigger Gate 表与 skill 双向闭合 ----------

    @Test
    void everySkillReferenceInAgentsMdResolvesToClasspathSkillMd() throws IOException {
        String agentsMd = loadAgentsMd();
        Set<String> referenced = extractSkillReferences(agentsMd);
        assertThat(referenced).as("AGENTS.md must reference at least one skill").isNotEmpty();
        for (String name : referenced) {
            Resource res = new ClassPathResource("skills/" + name + "/SKILL.md");
            assertThat(res.exists())
                .as("AGENTS.md references skill:%s but classpath:/skills/%s/SKILL.md not found", name, name)
                .isTrue();
        }
    }

    @Test
    void everyRegisteredSyncedSkillIsReferencedInAgentsMd() throws IOException {
        String agentsMd = loadAgentsMd();
        Set<String> referenced = extractSkillReferences(agentsMd);
        Set<String> registered = extractRegisteredSyncSkillArguments();
        for (String name : registered) {
            assertThat(referenced)
                .as("OpenCodeGatewayBeans registers skill:%s but AGENTS.md does not reference it in Trigger Gate or Skill Index", name)
                .contains(name);
        }
    }

    // ---------- Requirement: SkillResourceSyncer 注册一致性 ----------

    @Test
    void registeredSyncSkillArgumentsCoverAllExpectedSkills() {
        Set<String> registered = extractRegisteredSyncSkillArguments();
        Set<String> expected = new LinkedHashSet<>();
        expected.addAll(EXISTING_SKILL_NAMES);
        expected.addAll(NEW_SKILL_NAMES);
        expected.addAll(POST_REFACTOR_SKILL_NAMES);
        assertThat(registered)
            .as("OpenCodeGatewayBeans syncSkill calls must include all expected skill names")
            .containsAll(expected);
    }

    @Test
    void classpathSkillDirectoriesEqualRegisteredSyncArguments() throws IOException {
        Set<String> onClasspath = scanClasspathSkillDirectoryNames();
        Set<String> registered = extractRegisteredSyncSkillArguments();
        assertThat(onClasspath)
            .as("classpath skills/*/SKILL.md directory names must equal OpenCodeGatewayBeans syncSkill arguments")
            .isEqualTo(registered);
    }

    // ---------- Requirement: SKILL.md frontmatter 契约 ----------

    @Test
    void everyNewSkillFrontmatterIsValid() throws IOException {
        Pattern latinWord = Pattern.compile("[A-Za-z]{3,}");
        Pattern hanWord = Pattern.compile("\\p{IsHan}{2,}");
        Yaml yaml = new Yaml();

        for (String name : NEW_SKILL_NAMES) {
            String body = loadSkillMd(name);
            String frontmatter = extractFrontmatter(body, name);
            @SuppressWarnings("unchecked")
            Map<String, Object> meta = (Map<String, Object>) yaml.load(frontmatter);
            assertThat(meta)
                .as("skill:%s frontmatter must parse as a YAML mapping", name)
                .isNotNull();

            Object nameField = meta.get("name");
            assertThat(nameField)
                .as("skill:%s frontmatter `name` field must be present", name)
                .isNotNull();
            assertThat(String.valueOf(nameField))
                .as("skill:%s frontmatter `name` must equal parent directory name", name)
                .isEqualTo(name);

            Object descField = meta.get("description");
            assertThat(descField)
                .as("skill:%s frontmatter `description` field must be present", name)
                .isNotNull();
            String description = String.valueOf(descField);
            assertThat(description.length())
                .as("skill:%s frontmatter `description` length must be in [80, 600] (got %d)", name, description.length())
                .isBetween(80, 600);

            assertThat(latinWord.matcher(description).find())
                .as("skill:%s `description` must contain at least one English trigger word", name)
                .isTrue();
            assertThat(hanWord.matcher(description).find())
                .as("skill:%s `description` must contain at least one Chinese (CJK) trigger word", name)
                .isTrue();
        }
    }

    // ---------- Requirement: SKILL.md 不得包含模板占位符 ----------

    @Test
    void noNewSkillContainsTemplatePlaceholder() throws IOException {
        Pattern placeholder = Pattern.compile("\\{\\{[A-Z_]+\\}\\}");
        for (String name : NEW_SKILL_NAMES) {
            String body = loadSkillMd(name);
            Matcher m = placeholder.matcher(body);
            assertThat(m.find())
                .as("skill:%s/SKILL.md must NOT contain any {{XXX}} placeholder literal (static resource, not rendered by AgentPromptBuilder)", name)
                .isFalse();
        }
    }

    // ---------- Requirement: skill 切分边界唯一性（基于关键词清单）----------

    @Test
    void keywordOwnershipBoundary() throws IOException {
        Map<String, String> ownershipByKeyword = KEYWORD_OWNERSHIP;
        for (Map.Entry<String, String> entry : ownershipByKeyword.entrySet()) {
            String keyword = entry.getKey();
            String owner = entry.getValue();

            String ownerBody = loadSkillMd(owner);
            assertThat(ownerBody.contains(keyword))
                .as("Keyword `%s` must appear at least once in its owning skill `%s` (first complete definition)", keyword, owner)
                .isTrue();

            for (String other : NEW_SKILL_NAMES) {
                if (other.equals(owner)) continue;
                String otherBody = loadSkillMd(other);
                if (!otherBody.contains(keyword)) continue;
                String marker = "[[" + owner + "]]";
                assertThat(otherBody.contains(marker))
                    .as("Skill `%s` references keyword `%s` (owned by `%s`) but is missing the [[%s]] short reference",
                        other, keyword, owner, owner)
                    .isTrue();
            }
        }
    }

    // ---------- Helpers ----------

    private static String loadAgentsMd() throws IOException {
        try (var in = new ClassPathResource("agents/AGENTS.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String loadSkillMd(String skillName) throws IOException {
        try (var in = new ClassPathResource("skills/" + skillName + "/SKILL.md").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> extractSkillReferences(String agentsMd) {
        Pattern p = Pattern.compile("skill:([a-z0-9-]+)");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(agentsMd);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /**
     * Scan {@code OpenCodeGatewayBeans.java} source for all {@code syncSkill("<name>", ...)}
     * invocations. We text-scan the file rather than reflect because the calls live inside
     * a method body and not on a bean lifecycle that we can introspect cheaply.
     */
    private static Set<String> extractRegisteredSyncSkillArguments() {
        Path source = Path.of(
            "src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java");
        String body;
        try {
            body = Files.readString(source);
        } catch (IOException e) {
            throw new AssertionError(
                "Cannot read OpenCodeGatewayBeans.java at " + source.toAbsolutePath(), e);
        }
        Pattern p = Pattern.compile("syncSkill\\(\\s*\"([a-z0-9-]+)\"");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(body);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static Set<String> scanClasspathSkillDirectoryNames() throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath*:skills/*/SKILL.md");
        Set<String> names = new HashSet<>();
        Pattern p = Pattern.compile("/skills/([a-z0-9-]+)/SKILL\\.md");
        for (Resource r : resources) {
            String uri = r.getURI().toString();
            Matcher m = p.matcher(uri);
            if (m.find()) {
                names.add(m.group(1));
            }
        }
        return names;
    }

    private static String extractFrontmatter(String body, String skillName) {
        if (!body.startsWith("---")) {
            throw new AssertionError(
                "skill:" + skillName + "/SKILL.md must start with `---` YAML frontmatter");
        }
        int closing = body.indexOf("\n---", 3);
        if (closing < 0) {
            throw new AssertionError(
                "skill:" + skillName + "/SKILL.md is missing closing `---` for YAML frontmatter");
        }
        return body.substring(3, closing).trim();
    }

    // ---------- Import routing: intent-aware file-upload-routing ----------

    @Test
    void fileUploadRoutingSkillMentionsImportActionAndKeywords() throws IOException {
        String skillMd = loadSkillMd("file-upload-routing");

        // Must reference datatalk_import_data as the import action
        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must reference datatalk_import_data")
            .contains("datatalk_import_data");

        // Must include intent classification with import keywords
        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must contain intent classification for import")
            .contains("导入");

        // Must reference datatalk_export_data for export
        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must reference datatalk_export_data")
            .contains("datatalk_export_data");

        // Must NOT contain the old TODO(Task 13) marker
        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must not contain TODO(Task 13)")
            .doesNotContain("TODO(Task 13)");
    }

    /**
     * Closes BUG-0069 (SQL-file import path bypassed via file_read + execute_sql shortcut).
     * Asserts that the file-upload-routing SKILL.md keeps an explicit DO NOT subsection
     * naming the forbidden shortcut, plus a "MUST call import_data first" rule and a
     * tightened fallback gate that names concrete error codes (not "any non-recoverable
     * error"). Without these phrases the agent has reverted to the easier-to-rationalize
     * old wording that the original BUG-0069 trace showed in the wild.
     */
    @Test
    void fileUploadRoutingSqlSection_hasDoNotShortcut_andTightenedFallbackGate() throws IOException {
        String skillMd = loadSkillMd("file-upload-routing");

        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must keep the ❗ DO NOT subsection under SQL Files (BUG-0069)")
            .contains("❗ DO NOT");

        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must explicitly forbid the file_read + execute_sql shortcut for importable SQL files")
            .contains("datatalk_file_read")
            .contains("datatalk_execute_sql")
            .contains("import_data");

        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must reference BUG-0069 so the rationale is traceable when the section drifts")
            .contains("BUG-0069");

        assertThat(skillMd)
            .as("file-upload-routing SKILL.md must instruct agents to call import_data first, not pre-judge file contents")
            .containsAnyOf("MUST call import_data first", "MUST actually call `datatalk_import_data` first");

        // Tightened fallback gate: only fall back on these specific error codes,
        // not the old "any non-recoverable validation error" wording that LLMs
        // interpreted as "if you think it might fail, skip".
        assertThat(skillMd)
            .as("file-upload-routing SKILL.md fallback gate must name concrete error codes that justify the fallback")
            .contains("unsupported_sql_dialect")
            .containsAnyOf("unsupported_statement_type", "unrecoverable_parse_error");
    }

    /**
     * Closes BUG-0070 (datatalk_execute_sql bulk-bypass of datatalk_import_data).
     * Asserts that the sql-execution SKILL.md keeps an explicit ❗ DO NOT subsection
     * teaching the AI to route bulk write SQL through datatalk_import_data instead of
     * inlining the entire SQL into tool_call.input. Without this section the model
     * reverts to "just call execute_sql, it accepts anything," which burns tokens and
     * loses dialect-aware batching.
     */
    @Test
    void sqlExecutionSkill_hasDoNotSectionForBulkSql() throws IOException {
        String skillMd = loadSkillMd("sql-execution");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must keep the ❗ DO NOT subsection (BUG-0070)")
            .contains("❗ DO NOT");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must redirect bulk SQL to datatalk_import_data")
            .contains("datatalk_import_data");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must surface the machine-readable error code so AI can dispatch on it")
            .contains("use_import_data");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must state the byte threshold (4096)")
            .contains("4096");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must state the INSERT-count threshold (20 INSERT)")
            .contains("20 INSERT");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must carve out the user query editor exemption so the AI does not over-rotate to import_data")
            .contains("query editor");

        assertThat(skillMd)
            .as("sql-execution SKILL.md must reference BUG-0070 for traceability when this section drifts")
            .contains("BUG-0070");
    }

    @Test
    void agentsMdRegisteredActionsIncludeImportAndExport() throws IOException {
        String agentsMd = loadAgentsMd();

        assertThat(agentsMd)
            .as("AGENTS.md Registered Actions must include datatalk_import_data")
            .contains("| `datatalk_import_data`");

        assertThat(agentsMd)
            .as("AGENTS.md Registered Actions must include datatalk_export_data")
            .contains("| `datatalk_export_data`");
    }
}
