package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeBridgeStatus;
import com.datatalk.infra.opencode.OpenCodeMcpProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class OpenCodeBootstrapWriter {

    private static final String CONFIG_SCHEMA = "https://opencode.ai/config.json";
    private static final String MCP_SERVER_NAME = "datatalk";
    private static final String INSTRUCTIONS_FILE_NAME = "AGENTS.md";
    private static final String PLUGIN_FILE_NAME = "datatalk-mcp-context.js";

    private final OpenCodeMcpProperties properties;
    private final ObjectMapper objectMapper;
    private final OpenCodeBridgeStatus bridgeStatus;
    private final Clock clock;
    private Supplier<String> instructionsSupplier;

    @Autowired
    public OpenCodeBootstrapWriter(OpenCodeMcpProperties properties,
                                   ObjectMapper objectMapper,
                                   OpenCodeBridgeStatus bridgeStatus,
                                   Clock clock) {
        this(properties, objectMapper, bridgeStatus, clock, null);
    }

    OpenCodeBootstrapWriter(OpenCodeMcpProperties properties,
                            ObjectMapper objectMapper,
                            OpenCodeBridgeStatus bridgeStatus,
                            Clock clock,
                            Supplier<String> instructionsSupplier) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.bridgeStatus = bridgeStatus;
        this.clock = clock;
        this.instructionsSupplier = instructionsSupplier;
    }

    /**
     * Replace the instructions supplier used to render AGENTS.md.
     * Called after construction to inject dynamic content (e.g. tab digest).
     */
    public void setInstructionsSupplier(Supplier<String> supplier) {
        this.instructionsSupplier = supplier;
    }

    public BootstrapArtifacts write(int serverPort) throws IOException {
        Path configDir = properties.resolveConfigDir();
        Files.createDirectories(configDir);

        String nonce = UUID.randomUUID().toString();
        bridgeStatus.rotateNonce(nonce);

        Path instructionsFile = configDir.resolve(INSTRUCTIONS_FILE_NAME);
        writeTextFile(instructionsFile, instructionsContent());

        Path pluginFile = configDir.resolve("plugins").resolve(PLUGIN_FILE_NAME);
        Files.createDirectories(pluginFile.getParent());
        writeTextFile(pluginFile, pluginContent(nonce));
        trySetOwnerReadOnly(pluginFile);

        Path configFile = configDir.resolve("opencode.json");
        Map<String, Object> nextConfig = mergedConfig(configFile, serverPort, instructionsFile);
        writeConfig(configFile, nextConfig);

        return new BootstrapArtifacts(configDir, configFile, instructionsFile, pluginFile, desiredMcpConfig(serverPort));
    }

    private String instructionsContent() throws IOException {
        if (instructionsSupplier != null) {
            return instructionsSupplier.get();
        }
        try (var in = getClass().getClassLoader().getResourceAsStream("agents/AGENTS.md")) {
            if (in == null) {
                throw new IOException("agents/AGENTS.md not found on classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> mergedConfig(Path configFile, int serverPort, Path instructionsFile) throws IOException {
        Map<String, Object> config = readConfig(configFile);
        config.putIfAbsent("$schema", CONFIG_SCHEMA);

        Map<String, Object> mcp = mutableMap(config.get("mcp"));
        mcp.put(MCP_SERVER_NAME, desiredMcpConfig(serverPort));
        config.put("mcp", mcp);

        LinkedHashSet<String> instructions = new LinkedHashSet<>(stringList(config.get("instructions")));
        instructions.add(instructionsFile.toString());
        config.put("instructions", List.copyOf(instructions));

        return config;
    }

    private Map<String, Object> desiredMcpConfig(int serverPort) {
        return Map.of(
            "type", "remote",
            "url", "http://127.0.0.1:" + serverPort + "/mcp",
            "enabled", true
        );
    }

    private Map<String, Object> readConfig(Path configFile) throws IOException {
        if (!Files.exists(configFile)) {
            return new LinkedHashMap<>();
        }
        String raw = Files.readString(configFile);
        return objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private void writeConfig(Path configFile, Map<String, Object> nextConfig) throws IOException {
        String nextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(nextConfig) + System.lineSeparator();
        String currentJson = Files.exists(configFile) ? Files.readString(configFile) : null;
        if (nextJson.equals(currentJson)) {
            return;
        }

        if (currentJson != null) {
            Path backup = configFile.resolveSibling("opencode.json.dt-bak-" + clock.instant().toString().replace(':', '-'));
            Files.copy(configFile, backup, StandardCopyOption.REPLACE_EXISTING);
        }

        Path tempFile = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        Files.writeString(tempFile, nextJson, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        try {
            Files.move(tempFile, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.move(tempFile, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, Object> mutableMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> copy = new LinkedHashMap<>();
            raw.forEach((key, entryValue) -> copy.put(String.valueOf(key), entryValue));
            return copy;
        }
        return new LinkedHashMap<>();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> raw) {
            return raw.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private void writeTextFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private void trySetOwnerReadOnly(Path path) {
        try {
            Set<PosixFilePermission> permissions = EnumSet.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            );
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Ignore on non-POSIX filesystems.
        }
    }

    private String pluginContent(String nonce) {
        // OpenCode passes `{ args }` to the hook and then invokes the MCP tool with the
        // original `args` variable, not `output.args`. Mutating `output.args` in place is
        // the only way the bridge fields reach the backend; reassigning `output.args` to a
        // new object is silently dropped.
        return """
            const TOOL_PREFIX = 'datatalk_'
            const BRIDGE_PREFIX = '__dt'
            const BRIDGE_NONCE = '%s'

            export const DataTalkMcpContext = async () => ({
              'tool.execute.before': async (input, output) => {
                if (!input.tool?.startsWith(TOOL_PREFIX)) return
                if (!output.args || typeof output.args !== 'object') return
                const args = output.args
                for (const key of Object.keys(args)) {
                  if (key.startsWith(BRIDGE_PREFIX)) delete args[key]
                }
                args.__dtOpenCodeSessionId = input.sessionID
                args.__dtCallId = input.callID
                args.__dtBridgeNonce = BRIDGE_NONCE
              },
            })
            """.formatted(nonce);
    }

    public record BootstrapArtifacts(
        Path configDir,
        Path configFile,
        Path instructionsFile,
        Path pluginFile,
        Map<String, Object> mcpConfig
    ) {}
}
