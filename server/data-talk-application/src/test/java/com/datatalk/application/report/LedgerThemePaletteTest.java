package com.datatalk.application.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link LedgerThemePalette} 纯函数派生：默认 token、仅 accent 派生、显式透传、tints 处理、mix 公式。 */
class LedgerThemePaletteTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode theme(String json) throws Exception {
        return mapper.readTree(json);
    }

    @Test
    void mix_formula_is_round_per_channel_with_b_weight() {
        // r 是第二色权重：mix(accent,#000,.30)=70% accent，design.md 校验例
        assertThat(LedgerThemePalette.mix("#2F6FBF", "#000000", 0.30)).isEqualTo("#214E86");
        // 全 a / 全 b 边界
        assertThat(LedgerThemePalette.mix("#2F6FBF", "#FFFFFF", 0.0)).isEqualTo("#2F6FBF");
        assertThat(LedgerThemePalette.mix("#2F6FBF", "#FFFFFF", 1.0)).isEqualTo("#FFFFFF");
    }

    @Test
    void no_theme_yields_default_design_tokens_not_derived() {
        // 缺省路径：primary/surface 用独立设计 token，不是从默认 accent 派生
        LedgerThemePalette p = LedgerThemePalette.from(MissingNode.getInstance());
        assertThat(p.primary()).isEqualTo("#0F2A4A");
        assertThat(p.accent()).isEqualTo("#2F6FBF");
        assertThat(p.surface()).isEqualTo("#F4F7FB");
        assertThat(p.positive()).isEqualTo("#1F7A4E");
        assertThat(p.negative()).isEqualTo("#B33A3A");
        assertThat(p.neutral()).isEqualTo("#5E5E5B");
        // tints 恒从 accent 派生
        assertThat(p.tint(1)).isEqualTo("#E0E9F5");
        assertThat(p.tint(2)).isEqualTo("#C1D4EC");
        assertThat(p.tint(3)).isEqualTo("#8DB0DC");
        assertThat(p.tint(4)).isEqualTo("#4E85C9");
    }

    @Test
    void only_accent_derives_primary_and_surface() throws Exception {
        LedgerThemePalette p = LedgerThemePalette.from(theme("{ \"accent\": \"#2F6FBF\" }"));
        // 自定义 accent（即便等于默认值）走派生公式，而非默认设计 token
        assertThat(p.primary()).isEqualTo("#214E86"); // mix(accent,#000,.30)
        assertThat(p.surface()).isEqualTo("#F3F6FB"); // mix(accent,#FFF,.94)
        assertThat(p.accent()).isEqualTo("#2F6FBF");
    }

    @Test
    void explicit_roles_pass_through() throws Exception {
        LedgerThemePalette p = LedgerThemePalette.from(theme("""
            { "primary": "#102030", "accent": "#445566", "surface": "#FAFBFC" }
        """));
        assertThat(p.primary()).isEqualTo("#102030");
        assertThat(p.accent()).isEqualTo("#445566");
        assertThat(p.surface()).isEqualTo("#FAFBFC");
    }

    @Test
    void user_tints_take_first_four_and_derive_the_rest() throws Exception {
        LedgerThemePalette p = LedgerThemePalette.from(theme("""
            { "accent": "#2F6FBF", "tints": ["#111111", "#222222"] }
        """));
        assertThat(p.tint(1)).isEqualTo("#111111");
        assertThat(p.tint(2)).isEqualTo("#222222");
        // 缺位 tint-3/4 由 accent 按 step 派生补齐
        assertThat(p.tint(3)).isEqualTo("#8DB0DC");
        assertThat(p.tint(4)).isEqualTo("#4E85C9");
    }

    @Test
    void deterministic_same_input_same_output() throws Exception {
        JsonNode t = theme("{ \"accent\": \"#3A7BD5\" }");
        assertThat(LedgerThemePalette.from(t).toCssVars())
                .isEqualTo(LedgerThemePalette.from(t).toCssVars());
    }

    @Test
    void invalid_hex_falls_back_to_default_accent() throws Exception {
        LedgerThemePalette p = LedgerThemePalette.from(theme("{ \"accent\": \"blueish\" }"));
        // 非法 accent → 视作未提供 → 整套默认 token
        assertThat(p.accent()).isEqualTo("#2F6FBF");
        assertThat(p.primary()).isEqualTo("#0F2A4A");
    }

    @Test
    void echarts_palette_is_eight_literal_hexes_accent_first() {
        String[] palette = LedgerThemePalette.from(MissingNode.getInstance()).echartsPalette();
        assertThat(palette).hasSize(8);
        assertThat(palette[0]).isEqualTo("#2F6FBF"); // accent 打头
        assertThat(palette[1]).isEqualTo("#0F2A4A"); // primary
        assertThat(palette).allSatisfy(h -> assertThat(h).matches("^#[0-9A-F]{6}$"));
    }

    @Test
    void css_vars_emit_all_role_tokens() {
        String css = LedgerThemePalette.from(MissingNode.getInstance()).toCssVars();
        assertThat(css)
                .contains("--ledger-primary:#0F2A4A")
                .contains("--ledger-accent:#2F6FBF")
                .contains("--ledger-surface:#F4F7FB")
                .contains("--ledger-tint-1:#E0E9F5")
                .contains("--ledger-tint-4:#4E85C9")
                .contains("--ledger-positive:#1F7A4E")
                .contains("--ledger-negative:#B33A3A")
                .contains("--ledger-neutral:#5E5E5B");
    }
}
