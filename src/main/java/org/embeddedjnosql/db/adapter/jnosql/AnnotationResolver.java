package org.embeddedjnosql.db.adapter.jnosql;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/**
 * Runtime annotation resolver supporting three annotation sets:
 * <ol>
 *   <li><b>Eclipse JNoSQL</b> — {@code jakarta.nosql.Entity}, {@code jakarta.nosql.Id},
 *       {@code jakarta.nosql.Column}</li>
 *   <li><b>Jakarta Persistence / Hibernate</b> — {@code jakarta.persistence.Entity},
 *       {@code jakarta.persistence.Id}, {@code jakarta.persistence.Column},
 *       {@code jakarta.persistence.Transient}</li>
 *   <li><b>EmbedJNoSQL built-in</b> — {@code org.embeddedjnosql.db.adapter.jnosql.*} custom
 *       annotations (existing fallback)</li>
 * </ol>
 *
 * <p>All annotation class lookups are done via {@link Class#forName} so that this
 * class compiles and runs correctly even when the JNoSQL or JPA JARs are absent
 * from the classpath at runtime. Missing annotation JARs are simply treated as
 * "annotation not present".
 */
class AnnotationResolver {

    // -------------------------------------------------------------------------
    // Annotation class names (loaded lazily / defensively)
    // -------------------------------------------------------------------------

    private static final String JNOSQL_ENTITY       = "jakarta.nosql.Entity";
    private static final String JNOSQL_ID           = "jakarta.nosql.Id";
    private static final String JNOSQL_COLUMN       = "jakarta.nosql.Column";

    private static final String JPA_ENTITY          = "jakarta.persistence.Entity";
    private static final String JPA_TABLE           = "jakarta.persistence.Table";
    private static final String JPA_ID              = "jakarta.persistence.Id";
    private static final String JPA_COLUMN          = "jakarta.persistence.Column";
    private static final String JPA_TRANSIENT       = "jakarta.persistence.Transient";
    private static final String JPA_GENERATED       = "jakarta.persistence.GeneratedValue";
    private static final String JPA_EMBEDDED_ID     = "jakarta.persistence.EmbeddedId";
    private static final String JPA_ENUMERATED      = "jakarta.persistence.Enumerated";
    private static final String JPA_PRE_PERSIST     = "jakarta.persistence.PrePersist";
    private static final String JPA_PRE_UPDATE      = "jakarta.persistence.PreUpdate";
    private static final String JPA_POST_PERSIST    = "jakarta.persistence.PostPersist";
    private static final String JPA_POST_LOAD       = "jakarta.persistence.PostLoad";

    // Hibernate annotations
    private static final String HIBERNATE_CREATION_TS = "org.hibernate.annotations.CreationTimestamp";
    private static final String HIBERNATE_UPDATE_TS   = "org.hibernate.annotations.UpdateTimestamp";
    private static final String HIBERNATE_UUID_GEN    = "org.hibernate.annotations.UuidGenerator";
    private static final String HIBERNATE_FORMULA     = "org.hibernate.annotations.Formula";
    private static final String HIBERNATE_NATURAL_ID  = "org.hibernate.annotations.NaturalId";
    private static final String HIBERNATE_IMMUTABLE   = "org.hibernate.annotations.Immutable";

    // -------------------------------------------------------------------------
    // Collection / Entity / Table name resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the storage collection name for an entity class.
     * Priority: JPA @Table(name) → Eclipse JNoSQL @Entity(value) → JPA @Entity(name)
     *           → EmbedJNoSQL @Entity(value) → class simple-name (lower-cased).
     */
    static String resolveCollectionName(Class<?> clazz) {
        // 1. JPA @Table(name)
        Optional<String> table = readAnnotationAttribute(clazz, JPA_TABLE, "name");
        if (table.isPresent() && !table.get().isEmpty()) return table.get();

        // 2. Eclipse JNoSQL @Entity(value)
        Optional<String> jnosql = readAnnotationAttribute(clazz, JNOSQL_ENTITY, "value");
        if (jnosql.isPresent() && !jnosql.get().isEmpty()) return jnosql.get();

        // 3. JPA @Entity(name)
        Optional<String> jpa = readAnnotationAttribute(clazz, JPA_ENTITY, "name");
        if (jpa.isPresent() && !jpa.get().isEmpty()) return jpa.get();

        // 4. EmbedJNoSQL built-in @Entity(value)
        Entity builtIn = clazz.getAnnotation(Entity.class);
        if (builtIn != null && !builtIn.value().isEmpty()) return builtIn.value();

        // 5. Default: simple class name (lower-cased)
        return clazz.getSimpleName().toLowerCase();
    }

