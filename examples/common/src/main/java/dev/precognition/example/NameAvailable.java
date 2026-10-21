package dev.precognition.example;

import dev.precognition.spring.*;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NameAvailableValidator.class)
public @interface NameAvailable {
    String message() default "This name is already taken.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
