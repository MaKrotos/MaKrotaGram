package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for service-specific settings.
 * Each AI service should extend this class and define its own setting definitions.
 * This class provides automatic loading/saving based on definitions.
 */
public abstract class BaseServiceSettings {

    // Common setting keys
    protected static final String KEY_API_KEY = "api_key";
    protected static final String KEY_MODEL = "model";

    protected int account;
    protected SharedPreferences preferences;
    protected String preferencePrefix;
    protected Map<String, Object> currentValues = new HashMap<>();

    public BaseServiceSettings(int account) {
        this.account = account;
        this.preferences = ApplicationLoader.applicationContext.getSharedPreferences(
                getSharedPreferencesName(), Context.MODE_PRIVATE);
        this.preferencePrefix = getPreferencePrefix();
        load();
    }

    /**
     * Load settings from SharedPreferences into currentValues.
     * Subclasses can override to add custom logic, but should call super.load().
     */
    public void load() {
        currentValues.clear();
        for (SettingDefinition def : getSettingDefinitions()) {
            Object value = loadValue(def);
            currentValues.put(def.getKey(), value);
        }
        onLoad();
    }

    /**
     * Save currentValues to SharedPreferences.
     * Subclasses can override to add custom logic, but should call super.save().
     */
    public void save() {
        SharedPreferences.Editor editor = preferences.edit();
        for (SettingDefinition def : getSettingDefinitions()) {
            Object value = currentValues.get(def.getKey());
            if (value == null) {
                value = def.getDefaultValue();
            }
            saveValue(def, value, editor);
        }
        try {
            editor.apply();
            FileLog.d("BaseServiceSettings: Saved all preferences for " + getServiceType());
        } catch (Exception e) {
            FileLog.e("BaseServiceSettings: Failed to save preferences", e);
        }
        onSave();
    }

    /**
     * Validate the settings based on required definitions.
     * @return true if all required settings have non-empty values.
     */
    public boolean validate() {
        for (SettingDefinition def : getSettingDefinitions()) {
            if (def.isRequired()) {
                Object value = getValue(def.getKey());
                if (value == null || (value instanceof String && TextUtils.isEmpty((String) value))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get the service type this settings belong to.
     */
    public abstract AISettings.AIServiceType getServiceType();

    /**
     * Get the default model ID for this service.
     */
    public abstract String getDefaultModelId();

    /**
     * Get the display name of the service.
     */
    public abstract String getServiceDisplayName();

    /**
     * Get the list of setting definitions for this service.
     * Each definition describes a configurable parameter (API key, temperature, etc.)
     */
    public abstract List<SettingDefinition> getSettingDefinitions();

    /**
     * Get the current value of a setting by its key.
     * @param key the setting key (without prefix)
     * @return the value as Object (String, Integer, Float, Boolean) or null if not found.
     */
    public Object getValue(String key) {
        if (currentValues.containsKey(key)) {
            return currentValues.get(key);
        }
        // fallback to loading from preferences
        SettingDefinition def = findDefinition(key);
        if (def == null) {
            return null;
        }
        Object value = loadValue(def);
        currentValues.put(key, value);
        return value;
    }

    /**
     * Set the value of a setting by its key.
     * @param key the setting key (without prefix)
     * @param value the value (must match the setting type)
     */
    public void setValue(String key, Object value) {
        SettingDefinition def = findDefinition(key);
        if (def == null) {
            return;
        }
        // Validate type
        if (!isTypeMatch(def.getType(), value)) {
            FileLog.e("BaseServiceSettings: Type mismatch for key " + key);
            return;
        }
        currentValues.put(key, value);
        // Immediately persist
        SharedPreferences.Editor editor = preferences.edit();
        saveValue(def, value, editor);
        try {
            editor.apply();
            FileLog.d("BaseServiceSettings: Saved value for key " + key);
        } catch (Exception e) {
            FileLog.e("BaseServiceSettings: Failed to apply preference change for key " + key, e);
        }
    }

    /**
     * Get the API key for this service.
     */
    public String getApiKey() {
        return (String) getValue(KEY_API_KEY);
    }

    /**
     * Set the API key for this service.
     */
    public void setApiKey(String apiKey) {
        setValue(KEY_API_KEY, apiKey);
    }

    /**
     * Get the model ID for this service.
     */
    public String getModel() {
        return (String) getValue(KEY_MODEL);
    }

    /**
     * Set the model ID for this service.
     */
    public void setModel(String model) {
        setValue(KEY_MODEL, model);
    }

    /**
     * Get the maximum tokens limit for this service.
     * Default implementation returns -1 (unknown). Subclasses should override.
     */
    public int getMaxTokens() {
        return -1;
    }

    // Helper methods

    protected String getSharedPreferencesName() {
        return "ai_service_settings";
    }

    protected String getPreferencePrefix() {
        return getServiceType().name().toLowerCase() + "_";
    }

    protected String getKey(String suffix) {
        return preferencePrefix + suffix;
    }

    private SettingDefinition findDefinition(String key) {
        for (SettingDefinition def : getSettingDefinitions()) {
            if (def.getKey().equals(key)) {
                return def;
            }
        }
        return null;
    }

    private Object loadValue(SettingDefinition def) {
        String fullKey = getKey(def.getKey());
        switch (def.getType()) {
            case STRING:
            case CHOICE:
                return preferences.getString(fullKey, def.getStringDefault());
            case INT:
                return preferences.getInt(fullKey, def.getIntDefault());
            case FLOAT:
                return preferences.getFloat(fullKey, def.getFloatDefault());
            case BOOLEAN:
                return preferences.getBoolean(fullKey, def.getBooleanDefault());
            default:
                return null;
        }
    }

    private void saveValue(SettingDefinition def, Object value, SharedPreferences.Editor editor) {
        String fullKey = getKey(def.getKey());
        try {
            switch (def.getType()) {
                case STRING:
                case CHOICE:
                    editor.putString(fullKey, (String) value);
                    break;
                case INT:
                    editor.putInt(fullKey, (Integer) value);
                    break;
                case FLOAT:
                    editor.putFloat(fullKey, (Float) value);
                    break;
                case BOOLEAN:
                    editor.putBoolean(fullKey, (Boolean) value);
                    break;
            }
        } catch (Exception e) {
            FileLog.e("BaseServiceSettings: Failed to save value for key " + def.getKey(), e);
        }
    }

    private boolean isTypeMatch(SettingType type, Object value) {
        if (value == null) return false;
        switch (type) {
            case STRING:
            case CHOICE:
                return value instanceof String;
            case INT:
                return value instanceof Integer;
            case FLOAT:
                return value instanceof Float;
            case BOOLEAN:
                return value instanceof Boolean;
            default:
                return false;
        }
    }

    /**
     * Called after loading values. Subclasses can override to perform additional initialization.
     */
    protected void onLoad() {
        // optional override
    }

    /**
     * Called before saving values. Subclasses can override to perform additional actions.
     */
    protected void onSave() {
        // optional override
    }

    /**
     * Clear all settings for this service.
     */
    public void clear() {
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(preferencePrefix)) {
                editor.remove(key);
            }
        }
        try {
            editor.apply();
            currentValues.clear();
        } catch (Exception e) {
            FileLog.e("BaseServiceSettings: Failed to clear preferences", e);
        }
    }
}