    // -------------------------------------------------------------------------
    // @Id / identity field
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the field is annotated with any recognised {@code @Id}.
     * Checks: JNoSQL @Id → JPA @Id → JPA @EmbeddedId → EmbedJNoSQL @Id.
     */
    static boolean isIdField(Field field) {
        return isAnnotationPresent(field, JNOSQL_ID)
                || isAnnotationPresent(field, JPA_ID)
                || isAnnotationPresent(field, JPA_EMBEDDED_ID)
                || field.isAnnotationPresent(Id.class);
    }

    /**
     * Returns the storage key to use for an {@code @Id} field.
     * Always returns {@code "id"} (the EmbedJNoSQL canonical document id key).
     * Honours a custom value on EmbedJNoSQL's own {@code @Id} if set.
     */
    static String resolveIdKey(Field field) {
        Id builtIn = field.getAnnotation(Id.class);
        if (builtIn != null && !builtIn.value().isEmpty()) return builtIn.value();
        return "id";
    }

    // -------------------------------------------------------------------------
    // @Column name resolution
    // -------------------------------------------------------------------------

    /**
     * Resolves the document field name for a Java field.
     * Priority: JNoSQL @Column(value) → JPA @Column(name) → EmbedJNoSQL @Column(value)
     *           → Java field name.
     */
    static String resolveColumnName(Field field) {
        // 1. Eclipse JNoSQL @Column(value)
        Optional<String> jnosql = readAnnotationAttribute(field, JNOSQL_COLUMN, "value");
        if (jnosql.isPresent() && !jnosql.get().isEmpty()) return jnosql.get();

        // 2. JPA @Column(name)
        Optional<String> jpa = readAnnotationAttribute(field, JPA_COLUMN, "name");
        if (jpa.isPresent() && !jpa.get().isEmpty()) return jpa.get();

        // 3. EmbedJNoSQL @Column(value)
        Column builtIn = field.getAnnotation(Column.class);
        if (builtIn != null && !builtIn.value().isEmpty()) return builtIn.value();

        // 4. Default: Java field name
        return field.getName();
    }

