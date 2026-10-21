package dev.precognition.verification.security;

import dev.precognition.spring.*;

import dev.precognition.example.*;
import jakarta.validation.Valid;
import java.lang.annotation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import dev.precognition.spring.JsonSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

/** Optional profile: actual Spring Security filter chain, CSRF and method-security proxies. */
@SpringBootTest(classes = ExampleApplication.class, properties = "demo.authentication.enabled=false")
@Import(SecurityVerificationTest.SecurityFixtures.class)
class SecurityVerificationTest {
    @Autowired WebApplicationContext context;
    @Autowired Probe probe;
    @Autowired PrecognitionProtocol protocol;
    @Autowired PrecognitionAuthenticationResponseFilter authResponses;
    @Autowired JsonSupport mapper;
    MockMvc mvc;

    @BeforeEach void setUp() {
        probe.reset();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(authResponses).apply(springSecurity()).addFilters(protocol).build();
    }

    @Test void anonymousRequestsAreRejectedBeforeEitherValidationPath() throws Exception {
        assertEquals(401, mvc.perform(request("/users", false, true)).andReturn().getResponse().getStatus());
        var denied = mvc.perform(request("/users", true, true)).andReturn().getResponse();
        assertEquals(401, denied.getStatus());
        assertEquals("true", denied.getHeader("Precognition"));
        assertNull(denied.getHeader("Precognition-Success"));
        assertTrue(denied.getHeader("WWW-Authenticate").startsWith("Basic"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test void csrfIsRequiredForBothPaths() throws Exception {
        assertEquals(403, mvc.perform(request("/users", false, false).with(httpBasic("demo", "password")))
                .andReturn().getResponse().getStatus());
        var denied = mvc.perform(request("/users", true, false).with(httpBasic("demo", "password")))
                .andReturn().getResponse();
        assertEquals(403, denied.getStatus());
        assertEquals("true", denied.getHeader("Precognition"));
        assertNull(denied.getHeader("Precognition-Success"));
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test void authorizedNormalSubmitWorksWithRealSecurityChain() throws Exception {
        assertEquals(201, mvc.perform(request("/users", false, true).with(httpBasic("demo", "password")))
                .andReturn().getResponse().getStatus());
        assertEquals(new Probe.Snapshot(1, 1), probe.snapshot());
    }

    @Test void authorizedSelectedValidationStillSkipsActionAndUnselectedValidator() throws Exception {
        var response = mvc.perform(request("/users", true, true).with(httpBasic("demo", "password"))
                .header("Precognition-Validate-Only", "email")).andReturn().getResponse();
        assertEquals(204, response.getStatus());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test void normalMethodSecurityDeniesNonAdmin() throws Exception {
        assertEquals(403, mvc.perform(request("/security/admin", false, true)
                .with(httpBasic("demo", "password"))).andReturn().getResponse().getStatus());
        assertEquals(0, probe.snapshot().controllerCalls());
    }

    @Test void normalMethodSecurityAllowsAdmin() throws Exception {
        assertEquals(201, mvc.perform(request("/security/admin", false, true)
                .with(httpBasic("admin", "password"))).andReturn().getResponse().getStatus());
        assertEquals(1, probe.snapshot().controllerCalls());
    }

    @Test void precognitionRejectsUnsupportedMethodSecurityInsteadOfBypassingIt() throws Exception {
        for (String username : new String[]{"demo", "admin"}) {
            var response = mvc.perform(request("/security/admin", true, true)
                    .with(httpBasic(username, "password"))).andReturn().getResponse();
            assertEquals(412, response.getStatus());
            assertEquals("PRECOGNITION_NOT_ENABLED", mapper.readTree(response.getContentAsString()).get("code").asString());
        }
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    @Test void composedMethodSecurityIsAlsoRejected() throws Exception {
        var response = mvc.perform(request("/security/composed", true, true)
                .with(httpBasic("demo", "password"))).andReturn().getResponse();
        assertEquals(412, response.getStatus());
        assertEquals("PRECOGNITION_NOT_ENABLED", mapper.readTree(response.getContentAsString()).get("code").asString());
        assertEquals(new Probe.Snapshot(0, 0), probe.snapshot());
    }

    private MockHttpServletRequestBuilder request(String path, boolean precognitive, boolean csrfToken) {
        var request = post(path).contentType("application/json").accept("application/json")
                .content("{\"name\":\"Ada\",\"email\":\"ada@example.test\",\"age\":21}");
        if (precognitive) request.header("Precognition", "true");
        if (csrfToken) request.with(csrf());
        return request;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityFixtures {
        @Bean UserDetailsService users() {
            return new InMemoryUserDetailsManager(
                    User.withUsername("demo").password("{noop}password").roles("USER").build(),
                    User.withUsername("admin").password("{noop}password").roles("USER", "ADMIN").build());
        }
        @Bean SecurityFilterChain chain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults()).build();
        }
        @Bean SecuredController securedController(Probe probe) { return new SecuredController(probe); }
    }

    @RestController
    public static class SecuredController {
        private final Probe probe;
        SecuredController(Probe probe) { this.probe = probe; }

        @PreAuthorize("hasRole('ADMIN')")
        @PostMapping("/security/admin") @ResponseStatus(HttpStatus.CREATED)
        public Object admin(@Valid @RequestBody UserController.CreateUser body) {
            probe.recordControllerCall(); return body;
        }

        @AdminOnly
        @PostMapping("/security/composed")
        public Object composed(@Valid @RequestBody UserController.CreateUser body) {
            probe.recordControllerCall(); return body;
        }
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @PreAuthorize("hasRole('ADMIN')")
    public @interface AdminOnly {}
}
