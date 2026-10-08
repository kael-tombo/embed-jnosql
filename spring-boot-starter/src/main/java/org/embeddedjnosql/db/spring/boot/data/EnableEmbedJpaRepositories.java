package org.embeddedjnosql.db.spring.boot.data;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

/**
 * Declares Spring Data repositories over the embed-jnosql document engine — the counterpart of
 * {@code org.springframework.data.jpa.repository.config.EnableJpaRepositories}, without a
 * JPA provider anywhere in the graph:
 *
 * <pre>
 * &#64;SpringBootApplication
 * &#64;EnableEmbedJpaRepositories
 * public class App {
 *
 *     &#64;Entity
 *     static class Order { ... }
 *
 *     interface OrderRepository extends JpaRepository&lt;Order, String&gt; { }
 * }
 * </pre>
 *
 * <p>Every interface under {@link #basePackages() the configured packages} that extends
 * {@link org.springframework.data.repository.CrudRepository}, {@code JpaRepository}, or
 * {@link Repository} becomes an injectable bean whose queries (derived names, paging, sorting,
 * query-by-example) run against the document store through the transaction-routed database
 * bean.</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(EmbedJpaRepositoryRegistrar.class)
public @interface EnableEmbedJpaRepositories {

    /** Packages to scan for repository interfaces; empty = the annotated class's package. */
    String[] basePackages() default {};

    /** Entity base class restriction (all entities by default). */
    Class<?>[] entityBaseClasses() default {};

    /** Bean name suffix (Spring Data convention: {@code "Impl"} fragments unsupported). */
    String repositoryImplementationPostfix() default "";

    /** Should nested repository interfaces (member classes) be considered? */
    boolean considerNestedRepositories() default false;
}
