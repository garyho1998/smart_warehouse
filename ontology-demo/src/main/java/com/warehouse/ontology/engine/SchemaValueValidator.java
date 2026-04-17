package com.warehouse.ontology.engine;

import com.warehouse.ontology.schema.ObjectTypeDef;
import com.warehouse.ontology.schema.PropertyDef;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SchemaValueValidator {

    public LinkedHashMap<String, Object> validateForInsert(ObjectTypeDef typeDef, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        ensureKnownProperties(typeDef, safePayload);

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        typeDef.properties().values().forEach(propertyDef -> {
            if (safePayload.containsKey(propertyDef.name())) {
                normalized.put(propertyDef.name(), coerce(propertyDef, safePayload.get(propertyDef.name())));
                return;
            }
            if (propertyDef.defaultValue() != null && !propertyDef.defaultValue().isBlank()) {
                normalized.put(propertyDef.name(), coerceDefault(propertyDef));
                return;
            }
            if (propertyDef.required()) {
                throw new IllegalArgumentException("Missing required property '" + propertyDef.name()
                        + "' for type " + typeDef.id());
            }
        });

        return normalized;
    }

    public LinkedHashMap<String, Object> validateForUpdate(ObjectTypeDef typeDef, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return new LinkedHashMap<>();
        }

        ensureKnownProperties(typeDef, payload);

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        payload.forEach((propertyName, value) -> {
            if (typeDef.primaryKey().equals(propertyName)) {
                throw new IllegalArgumentException("Primary key '" + propertyName + "' cannot be updated for type "
                        + typeDef.id());
            }
            PropertyDef propertyDef = requireProperty(typeDef, propertyName);
            normalized.put(propertyName, coerce(propertyDef, value));
        });

        return normalized;
    }

    public Object validateFilterValue(ObjectTypeDef typeDef, String propertyName, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Filter value cannot be null for property '" + propertyName + "'");
        }
        return coerce(requireProperty(typeDef, propertyName), value);
    }

    public PropertyDef requireProperty(ObjectTypeDef typeDef, String propertyName) {
        PropertyDef propertyDef = typeDef.properties().get(propertyName);
        if (propertyDef == null) {
            throw new IllegalArgumentException("Unknown property '" + propertyName + "' for type " + typeDef.id());
        }
        return propertyDef;
    }

    private void ensureKnownProperties(ObjectTypeDef typeDef, Map<String, Object> payload) {
        payload.keySet().forEach(propertyName -> requireProperty(typeDef, propertyName));
    }

    private Object coerceDefault(PropertyDef propertyDef) {
        if ("timestamp".equals(propertyDef.type()) && "NOW".equalsIgnoreCase(propertyDef.defaultValue())) {
            return Timestamp.from(Instant.now());
        }
        return coerce(propertyDef, propertyDef.defaultValue());
    }

    private Object coerce(PropertyDef propertyDef, Object value) {
        if (value == null) {
            if (propertyDef.required()) {
                throw new IllegalArgumentException("Property '" + propertyDef.name() + "' is required");
            }
            return null;
        }

        return switch (propertyDef.type()) {
            case "string" -> String.valueOf(value);
            case "enum" -> validateEnum(propertyDef, String.valueOf(value));
            case "integer" -> coerceInteger(propertyDef, value);
            case "decimal" -> coerceDecimal(value);
            case "boolean" -> coerceBoolean(propertyDef, value);
            case "timestamp" -> coerceTimestamp(propertyDef, value);
            default -> throw new IllegalArgumentException("Unsupported ontology property type: " + propertyDef.type());
        };
    }

    private String validateEnum(PropertyDef propertyDef, String value) {
        if (!propertyDef.enumValues().isEmpty() && !propertyDef.enumValues().contains(value)) {
            throw new IllegalArgumentException("Invalid enum value '" + value + "' for property '"
                    + propertyDef.name() + "'");
        }
        return value;
    }

    private Integer coerceInteger(PropertyDef propertyDef, Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer value for property '" + propertyDef.name() + "'", exception);
        }
    }

    private BigDecimal coerceDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid decimal value '" + value + "'", exception);
        }
    }

    private Boolean coerceBoolean(PropertyDef propertyDef, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = String.valueOf(value).toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            return Boolean.valueOf(normalized);
        }
        throw new IllegalArgumentException("Invalid boolean value for property '" + propertyDef.name() + "'");
    }

    private Timestamp coerceTimestamp(PropertyDef propertyDef, Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        if (value instanceof Instant instant) {
            return Timestamp.from(instant);
        }
        try {
            String stringValue = String.valueOf(value);
            if (stringValue.contains(" ")) {
                return Timestamp.valueOf(stringValue);
            }
            return Timestamp.valueOf(LocalDateTime.parse(stringValue));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid timestamp value for property '" + propertyDef.name() + "'",
                    exception
            );
        }
    }
}
