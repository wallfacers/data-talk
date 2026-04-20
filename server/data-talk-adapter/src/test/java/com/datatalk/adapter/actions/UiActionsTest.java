package com.datatalk.adapter.actions;

import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.Executor;
import com.datatalk.domain.action.RiskLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UiActionsTest {

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
    void uiListAction_declaresClientExecutorAndUiCategory() {
        DataTalkAction ann = UiListAction.class.getAnnotation(DataTalkAction.class);
        assertThat(ann).isNotNull();
        assertThat(ann.id()).isEqualTo("datatalk.ui.list");
        assertThat(ann.executor()).isEqualTo(Executor.CLIENT);
        assertThat(ann.category()).containsExactly(Category.UI);
        assertThat(ann.riskLevel()).containsExactly(RiskLevel.L1);
    }
}
