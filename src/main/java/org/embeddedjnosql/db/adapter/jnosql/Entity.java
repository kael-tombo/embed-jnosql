package org.embeddedjnosql.db.adapter.jnosql;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Inherited
public @interface Entity {
    String value() default "";
}
