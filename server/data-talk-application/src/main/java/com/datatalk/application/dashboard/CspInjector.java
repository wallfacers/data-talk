package com.datatalk.application.dashboard;

/**
 * Generates CSP (Content-Security-Policy) meta tag content with placeholders.
 *
 * <p>The generated CSP string contains {@code __BEZEL_SERVER_ORIGIN__} as a placeholder
 * to be replaced at serve time with the actual origin. This allows the compiled HTML
 * artifact to be CSP-valid before origin is known.</p>
 */
public final class CspInjector {

    private static final String CSP_TEMPLATE = buildCspTemplate();

    private CspInjector() {}

    /**
     * Generate CSP content string for injection into the HTML meta tag.
     *
     * <p>Policy directives:</p>
     * <ul>
     *   <li>{@code default-src 'none'} — deny everything by default</li>
     *   <li>{@code script-src 'self' __BEZEL_SERVER_ORIGIN__} — own scripts + Bezel server</li>
     *   <li>{@code style-src 'unsafe-inline' 'self'} — inline styles needed for CSS variables</li>
     *   <li>{@code img-src 'self' data: __BEZEL_SERVER_ORIGIN__} — own images + data URIs</li>
     *   <li>{@code connect-src 'self' __BEZEL_SERVER_ORIGIN__ __BEZEL_SERVER_ORIGIN__/bezel/geo/}
     *       — XHR/fetch to self, Bezel server, and geo JSON endpoint for map charts</li>
     *   <li>{@code frame-src 'none'} — no iframes</li>
     *   <li>{@code object-src 'none'} — no plugins</li>
     *   <li>{@code base-uri 'none'} — no base tag manipulation</li>
     * </ul>
     *
     * @return CSP policy string with {@code __BEZEL_SERVER_ORIGIN__} placeholders
     */
    public static String generateCsp() {
        return CSP_TEMPLATE;
    }

    private static String buildCspTemplate() {
        StringBuilder sb = new StringBuilder();
        sb.append("default-src 'none'; ");
        sb.append("script-src 'self' __BEZEL_SERVER_ORIGIN__; ");
        sb.append("style-src 'unsafe-inline' 'self'; ");
        sb.append("img-src 'self' data: __BEZEL_SERVER_ORIGIN__; ");
        sb.append("connect-src 'self' __BEZEL_SERVER_ORIGIN__ __BEZEL_SERVER_ORIGIN__/bezel/geo/; ");
        sb.append("frame-src 'none'; ");
        sb.append("object-src 'none'; ");
        sb.append("base-uri 'none'");
        return sb.toString();
    }
}
