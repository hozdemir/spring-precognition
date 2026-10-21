package dev.precognition.spring;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.GroupSequence;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import java.lang.reflect.*;
import java.math.*;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.BeanUtils;
import org.springframework.util.ReflectionUtils;

/** Immutable, bounded schemas built from the application's deserializer and Validation metadata. */
final class ValidationEngine {
    private final Validator validator;
    private final JsonSupport json;
    private final PrecognitionSettings settings;
    private final Map<Key, Model> models = new ConcurrentHashMap<>();
    ValidationEngine(Validator validator, JsonSupport json, PrecognitionSettings settings) {
        this.validator = validator; this.json = json; this.settings = settings;
    }
    Model prepare(Class<?> type, PrecognitionMode mode) {
        return models.computeIfAbsent(new Key(type, mode), key -> {
            Map<String, Node> nodes = new LinkedHashMap<>();
            walk(type, mode, List.of(), "", "", new HashSet<>(), nodes);
            return new Model(Map.copyOf(nodes));
        });
    }
    Map<String, List<String>> validate(Object body, PrecognitionMode mode, boolean full, Set<String> selected) {
        Model model = prepare(body.getClass(), mode);
        Map<String, SortedSet<String>> errors = new TreeMap<>();
        if (full) collect(errors, "", model, validator.validate(body));
        else {
            Set<String> seen = new HashSet<>();
            for (String path : selected.stream().filter(path -> selected.stream().noneMatch(other ->
                    !path.equals(other) && path.startsWith(other + "."))).sorted().toList()) {
                Node node = model.nodes.get(path);
                Object owner = body;
                String prefix = "";
                boolean missing = false;
                for (Access access : node.owners) {
                    Object child = access.read(owner);
                    if (child == null) {
                        if (seen.add(prefix + access.name)) collect(errors, prefix, model, validator.validateProperty(owner, access.name));
                        missing = true; break;
                    }
                    owner = child;
                    prefix += access.name + ".";
                }
                if (missing) continue;
                if (seen.add(node.javaPath)) collect(errors, prefix, model, validator.validateProperty(owner, node.access.name));
                if (node.nested) {
                    Object child = node.access.read(owner);
                    if (child != null) collect(errors, node.javaPath + ".", model, validator.validate(child));
                }
            }
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        errors.forEach((path, messages) -> result.put(path, List.copyOf(messages)));
        return result;
    }
    private void walk(Class<?> type, PrecognitionMode mode, List<Access> owners, String wirePrefix,
                      String javaPrefix, Set<Class<?>> ancestors, Map<String, Node> nodes) {
        if (owners.size() > 8 || !ancestors.add(type) || type.isAnnotationPresent(GroupSequence.class))
            throw unsupported("Cyclic/deep model or custom Default group sequence: " + type.getName());
        rejectJsonFeatures(type);
        var descriptor = validator.getConstraintsForClass(type);
        if (mode == PrecognitionMode.SELECTIVE && descriptor.hasConstraints())
            throw unsupported("SELECTIVE endpoint contains a class constraint; use mode=FULL: " + type.getName());
        for (var constraint : descriptor.getConstraintDescriptors())
            if (!constraint.getGroups().equals(Set.of(Default.class))) throw unsupported("Custom constraint group");
        List<JsonSupport.JsonProperty> properties = json.properties(type);
        Set<String> javaNames = new HashSet<>();
        for (var property : properties) javaNames.add(property.javaName());
        for (var property : descriptor.getConstrainedProperties()) {
            if (!javaNames.contains(property.getPropertyName()) || !property.getGroupConversions().isEmpty())
                throw unsupported("Constrained property cannot be bound, or uses group conversion: " + property.getPropertyName());
            if (!property.getConstrainedContainerElementTypes().isEmpty()) throw unsupported("Container element constraints");
            for (var constraint : property.getConstraintDescriptors())
                if (!constraint.getGroups().equals(Set.of(Default.class))) throw unsupported("Custom constraint group");
        }
        if (properties.isEmpty()) throw unsupported("No deserializable DTO properties: " + type.getName());
        for (var property : properties) {
            String wire = property.wireName(), name = property.javaName();
            if (!wire.matches("[A-Za-z_][A-Za-z0-9_-]*") || Set.of("_form", "class", "classLoader", "metaClass").contains(wire)
                    || wire.length() > settings.getLimits().getMaxFieldNameLength()) throw unsupported("Reserved/invalid wire property: " + wire);
            Class<?> fieldType = property.type();
            if (fieldType.isPrimitive() || fieldType.isArray() || Collection.class.isAssignableFrom(fieldType)
                    || Map.class.isAssignableFrom(fieldType) || Optional.class.isAssignableFrom(fieldType))
                throw unsupported("Use boxed scalar properties; containers are unsupported: " + name);
            boolean nested = !scalar(fieldType);
            var constraints = descriptor.getConstraintsForProperty(name);
            if (nested && (constraints == null || !constraints.isCascaded())) throw unsupported("Nested property requires @Valid: " + name);
            Access access = access(type, name);
            Node node = new Node(wirePrefix + wire, javaPrefix + name, List.copyOf(owners), access, nested);
            if (nodes.putIfAbsent(node.wirePath, node) != null) throw unsupported("Ambiguous wire property: " + wire);
            if (nodes.size() > settings.getLimits().getMaxDtoFields()) throw unsupported("DTO field limit exceeded");
            if (nested) {
                List<Access> child = new ArrayList<>(owners); child.add(access);
                walk(fieldType, mode, child, node.wirePath + ".", node.javaPath + ".", new HashSet<>(ancestors), nodes);
            }
        }
    }
    static boolean scalar(Class<?> type) {
        return type == String.class || type == Boolean.class || type == Character.class || type.isEnum()
                || Set.of(Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class, BigDecimal.class, BigInteger.class).contains(type)
                || type == UUID.class || java.time.ZoneId.class.isAssignableFrom(type) || TemporalAccessor.class.isAssignableFrom(type) || type == java.time.Duration.class
                || type == java.time.Period.class || java.util.Date.class.isAssignableFrom(type);
    }
    private static Access access(Class<?> type, String name) {
        if (type.isRecord()) for (var component : type.getRecordComponents())
            if (component.getName().equals(name)) return new Access(name, component.getAccessor(), null);
        var property = BeanUtils.getPropertyDescriptor(type, name);
        if (property != null && property.getReadMethod() != null) return new Access(name, property.getReadMethod(), null);
        Field field = ReflectionUtils.findField(type, name);
        if (field != null) return new Access(name, null, field);
        throw unsupported("Property cannot be read for validation: " + type.getName() + "." + name);
    }
    private static void rejectJsonFeatures(Class<?> type) {
        List<AnnotatedElement> elements = new ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            elements.add(current); elements.addAll(List.of(current.getDeclaredFields())); elements.addAll(List.of(current.getDeclaredMethods()));
            elements.addAll(List.of(current.getDeclaredConstructors()));
        }
        for (AnnotatedElement element : elements) for (var annotation : element.getAnnotations())
            if (Set.of("com.fasterxml.jackson.annotation.JsonAlias", "com.fasterxml.jackson.annotation.JsonUnwrapped",
                    "com.fasterxml.jackson.annotation.JsonTypeInfo", "com.fasterxml.jackson.annotation.JsonAnySetter",
                    "com.fasterxml.jackson.annotation.JsonAnyGetter", "org.hibernate.validator.group.GroupSequenceProvider").contains(annotation.annotationType().getName()))
                throw unsupported("Unsupported mapping/group feature: " + annotation.annotationType().getSimpleName());
    }
    private void collect(Map<String, SortedSet<String>> errors, String prefix, Model model,
                         Set<? extends ConstraintViolation<?>> violations) {
        if (violations.size() > settings.getLimits().getMaxViolationCount()) throw new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR");
        for (var violation : violations) {
            String path = prefix + violation.getPropertyPath();
            String wire = path.isEmpty() ? "_form" : model.nodes.values().stream().filter(n -> n.javaPath.equals(path))
                    .map(n -> n.wirePath).findFirst().orElseThrow(() -> new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR"));
            errors.computeIfAbsent(wire, key -> new TreeSet<>()).add(violation.getMessage());
        }
        if (errors.values().stream().mapToInt(Set::size).sum() > settings.getLimits().getMaxViolationCount())
            throw new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR");
    }
    static IllegalStateException unsupported(String reason) { return new PrecognitionConfigurationException(reason); }
    record Model(Map<String, Node> nodes) {
        Set<String> paths() { return nodes.keySet(); }
        String wireName(String javaPath) {
            return nodes.values().stream().filter(n -> n.javaPath.equals(javaPath)).map(n -> n.wirePath).findFirst()
                    .orElseThrow(() -> new PrecognitionException(500, "PRECOGNITION_CONFIGURATION_ERROR"));
        }
    }
    private record Key(Class<?> type, PrecognitionMode mode) {}
    private record Node(String wirePath, String javaPath, List<Access> owners, Access access, boolean nested) {}
    private record Access(String name, Method method, Field field) {
        Object read(Object owner) {
            if (method != null) { ReflectionUtils.makeAccessible(method); return ReflectionUtils.invokeMethod(method, owner); }
            ReflectionUtils.makeAccessible(field); return ReflectionUtils.getField(field, owner);
        }
    }
}
