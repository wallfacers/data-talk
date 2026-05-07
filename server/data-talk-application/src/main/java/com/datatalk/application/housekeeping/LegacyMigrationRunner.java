package com.datatalk.application.housekeeping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One-time legacy migration on first startup after Part 1.
 * Spec §B.2.
 */
@Component
public class LegacyMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyMigrationRunner.class);

    private static final Set<String> WHITELIST = Set.of(
            ".current", ".gitignore", "AGENTS.md", "opencode.json",
            "plugins"
    );

    private final Path workdir;

    public LegacyMigrationRunner() {
        this.workdir = resolveWorkdir();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Path marker = workdir.resolve(".legacy-migrated");
        if (Files.exists(marker)) {
            log.debug("[legacy-migration] already migrated, skipping");
            return;
        }
        Path opencodeDir = workdir.resolve("opencode");
        if (!Files.isDirectory(opencodeDir)) {
            log.info("[legacy-migration] opencode dir not found, nothing to migrate");
            touchMarker(marker);
            return;
        }

        int filesMoved = 0;
        try (Stream<Path> walk = Files.list(opencodeDir)) {
            for (Path entry : (Iterable<Path>) walk::iterator) {
                String name = entry.getFileName().toString();
                if (WHITELIST.contains(name)) continue;
                if (name.startsWith("opencode.json.dt-bak-")) continue;
                if (name.startsWith("package.json") || name.equals("node_modules") || name.equals("v") || name.equals("sessions")) continue;

                Path legacyDir = workdir.resolve("_legacy");
                Files.createDirectories(legacyDir);
                Path dest = legacyDir.resolve(name);
                Files.move(entry, dest);
                filesMoved++;
            }
        } catch (IOException e) {
            log.warn("[legacy-migration] walk failed: {}", e.toString());
        }

        touchMarker(marker);
        if (filesMoved > 0) {
            log.info("[legacy-migration] moved {} files to _legacy", filesMoved);
        }
    }

    private void touchMarker(Path marker) {
        try { Files.writeString(marker, "migrated at " + System.currentTimeMillis()); }
        catch (IOException e) { log.warn("[legacy-migration] marker write failed: {}", e.toString()); }
    }

    private Path resolveWorkdir() {
        Path defaultPath = Path.of(System.getProperty("user.home")).resolve(".data-talk");
        String env = System.getenv("DATA_TALK_WORKDIR");
        if (env != null) return Path.of(env);
        String prop = System.getProperty("DATA_TALK_WORKDIR");
        return prop != null ? Path.of(prop) : defaultPath;
    }
}
