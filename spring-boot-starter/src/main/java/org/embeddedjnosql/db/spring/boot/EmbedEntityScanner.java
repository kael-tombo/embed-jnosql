package org.embeddedjnosql.db.spring.boot;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.util.ClassUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds entity classes in the auto-configuration packages — the scan Spring Data JPA performs
 * for {@code @Entity}, done here for the three entity annotations embed-jnosql maps:
 * {@code jakarta.persistence.Entity}, {@code jakarta.nosql.Entity}, and the built-in
 * {@code org.embeddedjnosql.db.adapter.jnosql.Entity}.
 *
 * <p>Detection is string-based on the class metadata (no annotation classes need to be on the
 * classpath for the scan to see them — mirrors how the core's {@code AnnotationResolver}
 * reads annotations reflectively by name).</p>
 */
final class EmbedEntityScanner {

    private static final Set<String> ENTITY_ANNOTATIONS = Set.of(
            "jakarta.persistence.Entity",
            "jakarta.nosql.Entity",
            "org.embeddedjnosql.db.adapter.jnosql.Entity"
    );

    private EmbedEntityScanner() {
    }

    static List<Class<?>> scan(List<String> basePackages, ClassLoader classLoader) {
        var provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(org.springframework.beans.factory.annotation.AnnotatedBeanDefinition beanDefinition) {
                // default implementation rejects plain concrete classes as independent
                // candidates; entities are concrete by definition
                return beanDefinition.getMetadata().isIndependent();
            }
        };
        provider.addIncludeFilter((MetadataReader reader, org.springframework.core.type.classreading.MetadataReaderFactory factory) -> {
            for (String annotation : ENTITY_ANNOTATIONS) {
                if (reader.getAnnotationMetadata().hasAnnotation(annotation)) {
                    return true;
                }
            }
            return false;
        });

        Set<BeanDefinition> candidates = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            candidates.addAll(provider.findCandidateComponents(basePackage));
        }

        List<Class<?>> entities = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = ClassUtils.forName(candidate.getBeanClassName(), classLoader);
                if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
                    entities.add(clazz);
                }
            } catch (ClassNotFoundException ignored) {
                // a candidate that cannot load is not ours to fail on
            }
        }
        return entities;
    }
}
