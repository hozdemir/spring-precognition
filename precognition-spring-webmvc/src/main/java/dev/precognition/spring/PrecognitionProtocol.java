package dev.precognition.spring;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(10)
public class PrecognitionProtocol extends OncePerRequestFilter {
    static final String CONTEXT = PrecognitionProtocol.class.getName() + ".selection";
    private final PrecognitionSettings settings;
    private final ResponseWriter writer;
    private final io.micrometer.observation.ObservationRegistry observations;
    public PrecognitionProtocol(JsonSupport json, PrecognitionSettings settings) {
        this(json, settings, io.micrometer.observation.ObservationRegistry.NOOP);
    }
    public PrecognitionProtocol(JsonSupport json, PrecognitionSettings settings, io.micrometer.observation.ObservationRegistry observations) {
        this.settings = settings; this.writer = new ResponseWriter(json, settings); this.observations = observations;
    }
    record Selection(boolean full, Set<String> fields) {}
    record Headers(boolean active, String selection) {}
    static Headers headers(HttpServletRequest request) {
        var signals = Collections.list(request.getHeaders("Precognition"));
        var selections = Collections.list(request.getHeaders("Precognition-Validate-Only"));
        if (signals.size() > 1 || selections.size() > 1
                || (!signals.isEmpty() && !Set.of("true", "false").contains(signals.get(0).trim())))
            throw new PrecognitionException(400, "INVALID_PRECOGNITION_HEADER");
        boolean active = !signals.isEmpty() && signals.get(0).trim().equals("true");
        if (!active && !selections.isEmpty()) throw new PrecognitionException(400, "ORPHAN_SELECTION_HEADER");
        return new Headers(active, selections.isEmpty() ? null : selections.get(0));
    }
    static Selection selection(HttpServletRequest request) { return (Selection) request.getAttribute(CONTEXT); }
    static void prepareBody(HttpServletRequest request) throws IOException {
        var limited = org.springframework.web.util.WebUtils.getNativeRequest(request, LimitedRequest.class);
        if (limited == null) throw new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR");
        limited.getInputStream();
    }
    @Override protected boolean shouldNotFilterAsyncDispatch() { return false; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        io.micrometer.observation.Observation observation = null;
        try {
            var headers = headers(request);
            if (!headers.active()) { chain.doFilter(request, response); return; }
            if (settings.getObservability().isMetricsEnabled())
                observation = io.micrometer.observation.Observation.createNotStarted("precognition.request", observations)
                        .lowCardinalityKeyValue("scope", headers.selection() == null ? "full" : "selected").start();
            response.setHeader("Precognition", "true");
            response.setHeader("Cache-Control", "no-store");
            if (!settings.isEnabled()) throw new PrecognitionException(412, "PRECOGNITION_DISABLED");
            if (request.getDispatcherType() != DispatcherType.REQUEST) throw new PrecognitionException(400, "PRECOGNITION_UNSUPPORTED_DISPATCH");
            String encoding = request.getHeader("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) throw new PrecognitionException(415, "PRECOGNITION_UNSUPPORTED_MEDIA_TYPE");
            try {
                var media = MediaType.parseMediaType(Optional.ofNullable(request.getContentType()).orElse("application/octet-stream"));
                if (!media.getType().equals("application") || !(media.getSubtype().equals("json") || media.getSubtype().endsWith("+json")))
                    throw new PrecognitionException(415, "PRECOGNITION_UNSUPPORTED_MEDIA_TYPE");
                String accept = request.getHeader("Accept");
                if (accept != null && MediaType.parseMediaTypes(accept).stream().noneMatch(t -> t.getQualityValue() > 0 && t.isCompatibleWith(MediaType.APPLICATION_JSON)))
                    throw new PrecognitionException(406, "PRECOGNITION_NOT_ACCEPTABLE");
            } catch (org.springframework.http.InvalidMediaTypeException invalid) {
                throw new PrecognitionException(415, "PRECOGNITION_UNSUPPORTED_MEDIA_TYPE");
            }
            var limits = settings.getLimits();
            if (request.getContentLengthLong() > limits.getMaxBodyBytes()) throw new PrecognitionException(413, "PRECOGNITION_PAYLOAD_TOO_LARGE");
            Set<String> fields = new LinkedHashSet<>();
            if (headers.selection() != null) {
                String header = headers.selection();
                if (header.length() > limits.getMaxSelectionHeaderBytes() || header.getBytes(StandardCharsets.UTF_8).length > limits.getMaxSelectionHeaderBytes())
                    throw new PrecognitionException(431, "PRECOGNITION_HEADERS_TOO_LARGE");
                String[] tokens = header.isBlank() ? new String[0] : header.split(",", -1);
                if (tokens.length > limits.getMaxSelectedFields()) throw new PrecognitionException(431, "PRECOGNITION_HEADERS_TOO_LARGE");
                for (String token : tokens) {
                    String field = token.trim();
                    if (field.length() > limits.getMaxFieldNameLength()) throw new PrecognitionException(431, "PRECOGNITION_HEADERS_TOO_LARGE");
                    if (!field.matches("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*)*")) throw new PrecognitionException(400, "INVALID_FIELD_SELECTION");
                    fields.add(field);
                }
            }
            request.setAttribute(CONTEXT, new Selection(headers.selection() == null, Set.copyOf(fields)));
            chain.doFilter(new LimitedRequest(request, limits.getMaxBodyBytes()), response);
        } catch (PrecognitionException failure) {
            if (!response.isCommitted()) writer.error(response, failure.status(), failure.code());
            else throw failure;
        } finally {
            if (observation != null) observation.lowCardinalityKeyValue("status", Integer.toString(response.getStatus())).stop();
            request.removeAttribute(CONTEXT);
            request.removeAttribute(PrecognitionConfiguration.REQUEST_CONTEXT);
        }
    }
    static final class BodyLimitException extends IOException {}
    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final int limit;
        private ServletInputStream limited;
        private IOException failure;
        LimitedRequest(HttpServletRequest request, int limit) { super(request); this.limit = limit; }
        @Override public ServletInputStream getInputStream() throws IOException {
            if (limited != null) return limited;
            if (failure != null) throw failure;
            // Consume the complete body before conversion, including any unread JSON suffix.
            // Delay this until binding so endpoint authorizers still run before body reads.
            byte[] bytes;
            try {
                var source = super.getInputStream();
                bytes = source.readNBytes(limit);
                if (source.read() != -1) throw new BodyLimitException();
            } catch (IOException exception) {
                failure = exception;
                throw exception;
            }
            var body = new ByteArrayInputStream(bytes);
            limited = new ServletInputStream() {
                @Override public int read() { return body.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return body.read(bytes, offset, length); }
                @Override public int available() { return body.available(); }
                @Override public boolean isFinished() { return body.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    throw new IllegalStateException("Precognition supports blocking request bodies only");
                }
            };
            return limited;
        }
        @Override public BufferedReader getReader() throws IOException {
            return new BufferedReader(new InputStreamReader(getInputStream(), Optional.ofNullable(getCharacterEncoding()).orElse("UTF-8")));
        }
    }
}
