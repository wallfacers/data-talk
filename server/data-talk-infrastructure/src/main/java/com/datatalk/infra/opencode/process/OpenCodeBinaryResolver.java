package com.datatalk.infra.opencode.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.FileSystemUtils;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * Resolves, downloads, and extracts OpenCode binaries from local cache,
 * classpath resources, or GitHub releases.
 */
public class OpenCodeBinaryResolver {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeBinaryResolver.class);
    static final String DATA_DIR = ".data-talk";
    static final String OPENCODE_DIR = DATA_DIR + "/opencode";
    private static final String CURRENT_FILE = ".current";
    static final String DEPS_RESOURCE = "opencode/opencode-deps.tar.gz";
    private static final String DEPS_MARKER = ".datatalk-deps-installed";
    static final String BEZEL_RESOURCE = "opencode/skills/bezel.tar.gz";
    static final String BEZEL_VERSION_RESOURCE = "opencode/skills/bezel.version";
    static final String BEZEL_MARKER = ".bezel-installed";

    /**
     * Resolve local binary from the base directory.
     * Checks ~/.data-talk/opencode/.current and verifies the binary exists.
     */
    public Path resolveLocal(Path baseDir) {
        Path opencodeDir = baseDir.resolve(OPENCODE_DIR);
        String version = readCurrentVersion(opencodeDir);
        if (version == null) {
            return null;
        }

        OpenCodePlatform platform = OpenCodePlatform.current();
        Path binaryPath = opencodeDir.resolve(version).resolve(platform.binaryName());
        if (Files.isExecutable(binaryPath)) {
            log.info("Found existing OpenCode binary at {}", binaryPath);
            return binaryPath;
        }
        return null;
    }

    /**
     * Extract binary from classpath resources.
     */
    public Path extractFromClasspath(Path baseDir) {
        OpenCodePlatform platform = OpenCodePlatform.current();
        String resourcePath = platform.resourcePath() + "/" + platform.binaryName();

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (in == null) {
                log.debug("No classpath resource at {}", resourcePath);
                return null;
            }

            String version = "embedded";
            Path targetDir = baseDir.resolve(OPENCODE_DIR).resolve(version);
            Path targetBinary = targetDir.resolve(platform.binaryName());
            Files.createDirectories(targetDir);

            Files.copy(in, targetBinary, StandardCopyOption.REPLACE_EXISTING);
            makeExecutable(targetBinary);
            writeCurrentVersion(baseDir.resolve(OPENCODE_DIR), version);

            log.info("Extracted OpenCode binary from classpath to {}", targetBinary);
            return targetBinary;
        } catch (IOException e) {
            log.warn("Failed to extract OpenCode binary from classpath: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Download binary from GitHub release.
     */
    public Path downloadFromGitHub(Path baseDir, String version) {
        OpenCodePlatform platform = OpenCodePlatform.current();
        String assetName = platform.githubAssetName(version);
        String downloadUrl = "https://github.com/anomalyco/opencode/releases/download/v" + version + "/" + assetName;

        Path opencodeDir = baseDir.resolve(OPENCODE_DIR);
        Path versionDir = opencodeDir.resolve("v" + version);

        if (Files.isDirectory(versionDir) && Files.isExecutable(versionDir.resolve(platform.binaryName()))) {
            log.info("OpenCode v{} already exists at {}", version, versionDir);
            return versionDir.resolve(platform.binaryName());
        }

        try {
            log.info("Downloading OpenCode v{} from {}", version, downloadUrl);

            HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("Accept", "application/octet-stream")
                .GET()
                .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                log.error("GitHub download failed: HTTP {}", response.statusCode());
                return null;
            }

            Path tempDir = opencodeDir.resolve("temp-" + UUID.randomUUID());
            Files.createDirectories(tempDir);

            if (assetName.endsWith(".zip")) {
                extractZip(response.body(), tempDir);
            } else if (assetName.endsWith(".tar.gz")) {
                extractTarGz(response.body(), tempDir);
            } else {
                log.error("Unknown archive format: {}", assetName);
                deleteRecursively(tempDir);
                return null;
            }

            Path extractedBinary = findBinary(tempDir, platform.binaryName());
            if (extractedBinary == null) {
                log.error("Binary '{}' not found in extracted archive", platform.binaryName());
                deleteRecursively(tempDir);
                return null;
            }

            Files.createDirectories(versionDir);
            Path targetBinary = versionDir.resolve(platform.binaryName());
            Files.move(extractedBinary, targetBinary, StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(tempDir);

            makeExecutable(targetBinary);
            writeCurrentVersion(opencodeDir, "v" + version);
            log.info("Downloaded OpenCode v{} to {}", version, targetBinary);
            return targetBinary;

        } catch (Exception e) {
            log.error("Failed to download OpenCode: {}", e.getMessage());
            return null;
        }
    }

    void extractTarGz(byte[] tarGzBytes, Path targetDir) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(tarGzBytes))) {
            byte[] header = new byte[512];
            int bytesRead;

            while ((bytesRead = gzip.read(header)) == 512) {
                if (isZeroBlock(header)) break;

                String filename = parseTarFilename(header);
                if (filename.isEmpty()) continue;

                long fileSize = parseTarSize(header);

                Path targetPath = targetDir.resolve(filename);
                if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Tar entry escapes target directory: " + filename);
                }

                if (fileSize > 0) {
                    Files.createDirectories(targetPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        long remaining = fileSize;
                        byte[] buffer = new byte[8192];
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = gzip.read(buffer, 0, toRead);
                            if (read == -1) break;
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                        long padding = (512 - (fileSize % 512)) % 512;
                        gzip.skipNBytes(padding);
                    }
                } else {
                    Files.createDirectories(targetPath);
                }

                header = new byte[512];
            }
        }
    }

    void extractZip(byte[] zipBytes, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            var entry = zip.getNextEntry();
            while (entry != null) {
                Path targetPath = targetDir.resolve(entry.getName());
                if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Zip entry escapes target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(zip, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
    }

    /**
     * Ensures the bundled Bezel skill is extracted into the project's
     * {@code .opencode/skills/bezel/} directory. Idempotent — skips when the
     * installed marker matches the embedded version.
     */
    public void ensureBezelSkill(Path projectRoot) {
        Path opencodeDir = projectRoot.resolve(".opencode");
        Path skillDir    = opencodeDir.resolve("skills").resolve("bezel");
        Path marker      = opencodeDir.resolve(BEZEL_MARKER);

        String embeddedVersion = readEmbeddedBezelVersion();
        if (Files.exists(marker) && Files.isDirectory(skillDir)) {
            try {
                if (embeddedVersion.equals(Files.readString(marker).trim())) {
                    return;
                }
            } catch (IOException ignored) { /* fall through to reinstall */ }
            deleteRecursively(skillDir);
        }

        try (InputStream in = openClasspathResource(BEZEL_RESOURCE)) {
            if (in == null) {
                return;  // no bundled bezel, skip silently
            }
            Files.createDirectories(skillDir);
            extractDepsTarGz(in, skillDir);
            Files.createDirectories(opencodeDir);
            Files.writeString(marker, embeddedVersion);
        } catch (Exception e) {
            log.warn("Failed to extract Bezel skill: {}", e.getMessage());
        }
    }

    InputStream openClasspathResource(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    String readEmbeddedBezelVersion() {
        try (InputStream in = openClasspathResource(BEZEL_VERSION_RESOURCE)) {
            if (in == null) return "";
            return new String(in.readAllBytes()).trim();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * Extracts bundled NPM dependencies from classpath to ~/.config/opencode/
     * so the OpenCode process can find them without network access.
     */
    public void ensureNodeModules() {
        Path configDir = Paths.get(System.getProperty("user.home"), ".config", "opencode");
        ensureNodeModulesWithConfigDir(configDir);
    }

    void ensureNodeModulesWithConfigDir(Path configDir) {
        Path nodeModules = configDir.resolve("node_modules");
        Path marker = configDir.resolve(DEPS_MARKER);

        if (Files.exists(marker) && Files.isDirectory(nodeModules)) {
            return;
        }

        try (InputStream in = getClass().getClassLoader().getResourceAsStream(DEPS_RESOURCE)) {
            if (in == null) {
                log.info("No bundled opencode deps on classpath, skipping extraction");
                return;
            }
            extractDepsTarGz(in, configDir);
            Files.writeString(marker, Instant.now().toString());
            log.info("Extracted bundled NPM deps to {}", configDir);
        } catch (Exception e) {
            log.warn("Failed to extract bundled NPM deps: {}", e.getMessage());
        }
    }

    void extractDepsTarGz(InputStream tarGzStream, Path targetDir) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(tarGzStream)) {
            byte[] header = new byte[512];
            int bytesRead;

            while ((bytesRead = gzip.read(header)) == 512) {
                if (isZeroBlock(header)) break;

                String filename = parseTarFilename(header);
                if (filename.isEmpty()) continue;

                long fileSize = parseTarSize(header);

                Path targetPath = targetDir.resolve(filename);
                if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Tar entry escapes target directory: " + filename);
                }

                if (fileSize > 0) {
                    Files.createDirectories(targetPath.getParent());
                    try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
                        long remaining = fileSize;
                        byte[] buffer = new byte[8192];
                        while (remaining > 0) {
                            int toRead = (int) Math.min(buffer.length, remaining);
                            int read = gzip.read(buffer, 0, toRead);
                            if (read == -1) break;
                            fos.write(buffer, 0, read);
                            remaining -= read;
                        }
                        long padding = (512 - (fileSize % 512)) % 512;
                        gzip.skipNBytes(padding);
                    }
                } else {
                    Files.createDirectories(targetPath);
                }

                header = new byte[512];
            }
        }
    }

    String readCurrentVersion(Path opencodeDir) {
        Path currentFile = opencodeDir.resolve(CURRENT_FILE);
        if (!Files.exists(currentFile)) return null;
        try {
            return Files.readString(currentFile).trim();
        } catch (IOException e) {
            return null;
        }
    }

    void writeCurrentVersion(Path opencodeDir, String version) {
        try {
            Files.createDirectories(opencodeDir);
            Files.writeString(opencodeDir.resolve(CURRENT_FILE), version);
        } catch (IOException e) {
            log.warn("Failed to write version file: {}", e.getMessage());
        }
    }

    private boolean isZeroBlock(byte[] block) {
        for (byte b : block) if (b != 0) return false;
        return true;
    }

    private String parseTarFilename(byte[] header) {
        int end = 0;
        while (end < 100 && header[end] != 0) end++;
        return new String(header, 0, end).trim();
    }

    private long parseTarSize(byte[] header) {
        byte[] sizeBytes = new byte[12];
        System.arraycopy(header, 124, sizeBytes, 0, 12);
        String sizeStr = new String(sizeBytes).trim().replaceAll("\0", "");
        try {
            return Long.parseLong(sizeStr, 8);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private Path findBinary(Path dir, String binaryName) throws IOException {
        try (var stream = Files.walk(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().equals(binaryName))
                .findFirst()
                .orElse(null);
        }
    }

    private void makeExecutable(Path path) {
        try {
            path.toFile().setExecutable(true);
        } catch (Exception e) {
            log.warn("Failed to set executable on {}: {}", path, e.getMessage());
        }
    }

    private void deleteRecursively(Path path) {
        try {
            FileSystemUtils.deleteRecursively(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}: {}", path, e.getMessage());
        }
    }
}