package com.datatalk.application.dashboard;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads template HTML from classpath, resolves theme CSS, and fills non-widget placeholders.
 *
 * <p>Templates live at {@code dashboard/templates/{templateId}.html}. CSS files live at
 * {@code dashboard/styles/base.css} and {@code dashboard/styles/{industry}.css}. Results are
 * cached in a {@link ConcurrentHashMap} keyed by resource path.</p>
 */
public final class TemplateResolver {

    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    private static final String TEMPLATE_PREFIX = "dashboard/templates/";
    private static final String TEMPLATE_SUFFIX = ".html";
    private static final String DASHBOARD_PREFIX = "dashboard/";
    private static final String BASE_CSS = "dashboard/styles/base.css";

    private TemplateResolver() {}

    /**
     * Load template HTML from classpath.
     *
     * @param templateId e.g. "single-focus" — loads "dashboard/templates/single-focus.html"
     * @return raw template HTML with placeholders still intact
     * @throws TemplateNotFoundException if the template does not exist on the classpath
     */
    public static String resolveTemplate(String templateId) {
        String path = TEMPLATE_PREFIX + templateId + TEMPLATE_SUFFIX;
        return CACHE.computeIfAbsent(path, TemplateResolver::loadFromClasspath);
    }

    /**
     * Load base CSS + theme CSS concatenated together.
     *
     * <p>Uses the {@link PatternCatalog} to look up which CSS file the theme slug maps to.
     * The result is cached per theme slug.</p>
     *
     * @param themeSlug e.g. "ecommerce" — loads base.css + ecommerce.css
     * @param catalog   the pattern catalog for resolving theme-to-CSS mapping
     * @return concatenated base CSS + theme CSS
     * @throws ThemeNotFoundException if the theme CSS file does not exist on the classpath
     */
    public static String resolveCss(String themeSlug, PatternCatalog catalog) {
        String cacheKey = "css:" + themeSlug;
        return CACHE.computeIfAbsent(cacheKey, k -> {
            String baseCss = loadFromClasspath(BASE_CSS);

            String themeCssFile = catalog.resolveThemeCss(themeSlug);
            if (themeCssFile == null || themeCssFile.isBlank()) {
                return baseCss;
            }
            String themeCss = loadFromClasspath(DASHBOARD_PREFIX + themeCssFile);
            return baseCss + "\n" + themeCss;
        });
    }

    private static String loadFromClasspath(String path) {
        try (InputStream is = TemplateResolver.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new TemplateNotFoundException(path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new TemplateLoadException(path, e);
        }
    }

    public static final class TemplateNotFoundException extends RuntimeException {
        public TemplateNotFoundException(String path) {
            super("Template not found on classpath: " + path);
        }
    }

    public static final class TemplateLoadException extends RuntimeException {
        public TemplateLoadException(String path, Throwable cause) {
            super("Failed to load template from classpath: " + path, cause);
        }
    }

    public static final class ThemeNotFoundException extends RuntimeException {
        public ThemeNotFoundException(String themeSlug) {
            super("Theme CSS not found for slug: " + themeSlug);
        }
    }
}
