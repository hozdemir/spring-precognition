package dev.precognition.example;

import dev.precognition.spring.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import dev.precognition.spring.JsonSupport.JsonValue;
import dev.precognition.spring.JsonSupport;

import static org.junit.jupiter.api.Assertions.*;

/** Real TCP/HTTP through embedded Tomcat, including Boot's actual filter registration. No MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.address=127.0.0.1")
class PrecognitionHttpIT {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final String VALID = """
            {"name":"Ada","email":"ada@example.test","age":21}
            """;
    private static final String EMAIL_ONLY = """
            {"name":"taken","email":"ada@example.test","age":12}
            """;
    private static final String INVALID = """
            {"name":"taken","email":"invalid","age":12}
            """;

    @Value("${local.server.port}") int port;
    @Autowired Probe probe;
    @Autowired JsonSupport mapper;

    @BeforeEach
    void resetCounters() {
        probe.reset();
    }

    @Test
    void normalSubmitRunsActionExactlyOnceOverHttp() throws Exception {
        var response = post("/users", VALID, false, null, true);
        assertEquals(201, response.statusCode());
        assertEquals("Ada", json(response).get("name").asString());
        assertTrue(response.headers().firstValue("Precognition").isEmpty());
        assertCounters(1, 1);
    }

    @Test
    void fullPrecognitionValidatesButDoesNotRunActionOverHttp() throws Exception {
        assertSuccess(post("/users", VALID, true, null, true));
        assertCounters(0, 1);
    }

    @Test
    void selectedSuccessSkipsUnselectedValidatorOverHttp() throws Exception {
        assertSuccess(post("/users", EMAIL_ONLY, true, "email", true));
        assertCounters(0, 0);
    }

    @Test
    void selectedFailureOnlyReportsEmailOverHttp() throws Exception {
        var response = post("/users", INVALID, true, "email", true);
        assertEquals(422, response.statusCode());
        assertEquals("true", response.headers().firstValue("Precognition").orElseThrow());
        assertTrue(response.headers().firstValue("Precognition-Success").isEmpty());
        var errors = json(response).get("errors");
        assertEquals(1, errors.size());
        assertTrue(errors.get("email").isArray());
        assertCounters(0, 0);
    }

    @Test
    void fullFailureReportsAllFieldsOverHttp() throws Exception {
        var response = post("/users", INVALID, true, null, true);
        assertEquals(422, response.statusCode());
        var errors = json(response).get("errors");
        assertEquals(3, errors.size());
        assertTrue(errors.has("name"));
        assertTrue(errors.has("email"));
        assertTrue(errors.has("age"));
        assertCounters(0, 1);
    }

    @Test
    void laterSubmitStillPerformsFullValidationOverHttp() throws Exception {
        assertSuccess(post("/users", EMAIL_ONLY, true, "email", true));
        var response = post("/users", EMAIL_ONLY, false, null, true);
        assertEquals(422, response.statusCode());
        assertTrue(json(response).get("errors").has("name"));
        assertTrue(json(response).get("errors").has("age"));
        assertCounters(0, 1);
    }

    @Test
    void bootRegisteredAuthenticationFilterRejectsBothRequestTypesOverHttp() throws Exception {
        var denied = post("/users", VALID, true, "email", false);
        assertEquals(401, denied.statusCode());
        assertEquals("true", denied.headers().firstValue("Precognition").orElseThrow());
        assertTrue(denied.headers().firstValue("Precognition-Success").isEmpty());
        assertEquals(401, post("/users", VALID, false, null, false).statusCode());
        assertCounters(0, 0);
    }

    @Test
    void nonOptedInEndpointRejectsPrecognitionOverHttp() throws Exception {
        var response = post("/legacy-users", VALID, true, null, true);
        assertEquals(412, response.statusCode());
        assertEquals("PRECOGNITION_NOT_ENABLED", json(response).get("code").asString());
        assertCounters(0, 0);
    }

    @Test
    void unknownFieldIsRejectedBeforeValidationOverHttp() throws Exception {
        var response = post("/users", VALID, true, "unknown", true);
        assertEquals(400, response.statusCode());
        assertEquals("UNKNOWN_VALIDATION_FIELD", json(response).get("code").asString());
        assertCounters(0, 0);
    }

    @Test
    void parallelHttpRequestsDoNotLeakValidationScope() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> requests = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                boolean selected = i % 2 == 0;
                requests.add(() -> post("/users", EMAIL_ONLY, selected,
                        selected ? "email" : null, true).statusCode());
            }
            var results = executor.invokeAll(requests);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(i % 2 == 0 ? 204 : 422, results.get(i).get());
            }
            assertCounters(0, 20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void emptySelectionSkipsAllValidatorsOverHttp() throws Exception {
        assertSuccess(post("/users", INVALID, true, "", true));
        assertCounters(0, 0);
    }

    @Test
    void nestedLeafSelectionSkipsRootAndSiblingValidatorsOverHttp() throws Exception {
        assertSuccess(post("/profiles", """
                {"name":"taken","address":{"city":"Istanbul","postcode":"taken"}}
                """, true, "address.city", true));
        assertCounters(0, 0);
    }

    @Test
    void nestedFailureUsesDotPathOverHttp() throws Exception {
        var response = post("/profiles", """
                {"name":"taken","address":{"city":"","postcode":"taken"}}
                """, true, "address.city", true);
        assertEquals(422, response.statusCode());
        assertEquals(1, json(response).get("errors").size());
        assertTrue(json(response).get("errors").has("address.city"));
        assertCounters(0, 0);
    }

    @Test
    void chunkedBodyLimitRejectsBeforeValidationOrController() throws Exception {
        byte[] bytes = ("{\"name\":\"" + "x".repeat(70000) + "\",\"email\":\"ada@example.test\",\"age\":21}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = HTTP.send(HttpRequest.newBuilder(uri("/users"))
                .header("Content-Type", "application/json").header("Authorization", "Bearer demo-token")
                .header("Precognition", "true").POST(HttpRequest.BodyPublishers.ofInputStream(
                        () -> new java.io.ByteArrayInputStream(bytes))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(413, response.statusCode());
        assertCounters(0, 0);
    }

    @Test
    void chunkedValidJsonWithOversizedTrailingWhitespaceIsRejectedBeforeValidation() throws Exception {
        byte[] bytes = (VALID + " ".repeat(100000)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = HTTP.send(HttpRequest.newBuilder(uri("/users"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Authorization", "Bearer demo-token")
                .header("Precognition", "true").POST(HttpRequest.BodyPublishers.ofInputStream(
                        () -> new java.io.ByteArrayInputStream(bytes))).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(413, response.statusCode());
        assertTrue(response.headers().firstValue("Precognition-Success").isEmpty());
        assertCounters(0, 0);
    }

    @Test
    void chunkedValidJsonExactlyAtTheBodyLimitStillSucceeds() throws Exception {
        byte[] bytes = (VALID + " ".repeat(65536 - VALID.getBytes(java.nio.charset.StandardCharsets.UTF_8).length))
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = HTTP.send(HttpRequest.newBuilder(uri("/users"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("Authorization", "Bearer demo-token")
                .header("Precognition", "true").POST(HttpRequest.BodyPublishers.ofInputStream(
                        () -> new java.io.ByteArrayInputStream(bytes))).build(), HttpResponse.BodyHandlers.ofString());
        assertSuccess(response);
        assertCounters(0, 1);
    }

    private HttpResponse<String> post(String path, String body, boolean precognitive,
                                      String fields, boolean authenticated) throws Exception {
        var request = HttpRequest.newBuilder(uri(path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (authenticated) request.header("Authorization", "Bearer demo-token");
        if (precognitive) request.header("Precognition", "true");
        if (fields != null) request.header("Precognition-Validate-Only", fields);
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private JsonValue json(HttpResponse<String> response) throws Exception {
        return mapper.readTree(response.body());
    }

    private void assertCounters(int controllerCalls, int nameValidationCalls) throws Exception {
        var response = HTTP.send(HttpRequest.newBuilder(uri("/demo/counters"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var counters = json(response);
        assertEquals(controllerCalls, counters.get("controllerCalls").asInt());
        assertEquals(nameValidationCalls, counters.get("nameValidationCalls").asInt());
    }

    private void assertSuccess(HttpResponse<String> response) {
        assertEquals(204, response.statusCode());
        assertEquals("true", response.headers().firstValue("Precognition").orElseThrow());
        assertEquals("true", response.headers().firstValue("Precognition-Success").orElseThrow());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.headers().firstValue("Content-Type").isEmpty());
        assertEquals("", response.body());
    }
}
