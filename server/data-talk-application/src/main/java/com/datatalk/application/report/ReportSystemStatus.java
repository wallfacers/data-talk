package com.datatalk.application.report;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 跨层共享的报告子系统状态（Chromium 准备好了吗 / 字体齐全吗 / skill 同步完了吗）。
 *
 * <p>infrastructure 的 ChromiumLifecycle 启动期写入 chromiumReady；
 * adapter 的 system-status REST 端点读取后返回给前端。
 */
@Component
public class ReportSystemStatus {

    private final AtomicBoolean chromiumReady = new AtomicBoolean(false);
    private final AtomicBoolean fontsReady = new AtomicBoolean(false);
    private final AtomicBoolean skillReady = new AtomicBoolean(false);
    private final AtomicReference<String> message = new AtomicReference<>(null);

    public boolean chromiumReady() { return chromiumReady.get(); }
    public boolean fontsReady() { return fontsReady.get(); }
    public boolean skillReady() { return skillReady.get(); }
    public String message() { return message.get(); }

    public void setChromiumReady(boolean v) { chromiumReady.set(v); }
    public void setFontsReady(boolean v) { fontsReady.set(v); }
    public void setSkillReady(boolean v) { skillReady.set(v); }
    public void setMessage(String m) { message.set(m); }
}
