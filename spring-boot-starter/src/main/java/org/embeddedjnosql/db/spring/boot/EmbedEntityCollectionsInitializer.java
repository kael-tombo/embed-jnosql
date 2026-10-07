package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;

/**
 * Materializes the backing collection of every detected entity at context startup — the
 * {@code ddl-auto=update} feel of the H2 setup: drop the jar and the annotated entities in,
 * and the database is ready to hold them before the first request arrives.
 *
 * <p>This is a deliberate, toggleable use of the product's create-on-first-use semantics
 * ({@code documentCollection(name)} materializes); it is off by default via
 * {@code embedjnosql.entities.auto-create-collections=false} for teams that prefer
 * collections to appear only when first written. Pure annotation mapping (without the
 * starter) still never creates anything — that library-level guarantee is untouched.</p>
 */
public class EmbedEntityCollectionsInitializer implements SmartInitializingSingleton {

    private final EmbedJNoSQL db;
    private final List<Class<?>> entities;

    EmbedEntityCollectionsInitializer(EmbedJNoSQL db, List<Class<?>> entities) {
        this.db = db;
        this.entities = entities;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (Class<?> entity : entities) {
            db.documentCollection(EntityMapper.getCollectionName(entity));
        }
    }

    List<Class<?>> entities() {
        return entities;
    }
}
