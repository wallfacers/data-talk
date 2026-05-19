package com.datatalk.infra.report;

import com.datatalk.application.report.ReportSystemStatus;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 启动期异步预热 Playwright headless Chromium，提供共享 Browser 实例。
 *
 * <p>首次启动会触发 Playwright SDK 下载 Chromium 浏览器到本地缓存（~300MB），可能耗时
 * 数十秒到几分钟。该过程不阻塞 Spring 启动；前端通过 {@link ReportSystemStatus#chromiumReady()}
 * 与 system-status REST 端点判定是否可点击 PDF/MD 导出按钮。
 */
@Component
public class ChromiumLifecycle {

    private static final Logger log = LoggerFactory.getLogger(ChromiumLifecycle.class);

    private final ReportSystemStatus systemStatus;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Playwright playwright;
    private volatile Browser browser;

    public ChromiumLifecycle(ReportSystemStatus systemStatus) {
        this.systemStatus = systemStatus;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startupAsync() {
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
            Playwright pw = Playwright.create();
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
