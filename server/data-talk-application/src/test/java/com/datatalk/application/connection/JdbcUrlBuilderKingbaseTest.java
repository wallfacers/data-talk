package com.datatalk.application.connection;

import com.datatalk.application.persistence.ConnectionRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUrlBuilderKingbaseTest {
    @Test
    void buildsKingbaseUrlWithDatabase() {
        var record = kingbaseRecord("dbX");
        assertThat(JdbcUrlBuilder.build(record)).isEqualTo("jdbc:kingbase8://h:54321/dbX");
    }

    @Test
    void rejectsBlankDatabase() {
        var record = kingbaseRecord("");
        assertThatThrownBy(() -> JdbcUrlBuilder.build(record))
            .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsNullDatabase() {
        var record = kingbaseRecord(null);
        assertThatThrownBy(() -> JdbcUrlBuilder.build(record))
            .isInstanceOf(Exception.class);
    }

    private static ConnectionRecord kingbaseRecord(String db) {
        return new ConnectionRecord("id", "n", "kingbase", "h", 54321, db, "kbuser", new byte[]{},
            "", 0L, 10, null, null, null, 1, true, null, false, "pg", null, null);
    }
}
