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
        assertThat(Files.readString(artifacts.pluginFile()))
            .contains("__dtBridgeNonce")
            .contains(status.bridgeNonce());

        JsonNode config = objectMapper.readTree(Files.readString(artifacts.configFile()));
        assertThat(config.path("mcp").path("datatalk").path("url").asText()).isEqualTo("http://127.0.0.1:8080/mcp");
        assertThat(instructions(config)).contains(artifacts.instructionsFile().toString());
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
