package com.datatalk.application.stage;

import com.datatalk.domain.stage.StageTab;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EditConflictMarkdownFormatterTest {

    @Test
    void versionConflict_includesStandardSections() {
        StageTab tab = tab("qe-1", "users monthly", 14, false, "session-1");

        String markdown = EditConflictMarkdownFormatter.versionConflict(
            tab, 12, 14, 12_000L);

        assertThat(markdown).contains("## Edit failed: version drifted");
        assertThat(markdown).contains("Tab: `qe-1`");
        assertThat(markdown).contains("(users monthly)");
        assertThat(markdown).contains("Reason: version_conflict");
        assertThat(markdown).contains("baseVersion=12");
        assertThat(markdown).contains("current version=14");
        assertThat(markdown).contains("12s ago");
        assertThat(markdown).contains("datatalk_ui_read");
        assertThat(markdown).contains("Suggested next step:");
    }

    @Test
    void expectedTextMismatch_includesExpectedAndActualCodeBlocks() {
        StageTab tab = tab("qe-2", "orders trend", 7, false, "session-1");

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            "WHERE created_at > '2026-01-01';\n",
            "WHERE u.created_at > '2026-01-01';\n",
            7,
            7,
            3_000L);

        assertThat(markdown).contains("Reason: expected_text_mismatch");
        assertThat(markdown).contains("edit#0");
        assertThat(markdown).contains("**Expected (your edit#0):**");
        assertThat(markdown).contains("**Current (now):**");
        assertThat(markdown).contains("```sql");
        assertThat(markdown).contains("WHERE created_at");
        assertThat(markdown).contains("WHERE u.created_at");
    }

    @Test
    void expectedTextMismatch_whenVersionUnchanged_blamesPositioningNotConcurrency() {
        StageTab tab = tab("qe-2b", "orders trend", 6, false, "session-1");

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            "  HOUR(created_at)",
            "ROUP BY",
            6,
            6,
            49_000L);

        assertThat(markdown).contains("## Edit failed: edit location did not match");
        assertThat(markdown).contains("baseVersion=6");
        assertThat(markdown).contains("no other session changed this tab");
        assertThat(markdown).contains("positioning error");
        assertThat(markdown).contains("1-based line/column");
        assertThat(markdown).doesNotContain("another session edited this tab");
        assertThat(markdown).doesNotContain("content drifted");
    }

    @Test
    void expectedTextMismatch_whenVersionDrifted_keepsConcurrencyHint() {
        StageTab tab = tab("qe-2c", "orders trend", 9, false, "session-1");

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            "old text",
            "new text",
            6,
            9,
            12_000L);

        assertThat(markdown).contains("## Edit failed: content drifted");
        assertThat(markdown).contains("another session edited this tab");
        assertThat(markdown).contains("version is now 9");
    }

    @Test
    void truncatesLongCodeBlocksToHead8Tail8() {
        StageTab tab = tab("qe-3", "long", 1, false, "session-1");
        String longExpected = lines(30);

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            longExpected,
            longExpected,
            1,
            1,
            0L);

        assertThat(markdown).contains("lines elided");
        assertThat(markdown).contains("line-01");
        assertThat(markdown).contains("line-08");
        assertThat(markdown).contains("line-23");
        assertThat(markdown).contains("line-30");
        assertThat(markdown).contains("14 lines elided");
        assertThat(markdown.length()).isLessThanOrEqualTo(3_000);
    }

    @Test
    void tabArchived_pointsToArchiveFalseUnarchive() {
        StageTab tab = tab("qe-4", "frozen", 1, true, "session-1");

        String markdown = EditConflictMarkdownFormatter.tabArchived(tab);

        assertThat(markdown).contains("Reason: tab_archived");
        assertThat(markdown).contains("workspace.archive(target=qe-4, archived=false)");
    }

    @Test
    void tabNotFound_minimalFormat() {
        String markdown = EditConflictMarkdownFormatter.tabNotFound("qe-deleted");

        assertThat(markdown).contains("Tab: `qe-deleted`");
        assertThat(markdown).contains("Reason: tab_not_found");
        assertThat(markdown).contains("It may have been trashed");
    }

    @Test
    void outOfRangeLines_explainsLineCountMismatch() {
        StageTab tab = tab("qe-5", "short tab", 3, false, "session-1");
        EditRange range = new EditRange(20, 1, 22, 1);

        String markdown = EditConflictMarkdownFormatter.outOfRangeLines(tab, range, 5);

        assertThat(markdown).contains("Reason: out_of_range_lines");
        assertThat(markdown).contains("range startLine=20");
        assertThat(markdown).contains("actual lineCount=5");
    }

    @Test
    void crlfNormalization_doesNotShowRawCarriageReturns() {
        StageTab tab = tab("qe-6", "crlf", 1, false, null);

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            "line1\r\nline2\n",
            "line1\nline2\n",
            1,
            1,
            0L);

        assertThat(markdown).doesNotContain("\r\n");
        assertThat(markdown).contains("line1\nline2");
    }

    @Test
    void usesSafeFenceAndEscapesInlineTitleWhenDynamicContentContainsBackticks() {
        StageTab tab = tab("qe-8", "bad```\ntitle", 2, false, null);

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            0,
            "```\nselect 1\n```\n",
            "```\nselect 2\n```\n",
            2,
            2,
            0L);

        assertThat(markdown).contains("Tab: `qe-8` (bad''' title)");
        assertThat(markdown).contains("````sql");
        assertThat(markdown).contains("\n````\n");
    }

    @Test
    void totalCapTruncatesVeryLargeMarkdown() {
        StageTab tab = tab("qe-9", "huge ".repeat(200), 3, false, null);
        String huge = "0123456789abcdef".repeat(500);

        String markdown = EditConflictMarkdownFormatter.expectedTextMismatch(
            tab,
            1,
            huge,
            huge,
            3,
            3,
            0L);

        assertThat(markdown.length()).isLessThanOrEqualTo(3_000);
        assertThat(markdown).contains("...(truncated)...");
    }

    @Test
    void editRangeRejectsInvalidCoordinates() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new EditRange(0, 1, 1, 1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("startLine");
    }

    @Test
    void tabArchived_handlesMissingOriginSession() {
        StageTab tab = tab("qe-7", "orphaned", 9, true, null);

        String markdown = EditConflictMarkdownFormatter.tabArchived(tab);

        assertThat(markdown).contains("Reason: tab_archived");
        assertThat(markdown).contains("Tab: `qe-7` (orphaned)");
    }

    private static StageTab tab(String id, String title, int version, boolean archived, String originSessionId) {
        try {
            Constructor<?> constructor = Arrays.stream(StageTab.class.getDeclaredConstructors())
                .findFirst()
                .orElseThrow();
            RecordComponent[] components = StageTab.class.getRecordComponents();
            Object[] args = new Object[components.length];
            long now = System.currentTimeMillis();

            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                args[i] = switch (component.getName()) {
                    case "id" -> id;
                    case "type" -> "query_editor";
                    case "scope" -> enumConstant(component.getType(), "WORKSPACE");
                    case "title" -> title;
                    case "connectionId", "databaseName", "schemaName" -> null;
                    case "originSessionId" -> originSessionId;
                    case "payloadVersion" -> version;
                    case "pinned" -> false;
                    case "archived" -> archived;
                    case "archivedAt" -> archived ? now : null;
                    case "createdAt", "lastTouchedAt" -> now;
                    default -> throw new IllegalStateException("Unhandled StageTab component: " + component.getName());
                };
            }

            constructor.setAccessible(true);
            return (StageTab) constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate StageTab for test", e);
        }
    }

    private static Object enumConstant(Class<?> enumType, String name) {
        if (!enumType.isEnum()) {
            throw new IllegalStateException("Expected enum type but got " + enumType.getName());
        }
        @SuppressWarnings({"rawtypes", "unchecked"})
        Object constant = Enum.valueOf((Class<? extends Enum>) enumType, name);
        return constant;
    }

    private static String lines(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            builder.append("line-").append(String.format("%02d", i)).append('\n');
        }
        return builder.toString();
    }
}
