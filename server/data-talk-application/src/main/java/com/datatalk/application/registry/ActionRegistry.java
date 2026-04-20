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
    private final Map<String, ActionDescriptor> descriptorsById = new LinkedHashMap<>();
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
            ActionDescriptor desc = buildDescriptor(meta, handler);
            if (descriptorsById.put(desc.id(), desc) != null) {
                throw new IllegalStateException("Duplicate action id: " + desc.id());
            }
            handlersById.put(desc.id(), handler);
        }
    }

    private ActionDescriptor buildDescriptor(DataTalkAction meta, ActionHandler<?, ?> handler) {
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
            category
        );
    }

    public Collection<ActionDescriptor> all() {
        return Collections.unmodifiableCollection(descriptorsById.values());
    }

    public ActionDescriptor require(String id) {
        ActionDescriptor d = descriptorsById.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown action: " + id);
        return d;
    }

    public ActionHandler<?, ?> handler(String id) {
        ActionHandler<?, ?> h = handlersById.get(id);
        if (h == null) throw new IllegalArgumentException("Unknown action: " + id);
        return h;
    }
}
