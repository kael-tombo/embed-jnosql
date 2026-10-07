package org.embeddedjnosql.db.adapter.jnosql;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Embeddable {
}
