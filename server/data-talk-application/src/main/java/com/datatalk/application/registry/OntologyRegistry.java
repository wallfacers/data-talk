package com.datatalk.application.registry;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OntologyRegistry implements InitializingBean {

    private final ApplicationContext ctx;
    private final Translator translator;
    private final Map<String, ObjectType> typesById = new LinkedHashMap<>();

    public OntologyRegistry(ApplicationContext ctx, Translator translator) {
        this.ctx = ctx;
        this.translator = translator;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void afterPropertiesSet() {
        Map<String, ObjectType> beans = ctx.getBeansOfType(ObjectType.class);
        for (ObjectType t : beans.values()) {
            if (typesById.put(t.id(), t) != null) {
                throw new IllegalStateException("Duplicate ObjectType id: " + t.id());
            }
        }
    }

    public Collection<ObjectTypeDescriptor> all() {
        return Collections.unmodifiableList(typesById.values().stream()
            .map(ObjectType::toDescriptor)
            .toList());
    }

    public ObjectTypeDescriptor require(String id) {
        ObjectType type = typesById.get(id);
        if (type == null) {
            throw new IllegalArgumentException(translator.get("error.object_type.unknown", id));
        }
        return type.toDescriptor();
    }

    public ObjectType type(String id) {
        ObjectType t = typesById.get(id);
        if (t == null) {
            throw new IllegalArgumentException(translator.get("error.object_type.unknown", id));
        }
        return t;
    }
}
