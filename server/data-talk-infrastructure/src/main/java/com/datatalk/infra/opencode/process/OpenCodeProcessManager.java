package com.datatalk.infra.opencode.process;

import com.datatalk.application.opencode.OpenCodeEventLoop;
import com.datatalk.infra.opencode.OpenCodeHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the embedded OpenCode process lifecycle. Startup is triggered from
 * ApplicationReadyEvent so the DataTalk web server already knows its real port.
 */
public class OpenCodeProcessManager implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeProcessManager.class);

    private final OpenCodeServeProperties serveProps;
    private final OpenCodeBinaryResolver binaryResolver;
    private final OpenCodePortAllocator portAllocator;
    private final Path homeDir;
    private final Path configDir;
    private final OpenCodeHttpClient httpClient;
    private final OpenCodeEventLoop eventLoop;
    private final boolean required;

    private Process process;
    private Thread shutdownHook;
    private volatile int actualPort = -1;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OpenCodeProcessManager(OpenCodeServeProperties serveProps,
                                  OpenCodeBinaryResolver binaryResolver,
                                  OpenCodePortAllocator portAllocator,
                                  Path homeDir,
                                  Path configDir,
                                  OpenCodeHttpClient httpClient,
                                  OpenCodeEventLoop eventLoop,
                                  boolean required) {
        this.serveProps = serveProps;
        this.binaryResolver = binaryResolver;
        this.portAllocator = portAllocator;
        this.homeDir = homeDir;
        this.configDir = configDir;
        this.httpClient = httpClient;
        this.eventLoop = eventLoop;
        this.required = required;
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
                log.warn("Continuing in degraded mode; OpenCode server is not available");
                return;
            }
        }
    }

    private void doStart() throws Exception {
        binaryResolver.ensureNodeModules();
        Path binary = resolveBinary();
        if (binary == null) {
            throw new IllegalStateException("No OpenCode binary available");
        }

        actualPort = portAllocator.allocate(serveProps.getBasePort(), serveProps.getPortRetries());
        log.info("Allocated port {} for OpenCode server", actualPort);

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

        ProcessBuilder pb = new ProcessBuilder(cmd)
            .directory(homeDir.resolve(OpenCodeBinaryResolver.OPENCODE_DIR).toFile())
            .redirectErrorStream(true);
        pb.environment().put("OPENCODE_CONFIG_DIR", configDir.toString());
        pb.environment().put("OPENCODE_CONFIG", configDir.resolve("opencode.json").toString());
        // Bun's fetch() chokes on proxy env vars ("proxy.url must be a non-empty string")
        pb.environment().remove("http_proxy");
        pb.environment().remove("https_proxy");
        pb.environment().remove("HTTP_PROXY");
        pb.environment().remove("HTTPS_PROXY");

        process = pb.start();

        Thread logThread = new Thread(() -> streamOutput(process), "opencode-log");
        logThread.setDaemon(true);
        logThread.start();

        waitForReady(process, actualPort);

        String actualBaseUrl = "http://" + hostname + ":" + actualPort;
        httpClient.setBaseUrl(actualBaseUrl);
        eventLoop.setBaseUrl(actualBaseUrl);
        log.info("OpenCode server ready at {}", actualBaseUrl);

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

        if (shutdownHook != null) {
            try { Runtime.getRuntime().removeShutdownHook(shutdownHook); }
            catch (IllegalStateException ignored) {}
        }

        eventLoop.stop();
        stopProcess();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public int getPhase() {
        return -100;
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    public int getActualPort() {
        return actualPort;
    }

    private Path resolveBinary() {
        Path local = binaryResolver.resolveLocal(homeDir);
        if (local != null) return local;

        Path classpath = binaryResolver.extractFromClasspath(homeDir);
        if (classpath != null) return classpath;

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
