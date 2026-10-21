package dev.precognition.spring;

import java.util.Locale;
import java.util.Set;

public record PrecognitionContext(boolean precognitive, ValidationScope scope, Set<String> selectedWireFields,
                                  Locale locale) {
    public enum ValidationScope { FULL, SELECTED }
    public PrecognitionContext { selectedWireFields = Set.copyOf(selectedWireFields); }
}