    // -------------------------------------------------------------------------
    // @Transient detection
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the field should be excluded from document mapping.
     * Checks: Java {@code transient} keyword → JPA @Transient → EmbedJNoSQL @Transient.
     */
    static boolean isTransient(Field field) {
        if (Modifier.isTransient(field.getModifiers())) return true;
        if (isAnnotationPresent(field, JPA_TRANSIENT))  return true;
        if (field.isAnnotationPresent(Transient.class)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // @GeneratedValue & @UuidGenerator detection (auto UUID)
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the field is marked for auto-generated value.
     * Checks: JPA @GeneratedValue → Hibernate @UuidGenerator → EmbedJNoSQL @GeneratedValue.
     */
    static boolean shouldAutoGenerate(Field field) {
        if (isAnnotationPresent(field, JPA_GENERATED))       return true;
        if (isAnnotationPresent(field, HIBERNATE_UUID_GEN))  return true;
        if (field.isAnnotationPresent(GeneratedValue.class)) return true;
        return false;
    }

    // -------------------------------------------------------------------------
    // Hibernate-specific annotations
    // -------------------------------------------------------------------------

    static boolean isCreationTimestamp(Field field) {
        return isAnnotationPresent(field, HIBERNATE_CREATION_TS);
    }

    static boolean isUpdateTimestamp(Field field) {
        return isAnnotationPresent(field, HIBERNATE_UPDATE_TS);
    }

    static boolean isUuidGenerator(Field field) {
        return isAnnotationPresent(field, HIBERNATE_UUID_GEN);
    }

    static Optional<String> resolveFormula(Field field) {
        return readAnnotationAttribute(field, HIBERNATE_FORMULA, "value");
    }

    static boolean isNaturalId(Field field) {
        return isAnnotationPresent(field, HIBERNATE_NATURAL_ID);
    }

    static boolean isImmutable(Class<?> clazz) {
        return isAnnotationPresent(clazz, HIBERNATE_IMMUTABLE);
    }

    // -------------------------------------------------------------------------
    // JPA Lifecycle Callbacks & Enums
    // -------------------------------------------------------------------------

    static boolean isEnumerated(Field field) {
        return isAnnotationPresent(field, JPA_ENUMERATED);
    }

    static boolean isEnumeratedOrdinal(Field field) {
        Optional<String> val = readAnnotationAttribute(field, JPA_ENUMERATED, "value");
        return val.map(v -> v.contains("ORDINAL")).orElse(false);
    }

    static java.util.List<Method> findLifecycleMethods(Class<?> clazz, String annotationClassName) {
        java.util.List<Method> result = new java.util.ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (isAnnotationPresent(m, annotationClassName)) {
                    result.add(m);
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    static java.util.List<Method> findPrePersistMethods(Class<?> clazz) {
        return findLifecycleMethods(clazz, JPA_PRE_PERSIST);
    }

    static java.util.List<Method> findPreUpdateMethods(Class<?> clazz) {
        return findLifecycleMethods(clazz, JPA_PRE_UPDATE);
    }

    static java.util.List<Method> findPostPersistMethods(Class<?> clazz) {
        return findLifecycleMethods(clazz, JPA_POST_PERSIST);
    }

    static java.util.List<Method> findPostLoadMethods(Class<?> clazz) {
        return findLifecycleMethods(clazz, JPA_POST_LOAD);
    }

    // -------------------------------------------------------------------------
    // Reflective helpers (no compile-time dep on optional annotation JARs)
    // -------------------------------------------------------------------------

    /**
     * Checks whether an annotation of the given binary class name is present on
     * the class-level element. Returns {@code false} if the annotation class is
     * not on the classpath.
     */
    @SuppressWarnings("unchecked")
    static boolean isAnnotationPresent(Class<?> element, String annotationClassName) {
        try {
            Class<? extends Annotation> annClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            return element.isAnnotationPresent(annClass);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    /**
     * Checks whether an annotation of the given binary class name is present on
     * the field-level element. Returns {@code false} if the annotation class is
     * not on the classpath.
     */
    @SuppressWarnings("unchecked")
    static boolean isAnnotationPresent(Field element, String annotationClassName) {
        try {
            Class<? extends Annotation> annClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            return element.isAnnotationPresent(annClass);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    static boolean isAnnotationPresent(Method element, String annotationClassName) {
        try {
            Class<? extends Annotation> annClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            return element.isAnnotationPresent(annClass);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return false;
        }
    }

    /**
     * Reads a single named attribute from an annotation on a class-level element.
     * Returns {@link Optional#empty()} if the annotation or attribute is absent.
     */
    @SuppressWarnings("unchecked")
    private static Optional<String> readAnnotationAttribute(
            Class<?> element, String annotationClassName, String attributeName) {
        try {
            Class<? extends Annotation> annClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            Annotation ann = element.getAnnotation(annClass);
            if (ann == null) return Optional.empty();
            Method method = annClass.getDeclaredMethod(attributeName);
            Object value = method.invoke(ann);
            return Optional.ofNullable(value instanceof String s ? s : null);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Reads a single named attribute from an annotation on a field-level element.
     */
    @SuppressWarnings("unchecked")
    private static Optional<String> readAnnotationAttribute(
            Field element, String annotationClassName, String attributeName) {
        try {
            Class<? extends Annotation> annClass =
                    (Class<? extends Annotation>) Class.forName(annotationClassName);
            Annotation ann = element.getAnnotation(annClass);
            if (ann == null) return Optional.empty();
            Method method = annClass.getDeclaredMethod(attributeName);
            Object value = method.invoke(ann);
            return Optional.ofNullable(value instanceof String s ? s : null);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
