package com.datatalk.application.registry;

import com.datatalk.application.i18n.Translator;
import com.datatalk.domain.ontology.ObjectType;
import com.datatalk.domain.ontology.ObjectTypeDescriptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = {OntologyRegistry.class, Translator.class, OntologyRegistryTest.TestTypes.class})
class OntologyRegistryTest {

    @Autowired
    OntologyRegistry registry;

    @Test
    void discoversAllObjectTypeBeans() {
        assertThat(registry.all())
                .extracting(ObjectTypeDescriptor::id)
                .containsExactlyInAnyOrder("test.foo", "test.bar");
    }

    @Test
    void throwsForUnknown() {
        assertThatThrownBy(() -> registry.require("test.missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Configuration
    static class TestTypes {
        @Bean
        ObjectType fooType() { return new FooType(); }

        @Bean
        ObjectType barType() { return new BarType(); }

        @Bean
        StaticMessageSource messageSource() {
            StaticMessageSource source = new StaticMessageSource();
            source.addMessage("error.object_type.unknown", Locale.ENGLISH, "Unknown object type: {0}");
            source.addMessage("error.object_type.unknown", Locale.SIMPLIFIED_CHINESE, "未知对象类型：{0}");
            return source;
        }
    }

    record FooValue(String id, String title) {}

    record BarValue(String id) {}

    static class FooType implements ObjectType {
        @Override public String id() { return "test.foo"; }
        @Override public String displayName() { return "Foo"; }
        @Override public Map<String, Object> propertySchema() { return Map.of("type", "object"); }
        @Override public List<String> primaryKey() { return List.of("id"); }
        @Override public Optional<String> titleField() { return Optional.of("title"); }
        @Override public String javaTypeName() { return FooValue.class.getName(); }
    }

    static class BarType implements ObjectType {
        @Override public String id() { return "test.bar"; }
        @Override public String displayName() { return "Bar"; }
        @Override public Map<String, Object> propertySchema() { return Map.of("type", "object"); }
        @Override public List<String> primaryKey() { return List.of("id"); }
        @Override public Optional<String> titleField() { return Optional.empty(); }
        @Override public String javaTypeName() { return BarValue.class.getName(); }
    }
}
