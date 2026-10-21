package dev.precognition.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@java.lang.annotation.Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Precognitive {
    PrecognitionMode mode() default PrecognitionMode.SELECTIVE;
}
