package com.quickform.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.postgresql.util.PGobject;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class JsonHelper {
    private final ObjectMapper objectMapper;

    public JsonHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        if (value == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize json", ex);
        }
    }

    public Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new HashMap<>();
        }
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        String json;
        if (value instanceof PGobject) {
            json = ((PGobject) value).getValue();
        } else {
            json = value.toString();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse json", ex);
        }
    }

    public <T> T toObject(Object value, Class<T> clazz) {
        if (value == null) {
            return null;
        }
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (clazz == Object.class && (value instanceof Map || value instanceof Iterable)) {
            return (T) value;
        }
        String json;
        if (value instanceof PGobject) {
            json = ((PGobject) value).getValue();
        } else {
            json = value.toString();
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse json", ex);
        }
    }
}
