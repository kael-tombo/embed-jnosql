package org.embeddedjnosql.db;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public API surface as <strong>experimental</strong>.
 *
 * <p>Experimental surface ships in the published jar but sits outside the release contract: it has
 * no production caller inside this codebase, no functional test coverage, and is excluded from the
 * JaCoCo gate. Its API may change or be removed in a future release without notice, and it is not
 * covered by the compatibility commitments in the release documents.</p>
 *
 * <p>The marker is runtime-visible so consumers can detect it reflectively before building on an
 * annotated type. The set of annotated classes is pinned by {@code ReleaseFeatureSweepTest}
 * (the {@code experimentalClassesAreLabeled} assertion), which fails if an annotated class loses
 * the label or an unlabeled class is added to that list.</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Experimental {

    /**
     * Short human-readable status of the annotated surface: why it is experimental today.
     */
    String value() default "";
}
