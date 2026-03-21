package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.telegram.messenger.FileLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings specific to Local AI service (sugar-pocketAI).
 */
public class LocalAISettings extends BaseServiceSettings {

    // Field keys
    private static final String KEY_HF_TOKEN = "hf_token";
    private static final String KEY_MODEL_PATH = "model_path";
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_TOKENS = "max_tokens";
    private static final String KEY_TOP_P = "top_p";
    private static final String KEY_TOP_K = "top_k";
    private static final String KEY_REPEAT_PENALTY = "repeat_penalty";
    private static final String KEY_BATCH_SIZE = "batch_size";
    private static final String KEY_THREADS = "threads";
    private static final String KEY_ACCELERATOR = "accelerator";

    // Default values
    public static final String DEFAULT_MODEL = "Gemma-3n-E2B-it";
    public static final float DEFAULT_TEMPERATURE = 0.8f;
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final float DEFAULT_TOP_P = 0.9f;
    public static final int DEFAULT_TOP_K = 40;
    public static final float DEFAULT_REPEAT_PENALTY = 1.1f;
    public static final int DEFAULT_BATCH_SIZE = 512;
    public static final int DEFAULT_THREADS = 4;
    public static final String DEFAULT_ACCELERATOR = "cpu";

    public LocalAISettings(int account) {
        super(account);
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.LOCAL_AI;
    }

    @Override
    public String getDefaultModelId() {
        return DEFAULT_MODEL;
    }

    @Override
    public String getServiceDisplayName() {
        return "Local AI";
    }

    @Override
    protected void onLoad() {
        FileLog.d("LocalAISettings load: temperature=" + getTemperature() +
                ", maxTokens=" + getMaxTokens() +
                ", modelPath=" + getModelPath());
    }

    @Override
    protected void onSave() {
        FileLog.d("LocalAISettings: Saved all preferences atomically");
    }

    // Convenience getters/setters

    public String getHfToken() {
        return (String) getValue(KEY_HF_TOKEN);
    }

    public void setHfToken(String token) {
        setValue(KEY_HF_TOKEN, token);
    }

    public String getModelPath() {
        return (String) getValue(KEY_MODEL_PATH);
    }

    public void setModelPath(String path) {
        setValue(KEY_MODEL_PATH, path);
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

    public int getTopK() {
        return (int) getValue(KEY_TOP_K);
    }

    public void setTopK(int topK) {
        setValue(KEY_TOP_K, topK);
    }

    public float getRepeatPenalty() {
        return (float) getValue(KEY_REPEAT_PENALTY);
    }

    public void setRepeatPenalty(float repeatPenalty) {
        setValue(KEY_REPEAT_PENALTY, repeatPenalty);
    }

    public int getBatchSize() {
        return (int) getValue(KEY_BATCH_SIZE);
    }

    public void setBatchSize(int batchSize) {
        setValue(KEY_BATCH_SIZE, batchSize);
    }

    public int getThreads() {
        return (int) getValue(KEY_THREADS);
    }

    public void setThreads(int threads) {
        setValue(KEY_THREADS, threads);
    }

    public String getAccelerator() {
        return (String) getValue(KEY_ACCELERATOR);
    }

    public void setAccelerator(String accelerator) {
        setValue(KEY_ACCELERATOR, accelerator);
    }

    @Override
    public List<SettingDefinition> getSettingDefinitions() {
        List<SettingDefinition> definitions = new ArrayList<>();
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_HF_TOKEN)
                .setType(SettingType.STRING)
                .setTitle("Hugging Face Token")
                .setDescription("Токен для загрузки моделей с Hugging Face (необязательно).")
                .setMasked(true)
                .setRequired(false)
                .setDefaultValue("")
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_MODEL_PATH)
                .setType(SettingType.STRING)
                .setTitle("Путь к модели")
                .setDescription("Локальный путь к файлу модели (.gguf, .litertlm). Оставьте пустым для выбора из каталога.")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue("")
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
                    put("max", 4096);
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
                .setKey(KEY_TOP_K)
                .setType(SettingType.INT)
                .setTitle("Top K")
                .setDescription("Количество наиболее вероятных токенов для выборки (1-100).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_TOP_K)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 100);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_REPEAT_PENALTY)
                .setType(SettingType.FLOAT)
                .setTitle("Repeat Penalty")
                .setDescription("Штраф за повторение токенов (1.0 - 2.0).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_REPEAT_PENALTY)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1.0f);
                    put("max", 2.0f);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_BATCH_SIZE)
                .setType(SettingType.INT)
                .setTitle("Batch Size")
                .setDescription("Размер батча для инференса (1-2048).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_BATCH_SIZE)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 2048);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_THREADS)
                .setType(SettingType.INT)
                .setTitle("Потоки")
                .setDescription("Количество потоков для вычислений (1-16).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_THREADS)
                .setConstraints(new HashMap<String, Object>() {{
                    put("min", 1);
                    put("max", 16);
                }})
                .build());
        definitions.add(new SettingDefinition.Builder()
                .setKey(KEY_ACCELERATOR)
                .setType(SettingType.CHOICE)
                .setTitle("Акселератор")
                .setDescription("Выбор аппаратного ускорения (CPU, GPU, NPU).")
                .setMasked(false)
                .setRequired(false)
                .setDefaultValue(DEFAULT_ACCELERATOR)
                .setConstraints(new HashMap<String, Object>() {{
                    put("choices", Arrays.asList(
                            "cpu",
                            "gpu"
                    ));
                }})
                .build());
        return definitions;
    }
}