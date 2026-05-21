package com.datatalk.infra.discovery;

import com.datatalk.application.registry.ActionRegistry;
import com.datatalk.application.registry.OntologyRegistry;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DiscoveryController {

    private final ActionRegistry actions;
    private final OntologyRegistry ontology;

    public DiscoveryController(ActionRegistry actions, OntologyRegistry ontology) {
        this.actions = actions;
        this.ontology = ontology;
    }

    @GetMapping("/actions")
    public Map<String, Collection<ActionDescriptor>> listActions() {
        return Map.of("actions", actions.all());
    }

    @GetMapping("/actions/{id}")
    public ActionDescriptor getAction(@PathVariable String id) {
        try {
            return actions.require(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/ontology")
    public Map<String, Collection<ObjectTypeDescriptor>> listOntology() {
        return Map.of("objects", ontology.all());
    }

    @GetMapping("/ontology/{id}")
    public ObjectTypeDescriptor getObjectType(@PathVariable String id) {
        try {
            return ontology.require(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping(value = "/actions.schema.json", produces = "application/json")
    public ResponseEntity<Map<String, Object>> mergedSchema() {
        return ResponseEntity.ok(Map.of(
                "$schema", "https://json-schema.org/draft/2020-12/schema",
                "actions", actions.all()
        ));
    }
}
