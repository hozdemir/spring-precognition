package dev.precognition.spring;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Serialize before committing and enforce byte bounds without allocating an unbounded response. */
final class ResponseWriter {
    private final JsonSupport json;
    private final PrecognitionSettings settings;
    ResponseWriter(JsonSupport json, PrecognitionSettings settings) { this.json = json; this.settings = settings; }
    void error(HttpServletResponse response, int status, String code) throws IOException {
        if (status == 412) response.setHeader("Precognition", null);
        write(response, status, Map.of("message", "The request could not be processed.", "code", code));
    }
    void write(HttpServletResponse response, int status, Object body) throws IOException {
        var output = new java.io.OutputStream() {
            final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            @Override public void write(int value) throws IOException {
                if (bytes.size() >= settings.getLimits().getMaxResponseBytes()) throw new IOException("Response limit exceeded");
                bytes.write(value);
            }
            @Override public void write(byte[] value, int offset, int length) throws IOException {
                if (length > settings.getLimits().getMaxResponseBytes() - bytes.size()) throw new IOException("Response limit exceeded");
                bytes.write(value, offset, length);
            }
        };
        byte[] bytes;
        try { json.writeValue(output, body); bytes = output.bytes.toByteArray(); }
        catch (Exception failure) {
            status = 500;
            bytes = "{\"message\":\"The request could not be processed.\",\"code\":\"PRECOGNITION_CONFIGURATION_ERROR\"}".getBytes(StandardCharsets.UTF_8);
        }
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getOutputStream().write(bytes);
    }
}
