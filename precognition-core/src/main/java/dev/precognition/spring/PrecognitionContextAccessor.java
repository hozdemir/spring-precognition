package dev.precognition.spring;

import java.util.Optional;

@FunctionalInterface
public interface PrecognitionContextAccessor {
    Optional<PrecognitionContext> current();
}
