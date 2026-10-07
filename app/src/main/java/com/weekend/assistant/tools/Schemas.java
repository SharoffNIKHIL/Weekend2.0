package com.weekend.assistant.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small helpers to build JSON Schemas for tool inputs. */
final class Schemas {

    private Schemas() {}

    static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> integer(String description, int min, int max) {
        return Map.of("type", "integer", "description", description, "minimum", min, "maximum", max);
    }

    static String optionalString(Map<String, Object> input, String key) {
        Object v = input == null ? null : input.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    static String requireString(Map<String, Object> input, String key) {
        Object v = input == null ? null : input.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new IllegalArgumentException("missing string field: " + key);
        }
        return s.trim();
    }
}
