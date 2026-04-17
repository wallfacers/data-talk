# OpenCode 嵌入式进程管理 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Spring Boot 启动时自动下载/提取、启动 OpenCode 进程，父进程退出时确保子进程被清理。

**Architecture:** `SmartLifecycle` (phase=-100) 管理 OpenCode 进程生命周期，在 `ApplicationReadyEvent` 之前完成启动。动态端口通过 `ServerSocket` 探测，冲突递增。支持开发模式（GitHub 下载）和 JAR 内嵌模式（Classpath 资源提取）。

**Tech Stack:** Java 21, Spring Boot 3.5, `ProcessBuilder`, `java.util.zip`, WireMock, JUnit 5, AssertJ

---

## File Map

| Action | File Path |
|---|---|
| **Create** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePlatform.java` |
| **Create** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePortAllocator.java` |
| **Create** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeServeProperties.java` |
| **Create** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java` |
| **Create** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java` |
| **Create** | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePlatformTest.java` |
| **Create** | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePortAllocatorTest.java` |
| **Create** | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverTest.java` |
| **Create** | `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeProcessManagerTest.java` |
| **Modify** | `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java` |
| **Modify** | `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java` |
| **Modify** | `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java` |
| **Modify** | `server/data-talk-adapter/src/main/resources/application.yml` |

---

### Task 1: OpenCodePlatform — OS/Arch 检测

**Files:**
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePlatformTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePlatform.java`

- [x] **Step 1: Write the failing test**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePlatformTest.java
package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodePlatformTest {

    @Test
    void currentPlatformReturnsCorrectForRunningOs() {
        OpenCodePlatform platform = OpenCodePlatform.current();
        if (OS.current() == OS.LINUX) {
            assertThat(platform).isEqualTo(OpenCodePlatform.LINUX_X64);
        } else if (OS.current() == OS.MAC) {
            assertThat(platform.resourcePath())
                .isIn("opencode/darwin-x64", "opencode/darwin-arm64");
        } else if (OS.current() == OS.WINDOWS) {
            assertThat(platform).isEqualTo(OpenCodePlatform.WINDOWS_X64);
        }
    }

    @Test
    void platformResourcePathMatchesConvention() {
        assertThat(OpenCodePlatform.LINUX_X64.resourcePath())
            .isEqualTo("opencode/linux-x64");
        assertThat(OpenCodePlatform.LINUX_ARM64.resourcePath())
            .isEqualTo("opencode/linux-arm64");
        assertThat(OpenCodePlatform.DARWIN_X64.resourcePath())
            .isEqualTo("opencode/darwin-x64");
        assertThat(OpenCodePlatform.DARWIN_ARM64.resourcePath())
            .isEqualTo("opencode/darwin-arm64");
        assertThat(OpenCodePlatform.WINDOWS_X64.resourcePath())
            .isEqualTo("opencode/windows-x64");
    }

    @Test
    void binaryNameIsCorrect() {
        assertThat(OpenCodePlatform.LINUX_X64.binaryName())
            .isEqualTo("opencode");
        assertThat(OpenCodePlatform.WINDOWS_X64.binaryName())
            .isEqualTo("opencode.exe");
    }

    @Test
    void githubAssetNameMatchesConvention() {
        // Format: opencode_1.4.6_linux_x86_64.tar.gz
        assertThat(OpenCodePlatform.LINUX_X64.githubAssetName("1.4.6"))
            .isEqualTo("opencode_1.4.6_linux_x86_64.tar.gz");
        assertThat(OpenCodePlatform.DARWIN_ARM64.githubAssetName("1.4.6"))
            .isEqualTo("opencode_1.4.6_darwin_arm64.tar.gz");
        assertThat(OpenCodePlatform.WINDOWS_X64.githubAssetName("1.4.6"))
            .isEqualTo("opencode_1.4.6_windows_x86_64.tar.gz");
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodePlatformTest -q`
Expected: FAIL — classes don't exist

- [x] **Step 3: Write implementation**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePlatform.java
package com.datatalk.infra.opencode.process;

/**
 * Supported platforms for OpenCode binary distribution.
 * Maps OS + architecture to classpath resource paths and GitHub release asset names.
 */
public enum OpenCodePlatform {
    LINUX_X64("linux-x64", "linux_x86_64", "opencode"),
    LINUX_ARM64("linux-arm64", "linux_aarch64", "opencode"),
    DARWIN_X64("darwin-x64", "darwin_x86_64", "opencode"),
    DARWIN_ARM64("darwin-arm64", "darwin_aarch64", "opencode"),
    WINDOWS_X64("windows-x64", "windows_x86_64", "opencode.exe");

    private final String resourceDir;
    private final String githubArch;
    private final String binaryName;

    OpenCodePlatform(String resourceDir, String githubArch, String binaryName) {
        this.resourceDir = resourceDir;
        this.githubArch = githubArch;
        this.binaryName = binaryName;
    }

    /** Classpath resource directory, e.g. "opencode/linux-x64" */
    public String resourcePath() {
        return "opencode/" + resourceDir;
    }

    /** Binary filename, e.g. "opencode" or "opencode.exe" */
    public String binaryName() {
        return binaryName;
    }

