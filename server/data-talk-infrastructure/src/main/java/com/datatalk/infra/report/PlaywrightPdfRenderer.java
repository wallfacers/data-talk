package com.datatalk.infra.report;

import com.datatalk.application.report.PdfRenderer;
import com.datatalk.application.report.ReportRenderingException;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.Margin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Playwright Java SDK 实现：headless Chromium 加载 HTML → 等待 LEDGER_READY → page.pdf。
 *
 * <p>URL 加载策略：localhost HTTP（避免 file:// 与 <base href> 冲突）。
 * 调用方需先把 HTML 通过 ReportController 暴露在 `/api/reports/{id}/download/html` 上，
 * 这里通过配置传入 backend base URL。
 */
@Component
public class PlaywrightPdfRenderer implements PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightPdfRenderer.class);

    private static final int WAIT_READY_TIMEOUT_MS = 30_000;
    private static final int OVERALL_TIMEOUT_MS = 60_000;

    private final ChromiumLifecycle chromium;
    private final String backendBaseUrl;

    public PlaywrightPdfRenderer(
            ChromiumLifecycle chromium,
            @Value("${server.port:8080}") int serverPort) {
        this.chromium = chromium;
        this.backendBaseUrl = "http://127.0.0.1:" + serverPort;
    }

    @Override
    public void render(Path htmlAbsolutePath, Path outputPdfPath) {
        Browser browser = chromium.browser();
        if (browser == null) {
            throw new ReportRenderingException("Chromium not ready");
        }
        long deadline = System.currentTimeMillis() + OVERALL_TIMEOUT_MS;
        try (BrowserContext ctx = browser.newContext()) {
            try (Page page = ctx.newPage()) {
                // Use file:// for now — base href + relative refs will resolve relative to the HTML file path.
                // assets must be reachable from the same parent dir (for v0 we put fonts/styles via http base).
                // Strategy: load file:// and intercept absolute / paths to backend.
                String url = "file://" + htmlAbsolutePath.toAbsolutePath();
                page.route("**/api/reports/_assets/**", route -> {
                    String requested = route.request().url();
                    // forward to backend
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
                page.navigate(url);
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) throw new ReportRenderingException("timeout before waitForFunction");
                try {
                    page.waitForFunction(
                            "() => window.__LEDGER_READY__",
                            null,
                            new Page.WaitForFunctionOptions().setTimeout(Math.min(WAIT_READY_TIMEOUT_MS, left)));
                } catch (Throwable t) {
                    log.warn("LEDGER_READY wait timed out, continuing to page.pdf: {}", t.getMessage());
                }
                page.pdf(new Page.PdfOptions()
                        .setPath(outputPdfPath)
                        .setFormat("A4")
                        .setPrintBackground(true)
                        .setMargin(new Margin().setTop("20mm").setBottom("20mm").setLeft("18mm").setRight("18mm")));
            }
        } catch (RuntimeException e) {
            throw new ReportRenderingException("PDF render failed: " + e.getMessage(), e);
        }
    }
}
