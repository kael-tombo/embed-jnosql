package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.springframework.data.repository.core.support.AbstractEntityInformation;



/**
 * Spring Data {@link org.springframework.data.repository.core.EntityInformation} backed by the
 * embed-jnosql {@link EntityMapper}: identity resolution (annotation-based or {@code id}
 * convention) and the {@code isNew} decision reuse the exact same rules as the core adapter,
 * so a Spring Data repository and an auto-registered {@code EmbedRepository} over the same
 * entity never disagree.
 */
public class EmbedJpaEntityInformation<T, ID> extends AbstractEntityInformation<T, ID> {

    private final Class<ID> idType;

    @SuppressWarnings("unchecked")
    public EmbedJpaEntityInformation(Class<T> domainType) {
        super(domainType);
        this.idType = (Class<ID>) EntityMapper.getIdFieldType(domainType);
    }

    @Override
    public boolean isNew(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        return id == null;
    }

    @Override
    public ID getId(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id == null) {
            return null;
        }
        if (getIdType().isInstance(id)) {
            return getIdType().cast(id);
        }
        return coerce(id);
    }

    @Override
    public Class<ID> getIdType() {
        return idType;
    }

    @SuppressWarnings("unchecked")
    private ID coerce(Object raw) {
        Class<ID> target = getIdType();
        if (target == String.class) {
            return (ID) raw.toString();
        }
        if (target == Long.class || target == long.class) {
            return raw instanceof Number n ? (ID) Long.valueOf(n.longValue()) : (ID) Long.valueOf(raw.toString());
        }
        if (target == Integer.class || target == int.class) {
            return raw instanceof Number n ? (ID) Integer.valueOf(n.intValue()) : (ID) Integer.valueOf(raw.toString());
        }
        if (target == java.util.UUID.class) {
            return (ID) java.util.UUID.fromString(raw.toString());
        }
        return (ID) raw;
    }
}
