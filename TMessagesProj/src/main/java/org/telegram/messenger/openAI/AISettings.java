package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;

public class AISettings {
    
    private static final String PREFS_NAME = "ai_service_settings";
    private static final String KEY_SELECTED_SERVICE = "selected_service";
    private static final String KEY_OPENAI_API_KEY = "openai_api_key";
    private static final String KEY_OPENAI_MODEL = "openai_model";
    private static final String KEY_GEMINI_API_KEY = "gemini_api_key";
    private static final String KEY_GEMINI_MODEL = "gemini_model";
    private static final String KEY_CUSTOM_API_URL = "custom_api_url";
    private static final String KEY_CUSTOM_MODEL = "custom_model";
    private static final String KEY_CUSTOM_API_KEY = "custom_api_key";
    private static final String KEY_SYSTEM_PROMPT = "system_prompt";
    
    // Модели по умолчанию
    public static final String DEFAULT_OPENAI_MODEL = "gpt-3.5-turbo";
    public static final String DEFAULT_GEMINI_MODEL = "gemini-pro";
    public static final String DEFAULT_LLAMA_MODEL = "meta-llama/Llama-3.3-70B-Instruct-Turbo";
    private static final String KEY_LLAMA_API_KEY = "llama_api_key";
    private static final String KEY_LLAMA_MODEL = "llama_model";

    
    // Доступные сервисы
    public enum AIServiceType {
        OPENAI("OpenAI"),
        GEMINI("Google Gemini"),
        CUSTOM("Custom API"),
        LLAMA("Llama (Together.ai)");
        
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
    
    public AISettings() {
        this(UserConfig.selectedAccount);
    }
    
    public AISettings(int account) {
        this.currentAccount = account;
        this.preferences = ApplicationLoader.applicationContext.getSharedPreferences(
            PREFS_NAME + "_" + account, Context.MODE_PRIVATE);
    }
    
