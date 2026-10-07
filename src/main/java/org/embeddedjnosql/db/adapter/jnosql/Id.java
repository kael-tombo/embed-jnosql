package org.embeddedjnosql.db.adapter.jnosql;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
@Documented
public @interface Id {
    String value() default "";
}
