package de.tototec.sbuild;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(value = {})
public @interface property {
    /** The unique name of the property. */
    String name();
    /** The description for the property, providing the SBuild user the required information he/she needs to set the correct value. */ 
    String description();
    /** The default value used, when the SBuild user does not give an value for this property. If not given, the property is mandatory. */
    String defaultValue() default "#-_UNSET_-#";
}
