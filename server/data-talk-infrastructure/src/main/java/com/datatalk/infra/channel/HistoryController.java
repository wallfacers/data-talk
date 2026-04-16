package com.datatalk.infra.channel;

import com.datatalk.application.channel.HistoryService;
import com.datatalk.application.persistence.ArtifactRecord;
import com.datatalk.domain.part.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
public class HistoryController {

    private final HistoryService svc;

    public HistoryController(HistoryService svc) { this.svc = svc; }

    @GetMapping("/messages")
    public Map<String, List<Message>> messages(@PathVariable String sessionId) {
        return Map.of("messages", svc.getMessages(sessionId));
    }

    @GetMapping("/artifacts")
    public Map<String, List<ArtifactRecord>> artifacts(@PathVariable String sessionId) {
        return Map.of("artifacts", svc.getArtifacts(sessionId));
    }
}
