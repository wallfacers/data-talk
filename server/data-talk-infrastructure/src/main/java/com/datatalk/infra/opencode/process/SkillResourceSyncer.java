package com.datatalk.infra.opencode.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Syncs skill directories from classpath resources ({@code skills/<name>/**})
 * into the OpenCode project root ({@code .opencode/skills/<name>/}).
 * Uses a SHA-256 hash of <b>all</b> classpath resources as the idempotency marker.
 */
public class SkillResourceSyncer {

    private static final Logger log = LoggerFactory.getLogger(SkillResourceSyncer.class);
    private static final String CLASSPATH_PREFIX = "skills/";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    public void syncSkill(String skillName, Path projectRoot) {
        Path opencodeDir = projectRoot.resolve(".opencode");
        Path targetDir = opencodeDir.resolve("skills").resolve(skillName);
        Path marker = opencodeDir.resolve("." + skillName + "-skill-synced");

        try {
            Resource[] resources = resolver.getResources("classpath*:" + CLASSPATH_PREFIX + skillName + "/**");
            String sourceHash = hashResources(resources, skillName);
            if (sourceHash == null) {
                return;
            }

            if (Files.exists(marker) && Files.isDirectory(targetDir)) {
                if (sourceHash.equals(Files.readString(marker).trim())) {
                    return;
                }
                deleteRecursively(targetDir);
            }

            Files.createDirectories(targetDir);

            int copied = 0;
            for (Resource resource : resources) {
                if (!resource.isReadable()) continue;
                String relative = extractRelativePath(resource, skillName);
                if (relative == null || relative.isEmpty()) continue;

                Path targetFile = targetDir.resolve(relative);
                Files.createDirectories(targetFile.getParent());
                Files.copy(resource.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }

            Files.createDirectories(opencodeDir);
            Files.writeString(marker, sourceHash);
            log.info("Synced skill '{}' ({} files) to {}", skillName, copied, targetDir);
        } catch (IOException e) {
            log.warn("Failed to sync skill '{}': {}", skillName, e.getMessage());
        }
    }

    private String hashResources(Resource[] resources, String skillName) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        boolean found = false;
        for (Resource resource : resources) {
            if (!resource.isReadable()) continue;
            String relative = extractRelativePath(resource, skillName);
            if (relative == null || relative.isEmpty()) continue;

            found = true;
            md.update(relative.getBytes());
            md.update(resource.getInputStream().readAllBytes());
        }

        if (!found) {
            return null;
        }
        return HexFormat.of().formatHex(md.digest());
    }

    private String extractRelativePath(Resource resource, String skillName) {
        try {
            String uri = resource.getURI().toString();
            String needle = CLASSPATH_PREFIX + skillName + "/";
            int idx = uri.indexOf(needle);
            if (idx < 0) return null;
            return uri.substring(idx + needle.length());
        } catch (IOException e) {
            return null;
        }
    }

    private static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteRecursively(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", path, e.getMessage());
        }
    }
}
