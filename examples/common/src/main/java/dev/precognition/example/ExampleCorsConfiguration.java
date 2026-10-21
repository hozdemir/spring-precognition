package dev.precognition.example;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.*;
import org.springframework.web.filter.CorsFilter;

/** Example-owned policy before authentication, including preflight and early 401 responses. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("demo.cors-origin")
public class ExampleCorsConfiguration {
    @Bean FilterRegistrationBean<CorsFilter> exampleCors(@Value("${demo.cors-origin}") String origin) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("POST", "PUT", "PATCH"));
        cors.setAllowedHeaders(List.of("Content-Type", "Accept", "Authorization", "Precognition", "Precognition-Validate-Only", "X-XSRF-TOKEN", "X-CSRF-TOKEN"));
        cors.setExposedHeaders(List.of("Precognition", "Precognition-Success"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/users", cors);
        source.registerCorsConfiguration("/profiles", cors);
        var registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
