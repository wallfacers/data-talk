package com.datatalk.adapter.actions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code action.execute_sql.description} in both English and Chinese
 * i18n properties. Enforces BUG-0070 BulkSqlGuard messaging stays present so the AI
 * sees the redirect rule before deciding which tool to call.
 */
class ExecuteSqlDescriptionContractTest {

    private static final String KEY = "action.execute_sql.description";
    private static final int MAX_LENGTH = 600;

    @Test
    void englishDescription_containsAllRequiredPhrases() throws IOException {
        String text = loadProperty("messages.properties");
        assertThat(text)
            .as("EN description must instruct the AI to use datatalk_import_data for bulk SQL")
            .contains("MUST USE datatalk_import_data");
        assertThat(text)
            .as("EN description must surface the machine-readable error code so AI can dispatch on it")
            .contains("use_import_data");
        assertThat(text)
            .as("EN description must state the byte threshold")
            .contains("4096");
        assertThat(text)
            .as("EN description must state the INSERT-count threshold")
            .contains("20 INSERT");
        assertThat(text)
            .as("EN description must carve out the user query_editor exemption")
            .contains("query editor");
        assertThat(text.length())
            .as("EN description must stay within %d chars to fit existing skill description budget", MAX_LENGTH)
            .isLessThanOrEqualTo(MAX_LENGTH);
    }

    @Test
    void chineseDescription_containsAllRequiredPhrases() throws IOException {
        String text = loadProperty("messages_zh_CN.properties");
        assertThat(text)
            .as("CN description must instruct the AI to use datatalk_import_data for bulk SQL")
            .contains("必须使用 datatalk_import_data");
        assertThat(text)
            .as("CN description must surface the machine-readable error code")
            .contains("use_import_data");
        assertThat(text)
            .as("CN description must state the byte threshold")
            .contains("4096");
        assertThat(text)
            .as("CN description must state the INSERT-count threshold")
            .contains("20 条 INSERT");
        assertThat(text)
            .as("CN description must carve out the user query_editor exemption")
            .contains("查询编辑器");
        assertThat(text.length())
            .as("CN description must stay within %d chars", MAX_LENGTH)
            .isLessThanOrEqualTo(MAX_LENGTH);
    }

    private static String loadProperty(String classpathResource) throws IOException {
        try (InputStream is = ExecuteSqlDescriptionContractTest.class.getClassLoader()
            .getResourceAsStream(classpathResource)) {
            assertThat(is).as("classpath resource %s must exist", classpathResource).isNotNull();
            Properties props = new Properties();
            props.load(new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
            String value = props.getProperty(KEY);
            assertThat(value).as("property %s missing in %s", KEY, classpathResource).isNotNull();
            return value;
        }
    }
}
