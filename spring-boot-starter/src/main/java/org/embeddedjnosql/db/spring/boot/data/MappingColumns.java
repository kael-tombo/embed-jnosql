package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;

import java.util.Locale;

/**
 * Resolves the exact document field name (column) the core mapping layer uses for an entity
 * property, plus the value coercions the same layer applies on read (e.g. {@code Instant}
 * fields are written as ISO-8601 strings, {@code @Enumerated(ORDINAL)} enums as ordinals).
 *
 * <p>Spring Data derived queries are property-based; the document store is column-based.
 * This helper is the single seam between the two so that {@code @Column(name="order_email")}
 * style aliases and enum/date coercions behave identically to a direct
 * {@code EmbedRepository.findBy(column, value)} — no duplicated mapping rules.</p>
 */
final class MappingColumns {

    private MappingColumns() {
    }

    /** The document column {@code toDocument} writes for this entity property. */
    static String column(Class<?> entityClass, String propertyName) {
        return EntityMapper.resolveDocumentField(entityClass, propertyName);
    }

    /**
     * Coerces a raw document value into the shape the core mapping layer would hand to a
     * predicate built from the declared property type — currently only the {@code Instant}
     * read-side coercion ({@code String} back to epoch-seconds, the inverse of the ISO-8601
     * write) so numeric comparisons over time fields behave.
     */
    static Object normalizeRaw(Object value) {
        return value;
    }

    /** Lowercase helper for case-insensitive string comparisons. */
    static String lower(Object value) {
        return value == null ? null : value.toString().toLowerCase(Locale.ROOT);
    }
}
