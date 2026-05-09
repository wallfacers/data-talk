package com.datatalk.application.connection;

import com.datatalk.domain.error.DataTalkException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KingbaseAliasNormalizationTest {

    @Test
    void canonicalLowerCaseAccepted() {
        assertThat(ConnectionKind.normalize("kingbase")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void mixedCaseAccepted() {
        assertThat(ConnectionKind.normalize("Kingbase")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("KINGBASE")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void kingbaseesAliasNormalizedToKingbase() {
        // umbrella §8 line 293: only Wave C kind with a permitted alias
        assertThat(ConnectionKind.normalize("kingbasees")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void kingbaseesMixedCaseNormalized() {
        assertThat(ConnectionKind.normalize("KingbaseES")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("KINGBASEES")).isEqualTo(ConnectionKind.KINGBASE);
        assertThat(ConnectionKind.normalize("KingBaseES")).isEqualTo(ConnectionKind.KINGBASE);
    }

    @Test
    void shortAliasesRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("kb"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kbase"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void chineseAliasesRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("金仓"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("人大金仓"))
            .isInstanceOf(DataTalkException.class);
    }

    @Test
    void versionSuffixedAliasRejected() {
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase7"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase8"))
            .isInstanceOf(DataTalkException.class);
        assertThatThrownBy(() -> ConnectionKind.normalize("kingbase9"))
            .isInstanceOf(DataTalkException.class);
    }
}
