package com.datatalk.application.dashboard;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads scheduler.js from classpath and provides it as the {@code __SCHEDULER_IIFE__} replacement.
 *
 * <p>The scheduler is a self-contained IIFE that initialises ECharts instances, manages polling
 * intervals, and handles postMessage communication with the host page. It is loaded once at
 * class-load time and cached as a static final field.</p>
 */
public final class SchedulerBundler {

    private static final String SCHEDULER_JS;

    static {
        SCHEDULER_JS = loadFromClasspath();
    }

    private SchedulerBundler() {}

    /**
     * Return the scheduler JS source to inject as the {@code __SCHEDULER_IIFE__} placeholder.
     *
     * @return the full scheduler.js content (an IIFE)
     */
    public static String getSchedulerIife() {
        return SCHEDULER_JS;
    }

    private static String loadFromClasspath() {
        String path = "dashboard/renderers/scheduler.js";
        try (InputStream is = SchedulerBundler.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new SchedulerNotFoundException(path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            if (e instanceof SchedulerNotFoundException) throw (SchedulerNotFoundException) e;
            throw new SchedulerLoadException(path, e);
        }
    }

    public static final class SchedulerNotFoundException extends RuntimeException {
        public SchedulerNotFoundException(String path) {
            super("Scheduler JS not found on classpath: " + path);
        }
    }

    public static final class SchedulerLoadException extends RuntimeException {
        public SchedulerLoadException(String path, Throwable cause) {
            super("Failed to load scheduler JS from classpath: " + path, cause);
        }
    }
}
