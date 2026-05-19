package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.infra.opencode.OpenCodeMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeBootstrapWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void writesNoncePluginInstructionsAndMcpConfig() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeMcpProperties properties = props(tempDir);
        OpenCodeBootstrapWriter writer = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            status,
            clock,
            () -> "Use datatalk_execute_sql"
        );

        OpenCodeBootstrapWriter.BootstrapArtifacts artifacts = writer.write(8080);

        assertThat(Files.readString(artifacts.instructionsFile())).contains("datatalk_execute_sql");
        String pluginBody = Files.readString(artifacts.pluginFile());
        assertThat(pluginBody)
            .contains("__dtBridgeNonce")
            .contains(status.bridgeNonce());
        // OpenCode invokes the MCP tool with the original `args` variable, not `output.args`,
        // so the plugin must mutate `output.args` in place. Reassigning `output.args = ...`
        // silently drops the bridge fields and causes "missing session context" errors.
        assertThat(pluginBody)
            .doesNotContain("output.args = args")
            .doesNotContain("output.args = {")
            .contains("args.__dtOpenCodeSessionId = input.sessionID");

        JsonNode config = objectMapper.readTree(Files.readString(artifacts.configFile()));
        assertThat(config.path("mcp").path("datatalk").path("url").asText()).isEqualTo("http://127.0.0.1:8080/mcp");
        assertThat(instructions(config)).contains(artifacts.instructionsFile().toString());
    }

    @Test
    void reusesExistingPluginNonceOnSubsequentWrite() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeMcpProperties properties = props(tempDir);
        OpenCodeBootstrapWriter writer = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            status,
            clock,
            () -> "Use datatalk_execute_sql"
        );

        OpenCodeBootstrapWriter.BootstrapArtifacts first = writer.write(8080);
        String firstNonce = status.bridgeNonce();
        assertThat(firstNonce).isNotBlank();
        assertThat(Files.readString(first.pluginFile())).contains(firstNonce);

        // Simulate a backend restart: a fresh BridgeStatus has empty nonce. write() must
        // recover the persisted plugin nonce, so the long-lived OpenCode process (which
        // loaded the plugin once at boot) keeps authenticating after every backend boot.
        OpenCodeBridgeStatus rebooted = new OpenCodeBridgeStatus(clock);
        OpenCodeBootstrapWriter rebootedWriter = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            rebooted,
            clock,
            () -> "Use datatalk_execute_sql"
        );
        OpenCodeBootstrapWriter.BootstrapArtifacts second = rebootedWriter.write(8080);

        assertThat(rebooted.bridgeNonce()).isEqualTo(firstNonce);
        assertThat(Files.readString(second.pluginFile())).contains(firstNonce);
    }

    @Test
    void generatesNewNonceWhenPluginFileMissing() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeMcpProperties properties = props(tempDir);
        OpenCodeBootstrapWriter writer = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            status,
            clock,
            () -> "Use datatalk_execute_sql"
        );

        OpenCodeBootstrapWriter.BootstrapArtifacts first = writer.write(8080);
        String firstNonce = status.bridgeNonce();
        Files.delete(first.pluginFile());

        OpenCodeBridgeStatus rebooted = new OpenCodeBridgeStatus(clock);
        OpenCodeBootstrapWriter rebootedWriter = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            rebooted,
            clock,
            () -> "Use datatalk_execute_sql"
        );
        rebootedWriter.write(8080);

        assertThat(rebooted.bridgeNonce()).isNotBlank().isNotEqualTo(firstNonce);
    }

    @Test
    void preservesUserFieldsAndDoesNotDuplicateManagedInstruction() throws Exception {
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeMcpProperties properties = props(tempDir);
        Path configFile = tempDir.resolve("opencode.json");
        Path customInstruction = tempDir.resolve("custom.md");
        Files.writeString(customInstruction, "custom");
        Files.writeString(configFile, """
            {
              "model": "openai/gpt-5",
              "instructions": ["%s"]
            }
            """.formatted(customInstruction.toString()));

        OpenCodeBootstrapWriter writer = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            status,
            clock,
            () -> "Use datatalk_execute_sql"
        );

        OpenCodeBootstrapWriter.BootstrapArtifacts first = writer.write(8080);
        OpenCodeBootstrapWriter.BootstrapArtifacts second = writer.write(8080);

        JsonNode config = objectMapper.readTree(Files.readString(second.configFile()));
        assertThat(config.path("model").asText()).isEqualTo("openai/gpt-5");
        assertThat(instructions(config))
            .contains(customInstruction.toString(), first.instructionsFile().toString());
        assertThat(instructions(config))
            .filteredOn(first.instructionsFile().toString()::equals)
            .hasSize(1);
        assertThat(Files.list(tempDir)
            .map(path -> path.getFileName().toString())
            .anyMatch(name -> name.startsWith("opencode.json.dt-bak-"))).isTrue();
    }

    @Test
    void writesPreActionExplorationProtocolAndPlaceholderSubstitutions() throws Exception {
        // Task 6.7: simulates AgentPromptBuilder.render() output that includes
        // the Pre-Action Exploration Protocol section and all 5 substituted placeholders.
        // Asserts the writer faithfully persists this content and registers the file path
        // in config.instructions so OpenCode picks it up at boot.
        String stubAgentsMd = """
            ## Pre-Action Exploration Protocol

            Always: get_data_context → schema_search → read_schema → execute_sql.

            ## Registered Actions

            - datatalk_schema_search
            - datatalk_query_history

            ## Live Context

            - Active connection: mysql · ledger_db
            - Backend port: 8080
            - Workspace id: ws-fixture-001
            - Recent successful: SELECT 1 FROM dual
            - Recent failures: <no recent failures>
            """;
        OpenCodeBridgeStatus status = new OpenCodeBridgeStatus(clock);
        OpenCodeMcpProperties properties = props(tempDir);
        OpenCodeBootstrapWriter writer = new OpenCodeBootstrapWriter(
            properties,
            objectMapper,
            status,
            clock,
            () -> stubAgentsMd
        );

        OpenCodeBootstrapWriter.BootstrapArtifacts artifacts = writer.write(8080);

        String written = Files.readString(artifacts.instructionsFile());
        assertThat(written)
            .contains("## Pre-Action Exploration Protocol")
            .contains("datatalk_schema_search")
            .contains("datatalk_query_history")
            .contains("mysql · ledger_db")
            .contains("8080")
            .contains("ws-fixture-001")
            .contains("SELECT 1 FROM dual")
            .contains("<no recent failures>")
            .doesNotContain("{{")
            .doesNotContain("}}");

        JsonNode config = objectMapper.readTree(Files.readString(artifacts.configFile()));
        assertThat(instructions(config))
            .contains(artifacts.instructionsFile().toString());
    }

    private static OpenCodeMcpProperties props(Path configDir) {
        OpenCodeMcpProperties properties = new OpenCodeMcpProperties();
        properties.setEnabled(true);
        properties.setConfigDir(configDir.toString());
        return properties;
    }

    private static List<String> instructions(JsonNode config) {
        return config.path("instructions").isArray()
            ? java.util.stream.StreamSupport.stream(config.path("instructions").spliterator(), false)
                .map(JsonNode::asText)
                .toList()
            : List.of();
    }
}
