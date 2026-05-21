package com.datatalk.application.registry;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.action.ActionDescriptor;
import com.datatalk.domain.action.ActionHandler;
import com.datatalk.domain.action.Category;
import com.datatalk.domain.action.DataTalkAction;
import com.datatalk.domain.action.RiskLevel;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActionRegistry implements InitializingBean {

    private final ApplicationContext ctx;
    private final Translator translator;
    private final Map<String, RegisteredAction> actionsById = new LinkedHashMap<>();
    private final Map<String, ActionHandler<?, ?>> handlersById = new LinkedHashMap<>();

    public ActionRegistry(ApplicationContext ctx, Translator translator) {
        this.ctx = ctx;
        this.translator = translator;
    }

    @Override
    public void afterPropertiesSet() {
        Map<String, Object> beans = ctx.getBeansWithAnnotation(DataTalkAction.class);
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            DataTalkAction meta = bean.getClass().getAnnotation(DataTalkAction.class);
            if (meta == null) {
                meta = org.springframework.core.annotation.AnnotationUtils.findAnnotation(bean.getClass(), DataTalkAction.class);
            }
            if (meta == null) {
                throw new IllegalStateException("Bean " + entry.getKey() + " expected @DataTalkAction");
            }
            if (!(bean instanceof ActionHandler<?, ?> handler)) {
                throw new IllegalStateException("Bean " + entry.getKey() + " annotated @DataTalkAction but does not implement ActionHandler");
            }
            if (actionsById.put(meta.id(), new RegisteredAction(meta, handler)) != null) {
                throw new IllegalStateException("Duplicate action id: " + meta.id());
            }
            handlersById.put(meta.id(), handler);
        }
    }

    private ActionDescriptor buildDescriptor(RegisteredAction registeredAction) {
        DataTalkAction meta = registeredAction.meta();
        ActionHandler<?, ?> handler = registeredAction.handler();
        RiskLevel riskLevel = meta.riskLevel().length > 0 ? meta.riskLevel()[0] : null;
        Category category = meta.category().length > 0 ? meta.category()[0] : null;
        return new ActionDescriptor(
            meta.id(),
            meta.executor(),
            translator.getOrDefault(meta.description(), meta.description()),
            handler.inputSchema(),
            handler.outputSchema(),
            Arrays.asList(meta.produces()),
            List.copyOf(handler.sideEffects()),
            meta.requiresConnection(),
            meta.timeoutMs(),
            riskLevel,
            category,
            meta.exposeToMcp()
        );
    }

    public Collection<ActionDescriptor> all() {
        return Collections.unmodifiableList(actionsById.values().stream()
            .map(this::buildDescriptor)
            .toList());
    }

    public Collection<ActionDescriptor> mcpExposed() {
        return Collections.unmodifiableList(actionsById.values().stream()
            .map(this::buildDescriptor)
            .filter(ActionDescriptor::exposeToMcp)
            .toList());
    }

    public ActionDescriptor require(String id) {
        RegisteredAction action = actionsById.get(id);
        if (action == null) {
            throw new IllegalArgumentException(translator.get("error.action.unknown", id));
        }
        return buildDescriptor(action);
    }

    public ActionHandler<?, ?> handler(String id) {
        ActionHandler<?, ?> h = handlersById.get(id);
        if (h == null) {
            throw new IllegalArgumentException(translator.get("error.action.unknown", id));
        }
        return h;
    }

    private record RegisteredAction(DataTalkAction meta, ActionHandler<?, ?> handler) {}
}
