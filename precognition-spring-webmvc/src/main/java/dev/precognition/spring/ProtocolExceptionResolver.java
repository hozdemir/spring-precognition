package dev.precognition.spring;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.*;

/** Installed before application advice; only accepted validation traffic and opted-in input errors. */
final class ProtocolExceptionResolver implements HandlerExceptionResolver {
    private final ValidationEngine engine;
    private final ResponseWriter writer;
    private final PrecognitionSettings settings;
    ProtocolExceptionResolver(ValidationEngine engine, ResponseWriter writer, PrecognitionSettings settings) {
        this.engine = engine; this.writer = writer; this.settings = settings;
    }
    @Override public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        boolean active = PrecognitionProtocol.selection(request) != null;
        try {
            if (exception instanceof MethodArgumentNotValidException invalid && handler instanceof HandlerMethod method
                    && method.getMethod().getDeclaredAnnotation(Precognitive.class) != null && (active || settings.getNormalErrors() == PrecognitionSettings.NormalErrors.LARAVEL)) {
                var mode = method.getMethodAnnotation(Precognitive.class).mode();
                var model = engine.prepare(invalid.getParameter().getParameterType(), mode);
                Map<String, SortedSet<String>> messages = new TreeMap<>();
                for (var error : invalid.getBindingResult().getFieldErrors()) {
                    String wire = model.wireName(error.getField());
                    messages.computeIfAbsent(wire, key -> new TreeSet<>()).add(Optional.ofNullable(error.getDefaultMessage()).orElse("Invalid value"));
                }
                for (var error : invalid.getBindingResult().getGlobalErrors())
                    messages.computeIfAbsent("_form", key -> new TreeSet<>()).add(Optional.ofNullable(error.getDefaultMessage()).orElse("Invalid value"));
                Map<String, List<String>> errors = new LinkedHashMap<>(); messages.forEach((key, value) -> errors.put(key, List.copyOf(value)));
                writer.write(response, 422, Map.of("message", "The given data was invalid.", "errors", errors));
            } else if (exception instanceof PrecognitionConfigurationException) writer.error(response, 500, "PRECOGNITION_CONFIGURATION_ERROR");
            else if (exception instanceof PrecognitionException failure) writer.error(response, failure.status(), failure.code());
            else if (active) {
                Throwable cause = exception;
                boolean large = false;
                for (int i = 0; cause != null && i < 32; i++, cause = cause.getCause())
                    if (cause instanceof PrecognitionProtocol.BodyLimitException) large = true;
                if (large) writer.error(response, 413, "PRECOGNITION_PAYLOAD_TOO_LARGE");
                else if (exception instanceof org.springframework.http.converter.HttpMessageNotReadableException)
                    writer.error(response, 400, "INVALID_REQUEST_BODY");
                else if (exception instanceof org.springframework.web.bind.ServletRequestBindingException || exception instanceof org.springframework.beans.TypeMismatchException)
                    writer.error(response, 400, "INVALID_REQUEST_ARGUMENT");
                else if (exception instanceof org.springframework.web.HttpMediaTypeNotSupportedException)
                    writer.error(response, 415, "PRECOGNITION_UNSUPPORTED_MEDIA_TYPE");
                else if (exception instanceof org.springframework.web.HttpMediaTypeNotAcceptableException)
                    writer.error(response, 406, "PRECOGNITION_NOT_ACCEPTABLE");
                else if (exception instanceof org.springframework.web.servlet.NoHandlerFoundException
                        || exception instanceof org.springframework.web.servlet.resource.NoResourceFoundException)
                    writer.error(response, 404, "PRECOGNITION_ROUTE_NOT_FOUND");
                else if (exception instanceof org.springframework.web.HttpRequestMethodNotSupportedException unsupported) {
                    unsupported.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
                    writer.error(response, 405, "PRECOGNITION_METHOD_NOT_ALLOWED");
                } else writer.error(response, 500, "PRECOGNITION_INTERNAL_ERROR");
            } else return null;
            return new ModelAndView();
        } catch (Exception writerFailure) {
            if (!response.isCommitted()) {
                try { writer.error(response, 500, "PRECOGNITION_CONFIGURATION_ERROR"); return new ModelAndView(); }
                catch (java.io.IOException ignored) { return null; }
            }
            return null;
        }
    }
}
