package com.datatalk.application.opencode;

import com.datatalk.application.persistence.SessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionTitleSyncerTest {

    private SessionRepository repo;
    private OpenCodeSessionMap sessionMap;
    private SessionTitleSyncer syncer;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(SessionRepository.class);
        sessionMap = new OpenCodeSessionMap();
        syncer = new SessionTitleSyncer(repo, sessionMap,
            Clock.fixed(Instant.ofEpochMilli(500L), ZoneOffset.UTC));
    }

    @Test
    void apply_writesWhenMapped() {
        sessionMap.bind("dt-1", "oc-1");
        syncer.apply("oc-1", "AI 标题");
        verify(repo).applyAutoTitle(eq("dt-1"), eq("AI 标题"), eq(500L));
    }

    @Test
    void apply_noOpWhenOcSessionNotMapped() {
        syncer.apply("oc-unknown", "AI 标题");
        verify(repo, never()).applyAutoTitle(Mockito.anyString(), Mockito.anyString(), anyLong());
    }

    @Test
    void tolerantOfRepositoryException() {
        sessionMap.bind("dt-1", "oc-1");
        when(repo.applyAutoTitle(Mockito.anyString(), Mockito.anyString(), anyLong()))
            .thenThrow(new RuntimeException("db down"));
        // 不应向外抛：syncer 容错 translator/event loop 才不被阻断
        syncer.apply("oc-1", "AI 标题");
        verify(repo).applyAutoTitle(eq("dt-1"), eq("AI 标题"), eq(500L));
    }
}