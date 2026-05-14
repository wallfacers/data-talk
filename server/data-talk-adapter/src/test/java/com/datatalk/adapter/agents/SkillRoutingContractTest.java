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

    /** 11 new skills introduced by this change (excludes pre-existing bezel + data-ingestion). */
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

    /** Pre-existing skills that the syncSkill registration block must still cover. */
    private static final List<String> EXISTING_SKILL_NAMES = List.of("bezel", "data-ingestion");

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
}
