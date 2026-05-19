package com.datatalk.application.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Resolves the local file-system location of the unpacked ledger skill assets.
 *
 * <p>The skill itself is bundled in {@code classpath:/skills/ledger/**} and synced to
 * {@code <opencodeCwd>/.opencode/skills/ledger/} at startup by
 * {@code SkillResourceSyncer.syncSkill("ledger", opencodeCwd)} in adapter layer
 * ({@code OpenCodeGatewayBeans}). This bean is application-layer, holds only paths;
 * actual extraction is owned by adapter.
 *
 * <p>Used by {@link ReportRenderer} to inline {@code ledger.css}, fonts, and ECharts
 * references when producing self-contained HTML.
 */
@Component
public class LedgerSkillResolver {

    private static final Logger log = LoggerFactory.getLogger(LedgerSkillResolver.class);
    private static final String DEFAULT_OPENCODE_CWD = ".data-talk/opencode-cwd";

    private final Path skillRoot;

    public LedgerSkillResolver(@Value("${datatalk.opencode.cwd:}") String opencodeCwdProp) {
        Path opencodeCwd;
        if (opencodeCwdProp == null || opencodeCwdProp.isBlank()) {
            opencodeCwd = Paths.get(System.getProperty("user.home"), DEFAULT_OPENCODE_CWD);
        } else {
            opencodeCwd = Paths.get(opencodeCwdProp);
        }
        this.skillRoot = opencodeCwd.resolve(".opencode").resolve("skills").resolve("ledger").toAbsolutePath();
        log.info("LedgerSkillResolver root resolved to {}", this.skillRoot);
    }

    /** 同步后的 ledger skill 根目录。 */
    public Path skillRoot() {
        return skillRoot;
    }

    public Path stylesheet() {
        return skillRoot.resolve("assets").resolve("styles").resolve("ledger.css");
    }

    public Path serifFont() {
        return skillRoot.resolve("assets").resolve("fonts").resolve("NotoSerifSC-Regular.otf");
    }

    public Path sansFont() {
        return skillRoot.resolve("assets").resolve("fonts").resolve("NotoSansSC-Regular.otf");
    }

    public boolean fontsReady() {
        return Files.isRegularFile(serifFont()) && Files.isRegularFile(sansFont());
    }

    public boolean skillReady() {
        return Files.isRegularFile(skillRoot.resolve("SKILL.md"));
    }
}
