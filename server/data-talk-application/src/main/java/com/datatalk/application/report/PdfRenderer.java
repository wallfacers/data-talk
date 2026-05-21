package com.datatalk.application.report;

import java.nio.file.Path;

/**
 * Port: 用 headless Chromium 加载 HTML 文件、等待 LEDGER_READY 信号、调用 page.pdf 生成 PDF。
 * infrastructure 用 Playwright Java SDK 实现。
 */
public interface PdfRenderer {

    /**
     * @param htmlAbsolutePath 已写盘的 HTML 文件绝对路径
     * @param outputPdfPath    PDF 输出目标绝对路径（调用方提前 mkdir）
     * @throws ReportRenderingException 渲染失败（超时 / Chromium 崩溃 / IO 异常）
     */
    void render(Path htmlAbsolutePath, Path outputPdfPath);
}
