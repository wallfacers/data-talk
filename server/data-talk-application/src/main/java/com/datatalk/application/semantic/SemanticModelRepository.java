package com.datatalk.application.semantic;

import com.datatalk.domain.semantic.PatchOp;
import com.datatalk.domain.semantic.SemanticModel;
import com.datatalk.domain.semantic.VerifiedQuery;

import java.util.List;
import java.util.Optional;

public interface SemanticModelRepository {

    List<String> listDomains(String connectionId);

    Optional<SemanticModel> loadDomain(String connectionId, String name);

    void saveDomain(String connectionId, SemanticModel model, String yamlText);

    void appendPatch(String connectionId, String domain, PatchOp patch);

    List<PatchOp> listPatches(String connectionId, String domain);

    void compactPatches(String connectionId, String domain, SemanticModel model);

    List<VerifiedQuery> listVerifiedQueries(String connectionId, int topK);

    String recordVerifiedQuery(String connectionId, VerifiedQuery vq);

    void incHit(String connectionId, String vqId);

    List<String> listPending(String connectionId);

    String savePending(String connectionId, String domain, String yamlText);

    void acceptPending(String connectionId, String domain);

    void rejectPending(String connectionId, String domain);

    int countVerifiedQueriesByConnection(String connectionId);

    void markStale(String connectionId, String vqId);

    void moveToTrash(String connectionId, long ts);

    void deleteAllByConnection(String connectionId);
}
