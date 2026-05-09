package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.telegram.messenger.FileLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Settings specific to Ollama service.
 */
public class OllamaSettings extends BaseServiceSettings {

    private static final String KEY_API_URL = "ollama_api_url";
    private static final String KEY_TEMPERATURE = "ollama_temperature";
    private static final String KEY_MAX_TOKENS = "ollama_max_tokens";
    private static final String KEY_TOP_P = "ollama_top_p";
    private static final String KEY_FREQUENCY_PENALTY = "ollama_frequency_penalty";
    private static final String KEY_PRESENCE_PENALTY = "ollama_presence_penalty";

    // Default values
    public static final String DEFAULT_API_URL = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "llama3";
    public static final float DEFAULT_TEMPERATURE = 0.7f;
    public static final int DEFAULT_MAX_TOKENS = 2048;
    public static final float DEFAULT_TOP_P = 0.9f;
    public static final float DEFAULT_FREQUENCY_PENALTY = 0.0f;
    public static final float DEFAULT_PRESENCE_PENALTY = 0.0f;

    public OllamaSettings(int account) {
        super(account);
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.OLLAMA;
    }

    @Override
    public String getDefaultModelId() {
        return DEFAULT_MODEL;
    }

    @Override
    public String getServiceDisplayName() {
        return "Ollama";
    }

    @Override
    protected void onLoad() {
        FileLog.d("OllamaSettings load: url=" + getApiUrl() +
                ", temperature=" + getTemperature() +
                ", model=" + getModel());
    }

    @Override
    protected void onSave() {
        FileLog.d("OllamaSettings: Saved preferences");
    }

    public String getApiUrl() {
        String url = (String) getValue(KEY_API_URL);
        return TextUtils.isEmpty(url) ? DEFAULT_API_URL : url;
    }

    public void setApiUrl(String apiUrl) {
        setValue(KEY_API_URL, apiUrl);
    }

    public float getTemperature() {
        return (float) getValue(KEY_TEMPERATURE);
    }

    public void setTemperature(float temperature) {
        setValue(KEY_TEMPERATURE, temperature);
    }

    public int getMaxTokens() {
        return (int) getValue(KEY_MAX_TOKENS);
    }

    public void setMaxTokens(int maxTokens) {
        setValue(KEY_MAX_TOKENS, maxTokens);
    }

    public float getTopP() {
        return (float) getValue(KEY_TOP_P);
    }

    public void setTopP(float topP) {
        setValue(KEY_TOP_P, topP);
    }

    public float getFrequencyPenalty() {
        return (float) getValue(KEY_FREQUENCY_PENALTY);
    }

    public void setFrequencyPenalty(float frequencyPenalty) {
        setValue(KEY_FREQUENCY_PENALTY, frequencyPenalty);
    }

    public float getPresencePenalty() {
        return (float) getValue(KEY_PRESENCE_PENALTY);
    }

    public void setPresencePenalty(float presencePenalty) {
        setValue(KEY_PRESENCE_PENALTY, presencePenalty);
    }

    @Override
    public List<SettingDefinition> getSettingDefinitions() {
        List<SettingDefinition> definitions = new ArrayList<>();
        
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_API_URL)
                .setType(SettingType.STRING)
                .setTitle("API URL")
                .setDescription("Адрес сервера Ollama (например, http://192.168.1.10:11434)")
                .setMasked(false)
                .setRequired(true)
                .setDefaultValue(DEFAULT_API_URL)
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_API_KEY)
                .setType(SettingType.STRING)
                .setTitle("API Key")
                .setDescription("Ключ API (если используется прокси или авторизация)")
                .setMasked(true)
                .setRequired(false)
                .setDefaultValue("")
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MODEL)
                .setType(SettingType.STRING)
                .setTitle("Модель")
                .setDescription("ID модели Ollama (например, llama3, mistral, phi3).")
                .setMasked(false)
                .setRequired(true)
                .setDefaultValue(DEFAULT_MODEL)
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_TEMPERATURE)
                .setType(SettingType.FLOAT)
                .setTitle("Температура")
                .setDescription("Контроль случайности ответов (0.0 - 2.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TEMPERATURE)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 0.0f);
                    put("max", 2.0f);
                }})
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MAX_TOKENS)
                .setType(SettingType.INT)
                .setTitle("Максимальное количество токенов")
                .setDescription("Ограничение длины ответа в токенах.")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_MAX_TOKENS)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 100000);
                }})
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_TOP_P)
                .setType(SettingType.FLOAT)
                .setTitle("Top P")
                .setDescription("Ядерная выборка (0.0 - 1.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TOP_P)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 0.0f);
                    put("max", 1.0f);
                }})
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_FREQUENCY_PENALTY)
                .setType(SettingType.FLOAT)
                .setTitle("Frequency Penalty")
                .setDescription("Штраф за частоту слов (-2.0 - 2.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_FREQUENCY_PENALTY)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", -2.0f);
                    put("max", 2.0f);
                }})
                .build());

        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_PRESENCE_PENALTY)
                .setType(SettingType.FLOAT)
                .setTitle("Presence Penalty")
                .setDescription("Штраф за присутствие слов (-2.0 - 2.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_PRESENCE_PENALTY)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", -2.0f);
                    put("max", 2.0f);
                }})
                .build());

        return definitions;
    }
}
