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
        // Format: opencode-{platform}-{arch}.{ext}
        assertThat(OpenCodePlatform.LINUX_X64.githubAssetName("1.4.6"))
            .isEqualTo("opencode-linux-x64.tar.gz");
        assertThat(OpenCodePlatform.DARWIN_ARM64.githubAssetName("1.4.6"))
            .isEqualTo("opencode-darwin-arm64.zip");
        assertThat(OpenCodePlatform.WINDOWS_X64.githubAssetName("1.4.6"))
            .isEqualTo("opencode-windows-x64.zip");
    }
}