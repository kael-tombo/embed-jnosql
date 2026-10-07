package org.junify.db.adapter.jnosql;

import org.junify.db.nosql.document.Document;
import org.junify.db.core.util.JsonSerde;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Maps Java entities ↔ JunifyDB {@link Document} objects.
 *
 * <p>Annotation resolution priority (first match wins):
 * <ol>
 *   <li>Eclipse JNoSQL — {@code jakarta.nosql.@Entity / @Id / @Column}</li>
 *   <li>Jakarta Persistence / Hibernate — {@code jakarta.persistence.@Entity / @Id /
 *       @Column / @Transient / @GeneratedValue}</li>
 *   <li>JunifyDB built-in — {@code org.junify.db.adapter.jnosql.@Entity / @Id /
 *       @Column / @Transient / @GeneratedValue}</li>
 *   <li>Convention — a field named exactly {@code id} is treated as the identity
 *       field when no explicit {@code @Id} annotation is found.</li>
 * </ol>
 *
 * <p>All annotation lookup is delegated to {@link AnnotationResolver}, which uses
 * reflective class loading so that the JNoSQL and JPA JARs are entirely optional.
 */
public class EntityMapper {

    /**
     * Converts a Java entity to a {@link Document}.
     *
     * <ul>
     *   <li>The {@code _entity} meta-field is set to the resolved collection name.</li>
     *   <li>The designated ID field (annotation or convention) becomes the document id.</li>
     *   <li>If {@code @GeneratedValue} (JPA or JunifyDB) is present and the id value
     *       is {@code null}, a random UUID is generated and written back to the entity.</li>
     *   <li>Fields marked {@code transient} (keyword) or any {@code @Transient} are skipped.</li>
     *   <li>Static fields are skipped.</li>
     * </ul>
     */
    public static <T> Document toDocument(T entity) {
        return toDocument(entity, false);
    }

