package org.telegram.messenger.openAI;

import java.util.List;
import java.util.Map;

public class SettingDefinition {
    private final String key;
    private final SettingType type;
    private final String title;
    private final String description;
    private final boolean masked;
    private final boolean required;
    private final Object defaultValue;
    private final Map<String, Object> constraints; // например, min, max, choices

    public SettingDefinition(String key, SettingType type, String title, String description,
                             boolean masked, boolean required, Object defaultValue, Map<String, Object> constraints) {
        this.key = key;
        this.type = type;
        this.title = title;
        this.description = description;
        this.masked = masked;
        this.required = required;
        this.defaultValue = defaultValue;
        this.constraints = constraints;
    }

    public String getKey() {
        return key;
    }

    public SettingType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public boolean isMasked() {
        return masked;
    }

    public boolean isRequired() {
        return required;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }

    public int getIntDefault() {
        return defaultValue instanceof Integer ? (Integer) defaultValue : 0;
    }

    public float getFloatDefault() {
        return defaultValue instanceof Float ? (Float) defaultValue : 0.0f;
    }

    public boolean getBooleanDefault() {
        return defaultValue instanceof Boolean ? (Boolean) defaultValue : false;
    }

    public String getStringDefault() {
        return defaultValue instanceof String ? (String) defaultValue : "";
    }

    @SuppressWarnings("unchecked")
    public List<String> getChoices() {
        if (constraints != null && constraints.containsKey("choices")) {
            return (List<String>) constraints.get("choices");
        }
        return null;
    }

    public float getMin() {
        if (constraints != null && constraints.containsKey("min")) {
            Object val = constraints.get("min");
            if (val instanceof Number) {
                return ((Number) val).floatValue();
            }
        }
        return Float.MIN_VALUE;
    }

    public float getMax() {
        if (constraints != null && constraints.containsKey("max")) {
            Object val = constraints.get("max");
            if (val instanceof Number) {
                return ((Number) val).floatValue();
            }
        }
        return Float.MAX_VALUE;
    }

    // Builder для удобства
    public static class Builder {
        private String key;
        private SettingType type;
        private String title;
        private String description;
        private boolean masked = false;
        private boolean required = true; // по умолчанию обязательно
        private Object defaultValue;
        private Map<String, Object> constraints;

        public Builder setKey(String key) {
            this.key = key;
            return this;
        }

        public Builder setType(SettingType type) {
            this.type = type;
            return this;
        }

        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }

        public Builder setMasked(boolean masked) {
            this.masked = masked;
            return this;
        }

        public Builder setRequired(boolean required) {
            this.required = required;
            return this;
        }

        public Builder setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder setConstraints(Map<String, Object> constraints) {
            this.constraints = constraints;
            return this;
        }

        public SettingDefinition build() {
            return new SettingDefinition(key, type, title, description, masked, required, defaultValue, constraints);
        }
    }
}