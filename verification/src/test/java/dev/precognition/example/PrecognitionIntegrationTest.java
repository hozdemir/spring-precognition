package dev.precognition.example;

import dev.precognition.spring.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import dev.precognition.spring.JsonSupport.JsonValue;
import dev.precognition.spring.JsonSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Full application context: real MVC adapter, converters, Hibernate Validator and servlet filters. */
@SpringBootTest
class PrecognitionIntegrationTest {
    private static final String VALID = """
            {"name":"Ada","email":"ada@example.test","age":21}
            """;
    private static final String INVALID = """
            {"name":"taken","email":"invalid","age":12}
            """;
    private static final String VALID_EMAIL_ONLY = """
            {"name":"taken","email":"ada@example.test","age":12}
            """;

    @Autowired WebApplicationContext context;
    @Autowired Probe probe;
    @Autowired JsonSupport mapper;
    @Autowired DemoAuthenticationFilter authentication;
    @Autowired PrecognitionProtocol protocol;
    @Autowired PrecognitionAuthenticationResponseFilter authResponses;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        probe.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(authResponses, authentication, protocol).build();
    }

    @Test
    void normalSubmitInvokesControllerAndValidatorExactlyOnce() throws Exception {
        var result = perform(normal("/users", VALID));
        assertEquals(201, result.getResponse().getStatus());
        assertEquals("Ada", json(result).get("name").asString());
        assertEquals(new Probe.Snapshot(1, 1), probe.snapshot());
        assertNull(result.getResponse().getHeader("Precognition"));
        assertNull(result.getResponse().getHeader("Precognition-Success"));
    }

    @Test
    void validFullPrecognitionResolvesBodyButNeverInvokesController() throws Exception {
        var result = perform(precognitive(VALID));
        assertSuccess(result);
        assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
    }

    @Test
    void invalidFullPrecognitionReturnsAllErrorsWithoutInvokingController() throws Exception {
        var result = perform(precognitive(INVALID));
        assertEquals(422, result.getResponse().getStatus());
        JsonValue errors = json(result).get("errors");
        assertEquals(3, errors.size());
        assertTrue(errors.has("name"));
        assertTrue(errors.has("email"));
        assertTrue(errors.has("age"));
        assertTrue(errors.get("email").isArray());
        assertNull(result.getResponse().getHeader("Precognition-Success"));
        assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
    }

    @Test
    void selectingEmailSkipsUnselectedValidatorEvenWhenNameIsTaken() throws Exception {
        var result = perform(precognitive(VALID_EMAIL_ONLY).header("Precognition-Validate-Only", "email"));
        assertSuccess(result);
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void selectedFailureContainsOnlySelectedField() throws Exception {
        var result = perform(precognitive(INVALID).header("Precognition-Validate-Only", "email"));
        assertEquals(422, result.getResponse().getStatus());
        assertEquals(1, json(result).get("errors").size());
        assertTrue(json(result).get("errors").has("email"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void duplicateSelectionRunsPropertyValidatorOnlyOnce() throws Exception {
        var result = perform(precognitive(INVALID).header("Precognition-Validate-Only", " name, name "));
        assertEquals(422, result.getResponse().getStatus());
        assertEquals(1, json(result).get("errors").size());
        assertTrue(json(result).get("errors").has("name"));
        assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
    }

    @Test
    void selectedSuccessDoesNotDisableFullValidationOnLaterSubmit() throws Exception {
        assertSuccess(perform(precognitive(VALID_EMAIL_ONLY).header("Precognition-Validate-Only", "email")));
        var submit = perform(normal("/users", VALID_EMAIL_ONLY));
        assertEquals(422, submit.getResponse().getStatus());
        assertTrue(json(submit).get("errors").has("name"));
        assertTrue(json(submit).get("errors").has("age"));
        assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
    }

    @Test
    void precognitiveRequestToNonOptedInEndpointNeverFallsThroughToAction() throws Exception {
        var result = perform(normal("/legacy-users", VALID).header("Precognition", "true"));
        assertEquals(412, result.getResponse().getStatus());
        assertEquals("PRECOGNITION_NOT_ENABLED", json(result).get("code").asString());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void normalNonOptedInEndpointStillWorks() throws Exception {
        assertEquals(201, perform(normal("/legacy-users", VALID)).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(1, 1), probe.snapshot());
    }

    @Test
    void unknownFieldIsRejectedBeforeValidationOrInvocation() throws Exception {
        var result = perform(precognitive(VALID).header("Precognition-Validate-Only", "class"));
        assertEquals(400, result.getResponse().getStatus());
        assertEquals("UNKNOWN_VALIDATION_FIELD", json(result).get("code").asString());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {"TRUE", "1", "", "true,false"})
    void malformedSignalCannotBecomeANormalSubmit(String signal) throws Exception {
        var result = perform(normal("/users", VALID).header("Precognition", signal));
        assertEquals(400, result.getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @ParameterizedTest
    @ValueSource(strings = {",", "email,", "email,,name", "items.*.name"})
    void malformedOrUnsupportedSelectionIsRejected(String fields) throws Exception {
        var result = perform(precognitive(VALID).header("Precognition-Validate-Only", fields));
        assertEquals(400, result.getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void emptySelectionDoesNotRunAnyValidatorsOrBecomeFullValidation() throws Exception {
        assertSuccess(perform(precognitive(INVALID).header("Precognition-Validate-Only", "")));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void earlyAuthenticationErrorIsRecognizedWithoutChangingStatus() throws Exception {
        var response = perform(post("/users").contentType("application/json").content(VALID)
                .header("Precognition", "true")).getResponse();
        assertEquals(401, response.getStatus());
        assertEquals("true", response.getHeader("Precognition"));
        assertNull(response.getHeader("Precognition-Success"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void selectionWithoutSignalIsRejected() throws Exception {
        assertEquals(400, perform(normal("/users", VALID)
                .header("Precognition-Validate-Only", "email")).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void duplicatedSignalIsRejected() throws Exception {
        assertEquals(400, perform(normal("/users", VALID)
                .header("Precognition", "true", "true")).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void explicitFalseIsANormalSubmit() throws Exception {
        assertEquals(201, perform(normal("/users", VALID)
                .header("Precognition", "false")).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(1, 1), probe.snapshot());
    }

    @Test
    void invalidJsonNeverInvokesValidationOrController() throws Exception {
        assertEquals(400, perform(precognitive("{broken")).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void unselectedFieldMustStillDeserialize() throws Exception {
        String body = """
                {"name":"Ada","email":"ada@example.test","age":"not-an-integer"}
                """;
        assertEquals(400, perform(precognitive(body)
                .header("Precognition-Validate-Only", "email")).getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void authenticationFilterStillRunsBeforeBothValidationAndSubmit() throws Exception {
        for (boolean precognitive : List.of(true, false)) {
            var request = post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID);
            if (precognitive) request.header("Precognition", "true");
            assertEquals(401, perform(request).getResponse().getStatus());
        }
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void mixedParallelRequestsDoNotLeakBinderValidationState() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> requests = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                boolean selected = i % 2 == 0;
                requests.add(() -> {
                    var request = selected
                            ? precognitive(VALID_EMAIL_ONLY).header("Precognition-Validate-Only", "email")
                            : normal("/users", VALID_EMAIL_ONLY);
                    return perform(request).getResponse().getStatus();
                });
            }
            var results = executor.invokeAll(requests);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(i % 2 == 0 ? 204 : 422, results.get(i).get());
            }
            assertEquals(new Probe.Snapshot(0, 20), probe.snapshot());
        } finally {
            executor.shutdownNow();
        }
    }

    private MockHttpServletRequestBuilder normal(String path, String body) {
        return post(path).header("Authorization", "Bearer demo-token")
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body);
    }

    private MockHttpServletRequestBuilder precognitive(String body) {
        return normal("/users", body).header("Precognition", "true");
    }

    private MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn();
    }

    private JsonValue json(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private void assertSuccess(MvcResult result) throws Exception {
        assertEquals(204, result.getResponse().getStatus());
        assertEquals("true", result.getResponse().getHeader("Precognition"));
        assertEquals("true", result.getResponse().getHeader("Precognition-Success"));
        assertEquals("no-store", result.getResponse().getHeader("Cache-Control"));
        assertEquals("", result.getResponse().getContentAsString());
        assertNull(result.getResponse().getContentType());
    }
}
