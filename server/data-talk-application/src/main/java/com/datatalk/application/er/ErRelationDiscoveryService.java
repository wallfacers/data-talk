package com.datatalk.application.er;

import com.datatalk.domain.er.ErGraph;

import java.util.List;

/**
 * Reads tables, columns, and FK relations for a connection.
 */
public interface ErRelationDiscoveryService {

    int MAX_TABLES = 100;

    /**
     * Discover an ER graph for seed tables.
     *
     * @param connectionId target connection id
     * @param tables seed table names
     * @param neighborDepth 0=strict; 1=direct FK neighbors; 2=two hops
     */
    ErGraph discover(String connectionId, List<String> tables, int neighborDepth);

    default ErGraph refresh(String connectionId, List<String> tables, int neighborDepth) {
        return discover(connectionId, tables, neighborDepth);
    }
}
