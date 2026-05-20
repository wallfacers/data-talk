package com.datatalk.application.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads and validates {@code dashboard/pattern-catalog.yaml} at startup.
 * Acts as the single source of truth for templates, chart types, patterns, themes, and color schemes.
 *
 * <p>Validates referential integrity after loading: pattern chart types reference known catalog entries,
 * color schemes are consistent, template slot IDs are unique, and renderKind is one of {chart, html}.</p>
 */
@Component
public class PatternCatalog {

    public record Catalog(
        List<String> colorSchemes,
        Map<String, ChartTypeDef> chartTypes,
        List<TemplateDef> templates,
        List<ThemeMapping> themes,
        Map<String, PatternDef> patterns
    ) {}

    public record ChartTypeDef(String defaultOptionFile, List<String> semanticFields, Boolean needsGeo) {
        public boolean requiresGeo() {
            return needsGeo != null && needsGeo;
        }
    }

    public record TemplateDef(String id, String description, String style, List<SlotDef> slots) {}

    public record SlotDef(String id, String kind, int capacity) {}

    public record ThemeMapping(String slug, String style, String css) {}

    public record PatternDef(
        String description,
        String industry,
        String renderKind,
        String defaultChartType,
        List<String> supportedChartTypes,
        String defaultColorScheme,
        List<String> supportedColorSchemes
    ) {}

    private static final String CATALOG_PATH = "dashboard/pattern-catalog.yaml";

    private final Catalog catalog;

    public PatternCatalog() {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream is = PatternCatalog.class.getClassLoader().getResourceAsStream(CATALOG_PATH)) {
            if (is == null) {
                throw new PatternCatalogNotFoundException(CATALOG_PATH);
            }
            this.catalog = yamlMapper.readValue(is, Catalog.class);
        } catch (Exception e) {
            if (e instanceof PatternCatalogNotFoundException pcnfe) throw pcnfe;
            throw new PatternCatalogLoadException(CATALOG_PATH, e);
        }
        validate();
    }

    private void validate() {
        Set<String> knownChartTypes = catalog.chartTypes().keySet();
        Set<String> knownColorSchemes = new HashSet<>(catalog.colorSchemes());

        // Validate patterns
        for (var entry : catalog.patterns().entrySet()) {
            String patternId = entry.getKey();
            PatternDef p = entry.getValue();

            // renderKind must be "chart" or "html"
            if (!"chart".equals(p.renderKind()) && !"html".equals(p.renderKind())) {
                throw new IllegalStateException(
                    "Pattern '%s' has invalid renderKind '%s'; must be 'chart' or 'html'"
                        .formatted(patternId, p.renderKind()));
            }

            // supportedChartTypes must be subset of catalog chartTypes
            if (p.supportedChartTypes() != null) {
                for (String ct : p.supportedChartTypes()) {
                    if (!knownChartTypes.contains(ct)) {
                        throw new IllegalStateException(
                            "Pattern '%s' references unknown chartType '%s'".formatted(patternId, ct));
                    }
                }
            }

            // defaultChartType must be in supportedChartTypes
            if (p.defaultChartType() != null) {
                if (p.supportedChartTypes() == null || !p.supportedChartTypes().contains(p.defaultChartType())) {
                    throw new IllegalStateException(
                        "Pattern '%s' defaultChartType '%s' not in supportedChartTypes %s"
                            .formatted(patternId, p.defaultChartType(), p.supportedChartTypes()));
                }
            }

            // Color scheme consistency for chart renderKind
            if ("chart".equals(p.renderKind())) {
                if (p.supportedColorSchemes() != null) {
                    for (String cs : p.supportedColorSchemes()) {
                        if (!knownColorSchemes.contains(cs)) {
                            throw new IllegalStateException(
                                "Pattern '%s' supportedColorSchemes contains unknown '%s'"
                                    .formatted(patternId, cs));
                        }
                    }
                }
                if (p.defaultColorScheme() != null) {
                    if (!knownColorSchemes.contains(p.defaultColorScheme())) {
                        throw new IllegalStateException(
                            "Pattern '%s' defaultColorScheme '%s' not in colorSchemes %s"
                                .formatted(patternId, p.defaultColorScheme(), catalog.colorSchemes()));
                    }
                    if (p.supportedColorSchemes() != null
                        && !p.supportedColorSchemes().contains(p.defaultColorScheme())) {
                        throw new IllegalStateException(
                            "Pattern '%s' defaultColorScheme '%s' not in supportedColorSchemes %s"
                                .formatted(patternId, p.defaultColorScheme(), p.supportedColorSchemes()));
                    }
                }
            }
        }

        // Slot IDs must be unique within each template
        for (TemplateDef tpl : catalog.templates()) {
            Set<String> slotIds = new HashSet<>();
            for (SlotDef slot : tpl.slots()) {
                if (!slotIds.add(slot.id())) {
                    throw new IllegalStateException(
                        "Template '%s' has duplicate slot id '%s'".formatted(tpl.id(), slot.id()));
                }
            }
        }
    }

    // ---- Accessors ----

    public Catalog catalog() {
        return catalog;
    }

    public TemplateDef getTemplate(String templateId) {
        return catalog.templates().stream()
            .filter(t -> t.id().equals(templateId))
            .findFirst()
            .orElse(null);
    }

    public PatternDef getPattern(String patternId) {
        return catalog.patterns().get(patternId);
    }

    public ChartTypeDef getChartType(String chartType) {
        return catalog.chartTypes().get(chartType);
    }

    public ThemeMapping getTheme(String themeSlug) {
        return catalog.themes().stream()
            .filter(t -> t.slug().equals(themeSlug))
            .findFirst()
            .orElse(null);
    }

    /**
     * Resolve the CSS filename for a theme slug.
     *
     * <p>Looks up the {@code themes} array in the catalog and returns the {@code css} field
     * (e.g. "styles/ecommerce.css") for the matching slug. Returns {@code null} if the slug
     * is not found.</p>
     *
     * @param themeSlug e.g. "ecommerce"
     * @return CSS path relative to classpath (e.g. "styles/ecommerce.css"), or null
     */
    public String resolveThemeCss(String themeSlug) {
        ThemeMapping tm = getTheme(themeSlug);
        return tm != null ? tm.css() : null;
    }

    // ---- Exceptions ----

    public static final class PatternCatalogNotFoundException extends RuntimeException {
        public PatternCatalogNotFoundException(String path) {
            super("Pattern catalog not found on classpath: " + path);
        }
    }

    public static final class PatternCatalogLoadException extends RuntimeException {
        public PatternCatalogLoadException(String path, Throwable cause) {
            super("Failed to load pattern catalog from classpath: " + path, cause);
        }
    }
}
