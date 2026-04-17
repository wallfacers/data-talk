package com.datatalk.application.opencode;

import com.datatalk.application.persistence.SessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 把 OpenCode 推送的 session title 同步到本地 SessionRepository。
 * 仅在 {@code title_locked=0} 时覆盖；外层调用方（translator）不应感知失败 —
 * 这里捕获并记录所有异常，让事件翻译链路继续运行。
 */
@Component
public class SessionTitleSyncer {

    private static final Logger log = LoggerFactory.getLogger(SessionTitleSyncer.class);

    private final SessionRepository repo;
    private final OpenCodeSessionMap sessionMap;
    private final Clock clock;

    public SessionTitleSyncer(SessionRepository repo, OpenCodeSessionMap sessionMap, Clock clock) {
        this.repo = repo;
        this.sessionMap = sessionMap;
        this.clock = clock;
    }

    /** 经 OpenCodeSessionMap 查 dtSessionId，若能映射则尝试 applyAutoTitle；title_locked=1 时 DB 层自行跳过。 */
    public void apply(String ocSessionId, String newTitle) {
        if (ocSessionId == null || newTitle == null || newTitle.isBlank()) return;
        String dtSessionId = sessionMap.dataTalkFor(ocSessionId);
        if (dtSessionId == null) return;
        try {
            repo.applyAutoTitle(dtSessionId, newTitle, clock.millis());
        } catch (RuntimeException e) {
            log.warn("applyAutoTitle failed for session={} title='{}'", dtSessionId, newTitle, e);
        }
    }
}