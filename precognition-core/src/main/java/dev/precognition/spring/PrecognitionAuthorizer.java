package dev.precognition.spring;

/** Runs before binding on both normal and precognitive opt-in requests. */
@FunctionalInterface
public interface PrecognitionAuthorizer {
    AuthorizationDecision authorize(EndpointRequest request);
}
