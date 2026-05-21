package com.datatalk.application.connection;

import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DamengAliasNormalizationTest {

    @Test
    void canonicalLowerCaseAccepted() {
        assertThat(ConnectionKind.normalize("dameng")).isEqualTo(ConnectionKind.DAMENG);
    }

    @Test
    void mixedCaseAccepted() {
        assertThat(ConnectionKind.normalize("Dameng")).isEqualTo(ConnectionKind.DAMENG);
        assertThat(ConnectionKind.normalize("DAMENG")).isEqualTo(ConnectionKind.DAMENG);
    }

    @Test
    void shortAliasDmIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("dm"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void versionSuffixedAliasIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("dm8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("DM7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("dameng7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("dameng8"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void chineseAliasIsRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("达梦"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("武汉达梦"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void nullAndBlankRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize(null))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize(""))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("   "))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void unknownKindRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("unknown"))
            .isInstanceOf(DataTalkException.class);
    }
}
