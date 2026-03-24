package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.telegram.messenger.FileLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Settings specific to OpenAI service.
 */
public class OpenAISettings extends BaseServiceSettings {

    // Field keys (already defined in parent)
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TOP_P = "top_p";
    private static final String KEY_FREQUENCY_PENALTY = "frequency_penalty";
    private static final String KEY_PRESENCE_PENALTY = "presence_penalty";

    // Default values
    public static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    public static final float DEFAULT_TEMPERATURE = 0.9f;
    public static final int DEFAULT_MAX_TOKENS = 1500;
    public static final float DEFAULT_TOP_P = 0.95f;
    public static final float DEFAULT_FREQUENCY_PENALTY = 0.5f;
    public static final float DEFAULT_PRESENCE_PENALTY = 0.6f;

    public OpenAISettings(int account) {
        super(account);
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.OPENAI;
    }

    @Override
    public String getDefaultModelId() {
        return DEFAULT_MODEL;
    }

    @Override
    public String getServiceDisplayName() {
        return "OpenAI";
    }

    @Override
    protected void onLoad() {
        // Logging for debugging
        FileLog.d("OpenAISettings load: temperature=" + getTemperature() +
                ", topP=" + getTopP() +
                ", frequencyPenalty=" + getFrequencyPenalty() +
                ", presencePenalty=" + getPresencePenalty() +
                ", maxTokens=" + getMaxTokens() +
                ", model=" + getModel());
    }

    @Override
    protected void onSave() {
        FileLog.d("OpenAISettings: Saved all preferences atomically");
    }

    // Convenience getters that use getValue
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
                .setKey(KEY_API_KEY)
                .setType(SettingType.STRING)
                .setTitle("API Key")
                .setDescription("Ключ API от OpenAI. Начинается с 'sk-'.")
                .setMasked(true)
                .setRequired(false) // API ключ не обязательный
                .setDefaultValue("")
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MODEL)
                .setType(SettingType.CHOICE)
                .setTitle("Модель")
                .setDescription("Модель OpenAI для генерации текста.")
                .setMasked(false)
                .setRequired(true)
                .setDefaultValue(DEFAULT_MODEL)
                .setConstraints(new HashMap<String, Object>() {{
                    put("choices", Arrays.asList(
                            "gpt-3.5-turbo",
                            "gpt-4",
                            "gpt-4-turbo",
                            "gpt-4o",
                            "gpt-4o-mini",
                            "gpt-4o-2024-08-06"
                    ));
                }})
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