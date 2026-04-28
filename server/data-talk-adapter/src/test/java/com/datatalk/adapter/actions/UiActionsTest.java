package com.datatalk.adapter.actions;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UiActionsTest {

    private final UiPatchAction uiPatchAction = new UiPatchAction();
    private final UiExecAction uiExecAction = new UiExecAction();

    @Test
    void uiReadAction_declaresClientExecutorAndUiCategory() {
        DataTalkAction ann = UiReadAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.read");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }

    @Test
    void uiPatchAction_declaresClientExecutorAndUiCategory() {
        DataTalkAction ann = UiPatchAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.patch");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }

    @Test
    void uiExecAction_usesLongTimeout() {
        DataTalkAction ann = UiExecAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.exec");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.timeoutMs()).isGreaterThanOrEqualTo(30_000);
    }

    @Test
    void uiPatch_contentReplace_requiresBaseVersion() {
        Map<String, Object> schema = uiPatchAction.inputSchema();
        Map<String, Object> ops = map(schema.get("properties"), "properties").get("ops") instanceof Map<?, ?> rawOps
            ? cast(rawOps)
            : Map.of();
        List<Map<String, Object>> oneOf = cast(map(ops.get("items"), "items").get("oneOf"));

        Map<String, Object> contentOp = oneOf.stream()
            .filter(o -> {
                Map<String, Object> path = map(map(o.get("properties"), "properties").get("path"), "path");
                return list(path.get("enum"), "enum").contains("/content");
            })
            .findFirst()
            .orElseThrow();

        assertThat(list(contentOp.get("required"), "required")).contains("baseVersion");
    }

    @Test
    void uiExec_applyTextEdits_eachEditRequiresExpectedText() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> qeBranch = findOneOfBranch(schema, "query_editor");
        Map<String, Object> editsParam = navigate(qeBranch, "properties", "params", "properties", "edits");
        Map<String, Object> editItem = map(editsParam.get("items"), "items");

        assertThat(list(editItem.get("required"), "required"))
            .containsExactlyInAnyOrder("range", "text", "expectedText");
    }

    @Test
    void uiExec_workspaceAction_includesNewVerbs() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> action = navigate(wsBranch, "properties", "action");

        assertThat(list(action.get("enum"), "enum"))
            .contains("open", "close", "focus", "choose_connection", "detach", "archive", "trash");
    }

    @Test
    void uiExec_workspaceArchive_archivedFlagOptionalDefaultTrue() {
        Map<String, Object> schema = uiExecAction.inputSchema();
        Map<String, Object> wsBranch = findOneOfBranch(schema, "workspace");
        Map<String, Object> archivedFlag = navigate(wsBranch, "properties", "params", "properties", "archived");

        assertThat(archivedFlag.get("type")).isEqualTo("boolean");
        assertThat(archivedFlag.get("default")).isEqualTo(Boolean.TRUE);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findOneOfBranch(Map<String, Object> schema, String objectEnumValue) {
        List<Map<String, Object>> oneOf = (List<Map<String, Object>>) schema.get("oneOf");
        return oneOf.stream()
            .filter(branch -> list(map(map(branch.get("properties"), "properties").get("object"), "object").get("enum"), "enum")
                .contains(objectEnumValue))
            .findFirst()
            .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> navigate(Map<String, Object> root, String... path) {
        Map<String, Object> cur = root;
        for (String key : path) {
            cur = (Map<String, Object>) cur.get(key);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value, String label) {
        assertThat(value)
            .as(label)
            .isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> list(Object value, String label) {
        assertThat(value)
            .as(label)
            .isInstanceOf(List.class);
        return (List<T>) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
