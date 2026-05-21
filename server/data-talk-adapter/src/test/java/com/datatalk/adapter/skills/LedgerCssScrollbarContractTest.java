package com.datatalk.adapter.skills;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract for {@code skills/ledger/assets/styles/ledger.css}. The ledger report HTML is
 * rendered inside a sandboxed iframe whose document does not inherit host CSS, so the
 * stylesheet itself must hide the WebKit scrollbar arrow buttons — otherwise the iframe
 * falls back to the platform default and the report viewer regrows the up/down triangles
 * BUG-0074 was filed to remove.
 */
class LedgerCssScrollbarContractTest {

    @Test
    void ledger_css_hides_webkit_scrollbar_arrow_buttons() throws IOException {
        String css = new ClassPathResource("skills/ledger/assets/styles/ledger.css")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(css)
                .as("must declare a ::-webkit-scrollbar-button rule")
                .containsPattern("::-webkit-scrollbar-button\\s*\\{[^}]*display:\\s*none");
        assertThat(css).contains("::-webkit-scrollbar-thumb");
        assertThat(css).contains("::-webkit-scrollbar-track");
    }
}
