package dev.precognition.spring;

/** Startup diagnostics stay on the server; runtime drift is serialized only as a stable error code. */
final class PrecognitionConfigurationException extends IllegalStateException {
    PrecognitionConfigurationException(String reason) { super("Precognition configuration: " + reason); }
}