    public static <T> Document toDocument(T entity, boolean isUpdate) {
        Class<?> clazz = entity.getClass();
        if (isUpdate && AnnotationResolver.isImmutable(clazz)) {
            throw new IllegalStateException("Entity " + clazz.getName() + " is immutable and cannot be updated");
        }

        // Trigger JPA lifecycle callbacks
        if (isUpdate) {
            invokeLifecycleMethods(entity, AnnotationResolver.findPreUpdateMethods(clazz));
        } else {
            invokeLifecycleMethods(entity, AnnotationResolver.findPrePersistMethods(clazz));
        }

        String collectionName = AnnotationResolver.resolveCollectionName(clazz);

        Document doc = new Document();
        doc.add("_entity", collectionName);

        List<Field> fields = getAllFields(clazz);
        Field idField = findDesignatedIdField(fields);

        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (AnnotationResolver.isTransient(field))   continue;
            // Formulas are read-only / calculated, skip storing in document
            if (AnnotationResolver.resolveFormula(field).isPresent()) continue;

            field.setAccessible(true);
            try {
                Object value = field.get(entity);

                // Handle Hibernate @CreationTimestamp
                if (AnnotationResolver.isCreationTimestamp(field)) {
                    if (!isUpdate || value == null) {
                        value = createTimestamp(field.getType());
                        field.set(entity, convertValue(value, field.getType()));
                    }
                }

                // Handle Hibernate @UpdateTimestamp
                if (AnnotationResolver.isUpdateTimestamp(field)) {
                    value = createTimestamp(field.getType());
                    field.set(entity, convertValue(value, field.getType()));
                }

                if (field.equals(idField)) {
                    // Auto-generate UUID if @GeneratedValue / @UuidGenerator is present and value is null
                    if (value == null && AnnotationResolver.shouldAutoGenerate(field)) {
                        value = UUID.randomUUID().toString();
                        field.set(entity, convertValue(value, field.getType()));
                    }
                    String idStr = value != null ? value.toString() : null;
                    doc.id(idStr);
                    if (idStr != null) {
                        String colName = AnnotationResolver.resolveColumnName(field);
                        doc.add(colName, idStr);
                        doc.add("id", idStr);
                    }
                } else {
                    if (value != null) {
                        String fieldName = AnnotationResolver.resolveColumnName(field);
                        if (value instanceof Enum<?> e) {
                            if (AnnotationResolver.isEnumeratedOrdinal(field)) {
                                doc.add(fieldName, e.ordinal());
                            } else {
                                doc.add(fieldName, e.name());
                            }
                        } else if (value instanceof java.time.Instant inst) {
                            doc.add(fieldName, inst.toString());
                        } else if (value instanceof java.time.LocalDateTime ldt) {
                            doc.add(fieldName, ldt.toString());
                        } else if (value instanceof java.util.Date d) {
                            doc.add(fieldName, d.toInstant().toString());
                        } else {
                            doc.add(fieldName, value);
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                // Skip inaccessible fields
            }
        }

        // Evaluate formulas on doc and entity
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (AnnotationResolver.isTransient(field)) continue;
            Optional<String> formula = AnnotationResolver.resolveFormula(field);
            if (formula.isPresent()) {
                field.setAccessible(true);
                Object formulaVal = evaluateFormula(doc, formula.get(), field.getType());
                if (formulaVal != null) {
                    try {
                        field.set(entity, convertValue(formulaVal, field.getType()));
                    } catch (IllegalAccessException ignored) {}
                    String colName = AnnotationResolver.resolveColumnName(field);
                    doc.add(colName, formulaVal);
                }
            }
        }

        return doc;
    }

    /**
     * Reconstructs a Java entity from a {@link Document}.
     *
     * <p>Uses {@link Constructor#setAccessible(boolean) setAccessible(true)} so that
     * package-private entity classes (common in test inner-classes) can be instantiated
     * from this mapper which may reside in a different package.
     */
    public static <T> T fromDocument(Document doc, Class<T> clazz) {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            ctor.setAccessible(true);   // handles package-private / inner-class entities
            T entity = ctor.newInstance();

            List<Field> fields = getAllFields(clazz);
            Field idField = findDesignatedIdField(fields);

            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                if (AnnotationResolver.isTransient(field))   continue;

                field.setAccessible(true);

                Optional<String> formula = AnnotationResolver.resolveFormula(field);
                if (formula.isPresent()) {
                    Object formulaVal = evaluateFormula(doc, formula.get(), field.getType());
                    if (formulaVal != null) {
                        field.set(entity, convertValue(formulaVal, field.getType()));
                    }
                    continue;
                }

                Object value;
                if (field.equals(idField)) {
                    // Document id lives in doc.getId(), not in the fields map
                    value = doc.getId();
                } else {
                    String fieldName = AnnotationResolver.resolveColumnName(field);
                    value = doc.get(fieldName);
                }

                if (value != null) {
                    field.set(entity, convertValue(value, field.getType()));
                }
            }

            // Trigger JPA @PostLoad callback
            invokeLifecycleMethods(entity, AnnotationResolver.findPostLoadMethods(clazz));

            return entity;
        } catch (Exception e) {
            throw new org.junify.db.core.exception.SerializationException("Failed to map document to entity: " + clazz.getName(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Static helpers used by CrudRepository / EclipseDocumentTemplate
    // -------------------------------------------------------------------------

    /**
     * Returns the resolved collection name for an entity class.
     */
    public static String getCollectionName(Class<?> clazz) {
        return AnnotationResolver.resolveCollectionName(clazz);
    }

    /**
     * Returns the document field key used as the ID for the given class.
     * Resolves annotation-based or convention-based ("id") field.
     */
    public static String getIdFieldName(Class<?> clazz) {
        Field idField = findDesignatedIdField(getAllFields(clazz));
        if (idField != null) {
            return AnnotationResolver.resolveIdKey(idField);
        }
        return "id";
    }

    /**
     * Resolves the mapped document field/column name for a field.
     */
    public static String resolveColumnName(Field field) {
        return AnnotationResolver.resolveColumnName(field);
    }

    /**
     * Returns true if the field is annotated with @NaturalId.
     */
    public static boolean isNaturalId(Field field) {
        return AnnotationResolver.isNaturalId(field);
    }

    /**
     * Returns the runtime value of the designated ID field for the entity,
     * or {@code null} if no ID field is found or the value is null.
     */
    public static Object getIdValue(Object entity) {
        Field idField = findDesignatedIdField(getAllFields(entity.getClass()));
        if (idField != null) {
            idField.setAccessible(true);
            try {
                return idField.get(entity);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private utilities
    // -------------------------------------------------------------------------

    /**
     * Determines the designated identity field for a class using two strategies:
     * <ol>
     *   <li><b>Annotation</b> — first non-static field carrying any {@code @Id}
     *       annotation (JNoSQL, JPA, or JunifyDB built-in).</li>
     *   <li><b>Convention</b> — first non-static field named exactly {@code "id"},
     *       used only when no annotated id field exists.</li>
     * </ol>
     *
     * @return the designated id {@link Field}, or {@code null} if none found.
     */
    private static Field findDesignatedIdField(List<Field> fields) {
        // Priority 1: explicit @Id annotation (any source)
        for (Field f : fields) {
            if (!Modifier.isStatic(f.getModifiers()) && AnnotationResolver.isIdField(f)) {
                return f;
            }
        }
        // Priority 2: convention — field named "id"
        for (Field f : fields) {
            if (!Modifier.isStatic(f.getModifiers()) && "id".equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    /**
     * Walks the class hierarchy (excluding {@link Object}) and collects all
     * declared fields, super-class fields first.
     */
    private static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(0, Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private static <T> T convertValue(Object value, Class<T> targetType) {
        if (value == null) return null;

        if (targetType.isInstance(value)) return (T) value;

        if (targetType == String.class) return (T) value.toString();

        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number n) return (T) Integer.valueOf(n.intValue());
            return (T) Integer.valueOf(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number n) return (T) Long.valueOf(n.longValue());
            return (T) Long.valueOf(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number n) return (T) Double.valueOf(n.doubleValue());
            return (T) Double.valueOf(value.toString());
        }
        if (targetType == float.class || targetType == Float.class) {
            if (value instanceof Number n) return (T) Float.valueOf(n.floatValue());
            return (T) Float.valueOf(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean b) return (T) b;
            return (T) Boolean.valueOf(value.toString());
        }
        if (targetType.isEnum()) {
            if (value instanceof Number n) {
                int idx = n.intValue();
                T[] constants = targetType.getEnumConstants();
                if (idx >= 0 && idx < constants.length) return constants[idx];
            }
            try {
                return (T) Enum.valueOf((Class<Enum>) targetType, value.toString());
            } catch (Exception ignored) {}
        }
        if (targetType == java.time.Instant.class) {
            if (value instanceof java.time.Instant i) return (T) i;
            if (value instanceof Number n) {
                if (n instanceof Double || n instanceof Float) {
                    long sec = n.longValue();
                    long nano = (long) ((n.doubleValue() - sec) * 1_000_000_000);
                    return (T) java.time.Instant.ofEpochSecond(sec, nano);
                }
                long val = n.longValue();
                if (val < 100_000_000_000L) {
                    return (T) java.time.Instant.ofEpochSecond(val);
                }
                return (T) java.time.Instant.ofEpochMilli(val);
            }
            try {
                return (T) java.time.Instant.parse(value.toString());
            } catch (Exception e) {
                try {
                    long l = Long.parseLong(value.toString());
                    return (T) (l < 100_000_000_000L ? java.time.Instant.ofEpochSecond(l) : java.time.Instant.ofEpochMilli(l));
                } catch (Exception ignored) {}
            }
        }
        if (targetType == java.time.LocalDateTime.class) {
            if (value instanceof java.time.LocalDateTime ldt) return (T) ldt;
            return (T) java.time.LocalDateTime.parse(value.toString());
        }
        if (targetType == java.util.Date.class) {
            if (value instanceof java.util.Date d) return (T) d;
            if (value instanceof Number n) return (T) new java.util.Date(n.longValue());
            // toDocument serializes Date via Instant.toString() (ISO-8601); mirror that on read
            // so the write/read paths are symmetric instead of leaking the raw String unchecked
            if (value instanceof String s) {
                try { return (T) java.util.Date.from(java.time.Instant.parse(s)); }
                catch (Exception ignored) { /* fall through */ }
            }
        }
        if (targetType == java.util.UUID.class) {
            if (value instanceof java.util.UUID u) return (T) u;
            return (T) java.util.UUID.fromString(value.toString());
        }
        if (value instanceof String str && (str.startsWith("{") || str.startsWith("["))) {
            try {
                return JsonSerde.fromJson(str, targetType);
            } catch (Exception ignored) { /* fall through */ }
        }

        return (T) value;
    }

    private static Object createTimestamp(Class<?> type) {
        if (type == java.time.Instant.class) return java.time.Instant.now();
        if (type == java.time.LocalDateTime.class) return java.time.LocalDateTime.now();
        if (type == java.util.Date.class) return new java.util.Date();
        if (type == Long.class || type == long.class) return System.currentTimeMillis();
        return java.time.Instant.now().toString();
    }

    private static void invokeLifecycleMethods(Object entity, List<java.lang.reflect.Method> methods) {
        if (methods == null || methods.isEmpty()) return;
        for (java.lang.reflect.Method m : methods) {
            try {
                m.setAccessible(true);
                m.invoke(entity);
            } catch (Exception ignored) {}
        }
    }

    private static Object evaluateFormula(Document doc, String formula, Class<?> targetType) {
        if (formula == null || formula.isBlank()) return null;
        String expr = formula.trim();

        if (doc.has(expr)) {
            return doc.getRaw(expr);
        }

        for (String op : new String[]{"*", "+", "-", "/"}) {
            int idx = expr.indexOf(op);
            if (idx > 0 && idx < expr.length() - 1) {
                String leftName = expr.substring(0, idx).trim();
                String rightName = expr.substring(idx + 1).trim();
                Object leftVal = doc.getRaw(leftName);
                Object rightVal = doc.getRaw(rightName);
                Double d1 = null;
                if (leftVal instanceof Number n1) {
                    d1 = n1.doubleValue();
                } else if (leftVal != null) {
                    try { d1 = Double.parseDouble(leftVal.toString()); } catch (Exception ignored) {}
                } else {
                    try { d1 = Double.parseDouble(leftName); } catch (Exception ignored) {}
                }

                Double d2 = null;
                if (rightVal instanceof Number n2) {
                    d2 = n2.doubleValue();
                } else if (rightVal != null) {
                    try { d2 = Double.parseDouble(rightVal.toString()); } catch (Exception ignored) {}
                } else {
                    try { d2 = Double.parseDouble(rightName); } catch (Exception ignored) {}
                }

                if (d1 != null && d2 != null) {
                    double res = switch (op) {
                        case "*" -> d1 * d2;
                        case "+" -> d1 + d2;
                        case "-" -> d1 - d2;
                        case "/" -> d2 != 0 ? d1 / d2 : 0;
                        default -> 0;
                    };
                    return convertValue(res, targetType);
                }
            }
        }

        if (expr.toUpperCase().startsWith("UPPER(") && expr.endsWith(")")) {
            String f = expr.substring(6, expr.length() - 1).trim();
            Object val = doc.getRaw(f);
            return val != null ? val.toString().toUpperCase() : null;
        }
        if (expr.toUpperCase().startsWith("LOWER(") && expr.endsWith(")")) {
            String f = expr.substring(6, expr.length() - 1).trim();
            Object val = doc.getRaw(f);
            return val != null ? val.toString().toLowerCase() : null;
        }

        return null;
    }
}