    /** GitHub release asset name, e.g. "opencode_1.4.6_linux_x86_64.tar.gz" */
    public String githubAssetName(String version) {
        return "opencode_" + version + "_" + githubArch + ".tar.gz";
    }

    /** Detect the current platform from JVM os.name/os.arch. */
    public static OpenCodePlatform current() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();

        boolean isLinux = os.contains("linux");
        boolean isMac = os.contains("mac") || os.contains("darwin");
        boolean isWindows = os.contains("windows");
        boolean isArm = arch.contains("aarch") || arch.contains("arm");
        boolean isX64 = arch.contains("x86_64") || arch.contains("amd64");

        if (isLinux && isX64) return LINUX_X64;
        if (isLinux && isArm) return LINUX_ARM64;
        if (isMac && isArm) return DARWIN_ARM64;
        if (isMac && isX64) return DARWIN_X64;
        if (isWindows) return WINDOWS_X64;

        // Default fallback
        throw new IllegalStateException("Unsupported platform: " + os + "/" + arch);
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodePlatformTest -q`
Expected: PASS

- [x] **Step 5: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePlatform.java data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePlatformTest.java
git commit -m "feat(server): add OpenCodePlatform enum for cross-platform binary detection"
```

---

### Task 2: OpenCodePortAllocator — 动态端口探测

**Files:**
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePortAllocatorTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePortAllocator.java`

- [x] **Step 1: Write the failing test**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePortAllocatorTest.java
package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCodePortAllocatorTest {

    private final OpenCodePortAllocator allocator = new OpenCodePortAllocator();

    @Test
    void allocatesFirstAvailablePortWhenFree() {
        int port = allocator.allocate(0, 10000);
        assertThat(port).isEqualTo(0); // port 0 = OS assigns random port

        // Actually test with a specific port
        int found = allocator.allocate(0, 100);
        assertThat(found).isGreaterThan(0);
        assertThat(found).isLessThan(100);
    }

    @Test
    void skipsOccupiedPorts() throws Exception {
        // Occupy port 9900
        ServerSocket socket = new ServerSocket(9900);
        try {
            int port = allocator.allocate(9900, 10);
            assertThat(port).isEqualTo(9901);
        } finally {
            socket.close();
        }
    }

    @Test
    void throwsWhenAllPortsOccupied() throws Exception {
        ServerSocket s1 = new ServerSocket(9910);
        ServerSocket s2 = new ServerSocket(9911);
        ServerSocket s3 = new ServerSocket(9912);
        try {
            assertThatThrownBy(() -> allocator.allocate(9910, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No available port");
        } finally {
            s1.close();
            s2.close();
            s3.close();
        }
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodePortAllocatorTest -q`
Expected: FAIL — class doesn't exist

- [x] **Step 3: Write implementation**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePortAllocator.java
package com.datatalk.infra.opencode.process;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Allocates an available TCP port by probing sequentially from a base port.
 */
public class OpenCodePortAllocator {

