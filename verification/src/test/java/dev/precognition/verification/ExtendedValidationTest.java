package dev.precognition.verification;

import dev.precognition.spring.*;

import dev.precognition.example.*;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.annotation.security.RolesAllowed;
import java.lang.annotation.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import dev.precognition.spring.JsonSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = ExampleApplication.class)
@Import(ExtendedValidationTest.Fixtures.class)
class ExtendedValidationTest {
    @Autowired WebApplicationContext context;
    @Autowired Probe probe;
    @Autowired JsonSupport mapper;
    @Autowired DemoAuthenticationFilter authentication;
    @Autowired PrecognitionProtocol protocol;
    @Autowired PrecognitionAuthenticationResponseFilter authResponses;
    MockMvc mvc;

    @BeforeEach
    void prepare() {
        probe.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(authResponses, authentication, protocol).build();
    }

    @Test
    void nestedNormalSubmitUsesCascadeValidation() throws Exception {
        var response = request("/verify/nested", "{\"address\":{\"city\":\"\"}}", false, null);
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("address.city"));
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test
    void nestedFullPrecognitionValidatesWithoutInvokingController() throws Exception {
        var response = request("/verify/nested", "{\"address\":{\"city\":\"Istanbul\"}}", true, null);
        assertEquals(204, response.getResponse().getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test
    void nestedSelectedPathIsValidated() throws Exception {
        var response = request("/verify/nested", "{\"address\":{\"city\":\"Istanbul\"}}", true, "address.city");
        assertEquals(204, response.getResponse().getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test
    void selectedNestedFailureUsesDotPath() throws Exception {
        var response = request("/verify/nested", "{\"address\":{\"city\":\"\"}}", true, "address.city");
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("address.city"));
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test
    void nestedLeafSelectionSkipsRootAndSiblingValidators() throws Exception {
        var response = request("/profiles", "{\"name\":\"taken\",\"address\":{\"city\":\"Istanbul\",\"postcode\":\"taken\"}}", true, "address.city");
        assertEquals(204, response.getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void selectingParentAndChildDoesNotValidateSubtreeTwice() throws Exception {
        var response = request("/profiles", "{\"name\":\"taken\",\"address\":{\"city\":\"Istanbul\",\"postcode\":\"taken\"}}", true, "address,address.city");
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("address.postcode"));
        assertEquals(new Probe.Snapshot(0, 1), probe.snapshot());
    }

    @Test
    void nullRequiredParentProducesParentConstraintError() throws Exception {
        var response = request("/profiles", "{\"name\":\"taken\",\"address\":null}", true, "address.city");
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("address"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test
    void fullNestedValidationExecutesEachRootAndChildRuleOnce() throws Exception {
        var response = request("/profiles", "{\"name\":\"taken\",\"address\":{\"city\":\"\",\"postcode\":\"taken\"}}", true, null);
        assertEquals(422, response.getResponse().getStatus());
        assertEquals(3, mapper.readTree(response.getResponse().getContentAsString()).get("errors").size());
        assertEquals(new Probe.Snapshot(0, 2), probe.snapshot());
    }

    @Test
    void selectedChildOfOptionalNullParentSkipsCascade() throws Exception {
        var response = request("/verify/nested", "{\"address\":null}", true, "address.city");
        assertEquals(204, response.getResponse().getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }


    @Test
    void fullModeRunsClassLevelConstraintWithoutInvokingController() throws Exception {
        var response = request("/verify/matching", "{\"password\":\"a\",\"confirmation\":\"b\"}", true, null);
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("_form"));
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test
    void normalClassLevelErrorsMustNotDisappearFromResponse() throws Exception {
        var response = request("/verify/matching", "{\"password\":\"a\",\"confirmation\":\"b\"}", false, null);
        assertEquals(422, response.getResponse().getStatus());
        assertTrue(mapper.readTree(response.getResponse().getContentAsString()).get("errors").has("_form"));
        assertEquals(0, probe.snapshot().controllerCalls());
    }



    @Test
    void exportActualSpringResponsesForOfficialClientTests() throws Exception {
        String valid = "{\"name\":\"Ada\",\"email\":\"ada@example.test\",\"age\":21}";
        String invalid = "{\"name\":\"taken\",\"email\":\"invalid\",\"age\":12}";
        Map<String, Object> fixtures = new LinkedHashMap<>();
        fixtures.put("selectedSuccess", fixture(request("/users", valid, true, "email")));
        fixtures.put("fullSuccess", fixture(request("/users", valid, true, null)));
        fixtures.put("selectedInvalid", fixture(request("/users", invalid, true, "email")));
        fixtures.put("normalSuccess", fixture(request("/users", valid, false, null)));
        fixtures.put("normalInvalid", fixture(request("/users", invalid, false, null)));
        fixtures.put("emptySelection", fixture(request("/users", valid, true, "")));
        fixtures.put("authDenied", fixture(mvc.perform(post("/users").contentType("application/json")
                .content(valid).header("Precognition", "true")).andReturn()));
        Path path = Path.of(System.getProperty("verification.fixtureFile", "target/validation/laravel-fixtures.json"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(fixtures));
    }

    private MvcResult request(String path, String body, boolean precognitive, String only) throws Exception {
        var request = post(path).contentType("application/json").accept("application/json")
                .header("Authorization", "Bearer demo-token").content(body);
        if (precognitive) request.header("Precognition", "true");
        if (only != null) request.header("Precognition-Validate-Only", only);
        return mvc.perform(request).andReturn();
    }

    private Map<String, Object> fixture(MvcResult result) throws Exception {
        var response = result.getResponse();
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) headers.put(name, response.getHeader(name));
        return Map.of("status", response.getStatus(), "headers", headers, "body", response.getContentAsString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Fixtures {
        @Bean FixtureController fixtureController(Probe probe) { return new FixtureController(probe); }
        @Bean RoleController roleController(Probe probe) { return new RoleController(probe); }
    }

    @RestController
    static class FixtureController {
        private final Probe probe;
        FixtureController(Probe probe) { this.probe = probe; }

        @Precognitive @PostMapping("/verify/nested")
        Object nested(@Valid @RequestBody Nested body) {
            probe.recordControllerCall();
            return body;
        }

        @PostMapping("/verify/collection")
        Object collection(@Valid @RequestBody CollectionModel body) {
            probe.recordControllerCall(); return body;
        }

        @Precognitive(mode = PrecognitionMode.FULL) @PostMapping("/verify/matching")
        Object matching(@Valid @RequestBody Matching body) {
            probe.recordControllerCall();
            return body;
        }

        @RolesAllowed("ADMIN")
        @PostMapping("/verify/role-method")
        Object roleMethod(@Valid @RequestBody UserController.CreateUser body) {
            probe.recordControllerCall(); return body;
        }
    }

    @RestController
    @RolesAllowed("ADMIN")
    static class RoleController {
        private final Probe probe;
        RoleController(Probe probe) { this.probe = probe; }
        @PostMapping("/verify/role-class")
        Object roleClass(@Valid @RequestBody UserController.CreateUser body) {
            probe.recordControllerCall(); return body;
        }
    }

    record Address(@NotBlank String city) {}
    record Nested(@Valid Address address) {}
    record CollectionModel(@Valid java.util.List<Address> addresses) {}
    @PasswordsMatch
    record Matching(String password, String confirmation) {}

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PasswordsMatchValidator.class)
    public @interface PasswordsMatch {
        String message() default "Passwords must match.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    public static class PasswordsMatchValidator implements ConstraintValidator<PasswordsMatch, Matching> {
        @Override public boolean isValid(Matching value, ConstraintValidatorContext context) {
            return java.util.Objects.equals(value.password(), value.confirmation());
        }
    }
}
