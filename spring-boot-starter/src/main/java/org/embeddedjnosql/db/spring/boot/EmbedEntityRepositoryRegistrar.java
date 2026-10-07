package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.springframework.core.ResolvableType;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import java.beans.Introspector;
import java.util.List;

/**
 * Registers an {@link EmbedRepository} bean for every entity class found in the
 * auto-configuration packages — the "Spring Data repositories without the interfaces" piece of
 * the H2-style experience: annotate a class with {@code @Entity} (any of the three supported
 * flavors), and a typed repository is injectable immediately, no manual bean, no marker
 * interface, no {@code @EnableJpaRepositories}-style annotation.
 *
 * <p>Bean naming follows the familiar decapitalized convention:
 * {@code Product} → {@code productRepository}. User-defined beans of the same name always win
 * (the registrar skips collisions), and repositories resolve their collection per operation so
 * they participate in Spring transactions through the routed database bean.</p>
 *
 * <p>Each definition carries a fully-parameterized target type
 * ({@code EmbedRepository<Product, String>}) so plain by-type injection — the only way anyone
 * uses a repository — resolves the generics, exactly like an interface-annotated Spring Data
 * repository bean. Without it a supplier-based definition is raw and injects by name only.</p>
 */
public class EmbedEntityRepositoryRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata,
                                        BeanDefinitionRegistry registry) {
        List<String> packages;
        try {
            packages = AutoConfigurationPackages.get(this.beanFactory);
        } catch (IllegalStateException e) {
            // no auto-configuration package registered (plain runner): nothing to scan
            return;
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null && beanFactory instanceof org.springframework.beans.factory.config.ConfigurableBeanFactory cbf) {
            classLoader = cbf.getBeanClassLoader();
        }
        if (classLoader == null) {
            classLoader = EmbedEntityRepositoryRegistrar.class.getClassLoader();
        }

        for (Class<?> entity : EmbedEntityScanner.scan(packages, classLoader)) {
            String beanName = Introspector.decapitalize(entity.getSimpleName()) + "Repository";
            if (registry.containsBeanDefinition(beanName)) {
                continue; // user-defined repository beans win
            }
            RootBeanDefinition definition = new RootBeanDefinition(EmbedRepository.class,
                    () -> EmbedRepository.of(entity, beanFactory.getBean(EmbedJNoSQL.class)));
            // supplier-based definitions are otherwise raw: by-type injection of
            // EmbedRepository<Entity, IdType> would fail with NoSuchBeanDefinitionException
            definition.setTargetType(ResolvableType.forClassWithGenerics(
                    EmbedRepository.class, entity, EntityMapper.getIdFieldType(entity)));
            registry.registerBeanDefinition(beanName, definition);
        }
    }
}
