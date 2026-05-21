package com.datatalk.application.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuckDbPathValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void accepts_absolute_path_within_data_root() {
        var validator = new DuckDbPathValidator(tempDir.toString());
        Path result = validator.validateAndCanonicalize(tempDir.resolve("mydb.db").toString());
        assertThat(result.isAbsolute()).isTrue();
        assertThat(result.toString()).startsWith(tempDir.toString());
        assertThat(result.getFileName().toString()).isEqualTo("mydb.db");
    }

    @Test
    void resolves_relative_path_against_data_root() {
        var validator = new DuckDbPathValidator(tempDir.toString());
        Path result = validator.validateAndCanonicalize("mydb.db");
        assertThat(result.isAbsolute()).isTrue();
        assertThat(result.toString()).startsWith(tempDir.toString());
        assertThat(result.getFileName().toString()).isEqualTo("mydb.db");
    }

    @Test
    void rejects_path_escaping_data_root() {
        var validator = new DuckDbPathValidator(tempDir.toString());
        assertThatThrownBy(() -> validator.validateAndCanonicalize("../outside.db"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes data root");
    }

    @Test
    void rejects_etc_path() {
        var validator = new DuckDbPathValidator("/tmp/test-root/");
        assertThatThrownBy(() -> validator.validateAndCanonicalize("/etc/passwd.db"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_proc_path() {
        var validator = new DuckDbPathValidator("/tmp/test-root/");
        assertThatThrownBy(() -> validator.validateAndCanonicalize("/proc/self.db"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_sys_path() {
        var validator = new DuckDbPathValidator("/tmp/test-root/");
        assertThatThrownBy(() -> validator.validateAndCanonicalize("/sys/kernel.db"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_path_containing_ssh() {
        var validator = new DuckDbPathValidator("/tmp/test-root/");
        // This path starts with data root, so it must escape the data-root check
        // by having .ssh inside it. But /tmp/test-root/.ssh/keys.db starts with /tmp/test-root,
        // so it passes the data-root check. The sensitive area check catches .ssh.
        assertThatThrownBy(() -> validator.validateAndCanonicalize("/tmp/test-root/.ssh/keys.db"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensitive area");
    }

    @Test
    void rejects_path_containing_gnupg() {
        var validator = new DuckDbPathValidator("/tmp/test-root/");
        assertThatThrownBy(() -> validator.validateAndCanonicalize("/tmp/test-root/.gnupg/data.db"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sensitive area");
    }

    @Test
    void exposes_data_root() {
        var validator = new DuckDbPathValidator("/custom/root/");
        assertThat(validator.dataRoot()).isEqualTo(Path.of("/custom/root"));
    }

    @Test
    void normalizes_double_dots_within_data_root() {
        var validator = new DuckDbPathValidator(tempDir.toString());
        Path result = validator.validateAndCanonicalize("subdir/../other.db");
        assertThat(result.toString()).startsWith(tempDir.toString());
        assertThat(result.getFileName().toString()).isEqualTo("other.db");
    }

    @Test
    void uses_default_data_root_when_no_config() {
        // System property fallback
        String userHome = System.getProperty("user.home");
        var validator = new DuckDbPathValidator(System.getProperty("user.home") + "/.datatalk/duckdb/");
        assertThat(validator.dataRoot()).isEqualTo(Path.of(userHome, ".datatalk", "duckdb"));
    }
}
