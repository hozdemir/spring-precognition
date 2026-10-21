package dev.precognition.verification;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.precognition.example.*;
import dev.precognition.spring.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Tests the starter as an external consumer; no library package is scanned by the application. */
@SpringBootTest(classes = ExampleApplication.class, properties = "spring.jackson.property-naming-strategy=SNAKE_CASE")
@Import(StarterBehaviorTest.Fixtures.class)
class StarterBehaviorTest {
    @Autowired WebApplicationContext context;
    @Autowired PrecognitionProtocol protocol;
    @Autowired Probe probe;
    @Autowired JsonSupport json;
    @Autowired PrecognitionSettings settings;
    @Autowired PrecognitionContextAccessor accessor;
    @Autowired Capture observations;
    MockMvc mvc;
    @BeforeEach void prepare() { probe.reset(); mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(protocol).build(); }
    @AfterEach void restore() {
        settings.setEnabled(true); settings.setNormalErrors(PrecognitionSettings.NormalErrors.LARAVEL);
        settings.getLimits().setMaxResponseBytes(65536);
    }
    private org.springframework.mock.web.MockHttpServletResponse request(String path, String payload, boolean active, String selected) throws Exception {
        var request = post(path).contentType("application/json").content(payload);
        if (active) request.header("Precognition", "true");
        if (selected != null) request.header("Precognition-Validate-Only", selected);
        return mvc.perform(request).andReturn().getResponse();
    }
    @Test void jsonPropertyUsesWireNameForSelectionAndErrors() throws Exception {
        var response = request("/starter/named", "{\"first_name\":\"\"}", true, "first_name");
        assertEquals(422, response.getStatus());
        assertTrue(json.readTree(response.getContentAsString()).get("errors").has("first_name"));
        assertEquals(400, request("/starter/named", "{\"first_name\":\"Ada\"}", true, "firstName").getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void applicationNamingStrategyUsesWireNamesWithoutJavaFallback() throws Exception {
        var response = request("/starter/naming", "{\"display_name\":\"\"}", true, "display_name");
        assertEquals(422, response.getStatus());
        assertTrue(json.readTree(response.getContentAsString()).get("errors").has("display_name"));
        assertEquals(400, request("/starter/naming", "{}", true, "displayName").getStatus());
    }
    @Test void localesMatchNormalValidationAndRemainIsolatedInParallel() throws Exception {
        java.util.Map<String, String> messages = new java.util.HashMap<>();
        for (String locale : new String[]{"en", "tr"}) {
            for (boolean active : new boolean[]{false, true}) {
                var builder = post("/starter/named").contentType("application/json").content("{\"first_name\":\"\"}")
                        .locale(java.util.Locale.forLanguageTag(locale));
                if (active) builder.header("Precognition", "true");
                var response = mvc.perform(builder).andReturn().getResponse();
                assertEquals(422, response.getStatus());
                String message = response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                if (active) assertEquals(messages.get(locale), message); else messages.put(locale, message);
            }
        }
        assertNotEquals(messages.get("en"), messages.get("tr"));
        var executor = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<Void>>();
            for (int i = 0; i < 20; i++) {
                String locale = i % 2 == 0 ? "en" : "tr";
                tasks.add(() -> {
                    var response = mvc.perform(post("/starter/named").contentType("application/json").content("{\"first_name\":\"\"}")
                            .header("Precognition", "true").locale(java.util.Locale.forLanguageTag(locale))).andReturn().getResponse();
                    assertEquals(messages.get(locale), response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) result.get();
        } finally { executor.shutdownNow(); }
    }
    @Test void normalErrorUsesTheSameWireName() throws Exception {
        var response = request("/starter/named", "{\"first_name\":\"\"}", false, null);
        assertEquals(422, response.getStatus());
        assertTrue(json.readTree(response.getContentAsString()).get("errors").has("first_name"));
        assertNull(response.getHeader("Precognition"));
    }
    @Test void ordinaryPojoAndStandardScalarTypesAreSupported() throws Exception {
        assertEquals(204, request("/starter/pojo", "{\"name\":\"Ada\"}", true, "name").getStatus());
        assertEquals(422, request("/starter/pojo", "{\"name\":\"\"}", true, "name").getStatus());
        assertEquals(204, request("/starter/scalars", "{\"amount\":12.5,\"enabled\":true,\"date\":\"2026-10-21\",\"id\":\"d83a224c-2796-4b4c-b9bb-2573e1895bd1\"}", true, null).getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void additionalBuiltInParametersAreBoundWithoutInvokingAction() throws Exception {
        assertEquals(204, request("/starter/params/12?revision=3", "{\"name\":\"Ada\"}", true, "name").getStatus());
        assertEquals(400, request("/starter/params/not-a-number?revision=3", "{\"name\":\"Ada\"}", true, "name").getStatus());
        assertEquals(400, request("/starter/params/12", "{\"name\":\"Ada\"}", true, "name").getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void authorizerProtectsBothRequestModesBeforeBinding() throws Exception {
        for (boolean active : new boolean[]{true, false}) {
            assertEquals(403, request("/starter/protected", "{broken", active, null).getStatus());
        }
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void disabledGuardNeverFallsThroughToNormalSubmit() throws Exception {
        settings.setEnabled(false);
        var response = request("/starter/pojo", "{\"name\":\"Ada\"}", true, null);
        assertEquals(412, response.getStatus());
        assertEquals("PRECOGNITION_DISABLED", json.readTree(response.getContentAsString()).get("code").asString());
        assertEquals(200, request("/starter/pojo", "{\"name\":\"Ada\"}", false, null).getStatus());
        assertEquals(1, probe.snapshot().controllerCalls());
        assertEquals(403, request("/starter/protected", "{}", false, null).getStatus());
    }
    @Test void preserveLeavesNormalValidationToSpring() throws Exception {
        settings.setNormalErrors(PrecognitionSettings.NormalErrors.PRESERVE);
        assertEquals(400, request("/starter/pojo", "{\"name\":\"\"}", false, null).getStatus());
        assertEquals(422, request("/starter/pojo", "{\"name\":\"\"}", true, null).getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void bodyLimitAppliesBeforeValidation() throws Exception {
        String large = "{\"name\":\"" + "x".repeat(70000) + "\"}";
        assertEquals(413, request("/starter/pojo", large, true, null).getStatus());

    }
    @Test void headerAndResponseLimitsProduceErrorsRatherThanPartialValidation() throws Exception {
        assertEquals(431, request("/starter/pojo", "{}", true, "x".repeat(129)).getStatus());
        settings.getLimits().setMaxResponseBytes(256);
        assertEquals(500, request("/starter/long-message", "{\"name\":\"\"}", true, null).getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void observationsUseBoundedTagsAndCanBeDisabled() throws Exception {
        int before = observations.stopped.size();
        assertEquals(204, request("/starter/pojo", "{\"name\":\"Ada\"}", true, "name").getStatus());
        assertEquals(before + 1, observations.stopped.size());
        var tags = observations.stopped.get(observations.stopped.size() - 1);
        assertEquals(java.util.Map.of("scope", "selected", "status", "204"), tags);
        settings.getObservability().setMetricsEnabled(false);
        try {
            assertEquals(204, request("/starter/pojo", "{\"name\":\"Ada\"}", true, "name").getStatus());
            assertEquals(before + 1, observations.stopped.size());
        } finally { settings.getObservability().setMetricsEnabled(true); }
    }
    @Test void applicationBodyAdviceAndGlobalExceptionPolicyRemainEffective() throws Exception {
        assertEquals(204, request("/starter/advice", "{\"name\":\"\"}", true, null).getStatus());
        assertEquals(200, request("/starter/advice", "{\"name\":\"\"}", false, null).getStatus());
        for (boolean active : new boolean[]{true, false}) assertEquals(422, request("/starter/global", "{\"first_name\":\"\"}", active, null).getStatus());
        assertEquals(400, request("/starter/global", "{broken", true, null).getStatus());
        assertEquals(418, request("/starter/global", "{broken", false, null).getStatus());
        assertEquals(418, request("/starter/service-error", "{\"name\":\"Ada\"}", false, null).getStatus());
        assertEquals(2, probe.snapshot().controllerCalls());
    }
    @Test void failingValidatorReturnsInternalErrorWithoutLeakingInputs() throws Exception {
        var response = request("/starter/exploding", "{\"name\":\"private-input\"}", true, null);
        assertEquals(500, response.getStatus());
        assertEquals("PRECOGNITION_INTERNAL_ERROR", json.readTree(response.getContentAsString()).get("code").asString());
        assertFalse(response.getContentAsString().contains("private-input"));
        assertFalse(response.getContentAsString().contains("secret"));
        assertNull(response.getHeader("Precognition-Success"));
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void requestContextDoesNotEscapeRequestLifetime() throws Exception {
        assertTrue(accessor.current().isEmpty());
        assertEquals(204, request("/starter/pojo", "{\"name\":\"Ada\"}", true, null).getStatus());
        assertTrue(accessor.current().isEmpty());
    }
    @Test void varyHeadersArePreservedAndDeduplicated() throws Exception {
        for (String vary : new String[]{"Accept-Encoding, precognition", "*"}) {
            jakarta.servlet.Filter seed = (request, response, chain) -> {
                ((jakarta.servlet.http.HttpServletResponse) response).setHeader("Vary", vary); chain.doFilter(request, response);
            };
            var local = MockMvcBuilders.webAppContextSetup(context).addFilters(seed, protocol).build();
            var response = local.perform(post("/starter/pojo").contentType("application/json")
                    .header("Precognition", "true").content("{\"name\":\"Ada\"}")).andReturn().getResponse();
            assertEquals(204, response.getStatus());
            assertEquals(vary.equals("*") ? "*" : "Accept-Encoding, precognition, Precognition-Validate-Only", response.getHeader("Vary"));
        }
    }
    @Test void malformedBodiesMediaAndDispatchNeverBecomeActions() throws Exception {
        for (String body : new String[]{"", "null", "[]", "{broken"}) assertEquals(400, request("/starter/pojo", body, true, null).getStatus());
        assertEquals(415, mvc.perform(post("/starter/pojo").contentType("text/plain").header("Precognition", "true").content("{}"))
                .andReturn().getResponse().getStatus());
        assertEquals(415, mvc.perform(post("/starter/pojo").contentType("application/json").header("Content-Encoding", "gzip")
                .header("Precognition", "true").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(406, mvc.perform(post("/starter/pojo").contentType("application/json").accept("text/html")
                .header("Precognition", "true").content("{}")).andReturn().getResponse().getStatus());
        assertEquals(400, mvc.perform(post("/starter/pojo").contentType("application/json").header("Precognition", "true")
                .content("{}").with(request -> { request.setDispatcherType(jakarta.servlet.DispatcherType.FORWARD); return request; }))
                .andReturn().getResponse().getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @Test void fullOnlyModeRejectsEvenEmptyFieldSelection() throws Exception {
        assertEquals(204, request("/starter/full", "{\"name\":\"Ada\"}", true, null).getStatus());
        assertEquals(400, request("/starter/full", "{\"name\":\"Ada\"}", true, "").getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }
    @TestConfiguration(proxyBeanMethods = false) static class Fixtures {
        @Bean BodyAdviceController bodyAdviceController(Probe probe) { return new BodyAdviceController(probe); }
        @Bean ApplicationAdvice applicationAdvice() { return new ApplicationAdvice(); }
        @Bean Capture capture() { return new Capture(); }
        @Bean io.micrometer.observation.ObservationRegistry observationRegistry(Capture capture) {
            var registry = io.micrometer.observation.ObservationRegistry.create();
            registry.observationConfig().observationHandler(capture); return registry;
        }
        @Bean FixtureController starterController(Probe probe) { return new FixtureController(probe); }
        @Bean PrecognitionAuthorizer authorizer() { return request -> new AuthorizationDecision(!request.route().equals("/starter/protected")); }
    }
    @RestController static class FixtureController {
        final Probe probe;
        FixtureController(Probe probe) { this.probe = probe; }
        Object called(Object body) { probe.recordControllerCall(); return body; }
        @Precognitive @PostMapping("/starter/exploding") Object exploding(@Valid @RequestBody Broken body) { return called(body); }
        @Precognitive @PostMapping("/starter/naming") Object naming(@Valid @RequestBody Naming body) { return called(body); }
        @Precognitive @PostMapping("/starter/named") Object named(@Valid @RequestBody Named body) { return called(body); }
        @Precognitive @PostMapping("/starter/pojo") Object pojo(@Valid @RequestBody Pojo body) { return called(body); }
        @Precognitive @PostMapping("/starter/scalars") Object scalars(@Valid @RequestBody Scalars body) { return called(body); }
        @Precognitive @PostMapping("/starter/params/{id}") Object params(@PathVariable Long id, @RequestParam Integer revision, @Valid @RequestBody Pojo body) { return called(body); }
        @Precognitive @PostMapping("/starter/protected") Object denied(@Valid @RequestBody Pojo body) { return called(body); }
        @Precognitive @PostMapping("/starter/long-message") Object large(@Valid @RequestBody LongMessage body) { return called(body); }
        @Precognitive(mode = PrecognitionMode.FULL) @PostMapping("/starter/full") Object full(@Valid @RequestBody Pojo body) { return called(body); }
    }
    @RestController static class BodyAdviceController {
        final Probe probe;
        BodyAdviceController(Probe probe) { this.probe = probe; }
        @Precognitive @PostMapping("/starter/advice") Object advice(@Valid @RequestBody Pojo body) { probe.recordControllerCall(); return body; }
        @Precognitive @PostMapping("/starter/global") Object global(@Valid @RequestBody Named body) { probe.recordControllerCall(); return body; }
        @Precognitive @PostMapping("/starter/service-error") Object error(@Valid @RequestBody Pojo body) { probe.recordControllerCall(); throw new IllegalArgumentException("application exception"); }
    }
    @RestControllerAdvice(assignableTypes = BodyAdviceController.class)
    static class ApplicationAdvice extends org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter {
        @Override public boolean supports(org.springframework.core.MethodParameter parameter, java.lang.reflect.Type type,
                Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter) {
            return parameter.getMethod().getName().equals("advice");
        }
        @Override public Object afterBodyRead(Object body, org.springframework.http.HttpInputMessage input, org.springframework.core.MethodParameter parameter,
                java.lang.reflect.Type type, Class<? extends org.springframework.http.converter.HttpMessageConverter<?>> converter) {
            ((Pojo) body).name = "Ada"; return body;
        }
        @ExceptionHandler(Exception.class) org.springframework.http.ResponseEntity<?> unexpected(Exception exception) {
            return org.springframework.http.ResponseEntity.status(418).body(java.util.Map.of("source", "application"));
        }
    }
    static class Capture implements io.micrometer.observation.ObservationHandler<io.micrometer.observation.Observation.Context> {
        final java.util.List<java.util.Map<String, String>> stopped = new java.util.concurrent.CopyOnWriteArrayList<>();
        public boolean supportsContext(io.micrometer.observation.Observation.Context context) { return context.getName().equals("precognition.request"); }
        public void onStop(io.micrometer.observation.Observation.Context context) {
            java.util.Map<String, String> tags = new java.util.TreeMap<>();
            for (var tag : context.getLowCardinalityKeyValues()) tags.put(tag.getKey(), tag.getValue());
            stopped.add(tags);
        }
    }
    @java.lang.annotation.Target(java.lang.annotation.ElementType.FIELD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @jakarta.validation.Constraint(validatedBy = ExplodingValidator.class)
    public @interface Explodes {
        String message() default "Invalid value";
        Class<?>[] groups() default {};
        Class<? extends jakarta.validation.Payload>[] payload() default {};
    }
    public static class ExplodingValidator implements jakarta.validation.ConstraintValidator<Explodes, String> {
        public boolean isValid(String value, jakarta.validation.ConstraintValidatorContext context) { throw new IllegalStateException("secret validator detail"); }
    }
    record Broken(@Explodes String name) {}
    record Naming(@NotBlank String displayName) {}
    record Named(@JsonProperty("first_name") @NotBlank String firstName) {}
    record Scalars(@NotNull BigDecimal amount, @NotNull Boolean enabled, @NotNull LocalDate date, @NotNull UUID id) {}
    public static class Pojo { @NotBlank public String name; }
    record LongMessage(@NotBlank(message = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA") String name) {}
}
