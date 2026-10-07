package org.embeddedjnosql.db.adapter.jnosql;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Column {
    String value() default "";
}
