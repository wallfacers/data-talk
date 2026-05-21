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
    private String version = "1.4.7";
    /** Absolute path to a pre-bundled OpenCode binary. When set and the file exists,
     *  it takes precedence over local cache / classpath / GitHub download. The packaged
     *  desktop app points this at backend/opencode/ so first launch never hits the network. */
    private String binaryPath;
    private int basePort = 4096;
    private int portRetries = 100;
    private String hostname = "127.0.0.1";
    private String cors = "http://localhost:8080";
    private boolean stripProxyEnv = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isAutoUpgrade() { return autoUpgrade; }
    public void setAutoUpgrade(boolean autoUpgrade) { this.autoUpgrade = autoUpgrade; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getBinaryPath() { return binaryPath; }
    public void setBinaryPath(String binaryPath) { this.binaryPath = binaryPath; }

    public int getBasePort() { return basePort; }
    public void setBasePort(int basePort) { this.basePort = basePort; }

    public int getPortRetries() { return portRetries; }
    public void setPortRetries(int portRetries) { this.portRetries = portRetries; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getCors() { return cors; }
    public void setCors(String cors) { this.cors = cors; }

    public boolean isStripProxyEnv() { return stripProxyEnv; }
    public void setStripProxyEnv(boolean stripProxyEnv) { this.stripProxyEnv = stripProxyEnv; }
}