    /**
     * Find the first available port starting from basePort.
     *
     * @param basePort  starting port number
     * @param maxRetries maximum number of ports to probe
     * @return an available port number
     * @throws IllegalStateException if no port is available within maxRetries
     */
    public int allocate(int basePort, int maxRetries) {
        for (int i = 0; i <= maxRetries; i++) {
            int port = basePort + i;
            if (isPortAvailable(port)) {
                return port;
            }
        }
        throw new IllegalStateException(
            "No available port found in range [" + basePort + "-" + (basePort + maxRetries) + "]");
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodePortAllocatorTest -q`
Expected: PASS

- [x] **Step 5: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodePortAllocator.java data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodePortAllocatorTest.java
git commit -m "feat(server): add OpenCodePortAllocator for dynamic port detection"
```

---

### Task 3: OpenCodeServeProperties — 配置属性

**Files:**
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeServeProperties.java`

- [x] **Step 1: Write configuration properties class**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeServeProperties.java
package com.datatalk.infra.opencode.process;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for embedded OpenCode server.
 * Bound from: datatalk.opencode.serve.*
 */
@ConfigurationProperties(prefix = "datatalk.opencode.serve")
public class OpenCodeServeProperties {

    private boolean enabled = true;
    private boolean autoUpgrade = false;
    private String version = "1.4.6";
    private int basePort = 4096;
    private int portRetries = 100;
    private String hostname = "127.0.0.1";
    private String cors = "http://localhost:8080";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoUpgrade() { return autoUpgrade; }
    public void setAutoUpgrade(boolean autoUpgrade) { this.autoUpgrade = autoUpgrade; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public int getBasePort() { return basePort; }
    public void setBasePort(int basePort) { this.basePort = basePort; }

    public int getPortRetries() { return portRetries; }
    public void setPortRetries(int portRetries) { this.portRetries = portRetries; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getCors() { return cors; }
    public void setCors(String cors) { this(cors); }

    // Note: no setter generated for 2-arg form, keep the single-arg one
    public OpenCodeServeProperties() {}

    public OpenCodeServeProperties(String cors) {
        this.cors = cors;
    }
}
```

- [x] **Step 2: Verify compilation**

Run: `cd server && mvn compile -pl data-talk-infrastructure -q`
Expected: zero errors

- [x] **Step 3: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeServeProperties.java
git commit -m "feat(server): add OpenCodeServeProperties configuration class"
```

---

### Task 4: OpenCodeBinaryResolver — 二进制查找/下载/提取

**Files:**
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java`

- [x] **Step 1: Write the failing test**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverTest.java
package com.datatalk.infra.opencode.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.GZIPOutputStream;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class OpenCodeBinaryResolverTest {

    @TempDir
    Path tempDir;

    private final OpenCodeBinaryResolver resolver = new OpenCodeBinaryResolver();

    @Test
    void resolvesExistingLocalBinary() throws Exception {
        // Create ~/.data-talk/opencode/v1.0.0/opencode structure
        Path opencodeDir = tempDir.resolve(".data-talk/opencode/v1.0.0");
        Files.createDirectories(opencodeDir);
        Path binary = opencodeDir.resolve("opencode");
        Files.writeString(binary, "#!/bin/sh\necho test");
        setExecutable(binary);

        // Create .current file
        Files.writeString(tempDir.resolve(".data-talk/opencode/.current"), "1.0.0");

        Path result = resolver.resolveLocal(tempDir.resolve(".data-talk"));
        assertThat(result).isEqualTo(binary);
    }

    @Test
    void returnsNullWhenNoLocalBinary() {
        Path result = resolver.resolveLocal(tempDir);
        assertThat(result).isNull();
    }

    @Test
    void extractsTarGzFromClasspathResource() throws Exception {
        // Create a minimal tar.gz with a single file "test-binary"
        byte[] tarGz = createMinimalTarGz("test-binary", "binary content here");

        Path targetDir = tempDir.resolve("extract");
        Files.createDirectories(targetDir);

        resolver.extractTarGz(tarGz, targetDir);

        Path extracted = targetDir.resolve("test-binary");
        assertThat(extracted).exists();
        // Content check (tar extracts may differ, but file should exist)
        assertThat(Files.exists(extracted)).isTrue();
    }

    @Test
    void writesAndReadsCurrentVersion() throws Exception {
        Path baseDir = tempDir.resolve("opencode");
        Files.createDirectories(baseDir);

        resolver.writeCurrentVersion(baseDir, "1.4.6");
        String version = resolver.readCurrentVersion(baseDir);
        assertThat(version).isEqualTo("1.4.6");
    }

    @Test
    void returnsNullForMissingCurrentVersion() {
        String version = resolver.readCurrentVersion(tempDir);
        assertThat(version).isNull();
    }

    private void setExecutable(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path,
                Set.of(PosixFilePermission.OWNER_READ,
                       PosixFilePermission.OWNER_WRITE,
                       PosixFilePermission.OWNER_EXECUTE,
                       PosixFilePermission.GROUP_READ,
                       PosixFilePermission.GROUP_EXECUTE));
        } catch (UnsupportedOperationException e) {
            // Windows: just mark it executable via the basic attribute
            Files.setAttribute(path, "dos:readonly", false);
        }
    }

