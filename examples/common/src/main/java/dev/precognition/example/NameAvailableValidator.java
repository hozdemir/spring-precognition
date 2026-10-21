package dev.precognition.example;

import dev.precognition.spring.*;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** A counted stand-in for an expensive repository lookup. No database is used. */
public class NameAvailableValidator implements ConstraintValidator<NameAvailable, String> {
    private final Probe probe;

    public NameAvailableValidator(Probe probe) {
        this.probe = probe;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        probe.recordNameValidation();
        return !"taken".equals(value);
    }
}
