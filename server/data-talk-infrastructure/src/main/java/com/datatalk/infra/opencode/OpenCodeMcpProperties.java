package com.datatalk.infra.opencode;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.nio.file.Paths;

@ConfigurationProperties(prefix = "datatalk.mcp")
public class OpenCodeMcpProperties {

    private boolean enabled = true;
    private String configDir = "~/.data-talk/opencode";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConfigDir() {
        return configDir;
    }

    public void setConfigDir(String configDir) {
        this.configDir = configDir;
    }

    public Path resolveConfigDir() {
        if (configDir == null || configDir.isBlank()) {
            return Paths.get(System.getProperty("user.home"), ".data-talk", "opencode");
        }
        if (configDir.startsWith("~/")) {
            return Paths.get(System.getProperty("user.home")).resolve(configDir.substring(2));
        }
        return Paths.get(configDir);
    }
}
