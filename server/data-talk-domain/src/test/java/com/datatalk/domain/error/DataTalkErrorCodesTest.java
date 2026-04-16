package com.datatalk.domain.error;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DataTalkErrorCodesTest {
    @Test void codesMatchSpecTable() {
        assertThat(DataTalkErrorCodes.SCHEMA_INPUT_INVALID).isEqualTo("schema.input_invalid");
        assertThat(DataTalkErrorCodes.CONNECTION_MISSING).isEqualTo("connection.missing");
        assertThat(DataTalkErrorCodes.SQL_SYNTAX_ERROR).isEqualTo("sql.syntax_error");
        assertThat(DataTalkErrorCodes.SQL_TIMEOUT).isEqualTo("sql.timeout");
        assertThat(DataTalkErrorCodes.SQL_FORBIDDEN).isEqualTo("sql.forbidden");
        assertThat(DataTalkErrorCodes.ARTIFACT_SUPERSEDES_NOT_FOUND).isEqualTo("artifact.supersedes_not_found");
        assertThat(DataTalkErrorCodes.ARTIFACT_TOO_LARGE).isEqualTo("artifact.too_large");
        assertThat(DataTalkErrorCodes.ACTION_TIMEOUT).isEqualTo("action.timeout");
        assertThat(DataTalkErrorCodes.ACTION_CANCELLED).isEqualTo("action.cancelled");
        assertThat(DataTalkErrorCodes.UPSTREAM_UNAVAILABLE).isEqualTo("upstream.unavailable");
    }
}