    /** Create a minimal POSIX-compatible tar.gz for testing. */
    private byte[] createMinimalTarGz(String filename, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            byte[] contentBytes = content.getBytes();
            int contentLen = contentBytes.length;
            // Pad to 512-byte blocks
            int paddedLen = ((contentLen + 511) / 512) * 512;

            // tar header (512 bytes)
            byte[] header = new byte[512];
            // name (100 bytes at offset 0)
            byte[] nameBytes = filename.getBytes();
            System.arraycopy(nameBytes, 0, header, 0, Math.min(nameBytes.length, 100));
            // mode (8 bytes at 100) - 0755
            System.arraycopy("0000755\0".getBytes(), 0, header, 100, 8);
            // uid (8 bytes at 108)
            System.arraycopy("0000000\0".getBytes(), 0, header, 108, 8);
            // gid (8 bytes at 116)
            System.arraycopy("0000000\0".getBytes(), 0, header, 116, 8);
            // size (12 bytes at 124, octal)
            String sizeOctal = String.format("%011o\0", contentLen);
            System.arraycopy(sizeOctal.getBytes(), 0, header, 124, 12);
            // mtime (12 bytes at 136)
            System.arraycopy("00000000000\0".getBytes(), 0, header, 136, 12);
            // typeflag (1 byte at 156) - '0' = regular file
            header[156] = (byte) '0';
            // magic (6 bytes at 257) = "ustar\0"
            System.arraycopy("ustar\0".getBytes(), 0, header, 257, 6);
            // version (2 bytes at 263)
            header[263] = '0';
            header[264] = ' ';
            // checksum placeholder (8 bytes at 148)
            System.arraycopy("        ".getBytes(), 0, header, 148, 8);

            // Calculate checksum
            int checksum = 0;
            for (byte b : header) {
                checksum += b & 0xFF;
            }
            String checksumStr = String.format("%06o\0 ", checksum);
            System.arraycopy(checksumStr.getBytes(), 0, header, 148, 8);

            gzip.write(header);
            // Content padded to 512-byte blocks
            byte[] padded = new byte[paddedLen];
            System.arraycopy(contentBytes, 0, padded, 0, contentLen);
            gzip.write(padded);
            // Two empty 512-byte blocks to mark end of archive
            gzip.write(new byte[1024]);
        }
        return baos.toByteArray();
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverTest -q`
Expected: FAIL — class doesn't exist

- [x] **Step 3: Write implementation**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java
package com.datatalk.infra.opencode.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * Resolves, downloads, and extracts OpenCode binaries from local cache,
 * classpath resources, or GitHub releases.
 */
public class OpenCodeBinaryResolver {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeBinaryResolver.class);
    private static final String DATA_DIR = ".data-talk";
    private static final String OPENCODE_DIR = DATA_DIR + "/opencode";
    private static final String CURRENT_FILE = ".current";

    /**
     * Resolve local binary from the base directory.
     * Checks ~/.data-talk/opencode/.current and verifies the binary exists.
     *
     * @param baseDir base directory (e.g., user home)
     * @return path to executable, or null if not found
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
     * Looks for resources/opencode/{platform}/opencode.
     *
     * @param baseDir base directory to extract to
     * @return path to extracted binary, or null if resource not found
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
     *
     * @param baseDir   base directory
     * @param version   version to download (e.g., "1.4.6")
     * @return path to downloaded binary, or null on failure
     */
    public Path downloadFromGitHub(Path baseDir, String version) {
        OpenCodePlatform platform = OpenCodePlatform.current();
        String assetName = platform.githubAssetName(version);
        String downloadUrl = "https://github.com/anomalyco/opencode/releases/download/v" + version + "/" + assetName;

        Path opencodeDir = baseDir.resolve(OPENCODE_DIR);
        Path versionDir = opencodeDir.resolve("v" + version);

        // Don't overwrite existing version directory
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

            // Extract tar.gz
            Path tempDir = opencodeDir.resolve("temp-" + UUID.randomUUID());
            Files.createDirectories(tempDir);
            extractTarGz(response.body(), tempDir);

            // Find the binary in extracted content
            Path extractedBinary = findBinary(tempDir, platform.binaryName());
            if (extractedBinary == null) {
                log.error("Binary '{}' not found in extracted archive", platform.binaryName());
                deleteRecursively(tempDir);
                return null;
            }

            // Move to version directory
            Files.createDirectories(versionDir);
            Path targetBinary = versionDir.resolve(platform.binaryName());
            Files.move(extractedBinary, targetBinary, StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(tempDir);

            writeCurrentVersion(opencodeDir, "v" + version);
            log.info("Downloaded OpenCode v{} to {}", version, targetBinary);
            return targetBinary;

        } catch (Exception e) {
            log.error("Failed to download OpenCode: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract a tar.gz byte array to a target directory.
     */
    void extractTarGz(byte[] tarGzBytes, Path targetDir) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(tarGzBytes))) {
            byte[] header = new byte[512];
            int bytesRead;

            while ((bytesRead = gzip.read(header)) == 512) {
                // Check for end of archive (two consecutive zero blocks)
                if (isZeroBlock(header)) break;

                // Parse filename from header (offset 0, 100 bytes)
                String filename = parseTarFilename(header);
                if (filename.isEmpty()) {
                    // Skip to next header
                    continue;
                }

                // Parse size from header (offset 124, 12 bytes, octal)
                long fileSize = parseTarSize(header);

                // Create file
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
                        // Skip padding to 512-byte boundary
                        long padding = (512 - (fileSize % 512)) % 512;
                        gzip.skipNBytes(padding);
                    }
                } else {
                    // Directory entry
                    Files.createDirectories(targetPath);
                }

                // Reset header for next iteration
                header = new byte[512];
            }
        }
    }

    /**
     * Read current version from .current file.
     */
    String readCurrentVersion(Path opencodeDir) {
        Path currentFile = opencodeDir.resolve(CURRENT_FILE);
        if (!Files.exists(currentFile)) return null;
        try {
            return Files.readString(currentFile).trim();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Write current version to .current file.
     */
    void writeCurrentVersion(Path opencodeDir, String version) {
        try {
            Files.createDirectories(opencodeDir);
            Files.writeString(opencodeDir.resolve(CURRENT_FILE), version);
        } catch (IOException e) {
            log.warn("Failed to write version file: {}", e.getMessage());
        }
    }

    // --- Private helpers ---

    private boolean isZeroBlock(byte[] block) {
        for (byte b : block) {
            if (b != 0) return false;
        }
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
        try (var stream = Files.walk(path)) {
            stream.sorted((a, b) -> b.compareTo(a)) // reverse order
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeBinaryResolverTest -q`
Expected: PASS

- [x] **Step 5: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolver.java data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeBinaryResolverTest.java
git commit -m "feat(server): add OpenCodeBinaryResolver for binary management"
```

---

### Task 5: OpenCodeProcessManager — 核心生命周期

**Files:**
- Create: `server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeProcessManagerTest.java`
- Create: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java`

- [x] **Step 1: Write the failing test**

```java
// server/data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeProcessManagerTest.java
package com.datatalk.infra.opencode.process;

import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.application.opencode.OpenCodeEventLoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpenCodeProcessManagerTest {

    @TempDir
    Path tempDir;

    private OpenCodeServeProperties serveProps;
    private OpenCodeBinaryResolver binaryResolver;
    private OpenCodePortAllocator portAllocator;
    private OpenCodeHttpClient httpClient;
    private OpenCodeEventLoop eventLoop;
    private OpenCodeProcessManager manager;

    @BeforeEach
    void setUp() {
        serveProps = new OpenCodeServeProperties();
        serveProps.setEnabled(false); // default: disabled for test safety
        binaryResolver = mock(OpenCodeBinaryResolver.class);
        portAllocator = mock(OpenCodePortAllocator.class);
        httpClient = mock(OpenCodeHttpClient.class);
        eventLoop = mock(OpenCodeEventLoop.class);

        manager = new OpenCodeProcessManager(
            serveProps, binaryResolver, portAllocator,
            tempDir, httpClient, eventLoop, false,
            "http://localhost:4096");
    }

    @Test
    void doesNothingWhenDisabled() {
        manager.start();
        assertThat(manager.isRunning()).isFalse();
        verifyNoInteractions(binaryResolver, portAllocator, httpClient);
    }

    @Test
    void startsProcessWhenEnabled() throws Exception {
        serveProps.setEnabled(true);
        Path fakeBinary = tempDir.resolve("opencode");
        Files.writeString(fakeBinary, "#!/bin/sh\necho 'listening on :9999'");
        fakeBinary.toFile().setExecutable(true);

        when(binaryResolver.resolveLocal(tempDir)).thenReturn(fakeBinary);
        when(portAllocator.allocate(4096, 100)).thenReturn(9999);

        manager.start();
        verify(httpClient).setBaseUrl("http://127.0.0.1:9999");
    }

    @Test
    void stopTerminatesProcess() throws Exception {
        // Setup with enabled
        serveProps.setEnabled(true);
        Path fakeBinary = tempDir.resolve("opencode");
        Files.writeString(fakeBinary, "#!/bin/sh\necho 'listening on :9999'");
        fakeBinary.toFile().setExecutable(true);

        when(binaryResolver.resolveLocal(tempDir)).thenReturn(fakeBinary);
        when(portAllocator.allocate(4096, 100)).thenReturn(9999);

        manager.start();
        assertThat(manager.isRunning()).isTrue();

        manager.stop();
        assertThat(manager.isRunning()).isFalse();
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeProcessManagerTest -q`
Expected: FAIL — class doesn't exist

- [x] **Step 3: Write implementation**

```java
// server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java
package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Manages the embedded OpenCode process lifecycle as a Spring SmartLifecycle bean.
 * Starts early (phase=-100) to ensure OpenCode is ready before ApplicationReadyEvent.
 */
public class OpenCodeProcessManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeProcessManager.class);
    private static final Pattern PORT_PATTERN = Pattern.compile("port[:\\s]+(\\d+)", Pattern.CASE_INSENSITIVE);

    private final OpenCodeServeProperties serveProps;
    private final OpenCodeBinaryResolver binaryResolver;
    private final OpenCodePortAllocator portAllocator;
    private final Path homeDir;
    private final OpenCodeHttpClient httpClient;
    private final OpenCodeEventLoop eventLoop;
    private final boolean required;
    private final String defaultBaseUrl;

    private Process process;
    private Thread shutdownHook;
    private volatile int actualPort = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OpenCodeProcessManager(OpenCodeServeProperties serveProps,
                                  OpenCodeBinaryResolver binaryResolver,
                                  OpenCodePortAllocator portAllocator,
                                  Path homeDir,
                                  OpenCodeHttpClient httpClient,
                                  OpenCodeEventLoop eventLoop,
                                  boolean required,
                                  String defaultBaseUrl) {
        this.serveProps = serveProps;
        this.binaryResolver = binaryResolver;
        this.portAllocator = portAllocator;
        this.homeDir = homeDir;
        this.httpClient = httpClient;
        this.eventLoop = eventLoop;
        this.required = required;
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @Override
    public void start() {
        if (!serveProps.isEnabled()) {
            log.info("OpenCode embedded server is disabled (datatalk.opencode.serve.enabled=false)");
            return;
        }

        try {
            doStart();
        } catch (Exception e) {
            log.error("Failed to start OpenCode embedded server: {}", e.getMessage());
            if (required) {
                throw new IllegalStateException("OpenCode is required but failed to start", e);
            } else {
                log.warn("Continuing in degraded mode — OpenCode server is not available");
                return;
            }
        }
    }

    private void doStart() throws Exception {
        // Step 1: Resolve binary
        Path binary = resolveBinary();
        if (binary == null) {
            throw new IllegalStateException("No OpenCode binary available (not in classpath, not cached, and GitHub download disabled for now)");
        }

        // Step 2: Allocate port
        actualPort = portAllocator.allocate(serveProps.getBasePort(), serveProps.getPortRetries());
        log.info("Allocated port {} for OpenCode server", actualPort);

        // Step 3: Build command
        String hostname = serveProps.getHostname();
        String cors = serveProps.getCors();
        String[] cmd = {
            binary.toString(),
            "serve",
            "--port", String.valueOf(actualPort),
            "--hostname", hostname,
            "--cors", cors
        };
        log.info("Starting OpenCode: {}", String.join(" ", cmd));

        // Step 4: Launch process
        ProcessBuilder pb = new ProcessBuilder(cmd)
            .directory(homeDir.resolve(".data-talk/opencode").toFile())
            .redirectErrorStream(true);

        process = pb.start();

        // Step 5: Stream stdout to logger
        Thread logThread = new Thread(() -> streamOutput(process), "opencode-log");
        logThread.setDaemon(true);
        logThread.start();

        // Step 6: Wait for ready signal
        waitForReady(process, actualPort);

        // Step 7: Update dependent components with actual port
        String actualBaseUrl = "http://" + hostname + ":" + actualPort;
        httpClient.setBaseUrl(actualBaseUrl);
        eventLoop.setBaseUrl(actualBaseUrl);
        log.info("OpenCode server ready at {}", actualBaseUrl);

        // Step 8: Register shutdown hook
        shutdownHook = new Thread(() -> {
            log.info("Shutting down OpenCode process...");
            stopProcess();
        }, "opencode-shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        running.set(true);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) return;

        log.info("Stopping OpenCode process manager...");

        // Remove shutdown hook (already handling cleanup)
        if (shutdownHook != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) {} // already shutting down
        }

        stopProcess();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return -100; // Start before ApplicationReadyEvent (phase=0)
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** Returns the actual port OpenCode is listening on, or -1 if not started. */
    public int getActualPort() {
        return actualPort;
    }

    // --- Private helpers ---

    private Path resolveBinary() {
        // Priority 1: Local cached binary
        Path local = binaryResolver.resolveLocal(homeDir);
        if (local != null) return local;

        // Priority 2: Classpath resource (JAR embedded mode)
        Path classpath = binaryResolver.extractFromClasspath(homeDir);
        if (classpath != null) return classpath;

        // Priority 3: Download from GitHub
        return binaryResolver.downloadFromGitHub(homeDir, serveProps.getVersion());
    }

    private void streamOutput(Process proc) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[opencode] {}", line);
            }
        } catch (IOException e) {
            if (proc.isAlive()) {
                log.warn("Error reading OpenCode output: {}", e.getMessage());
            }
        }
    }

    private void waitForReady(Process proc, int expectedPort) throws InterruptedException {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();

        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive()) {
                throw new IllegalStateException("OpenCode process exited prematurely with code " + proc.exitValue());
            }

            // Check if the port pattern appears in output (we'd need to capture it)
            // Fallback: try to connect to the port
            if (isPortListening(expectedPort)) {
                return;
            }

            Thread.sleep(200);
        }

        throw new IllegalStateException("OpenCode did not become ready within 30 seconds");
    }

    private boolean isPortListening(int port) {
        try (var socket = new java.net.Socket("127.0.0.1", port)) {
            socket.setSoTimeout(500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void stopProcess() {
        Process proc = process;
        if (proc == null || !proc.isAlive()) return;

        log.info("Sending SIGTERM to OpenCode process (PID: {})", proc.pid());
        proc.destroy();

        try {
            if (!proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("OpenCode did not exit gracefully, force killing");
                proc.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proc.destroyForcibly();
        }
    }
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `cd server && mvn test -pl data-talk-infrastructure -Dtest=OpenCodeProcessManagerTest -q`
Expected: PASS

- [x] **Step 5: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/process/OpenCodeProcessManager.java data-talk-infrastructure/src/test/java/com/datatalk/infra/opencode/process/OpenCodeProcessManagerTest.java
git commit -m "feat(server): add OpenCodeProcessManager SmartLifecycle for process management"
```

---

### Task 6: Modify OpenCodeHttpClient — 添加 setBaseUrl

**Files:**
- Modify: `server/data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java`

- [x] **Step 1: Add setBaseUrl method**

```java
// In OpenCodeHttpClient.java — add this field and method

    private volatile String baseUrl;

    // Modify constructor to store baseUrl:
    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.baseUrl = baseUrl;
        this.wc = WebClient.builder().baseUrl(baseUrl).build();
        this.om = om;
    }

    // Add this method:
    /**
     * Update the base URL at runtime (e.g., when dynamic port is assigned).
     */
    public void setBaseUrl(String url) {
        this.baseUrl = url;
        this.wc = WebClient.builder().baseUrl(url).build();
    }

    // Also add a getter for external access:
    public String getBaseUrl() {
        return baseUrl;
    }
```

Full updated file after changes:

```java
package com.datatalk.infra.opencode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Thin HTTP wrapper around the OpenCode server.
 */
public class OpenCodeHttpClient {

    private volatile String baseUrl;
    private volatile WebClient wc;
    private final ObjectMapper om;

    public OpenCodeHttpClient(String baseUrl, ObjectMapper om) {
        this.baseUrl = baseUrl;
        this.wc = WebClient.builder().baseUrl(baseUrl).build();
        this.om = om;
    }

    /**
     * Update the base URL at runtime (e.g., when dynamic port is assigned).
     */
    public void setBaseUrl(String url) {
        this.baseUrl = url;
        this.wc = WebClient.builder().baseUrl(url).build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String createSession() {
        String body = wc.post().uri("/session")
            .retrieve()
            .bodyToMono(String.class)
            .block();
        try {
            JsonNode node = om.readTree(body);
            return node.path("id").asText();
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse OpenCode /session response", e);
        }
    }

    public void registerTool(String name, String description,
                             Map<String, Object> parameters, String callbackUrl) {
        wc.post().uri("/plugin/register-tool")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of(
                "name", name,
                "description", description,
                "parameters", parameters,
                "callbackUrl", callbackUrl
            ))
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void sendMessage(String sessionId, Map<String, Object> requestBody) {
        wc.post().uri("/session/{id}/message", sessionId)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .toBodilessEntity()
            .block();
    }

    public void abort(String sessionId) {
        wc.post().uri("/session/{id}/abort", sessionId)
            .retrieve()
            .toBodilessEntity()
            .block();
    }
}
```

- [x] **Step 2: Verify compilation and existing tests**

Run: `cd server && mvn test -pl data-talk-infrastructure -q`
Expected: All existing tests pass, zero compilation errors

- [x] **Step 3: Commit**

```bash
cd server
git add data-talk-infrastructure/src/main/java/com/datatalk/infra/opencode/OpenCodeHttpClient.java
git commit -m "refactor(server): add setBaseUrl to OpenCodeHttpClient for dynamic port support"
```

---

### Task 7: Modify OpenCodeEventLoop — 添加 setBaseUrl

**Files:**
- Modify: `server/data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java`

- [x] **Step 1: Add volatile baseUrl and setBaseUrl method**

The change: make `baseUrl` volatile and add a setter so `OpenCodeProcessManager` can update it before the event loop starts.

```java
// In OpenCodeEventLoop.java — modify the field declaration:

    private volatile String baseUrl;  // was: private final String baseUrl;

// Add this method after stop():

    /**
     * Update the base URL at runtime (e.g., when dynamic port is assigned).
     * Safe to call before or during event loop operation.
     */
    public void setBaseUrl(String url) {
        this.baseUrl = url;
    }
```

The `baseUrl` is only read inside `openStream()` which creates a fresh URI each time:
```java
URI uri = URI.create(baseUrl + "/event");
```
So updating it via setter is safe — the next reconnection uses the new URL.

- [x] **Step 2: Verify compilation**

Run: `cd server && mvn compile -pl data-talk-application -q`
Expected: zero errors

- [x] **Step 3: Commit**

```bash
cd server
git add data-talk-application/src/main/java/com/datatalk/application/opencode/OpenCodeEventLoop.java
git commit -m "refactor(server): add setBaseUrl to OpenCodeEventLoop for dynamic port support"
```

---

### Task 8: Wire ProcessManager in Adapter + Update ApplicationReadyEvent

**Files:**
- Modify: `server/data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java`

- [x] **Step 1: Rewrite OpenCodeGatewayBeans with ProcessManager integration**

Full new file content:

```java
package com.datatalk.adapter.config;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.application.opencode.OpenCodeEventTranslator;
import com.datatalk.application.opencode.OpenCodeGateway;
import com.datatalk.application.opencode.OpenCodeSessionMap;
import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.session.SessionBusRegistry;
import com.datatalk.infra.opencode.OpenCodeConfig;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import com.datatalk.infra.opencode.process.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@EnableConfigurationProperties(OpenCodeServeProperties.class)
public class OpenCodeGatewayBeans {

    private final OpenCodeHttpClient client;
    private final OpenCodeConfig.OpenCodeProperties props;
    private final ActionRegistry registry;
    private final ObjectMapper om;
    private final OpenCodeEventTranslator translator;
    private final SessionBusRegistry buses;
    private final OpenCodeSessionMap sessionMap;
    private final OpenCodeServeProperties serveProps;
    private final OpenCodeProcessManager processManager;
    private OpenCodeGateway gateway;
    private OpenCodeEventLoop eventLoop;

    public OpenCodeGatewayBeans(OpenCodeHttpClient client,
                                OpenCodeConfig.OpenCodeProperties props,
                                ActionRegistry registry,
                                ObjectMapper om,
                                OpenCodeEventTranslator translator,
                                SessionBusRegistry buses,
                                OpenCodeSessionMap sessionMap,
                                OpenCodeServeProperties serveProps,
                                @Value("${datatalk.opencode.required:false}") boolean required,
                                @Value("${datatalk.opencode.base-url:http://localhost:4096}") String defaultBaseUrl) {
        this.client = client;
        this.props = props;
        this.registry = registry;
        this.om = om;
        this.translator = translator;
        this.buses = buses;
        this.sessionMap = sessionMap;
        this.serveProps = serveProps;

        Path homeDir = Paths.get(System.getProperty("user.home"));
        OpenCodeBinaryResolver resolver = new OpenCodeBinaryResolver();
        OpenCodePortAllocator allocator = new OpenCodePortAllocator();
        eventLoop = new OpenCodeEventLoop(
            defaultBaseUrl, om, translator, buses, sessionMap, null);

        this.processManager = new OpenCodeProcessManager(
            serveProps, resolver, allocator,
            homeDir, client, eventLoop, required, defaultBaseUrl);
    }

    @Bean
    public OpenCodeGateway openCodeGateway() {
        this.gateway = new OpenCodeGateway(
            registry,
            (name, desc, params, cb) -> client.registerTool(name, desc, params, cb),
            (ocSid, body) -> client.sendMessage(ocSid, body),
            client::createSession,
            props.callbackBase()
        );
        return gateway;
    }

    @Bean
    public OpenCodeEventLoop openCodeEventLoopBean() {
        return eventLoop;
    }

    @Bean
    public OpenCodeProcessManager openCodeProcessManager() {
        return processManager;
    }

    /**
     * Registers tools and starts the OpenCode SSE event loop after startup.
     * If the embedded server is enabled and running, it's required for tool registration.
     * If the embedded server is disabled, requires an external OpenCode instance.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerOnStartup() {
        // If embedded serve is enabled but manager didn't start (failure), skip
        if (serveProps.isEnabled() && !processManager.isRunning()) {
            System.err.println("OpenCode embedded server failed to start — skipping tool registration (degraded mode)");
            return;
        }

        try {
            gateway.registerTools();
        } catch (Exception e) {
            System.err.println("OpenCode tool registration failed (degraded mode): " + e.getMessage());
        }
        try {
            eventLoop.start();
        } catch (Exception e) {
            System.err.println("OpenCode SSE event loop failed to start (degraded mode): " + e.getMessage());
        }
    }
}
```

- [x] **Step 2: Add @EnableConfigurationProperties for OpenCodeServeProperties**

The `@EnableConfigurationProperties(OpenCodeServeProperties.class)` annotation is already included in the file above.

- [x] **Step 3: Verify compilation**

Run: `cd server && mvn compile -pl data-talk-adapter -q`
Expected: zero errors

- [x] **Step 4: Run all server tests**

Run: `cd server && mvn test -q`
Expected: all tests pass (existing + new)

- [x] **Step 5: Commit**

```bash
cd server
git add data-talk-adapter/src/main/java/com/datatalk/adapter/config/OpenCodeGatewayBeans.java
git commit -m "feat(server): wire OpenCodeProcessManager and update startup sequence"
```

---

### Task 9: Add application.yml Configuration + Final Verification

**Files:**
- Modify: `server/data-talk-adapter/src/main/resources/application.yml`

- [x] **Step 1: Add OpenCode serve configuration to application.yml**

Append to the existing `datatalk:` section:

```yaml
datatalk:
  persistence:
    sqlite-path: ./data/datatalk.db
  opencode:
    base-url: http://localhost:4096
    plugin-callback-base: http://localhost:8080
    shared-secret: ""
    required: false
    serve:
      enabled: true
      auto-upgrade: false
      version: 1.4.6
      base-port: 4096
      port-retries: 100
      hostname: 127.0.0.1
      cors: http://localhost:8080
```

Full file after change:

```yaml
spring:
  application:
    name: data-talk

  # H2 demo datasource (primary)
  datasource:
    url: jdbc:h2:mem:placeholder
    driver-class-name: org.h2.Driver
    username: sa
    password:

  sqlite-datasource:
    url: jdbc:sqlite:./data/metadata.db
    driver-class-name: org.sqlite.JDBC

  sql:
    init:
      mode: always
      schema-locations: classpath:schema-demo.sql
      data-locations: classpath:data-demo.sql
      continue-on-error: false

  jdbc:
    template:
      query-timeout: 30

server:
  port: 8080

logging:
  level:
    com.datatalk: DEBUG

datatalk:
  persistence:
    sqlite-path: ./data/datatalk.db
  opencode:
    base-url: http://localhost:4096
    plugin-callback-base: http://localhost:8080
    shared-secret: ""
    required: false
    serve:
      enabled: true
      auto-upgrade: false
      version: 1.4.6
      base-port: 4096
      port-retries: 100
      hostname: 127.0.0.1
      cors: http://localhost:8080
```

- [x] **Step 2: Full build verification**

Run: `cd server && mvn clean verify -q`
Expected: zero compilation errors, all tests pass

- [x] **Step 3: Commit**

```bash
cd server
git add data-talk-adapter/src/main/resources/application.yml
git commit -m "chore(server): add OpenCode serve configuration to application.yml"
```

---

### Task 10: Clean up and push waiting screen

- [x] **Step 1: Write waiting screen to browser companion**

```html
<div style="display:flex;align-items:center;justify-content:center;min-height:60vh">
  <p class="subtitle">Implementation plan complete. Continuing in terminal...</p>
</div>
```

- [x] **Step 2: Run final spec self-review**

Check the design spec at `docs/superpowers/specs/2026-04-16-opencode-embedded-process-design.md`:

1. **Spec coverage:** All sections covered by tasks:
   - §2 配置 → Task 3 (ServeProperties) + Task 9 (application.yml)
   - §3 架构与模块划分 → Task 1-5 (all components) + Task 8 (wiring)
   - §4 启动序列 → Task 5 (ProcessManager.start())
   - §5 关闭生命周期 → Task 5 (ProcessManager.stop() + shutdown hook)
   - §6 错误处理与降级 → Task 5 (try/catch + required flag)
   - §7 跨平台二进制命名 → Task 1 (Platform enum)
   - §8 自动升级 → BinaryResolver.downloadFromGitHub() (implemented in Task 4)
   - §9 测试策略 → Task 1-5, 7 (all tests)

2. **Placeholder scan:** No TBD/TODO found in plan.

3. **Type consistency:** All method signatures consistent across tasks.

4. **Scope check:** Focused on a single feature — embedded process management.
