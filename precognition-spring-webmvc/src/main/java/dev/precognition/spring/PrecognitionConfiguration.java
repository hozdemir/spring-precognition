package dev.precognition.spring;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.*;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.*;
import org.springframework.web.method.*;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.*;

@Configuration(proxyBeanMethods = false)
public class PrecognitionConfiguration implements WebMvcConfigurer, SmartInitializingSingleton {
    static final String REQUEST_CONTEXT = PrecognitionConfiguration.class.getName() + ".context";
    private final ValidationEngine engine;
    private final ResponseWriter writer;
    private final PrecognitionSettings settings;
    private final List<PrecognitionAuthorizer> authorizers;
    private final ApplicationContext application;
    private final Validator validator;
    private final JsonSupport json;
    private final Map<Method, Descriptor> endpoints = new ConcurrentHashMap<>();

    public PrecognitionConfiguration(Validator validator, JsonSupport json, PrecognitionSettings settings,
                                     ObjectProvider<PrecognitionAuthorizer> authorizers, ApplicationContext application) {
        this.validator = validator; this.json = json; this.settings = settings; this.application = application;
        this.engine = new ValidationEngine(validator, json, settings);
        this.writer = new ResponseWriter(json, settings);
        this.authorizers = authorizers.orderedStream().toList();
    }
    public RequestMappingHandlerAdapter createHandlerAdapter() { return new PrecognitionHandlerAdapter(); }
    final class PrecognitionHandlerAdapter extends RequestMappingHandlerAdapter {
        @Override protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handler) {
            return new PrecognitionInvocation(handler);
        }
    }
    @Override public void afterSingletonsInstantiated() {
        settings.validate();
        for (var mapping : application.getBeansOfType(RequestMappingHandlerMapping.class).values())
            mapping.getHandlerMethods().forEach((info, handler) -> {
                if (handler.getMethod().getDeclaredAnnotation(Precognitive.class) == null) return;
                try {
                    var methods = info.getMethodsCondition().getMethods();
                    if (methods.isEmpty() || !Set.of(RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH).containsAll(methods))
                        throw ValidationEngine.unsupported("Declare POST, PUT or PATCH mappings explicitly");
                    endpoints.put(handler.getMethod(), describe(handler));
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Invalid @Precognitive endpoint " + handler.getBeanType().getName()
                            + "#" + handler.getMethod().getName() + ": " + failure.getMessage(), failure);
                }
            });
        if (!endpoints.isEmpty()) {
            var adapters = application.getBeansOfType(RequestMappingHandlerAdapter.class);
            if (adapters.size() != 1 || !(adapters.values().iterator().next() instanceof PrecognitionHandlerAdapter))
                throw ValidationEngine.unsupported("MVC handler adapter conflict; remove @EnableWebMvc or custom WebMvcRegistrations/RequestMappingHandlerAdapter");
            var adapter = adapters.values().iterator().next();
            if (!(adapter.getWebBindingInitializer() instanceof org.springframework.web.bind.support.ConfigurableWebBindingInitializer binder)
                    || !json.usesApplicationValidator(binder.getValidator(), validator))
                throw ValidationEngine.unsupported("MVC must use the same Bean Validation validator; custom MVC validators/binders are unsupported");
            Set<String> builtIns = Set.of("RequestResponseBodyMethodProcessor", "PathVariableMethodArgumentResolver", "RequestParamMethodArgumentResolver",
                    "RequestHeaderMethodArgumentResolver", "ServletRequestMethodArgumentResolver", "PrincipalMethodArgumentResolver");
            for (var method : endpoints.keySet()) {
                for (int index = 0; index < method.getParameterCount(); index++) {
                    var parameter = new MethodParameter(method, index);
                    var resolver = adapter.getArgumentResolvers().stream().filter(value -> value.supportsParameter(parameter)).findFirst().orElseThrow();
                    if (!resolver.getClass().getPackageName().startsWith("org.springframework.web.") || !builtIns.contains(resolver.getClass().getSimpleName()))
                        throw ValidationEngine.unsupported("Custom argument resolvers are unsupported");
                }
            }
            for (var endpoint : endpoints.values()) {
                var converter = adapter.getMessageConverters().stream().filter(value -> value.canRead(endpoint.type, org.springframework.http.MediaType.APPLICATION_JSON))
                        .findFirst().orElseThrow(() -> ValidationEngine.unsupported("No JSON converter for " + endpoint.type.getName()));
                if (!json.usesApplicationMapper(converter, endpoint.type))
                    throw ValidationEngine.unsupported("MVC converter must use the application's JSON mapper; per-type/custom converters are unsupported");
            }
        }
    }
    private Descriptor describe(HandlerMethod handler) {
        if (methodSecurity(handler) || MergedAnnotations.from(handler.getBeanType(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).isPresent(Validated.class))
            throw ValidationEngine.unsupported("Method security / controller method validation is unsupported; use the filter chain or PrecognitionAuthorizer");
        Object bean = handler.getBean() instanceof String name ? application.getBean(name) : handler.getBean();
        if (org.springframework.aop.support.AopUtils.isAopProxy(bean))
            throw ValidationEngine.unsupported("Proxied opt-in controllers are unsupported; use URL security or PrecognitionAuthorizer");
        if (MergedAnnotations.from(handler.getBeanType(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY).isPresent(SessionAttributes.class))
            throw ValidationEngine.unsupported("@SessionAttributes is unsupported");
        for (var method : org.springframework.util.ReflectionUtils.getAllDeclaredMethods(handler.getBeanType()))
            if (method.isAnnotationPresent(InitBinder.class) || method.isAnnotationPresent(ModelAttribute.class))
                throw ValidationEngine.unsupported("@InitBinder/@ModelAttribute methods are unsupported");
        for (var advice : ControllerAdviceBean.findAnnotatedBeans(application)) {
            if (!advice.isApplicableToBeanType(handler.getBeanType())) continue;
            for (var method : org.springframework.util.ReflectionUtils.getAllDeclaredMethods(advice.getBeanType()))
                if (method.isAnnotationPresent(InitBinder.class) || method.isAnnotationPresent(ModelAttribute.class))
                    throw ValidationEngine.unsupported("Applicable controller advice has @InitBinder/@ModelAttribute");
        }
        Class<?> returned = handler.getMethod().getReturnType();
        if (java.util.concurrent.Future.class.isAssignableFrom(returned) || java.util.concurrent.Callable.class.isAssignableFrom(returned)
                || java.util.concurrent.CompletionStage.class.isAssignableFrom(returned)
                || org.springframework.web.context.request.async.DeferredResult.class.isAssignableFrom(returned)
                || ResponseBodyEmitter.class.isAssignableFrom(returned) || StreamingResponseBody.class.isAssignableFrom(returned)
                || returned.getName().startsWith("reactor.") || returned.getName().startsWith("org.reactivestreams."))
            throw ValidationEngine.unsupported("Async/reactive/streaming return types are unsupported");
        int index = -1;
        var parameters = handler.getMethodParameters();
        for (int i = 0; i < parameters.length; i++) {
            var p = parameters[i];
            for (var annotation : p.getParameterAnnotations())
                if (annotation.annotationType().isAnnotationPresent(jakarta.validation.Constraint.class))
                    throw ValidationEngine.unsupported("Executable parameter constraints are unsupported");
            if (p.hasParameterAnnotation(RequestBody.class)) {
                if (index != -1 || !p.getParameterAnnotation(RequestBody.class).required())
                    throw ValidationEngine.unsupported("Exactly one required @RequestBody is supported");
                var validated = p.getParameterAnnotation(Validated.class);
                if (!p.hasParameterAnnotation(Valid.class) && validated == null)
                    throw ValidationEngine.unsupported("Body requires @Valid or @Validated");
                if (validated != null && validated.value().length > 0 && !Arrays.equals(validated.value(), new Class<?>[]{jakarta.validation.groups.Default.class}))
                    throw ValidationEngine.unsupported("Only the Default validation group is supported");
                index = i;
            } else if (!(p.hasParameterAnnotation(PathVariable.class) || p.hasParameterAnnotation(RequestParam.class)
                    || p.hasParameterAnnotation(RequestHeader.class) || p.getParameterType() == Principal.class))
                throw ValidationEngine.unsupported("Unsupported controller parameter: " + p.getParameterType().getName());
            else if (p.getParameterType() != Principal.class && !p.getParameterType().isPrimitive() && !ValidationEngine.scalar(p.getParameterType()))
                throw ValidationEngine.unsupported("Only scalar built-in controller parameters are supported");
        }
        if (index < 0) throw ValidationEngine.unsupported("Missing @RequestBody");
        var executable = validator.getConstraintsForClass(handler.getBeanType()).getConstraintsForMethod(handler.getMethod().getName(), handler.getMethod().getParameterTypes());
        if (executable != null && (executable.getCrossParameterDescriptor().hasConstraints() || executable.hasConstrainedReturnValue()))
            throw ValidationEngine.unsupported("Executable constraints are unsupported");
        var mode = handler.getMethodAnnotation(Precognitive.class).mode();
        Class<?> type = parameters[index].getParameterType();
        engine.prepare(type, mode);
        return new Descriptor(index, type, mode);
    }
    @Override public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
        resolvers.add(0, new ProtocolExceptionResolver(engine, writer, settings));
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
                var selection = PrecognitionProtocol.selection(request);
                if (selection == null && PrecognitionProtocol.headers(request).active()) {
                    if (request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST)
                        throw new PrecognitionException(400, "PRECOGNITION_UNSUPPORTED_DISPATCH");
                    throw new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR");
                }
                if (selection != null && request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST)
                    throw new PrecognitionException(400, "PRECOGNITION_UNSUPPORTED_DISPATCH");
                Descriptor descriptor = handler instanceof HandlerMethod method ? endpoints.get(method.getMethod()) : null;
                if (descriptor == null) {
                    if (selection != null) { writer.error(response, 412, "PRECOGNITION_NOT_ENABLED"); return false; }
                    return true;
                }
                Map<String, String> vary = new LinkedHashMap<>();
                for (String line : response.getHeaders("Vary")) for (String token : line.split(","))
                    if (!token.isBlank()) vary.putIfAbsent(token.trim().toLowerCase(Locale.ROOT), token.trim());
                if (!vary.containsKey("*")) {
                    vary.putIfAbsent("precognition", "Precognition");
                    vary.putIfAbsent("precognition-validate-only", "Precognition-Validate-Only");
                    response.setHeader("Vary", String.join(", ", vary.values()));
                }
                if (selection != null) { response.setHeader("Precognition", "true"); response.setHeader("Cache-Control", "no-store"); }
                request.setAttribute(REQUEST_CONTEXT, new PrecognitionContext(selection != null,
                        selection == null || selection.full() ? PrecognitionContext.ValidationScope.FULL : PrecognitionContext.ValidationScope.SELECTED,
                        selection == null ? Set.of() : selection.fields(), org.springframework.context.i18n.LocaleContextHolder.getLocale()));
                var route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                var variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                Map<String, String> paths = new LinkedHashMap<>();
                if (variables instanceof Map<?, ?> map) map.forEach((key, value) -> paths.put(key.toString(), value.toString()));
                var endpoint = new EndpointRequest(((HandlerMethod) handler).getMethod().toGenericString(), request.getMethod(),
                        String.valueOf(route), paths, request.getUserPrincipal(), request.getLocale());
                for (var authorizer : authorizers) {
                    var decision = authorizer.authorize(endpoint);
                    if (decision == null || !decision.granted()) { writer.error(response, 403, "PRECOGNITION_ACCESS_DENIED"); return false; }
                }
                return true;
            }
            @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
                request.removeAttribute(REQUEST_CONTEXT);
            }
        });
    }
    static boolean methodSecurity(HandlerMethod handler) {
        Set<String> annotations = Set.of("org.springframework.security.access.prepost.PreAuthorize", "org.springframework.security.access.prepost.PostAuthorize",
                "org.springframework.security.access.prepost.PreFilter", "org.springframework.security.access.prepost.PostFilter",
                "org.springframework.security.access.annotation.Secured", "jakarta.annotation.security.RolesAllowed",
                "jakarta.annotation.security.DenyAll", "jakarta.annotation.security.PermitAll");
        return java.util.stream.Stream.of(MergedAnnotations.from(handler.getMethod(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY),
                MergedAnnotations.from(handler.getBeanType(), MergedAnnotations.SearchStrategy.TYPE_HIERARCHY))
                .flatMap(MergedAnnotations::stream).anyMatch(a -> annotations.contains(a.getType().getName()));
    }
    record Descriptor(int bodyIndex, Class<?> type, PrecognitionMode mode) {}
    final class PrecognitionInvocation extends ServletInvocableHandlerMethod {
        PrecognitionInvocation(HandlerMethod handler) { super(handler); }
        @Override public void setDataBinderFactory(WebDataBinderFactory delegate) {
            super.setDataBinderFactory(new WebDataBinderFactory() {
                public WebDataBinder createBinder(NativeWebRequest request, Object target, String name) throws Exception {
                    return configure(request, delegate.createBinder(request, target, name));
                }
                public WebDataBinder createBinder(NativeWebRequest request, Object target, String name, ResolvableType type) throws Exception {
                    return configure(request, delegate.createBinder(request, target, name, type));
                }
                private WebDataBinder configure(NativeWebRequest request, WebDataBinder binder) {
                    if (request.getAttribute(PrecognitionProtocol.CONTEXT, NativeWebRequest.SCOPE_REQUEST) != null) binder.replaceValidators();
                    return binder;
                }
            });
        }
        @Override public void invokeAndHandle(ServletWebRequest request, ModelAndViewContainer container, Object... providedArgs) throws Exception {
            var selection = PrecognitionProtocol.selection(request.getRequest());
            if (selection == null) { super.invokeAndHandle(request, container, providedArgs); return; }
            Descriptor descriptor = endpoints.get(getMethod());
            if (descriptor == null) throw new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR");
            if (descriptor.mode == PrecognitionMode.FULL && !selection.full()) throw new PrecognitionException(400, "SELECTION_NOT_SUPPORTED");
            if (!engine.prepare(descriptor.type, descriptor.mode).paths().containsAll(selection.fields()))
                throw new PrecognitionException(400, "UNKNOWN_VALIDATION_FIELD");
            PrecognitionProtocol.prepareBody(request.getRequest());
            Object body = getMethodArgumentValues(request, container, providedArgs)[descriptor.bodyIndex];
            if (body == null) throw new PrecognitionException(400, "INVALID_REQUEST_BODY");
            var errors = engine.validate(body, descriptor.mode, selection.full(), selection.fields());
            if (errors.isEmpty()) {
                request.getResponse().setStatus(204);
                request.getResponse().setHeader("Precognition-Success", "true");
            } else writer.write(request.getResponse(), 422, Map.of("message", "The given data was invalid.", "errors", errors));
            container.setRequestHandled(true);
        }
    }
}
