package com.datatalk.application.registry;

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
    private final Map<String, ObjectType> typesById = new LinkedHashMap<>();
    private final Map<String, ObjectTypeDescriptor> descriptorsById = new LinkedHashMap<>();

    public OntologyRegistry(ApplicationContext ctx) {
        this.ctx = ctx;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void afterPropertiesSet() {
        Map<String, ObjectType> beans = ctx.getBeansOfType(ObjectType.class);
        for (ObjectType t : beans.values()) {
            if (typesById.put(t.id(), t) != null) {
                throw new IllegalStateException("Duplicate ObjectType id: " + t.id());
            }
            descriptorsById.put(t.id(), t.toDescriptor());
        }
    }

    public Collection<ObjectTypeDescriptor> all() {
        return Collections.unmodifiableCollection(descriptorsById.values());
    }

    public ObjectTypeDescriptor require(String id) {
        ObjectTypeDescriptor d = descriptorsById.get(id);
        if (d == null) throw new IllegalArgumentException("Unknown object type: " + id);
        return d;
    }

    public ObjectType type(String id) {
        ObjectType t = typesById.get(id);
        if (t == null) throw new IllegalArgumentException("Unknown object type: " + id);
        return t;
    }
}
