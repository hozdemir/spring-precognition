package dev.precognition.verification;

import dev.precognition.spring.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import static org.junit.jupiter.api.Assertions.*;

class StartupVerificationTest {
    final WebApplicationContextRunner runner = new WebApplicationContextRunner().withUserConfiguration(Base.class);
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration static class Base {}
    void rejects(Class<?> fixture, String reason) {
        runner.withUserConfiguration(fixture).run(context -> {
            assertNotNull(context.getStartupFailure());
            StringBuilder messages = new StringBuilder();
            for (Throwable failure = context.getStartupFailure(); failure != null; failure = failure.getCause()) messages.append(failure.getMessage());
            assertTrue(messages.toString().contains(reason), messages.toString());
        });
    }
    @Test void validConsumerAutomaticallyInstallsAdapterWithoutScanningLibrary() {
        runner.withUserConfiguration(ValidController.class).run(context -> {
            assertNull(context.getStartupFailure());
            assertNotNull(context.getBean(PrecognitionProtocol.class));
            assertNotNull(context.getBean(PrecognitionContextAccessor.class));
        });
    }
    @Test void collectionsFailAtStartup() { rejects(CollectionController.class, "containers are unsupported"); }
    @Test void missingValidationAnnotationFailsAtStartup() { rejects(UnvalidatedController.class, "requires @Valid"); }
    @Test void methodSecurityFailsAtStartup() { rejects(SecuredController.class, "Method security"); }
    @Test void binderCustomizationFailsAtStartup() { rejects(BinderController.class, "@InitBinder"); }
    @Test void customMvcAdapterFailsAtStartup() { rejects(CustomAdapter.class, "MVC handler adapter conflict"); }
    @Test void arbitraryControllerProxyFailsAtStartup() { rejects(ProxiedController.class, "Proxied opt-in"); }
    @Test void customMvcValidatorFailsAtStartup() { rejects(CustomValidator.class, "same Bean Validation validator"); }
    @Test void customJsonConverterFailsAtStartup() { rejects(CustomConverter.class, "application's JSON mapper"); }
    @Test void invalidLimitsFailAtStartupEvenWhenExecutionIsDisabled() {
        runner.withPropertyValues("precognition.enabled=false", "precognition.limits.max-body-bytes=0").run(context -> assertNotNull(context.getStartupFailure()));
    }
    @Test void fullOnlyClassConstraintWorksButSelectiveModeFailsAtStartup() { rejects(ClassConstraintController.class, "class constraint"); }
    @Test void unsupportedHttpMethodFailsAtStartup() { rejects(GetController.class, "POST, PUT or PATCH"); }
    @Test void aliasIsRejectedRatherThanSilentlyUsingWrongField() { rejects(AliasController.class, "JsonAlias"); }
    @Test void applicationMvcRegistrationsArePreservedWithoutOptInEvenWhenDisabled() throws Exception {
        for (boolean enabled : new boolean[]{true, false}) {
            withApplicationRegistrations().withPropertyValues("precognition.enabled=" + enabled).run(context -> {
                assertNull(context.getStartupFailure());
                assertInstanceOf(ApplicationAdapter.class, context.getBean(RequestMappingHandlerAdapter.class));
                assertInstanceOf(ApplicationMapping.class, context.getBean("requestMappingHandlerMapping"));
                var resolvers = (org.springframework.web.servlet.handler.HandlerExceptionResolverComposite) context.getBean("handlerExceptionResolver");
                assertTrue(resolvers.getExceptionResolvers().stream().anyMatch(ApplicationExceptionResolver.class::isInstance));
            });
        }
    }
    @Test void applicationMvcRegistrationsWithOptInFailClearlyEvenWhenDisabled() throws Exception {
        for (boolean enabled : new boolean[]{true, false}) {
            withApplicationRegistrations().withUserConfiguration(ValidController.class)
                    .withPropertyValues("precognition.enabled=" + enabled).run(context -> {
                        assertNotNull(context.getStartupFailure());
                        assertTrue(context.getStartupFailure().getMessage().contains("MVC handler adapter conflict"));
                    });
        }
    }
    private WebApplicationContextRunner withApplicationRegistrations() throws ClassNotFoundException {
        // Boot moves this extension interface in version 4; the same consumer tests run on both.
        Class<?> type;
        try { type = Class.forName("org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations"); }
        catch (ClassNotFoundException boot3) { type = Class.forName("org.springframework.boot.autoconfigure.web.servlet.WebMvcRegistrations"); }
        return withApplicationRegistrations(type);
    }
    private <T> WebApplicationContextRunner withApplicationRegistrations(Class<T> type) {
        return runner.withBean("applicationMvcRegistrations", type, () -> type.cast(java.lang.reflect.Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> switch (method.getName()) {
                    case "getRequestMappingHandlerAdapter" -> new ApplicationAdapter();
                    case "getRequestMappingHandlerMapping" -> new ApplicationMapping();
                    case "getExceptionHandlerExceptionResolver" -> new ApplicationExceptionResolver();
                    case "toString" -> "Application MVC registrations";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                })));
    }
    static class ApplicationAdapter extends RequestMappingHandlerAdapter {}
    static class ApplicationMapping extends org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping {}
    static class ApplicationExceptionResolver extends org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver {}
    record Body(@NotBlank String name) {}
    record CollectionBody(java.util.List<String> names) {}
    record AliasBody(@com.fasterxml.jackson.annotation.JsonAlias("other") String name) {}
    @RestController static class ValidController {
        @Precognitive @PostMapping("/valid") Object save(@Valid @RequestBody Body body) { return body; }
    }
    @RestController static class CollectionController {
        @Precognitive @PostMapping("/collection") Object save(@Valid @RequestBody CollectionBody body) { return body; }
    }
    @RestController static class UnvalidatedController {
        @Precognitive @PostMapping("/no-valid") Object save(@RequestBody Body body) { return body; }
    }
    @RestController static class SecuredController {
        @Precognitive @RolesAllowed("ADMIN") @PostMapping("/secured") Object save(@Valid @RequestBody Body body) { return body; }
    }
    @RestController static class BinderController {
        @InitBinder void binder(org.springframework.web.bind.WebDataBinder binder) {}
        @Precognitive @PostMapping("/binder") Object save(@Valid @RequestBody Body body) { return body; }
    }
    @Configuration(proxyBeanMethods = false) @Import(ValidController.class) static class CustomAdapter {
        @Bean RequestMappingHandlerAdapter anotherAdapter() { return new RequestMappingHandlerAdapter(); }
    }
    @Configuration(proxyBeanMethods = false) static class ProxiedController {
        @Bean ValidController proxyController() {
            var factory = new org.springframework.aop.framework.ProxyFactory(new ValidController());
            factory.setProxyTargetClass(true);
            factory.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> invocation.proceed());
            return (ValidController) factory.getProxy();
        }
    }
    @Configuration(proxyBeanMethods = false) @Import(ValidController.class) static class CustomValidator implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override public org.springframework.validation.Validator getValidator() {
            return new org.springframework.validation.Validator() {
                public boolean supports(Class<?> type) { return true; }
                public void validate(Object target, org.springframework.validation.Errors errors) {}
            };
        }
    }
    @Configuration(proxyBeanMethods = false) @Import(ValidController.class) static class CustomConverter implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer {
        @Override public void extendMessageConverters(java.util.List<org.springframework.http.converter.HttpMessageConverter<?>> converters) {
            converters.add(0, new org.springframework.http.converter.AbstractHttpMessageConverter<Object>(org.springframework.http.MediaType.APPLICATION_JSON) {
                protected boolean supports(Class<?> type) { return true; }
                protected Object readInternal(Class<?> type, org.springframework.http.HttpInputMessage input) { return null; }
                protected void writeInternal(Object value, org.springframework.http.HttpOutputMessage output) {}
            });
        }
    }
    @RestController static class ClassConstraintController {
        @Precognitive @PostMapping("/matching") Object save(@Valid @RequestBody ExtendedValidationTest.Matching body) { return body; }
    }
    @RestController static class GetController {
        @Precognitive @GetMapping("/get") Object save(@Valid @RequestBody Body body) { return body; }
    }
    @RestController static class AliasController {
        @Precognitive @PostMapping("/alias") Object save(@Valid @RequestBody AliasBody body) { return body; }
    }
}