    /**
     * Сохраняет все настройки
     */
    public void saveSettings(AISettingsData data) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_SELECTED_SERVICE, data.selectedService.name());
        editor.putString(KEY_OPENAI_API_KEY, data.openaiApiKey);
        editor.putString(KEY_OPENAI_MODEL, data.openaiModel);
        editor.putString(KEY_GEMINI_API_KEY, data.geminiApiKey);
        editor.putString(KEY_GEMINI_MODEL, data.geminiModel);
        editor.putString(KEY_CUSTOM_API_URL, data.customApiUrl);
        editor.putString(KEY_CUSTOM_MODEL, data.customModel);
        editor.putString(KEY_CUSTOM_API_KEY, data.customApiKey);
        editor.putString(KEY_SYSTEM_PROMPT, data.systemPrompt);
        editor.apply();
    }
    
    /**
     * Загружает все настройки
     */
    public AISettingsData loadSettings() {
        AISettingsData data = new AISettingsData();
        data.selectedService = AIServiceType.fromString(
            preferences.getString(KEY_SELECTED_SERVICE, AIServiceType.OPENAI.name()));
        data.openaiApiKey = preferences.getString(KEY_OPENAI_API_KEY, "");
        data.openaiModel = preferences.getString(KEY_OPENAI_MODEL, DEFAULT_OPENAI_MODEL);
        data.geminiApiKey = preferences.getString(KEY_GEMINI_API_KEY, "");
        data.geminiModel = preferences.getString(KEY_GEMINI_MODEL, DEFAULT_GEMINI_MODEL);
        data.customApiUrl = preferences.getString(KEY_CUSTOM_API_URL, "");
        data.customModel = preferences.getString(KEY_CUSTOM_MODEL, "");
        data.customApiKey = preferences.getString(KEY_CUSTOM_API_KEY, "");
        data.systemPrompt = preferences.getString(KEY_SYSTEM_PROMPT, "");
        return data;
    }
    
    /**
     * Проверяет, валидны ли настройки для выбранного сервиса
     */
    public boolean hasValidConfig() {
        AISettingsData data = loadSettings();
        return hasValidConfig(data, data.selectedService);
    }
    
    /**
     * Проверяет, валидны ли настройки для указанного сервиса
     */
    public boolean hasValidConfig(AIServiceType serviceType) {
        AISettingsData data = loadSettings();
        return hasValidConfig(data, serviceType);
    }
    
    private boolean hasValidConfig(AISettingsData data, AIServiceType serviceType) {
        switch (serviceType) {
            case OPENAI:
                return !TextUtils.isEmpty(data.openaiApiKey);
            case GEMINI:
                return !TextUtils.isEmpty(data.geminiApiKey);
            case CUSTOM:
                return !TextUtils.isEmpty(data.customApiUrl) && 
                       !TextUtils.isEmpty(data.customApiKey);
            default:
                return false;
        }
    }
    
    /**
     * Возвращает имя выбранного сервиса
     */
    public String getServiceName() {
        return loadSettings().selectedService.getDisplayName();
    }
    
    /**
     * Возвращает тип выбранного сервиса
     */
    public AIServiceType getSelectedServiceType() {
        return loadSettings().selectedService;
    }
    
    /**
     * Возвращает модель для выбранного сервиса
     */
    public String getCurrentModel() {
        AISettingsData data = loadSettings();
        switch (data.selectedService) {
            case OPENAI:
                return data.openaiModel;
            case GEMINI:
                return data.geminiModel;
            case CUSTOM:
                return data.customModel;
            default:
                return "";
        }
    }
    
    /**
     * Возвращает API ключ для выбранного сервиса
     */
    public String getCurrentApiKey() {
        AISettingsData data = loadSettings();
        switch (data.selectedService) {
            case OPENAI:
                return data.openaiApiKey;
            case GEMINI:
                return data.geminiApiKey;
            case CUSTOM:
                return data.customApiKey;
            default:
                return "";
        }
    }
    
    /**
     * Возвращает URL для custom сервиса
     */
    public String getCustomApiUrl() {
        return loadSettings().customApiUrl;
    }
    
    /**
     * Возвращает системный промпт
     */
    public String getSystemPrompt() {
        String prompt = loadSettings().systemPrompt;
        if (TextUtils.isEmpty(prompt)) {
            return null; // Будет использоваться дефолтный из BaseAIService
        }
        return prompt;
    }
    
    /**
     * Сохраняет системный промпт
     */
    public void setSystemPrompt(String prompt) {
        AISettingsData data = loadSettings();
        data.systemPrompt = prompt;
        saveSettings(data);
    }
    
    /**
     * Сохраняет выбранный сервис
     */
    public void setSelectedService(AIServiceType serviceType) {
        AISettingsData data = loadSettings();
        data.selectedService = serviceType;
        saveSettings(data);
    }
    
    /**
     * Сохраняет API ключ для OpenAI
     */
    public void setOpenAIApiKey(String apiKey) {
        AISettingsData data = loadSettings();
        data.openaiApiKey = apiKey;
        saveSettings(data);
    }
    
    /**
     * Сохраняет модель для OpenAI
     */
    public void setOpenAIModel(String model) {
        AISettingsData data = loadSettings();
        data.openaiModel = model;
        saveSettings(data);
    }
    
    /**
     * Сохраняет API ключ для Gemini
     */
    public void setGeminiApiKey(String apiKey) {
        AISettingsData data = loadSettings();
        data.geminiApiKey = apiKey;
        saveSettings(data);
    }
    
    /**
     * Сохраняет модель для Gemini
     */
    public void setGeminiModel(String model) {
        AISettingsData data = loadSettings();
        data.geminiModel = model;
        saveSettings(data);
    }
    
    /**
     * Сохраняет настройки для Custom API
     */
    public void setCustomSettings(String apiUrl, String apiKey, String model) {
        AISettingsData data = loadSettings();
        data.customApiUrl = apiUrl;
        data.customApiKey = apiKey;
        data.customModel = model;
        saveSettings(data);
    }
    
    /**
     * Очищает все настройки
     */
    public void clearSettings() {
        preferences.edit().clear().apply();
    }
    
    /**
     * Очищает настройки для конкретного сервиса
     */
    public void clearServiceSettings(AIServiceType serviceType) {
        AISettingsData data = loadSettings();
        switch (serviceType) {
            case OPENAI:
                data.openaiApiKey = "";
                data.openaiModel = DEFAULT_OPENAI_MODEL;
                break;
            case GEMINI:
                data.geminiApiKey = "";
                data.geminiModel = DEFAULT_GEMINI_MODEL;
                break;
            case CUSTOM:
                data.customApiUrl = "";
                data.customApiKey = "";
                data.customModel = "";
                break;
        }
        saveSettings(data);
    }
    
    /**
     * Проверяет, установлен ли API ключ для выбранного сервиса
     */
    public boolean hasApiKey() {
        return !TextUtils.isEmpty(getCurrentApiKey());
    }
    
    /**
     * Возвращает информацию о текущей конфигурации
     */
    public String getConfigSummary() {
        AISettingsData data = loadSettings();
        StringBuilder summary = new StringBuilder();
        summary.append("Сервис: ").append(data.selectedService.getDisplayName()).append("\n");
        
        switch (data.selectedService) {
            case OPENAI:
                summary.append("Модель: ").append(data.openaiModel).append("\n");
                summary.append("API ключ: ").append(TextUtils.isEmpty(data.openaiApiKey) ? 
                    "не установлен" : "установлен");
                break;
            case GEMINI:
                summary.append("Модель: ").append(data.geminiModel).append("\n");
                summary.append("API ключ: ").append(TextUtils.isEmpty(data.geminiApiKey) ? 
                    "не установлен" : "установлен");
                break;
            case CUSTOM:
                summary.append("URL: ").append(data.customApiUrl).append("\n");
                summary.append("Модель: ").append(data.customModel).append("\n");
                summary.append("API ключ: ").append(TextUtils.isEmpty(data.customApiKey) ? 
                    "не установлен" : "установлен");
                break;
        }
        
        return summary.toString();
    }
    
    /**
     * Класс данных для хранения всех настроек
     */
    public static class AISettingsData {
        public AIServiceType selectedService = AIServiceType.OPENAI;
        public String openaiApiKey = "";
        public String openaiModel = DEFAULT_OPENAI_MODEL;
        public String geminiApiKey = "";
        public String geminiModel = DEFAULT_GEMINI_MODEL;
        public String customApiUrl = "";
        public String customModel = "";
        public String customApiKey = "";
        public String systemPrompt = "";
        public String llamaApiKey = "";
        public String llamaModel = DEFAULT_LLAMA_MODEL;
        
        /**
         * Создает копию объекта
         */
        public AISettingsData copy() {
            AISettingsData copy = new AISettingsData();
            copy.selectedService = this.selectedService;
            copy.openaiApiKey = this.openaiApiKey;
            copy.openaiModel = this.openaiModel;
            copy.geminiApiKey = this.geminiApiKey;
            copy.geminiModel = this.geminiModel;
            copy.customApiUrl = this.customApiUrl;
            copy.customModel = this.customModel;
            copy.customApiKey = this.customApiKey;
            copy.systemPrompt = this.systemPrompt;
            return copy;
        }
        
        /**
         * Проверяет, равны ли два объекта настроек
         */
        public boolean equals(AISettingsData other) {
            if (other == null) return false;
            return selectedService == other.selectedService &&
                   openaiApiKey.equals(other.openaiApiKey) &&
                   openaiModel.equals(other.openaiModel) &&
                   geminiApiKey.equals(other.geminiApiKey) &&
                   geminiModel.equals(other.geminiModel) &&
                   customApiUrl.equals(other.customApiUrl) &&
                   customModel.equals(other.customModel) &&
                   customApiKey.equals(other.customApiKey) &&
                   systemPrompt.equals(other.systemPrompt);
        }
        
        /**
         * Возвращает true, если настройки для выбранного сервиса валидны
         */
        public boolean isValid() {
            switch (selectedService) {
                case OPENAI:
                    return !TextUtils.isEmpty(openaiApiKey);
                case GEMINI:
                    return !TextUtils.isEmpty(geminiApiKey);
                case CUSTOM:
                    return !TextUtils.isEmpty(customApiUrl) && 
                           !TextUtils.isEmpty(customApiKey);
                default:
                    return false;
            }
        }
    }
}