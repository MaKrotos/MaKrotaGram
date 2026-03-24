package org.telegram.messenger.openAI;

import org.json.JSONArray;
import org.json.JSONObject;
import android.text.TextUtils;
import org.telegram.messenger.FileLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings specific to Google Gemini service.
 */
public class GeminiSettings extends BaseServiceSettings {

    // Field keys
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_OUTPUT_TOKENS = "max_output_tokens";
    private static final String KEY_TOP_P = "top_p";
    private static final String KEY_TOP_K = "top_k";
    private static final String KEY_SAFETY_SETTINGS = "safety_settings";

    // Default values
    public static final String DEFAULT_MODEL = "gemini-pro";
    public static final float DEFAULT_TEMPERATURE = 0.9f;
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 1500;
    public static final float DEFAULT_TOP_P = 0.95f;
    public static final int DEFAULT_TOP_K = 40;
    public static final String DEFAULT_SAFETY_SETTING = "BLOCK_NONE";

    public GeminiSettings(int account) {
        super(account);
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.GEMINI;
    }

    @Override
    public String getDefaultModelId() {
        return DEFAULT_MODEL;
    }

    @Override
    public String getServiceDisplayName() {
        return "Google Gemini";
    }

    @Override
    protected void onLoad() {
        // Можно добавить логирование
        FileLog.d("GeminiSettings loaded: temperature=" + getTemperature() +
                ", maxOutputTokens=" + getMaxOutputTokens() +
                ", topP=" + getTopP() +
                ", topK=" + getTopK());
    }

    // Convenience getters that use getValue
    public float getTemperature() {
        return (float) getValue(KEY_TEMPERATURE);
    }

    public void setTemperature(float temperature) {
        setValue(KEY_TEMPERATURE, temperature);
    }

    public int getMaxOutputTokens() {
        return (int) getValue(KEY_MAX_OUTPUT_TOKENS);
    }

    public void setMaxOutputTokens(int maxOutputTokens) {
        setValue(KEY_MAX_OUTPUT_TOKENS, maxOutputTokens);
    }

    @Override
    public int getMaxTokens() {
        return getMaxOutputTokens();
    }

    public float getTopP() {
        return (float) getValue(KEY_TOP_P);
    }

    public void setTopP(float topP) {
        setValue(KEY_TOP_P, topP);
    }

    public int getTopK() {
        return (int) getValue(KEY_TOP_K);
    }

    public void setTopK(int topK) {
        setValue(KEY_TOP_K, topK);
    }

    // Safety settings as JSON string
    public Map<String, String> getSafetySettings() {
        String json = (String) getValue(KEY_SAFETY_SETTINGS);
        Map<String, String> map = new HashMap<>();
        if (TextUtils.isEmpty(json)) {
            // Defaults
            map.put("HARM_CATEGORY_HARASSMENT", DEFAULT_SAFETY_SETTING);
            map.put("HARM_CATEGORY_HATE_SPEECH", DEFAULT_SAFETY_SETTING);
            map.put("HARM_CATEGORY_SEXUALLY_EXPLICIT", DEFAULT_SAFETY_SETTING);
            map.put("HARM_CATEGORY_DANGEROUS_CONTENT", DEFAULT_SAFETY_SETTING);
            return map;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                String category = obj.optString("category");
                String threshold = obj.optString("threshold");
                if (category != null && threshold != null) {
                    map.put(category, threshold);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return map;
    }

    public void setSafetySettings(Map<String, String> safetySettings) {
        if (safetySettings == null || safetySettings.isEmpty()) {
            setValue(KEY_SAFETY_SETTINGS, "");
            return;
        }
        try {
            JSONArray array = new JSONArray();
            for (Map.Entry<String, String> entry : safetySettings.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("category", entry.getKey());
                obj.put("threshold", entry.getValue());
                array.put(obj);
            }
            setValue(KEY_SAFETY_SETTINGS, array.toString());
        } catch (Exception e) {
            // ignore
        }
    }

    public void setSafetySetting(String category, String threshold) {
        Map<String, String> map = getSafetySettings();
        map.put(category, threshold);
        setSafetySettings(map);
    }

    public String getSafetySetting(String category) {
        return getSafetySettings().get(category);
    }

    @Override
    public List<SettingDefinition> getSettingDefinitions() {
        List<SettingDefinition> definitions = new ArrayList<>();
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_API_KEY)
                .setType(SettingType.STRING)
                .setTitle("API Key")
                .setDescription("Ключ API от Google AI Studio. Начинается с 'AIza'.")
                .setMasked(true)
                .setRequired(false) // не обязательный
                .setDefaultValue("")
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MODEL)
                .setType(SettingType.CHOICE)
                .setTitle("Модель")
                .setDescription("Модель Gemini для генерации текста.")
                .setMasked(false)
                .setRequired(true)
                .setDefaultValue(DEFAULT_MODEL)
                .setConstraints(new HashMap<String, Object>() {{
                    put("choices", Arrays.asList(
                            "gemini-pro",
                            "gemini-pro-vision",
                            "gemini-1.5-pro",
                            "gemini-1.5-flash",
                            "gemini-1.5-pro-latest"
                    ));
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_TEMPERATURE)
                .setType(SettingType.FLOAT)
                .setTitle("Температура")
                .setDescription("Контроль случайности ответов (0.0 - 1.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TEMPERATURE)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 0.0f);
                    put("max", 1.0f);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MAX_OUTPUT_TOKENS)
                .setType(SettingType.INT)
                .setTitle("Максимальное количество токенов")
                .setDescription("Ограничение длины ответа в токенах.")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_MAX_OUTPUT_TOKENS)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 100000);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_TOP_P)
                .setType(SettingType.FLOAT)
                .setTitle("Top P")
                .setDescription("Альтернатива температуре, ядерная выборка (0.0 - 1.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TOP_P)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 0.0f);
                    put("max", 1.0f);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_TOP_K)
                .setType(SettingType.INT)
                .setTitle("Top K")
                .setDescription("Количество наиболее вероятных токенов для выборки (1-40).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TOP_K)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 40);
                }})
                .build());
        // Safety settings - сложный объект, пока оставим как строку JSON с пояснением
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_SAFETY_SETTINGS)
                .setType(SettingType.STRING)
                .setTitle("Настройки безопасности")
                .setDescription("JSON-массив настроек безопасности (категория и порог).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue("")
                .build());
        return definitions;
    }
}