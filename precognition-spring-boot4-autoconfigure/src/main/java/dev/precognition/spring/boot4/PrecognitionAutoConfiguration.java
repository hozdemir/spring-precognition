package dev.precognition.spring.boot4;

import dev.precognition.spring.JsonSupport;
import dev.precognition.spring.PrecognitionConfiguration;
import java.io.IOException;
import java.io.OutputStream;
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.ObjectMapper;

@org.springframework.boot.autoconfigure.AutoConfiguration
@org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication(type = org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET)
@org.springframework.context.annotation.Import({PrecognitionConfiguration.class})
public class PrecognitionAutoConfiguration {
    @Bean
    @org.springframework.boot.context.properties.ConfigurationProperties("precognition")
    dev.precognition.spring.PrecognitionSettings precognitionSettings() {
        return new dev.precognition.spring.PrecognitionSettings();
    }
    @Bean dev.precognition.spring.PrecognitionProtocol precognitionProtocol(JsonSupport json, dev.precognition.spring.PrecognitionSettings settings, org.springframework.beans.factory.ObjectProvider<io.micrometer.observation.ObservationRegistry> observations) {
        return new dev.precognition.spring.PrecognitionProtocol(json, settings, observations.getIfAvailable(() -> io.micrometer.observation.ObservationRegistry.NOOP));
    }
    @Bean org.springframework.boot.web.servlet.FilterRegistrationBean<dev.precognition.spring.PrecognitionProtocol> precognitionProtocolRegistration(dev.precognition.spring.PrecognitionProtocol filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setOrder(10);
        registration.setDispatcherTypes(jakarta.servlet.DispatcherType.REQUEST, jakarta.servlet.DispatcherType.FORWARD, jakarta.servlet.DispatcherType.ASYNC);
        registration.setAsyncSupported(true);
        return registration;
    }
    @Bean dev.precognition.spring.PrecognitionAuthenticationResponseFilter precognitionAuthenticationResponses() {
        return new dev.precognition.spring.PrecognitionAuthenticationResponseFilter();
    }
    @Bean dev.precognition.spring.PrecognitionContextAccessor precognitionContextAccessor() {
        return () -> {
            var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attributes == null) return java.util.Optional.empty();
            return java.util.Optional.ofNullable((dev.precognition.spring.PrecognitionContext) attributes.getAttribute(
                    dev.precognition.spring.PrecognitionConfiguration.class.getName() + ".context", org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST));
        };
    }
    @Bean JsonSupport jsonSupport(ObjectMapper mapper) {
        return new JsonSupport() {
            public boolean usesApplicationValidator(org.springframework.validation.Validator mvc, jakarta.validation.Validator validator) {
                return mvc == validator || (mvc instanceof org.springframework.boot.validation.autoconfigure.ValidatorAdapter adapter && adapter.getTarget() == validator);
            }
            public boolean usesApplicationMapper(org.springframework.http.converter.HttpMessageConverter<?> converter, Class<?> type) {
                if (!(converter instanceof org.springframework.http.converter.json.JacksonJsonHttpMessageConverter jackson) || jackson.getMapper() != mapper) return false;
                var overrides = jackson.getMappersForType(type);
                return overrides == null || overrides.values().stream().allMatch(value -> value == mapper);
            }
            public java.util.List<JsonProperty> properties(Class<?> type) {
                var config = mapper.deserializationConfig();
                var introspector = config.classIntrospectorInstance().forOperation(config);
                var javaType = mapper.constructType(type);
                return introspector.introspectForDeserialization(javaType, introspector.introspectClassAnnotations(javaType)).findProperties().stream()
                        .filter(property -> property.couldDeserialize())
                        .map(property -> new JsonProperty(property.getName(), property.getInternalName(), property.getPrimaryType().getRawClass()))
                        .toList();
            }
            public void writeValue(OutputStream out, Object value) throws IOException { mapper.writeValue(out, value); }
            public String writeValueAsString(Object value) { return mapper.writeValueAsString(value); }
            public JsonValue readTree(String json) { return new JsonValue(mapper.readValue(json, Object.class)); }
        };
    }

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(WebMvcRegistrations.class)
    WebMvcRegistrations precognitionRegistrations(PrecognitionConfiguration configuration) {
        return new WebMvcRegistrations() {
            @Override public RequestMappingHandlerAdapter getRequestMappingHandlerAdapter() {
                return configuration.createHandlerAdapter();
            }
        };
    }
}
