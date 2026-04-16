package com.datatalk.infra.opencode.process;

/**
 * Supported platforms for OpenCode binary distribution.
 * Maps OS + architecture to classpath resource paths and GitHub release asset names.
 */
public enum OpenCodePlatform {
    LINUX_X64("linux-x64", "opencode", ".tar.gz"),
    LINUX_ARM64("linux-arm64", "opencode", ".tar.gz"),
    DARWIN_X64("darwin-x64", "opencode", ".zip"),
    DARWIN_ARM64("darwin-arm64", "opencode", ".zip"),
    WINDOWS_X64("windows-x64", "opencode.exe", ".zip");

    private final String resourceDir;
    private final String binaryName;
    private final String archiveExt;

    OpenCodePlatform(String resourceDir, String binaryName, String archiveExt) {
        this.resourceDir = resourceDir;
        this.binaryName = binaryName;
        this.archiveExt = archiveExt;
    }

    /** Classpath resource directory, e.g. "opencode/linux-x64" */
    public String resourcePath() {
        return "opencode/" + resourceDir;
    }

    /** Binary filename, e.g. "opencode" or "opencode.exe" */
    public String binaryName() {
        return binaryName;
    }

    /** GitHub release asset name, e.g. "opencode-linux-x64.tar.gz" */
    public String githubAssetName(String version) {
        return "opencode-" + resourceDir + archiveExt;
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