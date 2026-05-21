package com.datatalk.application.report;

import java.nio.file.Path;
import java.util.Map;

/**
 * Port: 加载 ledger 报告 HTML 后等待 LEDGER_READY 信号，对每个 chart 容器
 * (data-ledger-chart-id) 截图为 PNG。infrastructure 提供 Playwright 实现。
 *
 * <p>返回 Map&lt;blockId, png-file-artifact-id&gt; — 调用方据此把 chart PNG 路径
 * 写入 MarkdownRenderer 的输入。
 */
public interface ChartCaptureRenderer {

    /**
     * 截图全部 chart block。
     *
     * @param htmlAbsolutePath 已写盘的 HTML 文件绝对路径
     * @param reportId         用于命名 PNG file artifact
     * @return blockId → png absolute path 映射。某个 chart 截图失败时其 blockId 不出现在 map 里。
     */
    Map<String, Path> captureChartsToPng(Path htmlAbsolutePath, String reportId);
}
