package dev.precognition.spring;

import java.security.Principal;
import java.util.Locale;
import java.util.Map;

/** Metadata only: authorizers cannot accidentally execute an action or read an unvalidated body. */
public record EndpointRequest(String handler, String method, String route, Map<String, String> pathVariables,
                              Principal principal, Locale locale) {
    public EndpointRequest { pathVariables = Map.copyOf(pathVariables); }
}
