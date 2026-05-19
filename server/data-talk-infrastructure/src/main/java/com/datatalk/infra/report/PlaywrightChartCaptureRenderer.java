package com.datatalk.infra.report;

import com.datatalk.application.report.ChartCaptureRenderer;
import com.datatalk.application.report.ReportRenderingException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.ViewportSize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Playwright 实现：复用 {@link ChromiumLifecycle} 的共享 Browser，加载 HTML 后对所有
 * {@code [data-ledger-chart-id]} DOM 元素截图为 PNG。
 *
 * <p>视口宽度固定 1200px @ 2x DPI（为 Markdown 阅读视觉一致）。
 */
@Component
public class PlaywrightChartCaptureRenderer implements ChartCaptureRenderer {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightChartCaptureRenderer.class);

    private static final int WAIT_READY_TIMEOUT_MS = 30_000;

    private final ChromiumLifecycle chromium;
    private final String backendBaseUrl;

    public PlaywrightChartCaptureRenderer(
            ChromiumLifecycle chromium,
            @Value("${datatalk.report.backend-base-url:http://localhost:8080}") String backendBaseUrl) {
        this.chromium = chromium;
        this.backendBaseUrl = backendBaseUrl;
    }

    @Override
    public Map<String, Path> captureChartsToPng(Path htmlAbsolutePath, String reportId) {
        Browser browser = chromium.browser();
        if (browser == null) {
            throw new ReportRenderingException("Chromium not ready");
        }
        Map<String, Path> result = new LinkedHashMap<>();
        Path outputDir = htmlAbsolutePath.getParent().resolve("assets");
        try {
            Files.createDirectories(outputDir);
        } catch (Exception e) {
            throw new ReportRenderingException("Failed to mkdir " + outputDir, e);
        }
        try (BrowserContext ctx = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(new ViewportSize(1200, 800))
                .setDeviceScaleFactor(2.0))) {
            try (Page page = ctx.newPage()) {
                page.route("**/api/reports/_assets/**", route -> {
                    String requested = route.request().url();
                    String rewritten = requested.replaceFirst("^.*?(/api/reports/_assets/.*)$",
                            backendBaseUrl + "$1");
                    try {
                        var resp = page.request().get(rewritten);
                        route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                                .setStatus(resp.status())
                                .setBodyBytes(resp.body())
                                .setContentType(resp.headers().getOrDefault("content-type", "application/octet-stream")));
                    } catch (Throwable t) {
                        log.warn("Asset proxy failed for {}: {}", requested, t.getMessage());
                        route.abort();
                    }
                });
                page.navigate("file://" + htmlAbsolutePath.toAbsolutePath());
                try {
                    page.waitForFunction("() => window.__LEDGER_READY__",
                            null,
                            new Page.WaitForFunctionOptions().setTimeout(WAIT_READY_TIMEOUT_MS));
                } catch (Throwable t) {
                    log.warn("LEDGER_READY timed out, attempting chart screenshots anyway");
                }
                List<ElementHandle> elements = page.querySelectorAll("[data-ledger-chart-id]");
                for (ElementHandle el : elements) {
                    String id = el.getAttribute("data-ledger-chart-id");
                    if (id == null || id.isBlank()) continue;
                    Path png = outputDir.resolve("chart-" + id + ".png");
                    try {
                        el.screenshot(new ElementHandle.ScreenshotOptions()
                                .setPath(png)
                                .setType(ScreenshotType.PNG)
                                .setOmitBackground(false));
                        result.put(id, png);
                    } catch (Throwable t) {
                        log.warn("Chart {} screenshot failed: {}", id, t.getMessage());
                    }
                }
            }
        } catch (RuntimeException e) {
            throw new ReportRenderingException("ChartCapture failed: " + e.getMessage(), e);
        }
        return result;
    }
}
