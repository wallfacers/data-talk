package com.datatalk.domain.er;

import java.util.List;

/**
 * Complete graph emitted by ErRelationDiscoveryService.
 *
 * @param nodes    tables in the inspector view
 * @param edges    all FK edges among nodes
 * @param summary  short English line for AI context
 * @param warnings structured warnings such as truncation notices
 */
public record ErGraph(
    List<ErTableMeta> nodes,
    List<ErRelation> edges,
    String summary,
    List<String> warnings
) {}
