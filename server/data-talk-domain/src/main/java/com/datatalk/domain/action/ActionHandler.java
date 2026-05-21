package com.datatalk.domain.action;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface ActionHandler<I, O> {
    Map<String, Object> inputSchema();
    Map<String, Object> outputSchema();
    List<OntologyEffect> sideEffects();
    Class<I> inputType();
    CompletionStage<O> handle(ActionContext ctx, I input);
}
