package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.beans.Introspector;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Scans the packages declared on {@link EnableEmbedJpaRepositories} for repository interfaces
 * and registers one {@link EmbedJpaRepositoryFactoryBean} per interface — the same shape
 * {@code JpaRepositoryRegistrar} produces for {@code @EnableJpaRepositories}. The routed
 * {@code EmbedJNoSQL} bean is looked up by name at instantiation time so the generated
 * repositories inherit Spring-transaction routing like every other bean built on the database.
 */
public class EmbedJpaRepositoryRegistrar
        implements ImportBeanDefinitionRegistrar, ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(
                        EnableEmbedJpaRepositories.class.getName(), false));
        if (attributes == null) {
            return;
        }

        Set<String> basePackages = new LinkedHashSet<>();
        for (String pkg : attributes.getStringArray("basePackages")) {
            if (StringUtils.hasText(pkg)) {
                basePackages.add(pkg.trim());
            }
        }
        if (basePackages.isEmpty()) {
            basePackages.add(ClassUtils.getPackageName(importingClassMetadata.getClassName()));
        }

        Set<Class<?>> interfaces = scanRepositoryInterfaces(basePackages);
        for (Class<?> repositoryInterface : interfaces) {
            String beanName = Introspector.decapitalize(repositoryInterface.getSimpleName());
            if (registry.containsBeanDefinition(beanName)) {
                continue; // user-defined bean wins, exactly like the auto-registrar
            }
            BeanDefinitionBuilder builder = BeanDefinitionBuilder
                    .rootBeanDefinition(EmbedJpaRepositoryFactoryBean.class);
            builder.addConstructorArgValue(repositoryInterface.getName());
            builder.addConstructorArgReference(EmbedJNoSQLDatabaseHolder.BEAN_NAME);
            builder.setLazyInit(true);
            registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        }
    }

    private Set<Class<?>> scanRepositoryInterfaces(Set<String> basePackages) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        // independent top-level or static-nested interfaces only
                        return beanDefinition.getMetadata().isInterface()
                                && beanDefinition.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));

        ClassLoader classLoader = resourceLoader != null
                ? resourceLoader.getClassLoader()
                : EmbedJpaRepositoryRegistrar.class.getClassLoader();

        Set<Class<?>> found = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                try {
                    Class<?> clazz = ClassUtils.forName(candidate.getBeanClassName(), classLoader);
                    if (clazz.isInterface() && Repository.class.isAssignableFrom(clazz)) {
                        found.add(clazz);
                    }
                } catch (ClassNotFoundException ignored) {
                    // a candidate that cannot load is not ours to fail on
                }
            }
        }
        return found;
    }

    /** Well-known name of the (routed) database bean the factory bean resolves lazily. */
    static final class EmbedJNoSQLDatabaseHolder {
        static final String BEAN_NAME = "embedJNoSQL";
    }
}
