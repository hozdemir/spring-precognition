package dev.precognition.verification;

import dev.precognition.example.ExampleApplication;
import dev.precognition.example.Probe;
import dev.precognition.spring.*;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.DelegatingServletInputStream;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = ExampleApplication.class)
@Import(ProtocolRegressionTest.Authorization.class)
class ProtocolRegressionTest {
    private static final String VALID = "{\"name\":\"Ada\",\"email\":\"ada@example.test\",\"age\":21}";
    @Autowired WebApplicationContext context;
    @Autowired PrecognitionProtocol protocol;
    @Autowired PrecognitionSettings settings;
    @Autowired Probe probe;
    MockMvc mvc;

    @BeforeEach void prepare() {
        probe.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(protocol).build();
    }
    private MockHttpServletRequestBuilder request(String path, String body) {
        return post(path).contentType("application/json").content(body);
    }
    private MockHttpServletRequestBuilder errorDispatch(MockHttpServletRequestBuilder request) {
        return request.with(value -> {
            value.setDispatcherType(DispatcherType.ERROR);
            value.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/original");
            return value;
        });
    }
    @Test void errorDispatchRejectsActiveMalformedAndOrphanProtocolHeadersWithoutCallingAction() throws Exception {
        boolean enabled = settings.isEnabled();
        try {
            for (boolean state : new boolean[]{true, false}) {
                settings.setEnabled(state);
                for (String[] signals : new String[][]{{"true"}, {" true "}, {"false", "true"}, {"TRUE"}}) {
                    var response = mvc.perform(errorDispatch(request("/users", VALID)
                            .header("Precognition", (Object[]) signals))).andReturn().getResponse();
                    assertEquals(400, response.getStatus());
                    assertNull(response.getHeader("Precognition-Success"));
                }
                assertEquals(400, mvc.perform(errorDispatch(request("/users", VALID)
                        .header("Precognition-Validate-Only", "email"))).andReturn().getResponse().getStatus());
                assertEquals(400, mvc.perform(errorDispatch(request("/users", VALID).header("Precognition", "true")
                        .header("Precognition-Validate-Only", "email", "age"))).andReturn().getResponse().getStatus());
            }
        } finally { settings.setEnabled(enabled); }
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }
    @Test void missingFilterCannotTurnProtocolHeadersIntoNormalSubmissions() throws Exception {
        var noFilter = MockMvcBuilders.webAppContextSetup(context).build();
        for (String signal : new String[]{"true", " true "}) {
            assertEquals(500, noFilter.perform(request("/users", VALID).header("Precognition", signal))
                    .andReturn().getResponse().getStatus());
        }
        assertEquals(400, noFilter.perform(request("/users", VALID).header("Precognition", "false", "true"))
                .andReturn().getResponse().getStatus());
        assertEquals(400, noFilter.perform(request("/users", VALID).header("Precognition-Validate-Only", "email"))
                .andReturn().getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }
    @Test void whitespaceInActiveSignalStillWorksOnNormalRequestDispatch() throws Exception {
        var response = mvc.perform(request("/users", VALID).header("Precognition", " true ")
                .header("Precognition-Validate-Only", "email")).andReturn().getResponse();
        assertEquals(204, response.getStatus());
        assertEquals("true", response.getHeader("Precognition-Success"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }
    @Test void unknownLengthTrailingBodyIsBoundedBeforeAnyValidatorRuns() throws Exception {
        int limit = settings.getLimits().getMaxBodyBytes();
        var read = new AtomicInteger();
        var response = mvc.perform(request("/users", VALID + " ".repeat(limit * 2)).header("Precognition", "true")
                .with(original -> new UnknownLengthRequest(original, read))).andReturn().getResponse();
        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("PRECOGNITION_PAYLOAD_TOO_LARGE"));
        assertNull(response.getHeader("Precognition-Success"));
        assertEquals(limit + 1, read.get());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }
    @Test void unknownLengthBodiesAtAndBelowTheLimitRemainValid() throws Exception {
        int limit = settings.getLimits().getMaxBodyBytes();
        for (int length : new int[]{limit - 1, limit}) {
            probe.reset();
            var read = new AtomicInteger();
            var response = mvc.perform(request("/users", VALID + " ".repeat(length - VALID.length()))
                    .header("Precognition", "true").with(original -> new UnknownLengthRequest(original, read)))
                    .andReturn().getResponse();
            assertEquals(204, response.getStatus());
            assertEquals(length, read.get());
            assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
        }
    }
    @Test void endpointAuthorizationStillRunsBeforeReadingAnUnknownLengthBody() throws Exception {
        var read = new AtomicInteger();
        var response = mvc.perform(request("/profiles", VALID + " ".repeat(100000)).header("Precognition", "true")
                .with(original -> new UnknownLengthRequest(original, read))).andReturn().getResponse();
        assertEquals(403, response.getStatus());
        assertEquals(0, read.get());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }
    @TestConfiguration(proxyBeanMethods = false) static class Authorization {
        @Bean PrecognitionAuthorizer denyProfile() {
            return endpoint -> new AuthorizationDecision(!endpoint.route().equals("/profiles"));
        }
    }
    private static final class UnknownLengthRequest extends MockHttpServletRequest {
        private final ServletInputStream stream;
        UnknownLengthRequest(MockHttpServletRequest original, AtomicInteger read) {
            super(original.getServletContext());
            setMethod(original.getMethod());
            setRequestURI(original.getRequestURI());
            setServletPath(original.getServletPath());
            setContentType(original.getContentType());
            Collections.list(original.getHeaderNames()).forEach(name -> Collections.list(original.getHeaders(name))
                    .forEach(value -> addHeader(name, value)));
            byte[] bytes = original.getContentAsByteArray();
            setContent(bytes);
            stream = new DelegatingServletInputStream(new ByteArrayInputStream(bytes) {
                @Override public synchronized int read() {
                    int value = super.read();
                    if (value >= 0) read.incrementAndGet();
                    return value;
                }
                @Override public synchronized int read(byte[] buffer, int offset, int length) {
                    int count = super.read(buffer, offset, length);
                    if (count > 0) read.addAndGet(count);
                    return count;
                }
            });
        }
        @Override public int getContentLength() { return -1; }
        @Override public long getContentLengthLong() { return -1; }
        @Override public ServletInputStream getInputStream() { return stream; }
    }
}
