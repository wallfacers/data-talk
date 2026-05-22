package com.datatalk.infra.report;

import com.datatalk.application.report.ReportSystemStatus;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class ChromiumLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChromiumLifecycle.class);

    private final ReportSystemStatus systemStatus;
    private final boolean chromiumEnabled;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Playwright playwright;
    private volatile Browser browser;

    public ChromiumLifecycle(
            ReportSystemStatus systemStatus,
            @Value("${datatalk.report.chromium.enabled:false}") boolean chromiumEnabled) {
        this.systemStatus = systemStatus;
        this.chromiumEnabled = chromiumEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startupAsync() {
        if (!chromiumEnabled) {
            log.info("Chromium warmup disabled (datatalk.report.chromium.enabled=false)");
            systemStatus.setChromiumReady(false);
            return;
        }
        Thread.ofVirtual().name("chromium-warmup").start(this::ensureBrowser);
    }

    /**
     * Lazily initialise Playwright + Chromium. Idempotent; safe to call from multiple threads.
     */
    public Browser browser() {
        ensureBrowser();
        return browser;
    }

    private void ensureBrowser() {
        if (browser != null) return;
        lock.lock();
        try {
            if (browser != null) return;
            systemStatus.setMessage("Chromium installing");
            log.info("Initialising Playwright + Chromium (first-time may download ~300MB)");
            Playwright pw = Playwright.create(
                    new Playwright.CreateOptions()
                            .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            Browser br = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true));
            this.playwright = pw;
            this.browser = br;
            systemStatus.setChromiumReady(true);
            systemStatus.setMessage(null);
            log.info("Chromium ready");
        } catch (Throwable t) {
            log.error("Failed to initialise Chromium: {}", t.getMessage(), t);
            systemStatus.setChromiumReady(false);
            systemStatus.setMessage("Chromium init failed: " + t.getMessage());
        } finally {
            lock.unlock();
        }
    }

    @PreDestroy
    public void shutdown() {
        try {
            if (browser != null) browser.close();
        } catch (Throwable ignored) {}
        try {
            if (playwright != null) playwright.close();
        } catch (Throwable ignored) {}
        log.info("Chromium shut down");
    }
}
