package dev.precognition.spring;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/** Platform bridge: use the application's Jackson mapper without coupling MVC logic to Jackson 2/3. */
public interface JsonSupport {
    void writeValue(OutputStream output, Object value) throws IOException;
    String writeValueAsString(Object value) throws IOException;
    JsonValue readTree(String json) throws IOException;

    List<JsonProperty> properties(Class<?> type);
    boolean usesApplicationValidator(org.springframework.validation.Validator mvc, jakarta.validation.Validator validator);
    boolean usesApplicationMapper(org.springframework.http.converter.HttpMessageConverter<?> converter, Class<?> type);
    record JsonProperty(String wireName, String javaName, Class<?> type) {}

    record JsonValue(Object value) {
        public JsonValue get(String key) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(key)) return null;
            return new JsonValue(map.get(key));
        }
        public boolean has(String key) { return value instanceof Map<?, ?> map && map.containsKey(key); }
        public boolean isArray() { return value instanceof List<?>; }
        public int size() {
            if (value instanceof Map<?, ?> map) return map.size();
            if (value instanceof List<?> list) return list.size();
            return 0;
        }
        public String asString() { return value == null ? null : value.toString(); }
        public int asInt() { return ((Number) value).intValue(); }
    }
}
