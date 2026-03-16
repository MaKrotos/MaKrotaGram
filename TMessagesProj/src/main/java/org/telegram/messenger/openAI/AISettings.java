package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

import java.util.HashMap;
import java.util.Map;

public class AISettings {
    
    private static final String PREFS_NAME = "ai_service_settings";
    private static final String KEY_SELECTED_SERVICE = "selected_service";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    
    // Old keys removed (migration disabled)

    // Default models
    public static final String DEFAULT_OPENAI_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-pro";
    
    // Available services
    public enum AIServiceType {
        OPENAI("OpenAI"),
        GEMINI("Google Gemini"),
        CUSTOM("Custom API");
        
        private final String displayName;
        
        AIServiceType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        public static AIServiceType fromString(String value) {
            try {
                return valueOf(value);
            } catch (Exception e) {
                return OPENAI;
            }
        }
    }
    
    private SharedPreferences preferences;
    private int currentAccount;
    private Map<AIServiceType, BaseServiceSettings> serviceSettingsMap;
    private AIServiceType selectedService;
    private String systemPrompt = "";
    
    public AISettings() {
        this(UserConfig.selectedAccount);
    }
    
    public AISettings(int account) {
        this.currentAccount = account;
        this.preferences = ApplicationLoader.applicationContext.getSharedPreferences(
            PREFS_NAME + "_" + account, Context.MODE_PRIVATE); // настройки промпта и выбранного сервиса зависят от аккаунта
        this.serviceSettingsMap = new HashMap<>();
        loadAll();
    }
    
    /**
     * Load all settings from SharedPreferences.
     */
    private void loadAll() {
        // Load selected service
        selectedService = AIServiceType.fromString(
            preferences.getString(KEY_SELECTED_SERVICE, AIServiceType.OPENAI.name()));
        
        // Load system prompt
        systemPrompt = preferences.getString(KEY_SYSTEM_PROMPT, "");
        
        // Initialize service settings objects
        serviceSettingsMap.put(AIServiceType.OPENAI, new OpenAISettings(currentAccount));
        serviceSettingsMap.put(AIServiceType.GEMINI, new GeminiSettings(currentAccount));
        // TODO: Custom service settings
    }
    
    
    /**
     * Save all settings (selected service, system prompt, and each service's settings).
     */
    public void saveAll() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SELECTED_SERVICE, selectedService.name());
        editor.putString(KEY_SYSTEM_PROMPT, systemPrompt);
        editor.apply();
        
        for (BaseServiceSettings settings : serviceSettingsMap.values()) {
            settings.save();
        }
    }
    
    /**
     * Get settings for a specific service type.
     */
    public BaseServiceSettings getServiceSettings(AIServiceType type) {
        BaseServiceSettings settings = serviceSettingsMap.get(type);
        if (settings == null) {
            // Lazy initialization (should not happen)
            switch (type) {
                case OPENAI:
                    settings = new OpenAISettings(currentAccount);
                    break;
                case GEMINI:
                    settings = new GeminiSettings(currentAccount);
                    break;
                default:
                    settings = new OpenAISettings(currentAccount);
            }
            serviceSettingsMap.put(type, settings);
        }
        return settings;
    }
    
    /**
     * Get the currently selected service type.
     */
    public AIServiceType getSelectedServiceType() {
        return selectedService;
    }
    
    /**
     * Set the selected service type.
     */
    public void setSelectedService(AIServiceType serviceType) {
        this.selectedService = serviceType;
        saveAll();
    }
    
    /**
     * Get the system prompt.
     */
    public String getSystemPrompt() {
        if (TextUtils.isEmpty(systemPrompt)) {
            return null;
        }
        return systemPrompt;
    }
    
    /**
     * Set the system prompt.
     */
    public void setSystemPrompt(String prompt) {
        this.systemPrompt = prompt != null ? prompt : "";
        saveAll();
    }
    
    /**
     * Check if the selected service has valid configuration.
     */
    public boolean hasValidConfig() {
        BaseServiceSettings settings = getServiceSettings(selectedService);
        return settings.validate();
    }
    
    /**
     * Check if a specific service has valid configuration.
     */
    public boolean hasValidConfig(AIServiceType serviceType) {
        BaseServiceSettings settings = getServiceSettings(serviceType);
        return settings.validate();
    }
    
    /**
     * Get the display name of the selected service.
     */
    public String getServiceName() {
        return selectedService.getDisplayName();
    }
    
    /**
     * Get the API key for the selected service.
     */
    public String getCurrentApiKey() {
        BaseServiceSettings settings = getServiceSettings(selectedService);
        return settings.getApiKey();
    }
    
    /**
     * Get the model for the selected service.
     */
    public String getCurrentModel() {
        BaseServiceSettings settings = getServiceSettings(selectedService);
        return settings.getModel();
    }
    
    /**
     * Clear all settings (including per-service).
     */
    public void clearSettings() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.apply();
        // Re-initialize settings map
        serviceSettingsMap.clear();
        loadAll();
    }
    
    /**
     * Clear settings for a specific service.
     */
    public void clearServiceSettings(AIServiceType serviceType) {
        BaseServiceSettings settings = getServiceSettings(serviceType);
        settings.clear();
        settings.save();
    }
    
    /**
     * Check if API key is set for selected service.
     */
    public boolean hasApiKey() {
        return !TextUtils.isEmpty(getCurrentApiKey());
    }
    
    /**
     * Get a summary of the current configuration.
     */
    public String getConfigSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Сервис: ").append(selectedService.getDisplayName()).append("\n");
        
        BaseServiceSettings settings = getServiceSettings(selectedService);
        String model = settings.getModel();
        String apiKey = settings.getApiKey();
        
        summary.append("Модель: ").append(model).append("\n");
        summary.append("API ключ: ").append(TextUtils.isEmpty(apiKey) ? "не установлен" : "установлен");
        
        return summary.toString();
    }
    
    /**
     * Legacy methods for compatibility (delegate to new structure).
     */
    public void setOpenAIApiKey(String apiKey) {
        OpenAISettings openai = (OpenAISettings) getServiceSettings(AIServiceType.OPENAI);
        openai.setApiKey(apiKey);
        openai.save();
    }
    
    public void setOpenAIModel(String model) {
        OpenAISettings openai = (OpenAISettings) getServiceSettings(AIServiceType.OPENAI);
        openai.setModel(model);
        openai.save();
    }
    
    public void setGeminiApiKey(String apiKey) {
        GeminiSettings gemini = (GeminiSettings) getServiceSettings(AIServiceType.GEMINI);
        gemini.setApiKey(apiKey);
        gemini.save();
    }
    
    public void setGeminiModel(String model) {
        GeminiSettings gemini = (GeminiSettings) getServiceSettings(AIServiceType.GEMINI);
        gemini.setModel(model);
        gemini.save();
    }
    
    
    public void setCustomSettings(String apiUrl, String apiKey, String model) {
        // TODO: implement custom service settings
    }
    
}