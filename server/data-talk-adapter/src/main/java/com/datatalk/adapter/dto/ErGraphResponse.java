package com.datatalk.adapter.dto;

import com.datatalk.domain.er.ErGraph;
import com.datatalk.domain.er.ErRelation;
import com.datatalk.domain.er.ErTableMeta;

import java.util.List;

public record ErGraphResponse(
    List<ErTableMeta> nodes,
    List<ErRelation> edges,
    String summary,
    List<String> warnings
) {
    public static ErGraphResponse from(ErGraph graph) {
        return new ErGraphResponse(graph.nodes(), graph.edges(), graph.summary(), graph.warnings());
    }
}
