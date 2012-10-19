package de.tototec.sbuild;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {})
public @interface property {
    String name();
    String description();
    String defaultValue() default "#-_UNSET_-#";
    boolean password() default false;
}
