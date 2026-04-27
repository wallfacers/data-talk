package com.datatalk.adapter.controller;

import com.datatalk.adapter.actions.UiFindAction;
import com.datatalk.application.stage.StageFindService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for the sidebar find/search endpoint.
 * Delegates to StageFindService via UiFindAction parse/serialize.
 */
@RestController
@RequestMapping("/api/stage/find")
public class StageFindController {

    private final StageFindService service;

    public StageFindController(StageFindService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> find(@RequestBody Map<String, Object> body) {
        var query = UiFindAction.parse(body);
        var result = service.execute(query);
        return UiFindAction.toEnvelope(result);
    }
}
