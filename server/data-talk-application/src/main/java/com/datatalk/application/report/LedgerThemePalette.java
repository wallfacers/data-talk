package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.regex.Pattern;

/**
 * 把 report.json {@code theme} 解析为完整的 ledger 色彩角色 token。
 *
 * <p>三角色：{@code primary}（专业主色）/ {@code accent}（创新强调）/ {@code surface}（干净背景层），
 * 外加由 accent 同色阶派生的 {@code tint-1..4} 与固定语义数据色（positive/negative/neutral）。
 *
 * <p>解析规则（确定性、纯函数）：
 * <ul>
 *   <li>用户未提供 {@code theme}（无 accent）→ 整套默认 token（深蓝系，{@link #DEFAULT_PRIMARY} 等）。
 *       默认 primary/surface 是独立挑选的设计色，<b>不</b>由默认 accent 跑派生公式得出。</li>
 *   <li>用户只给 {@code accent} → primary/surface 由该 accent 派生（{@code mix(accent,#000,.30)} /
 *       {@code mix(accent,#FFF,.94)}），保证任意自定义 accent 都有协调的角色色。</li>
 *   <li>用户显式给某角色 → 透传该角色值。</li>
 *   <li>{@code tints} 恒从 accent 派生 4 档（{@link #TINT_STEPS}）；用户提供时取前 4 个、不足由派生补齐。</li>
 * </ul>
 *
 * <p>{@code mix(a, b, r)} 逐通道 {@code round(a*(1-r)+b*r)}，{@code r} 是第二色 {@code b} 的权重。
 */
public final class LedgerThemePalette {

    public static final String DEFAULT_PRIMARY = "#0F2A4A";
    public static final String DEFAULT_ACCENT = "#2F6FBF";
    public static final String DEFAULT_SURFACE = "#F4F7FB";
    public static final String DEFAULT_POSITIVE = "#1F7A4E";
    public static final String DEFAULT_NEGATIVE = "#B33A3A";
    public static final String DEFAULT_NEUTRAL = "#5E5E5B";

    /** tint-1..4 由 accent 向白派生的混合比例（由浅到深）。 */
    public static final double[] TINT_STEPS = {0.85, 0.70, 0.45, 0.15};

    private static final String BLACK = "#000000";
    private static final String WHITE = "#FFFFFF";
    private static final Pattern HEX6 = Pattern.compile("^#[0-9a-fA-F]{6}$");

    private final String primary;
    private final String accent;
    private final String surface;
    private final String positive;
    private final String negative;
    private final String neutral;
    private final String[] tints; // length 4, tint-1..4

    private LedgerThemePalette(String primary, String accent, String surface,
                               String positive, String negative, String neutral, String[] tints) {
        this.primary = primary;
        this.accent = accent;
        this.surface = surface;
        this.positive = positive;
        this.negative = negative;
        this.neutral = neutral;
        this.tints = tints;
    }

    /** 由 theme 节点（可能为 missing/null/object）解析完整调色板。 */
    public static LedgerThemePalette from(JsonNode theme) {
        JsonNode t = theme == null ? null : theme;
        boolean accentGiven = t != null && isHex(t.path("accent").asText(""));
        String accent = accentGiven ? normalize(t.path("accent").asText("")) : DEFAULT_ACCENT;

        String primary;
        if (t != null && isHex(t.path("primary").asText(""))) {
            primary = normalize(t.path("primary").asText(""));
        } else if (accentGiven) {
            primary = mix(accent, BLACK, 0.30);
        } else {
            primary = DEFAULT_PRIMARY;
        }

        String surface;
        if (t != null && isHex(t.path("surface").asText(""))) {
            surface = normalize(t.path("surface").asText(""));
        } else if (accentGiven) {
            surface = mix(accent, WHITE, 0.94);
        } else {
            surface = DEFAULT_SURFACE;
        }

        String[] tints = new String[4];
        JsonNode tintsNode = t == null ? null : t.path("tints");
        for (int i = 0; i < 4; i++) {
            String given = null;
            if (tintsNode != null && tintsNode.isArray() && i < tintsNode.size()
                    && isHex(tintsNode.get(i).asText(""))) {
                given = normalize(tintsNode.get(i).asText(""));
            }
            tints[i] = given != null ? given : mix(accent, WHITE, TINT_STEPS[i]);
        }

        return new LedgerThemePalette(primary, accent, surface,
                DEFAULT_POSITIVE, DEFAULT_NEGATIVE, DEFAULT_NEUTRAL, tints);
    }

    public String primary() { return primary; }
    public String accent() { return accent; }
    public String surface() { return surface; }
    public String positive() { return positive; }
    public String negative() { return negative; }
    public String neutral() { return neutral; }

    /** tint-n（n ∈ 1..4）。 */
    public String tint(int n) {
        return tints[Math.max(1, Math.min(4, n)) - 1];
    }

    /**
     * ECharts 8 色调色板：{@code [accent, primary, tint-2, tint-3, positive, negative, neutral, tint-1]}。
     * accent 打头作主系列色。
     */
    public String[] echartsPalette() {
        return new String[]{accent, primary, tints[1], tints[2], positive, negative, neutral, tints[0]};
    }

    /**
     * 产出注入 {@code <style>} 的 {@code :root} CSS 变量声明体（不含外层 {@code :root{}}）。
     * 覆盖 ledger.css 的同名默认变量。
     */
    public String toCssVars() {
        return "--ledger-primary:" + primary + ";"
                + "--ledger-accent:" + accent + ";"
                + "--ledger-surface:" + surface + ";"
                + "--ledger-tint-1:" + tints[0] + ";"
                + "--ledger-tint-2:" + tints[1] + ";"
                + "--ledger-tint-3:" + tints[2] + ";"
                + "--ledger-tint-4:" + tints[3] + ";"
                + "--ledger-positive:" + positive + ";"
                + "--ledger-negative:" + negative + ";"
                + "--ledger-neutral:" + neutral + ";";
    }

    /** 逐通道 sRGB 线性混合：{@code round(a*(1-r)+b*r)}，r 为第二色 b 的权重。 */
    public static String mix(String aHex, String bHex, double r) {
        int[] a = parse(aHex);
        int[] b = parse(bHex);
        int[] o = new int[3];
        for (int i = 0; i < 3; i++) {
            o[i] = (int) Math.round(a[i] * (1.0 - r) + b[i] * r);
            o[i] = Math.max(0, Math.min(255, o[i]));
        }
        return String.format("#%02X%02X%02X", o[0], o[1], o[2]);
    }

    static boolean isHex(String s) {
        return s != null && HEX6.matcher(s).matches();
    }

    /** 合法 hex 统一为大写 #RRGGBB。 */
    private static String normalize(String s) {
        return "#" + s.substring(1).toUpperCase();
    }

    private static int[] parse(String hex) {
        return new int[]{
                Integer.parseInt(hex.substring(1, 3), 16),
                Integer.parseInt(hex.substring(3, 5), 16),
                Integer.parseInt(hex.substring(5, 7), 16)
        };
    }
}
