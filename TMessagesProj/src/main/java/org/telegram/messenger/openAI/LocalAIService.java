package org.telegram.messenger.openAI;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.fdw.sugar_pocketai.inference.InferenceConfig;
import com.fdw.sugar_pocketai.inference.InferenceEngine;
import com.fdw.sugar_pocketai.inference.InferenceResult;
import com.fdw.sugar_pocketai.inference.LiteRTEngine;
import com.fdw.sugar_pocketai.model.ModelCatalog;
import com.fdw.sugar_pocketai.model.ModelItem;
import com.fdw.sugar_pocketai.model.ModelManager;
import com.fdw.sugar_pocketai.model.ModelEntity;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalAIService extends BaseAIService {

    private static final String TAG = "LocalAIService";

    private final Context appContext;
    private InferenceEngine inferenceEngine;
    private final Map<String, AIModel> modelsMap = new HashMap<>();

    // Определяем доступные модели из каталога
    private static final AIModel[] AVAILABLE_MODELS = {
            new AIModel("Gemma-3n-E2B-it", "Gemma 3n E2B", "Gemma 3n E2B with text, vision, audio support", 4096, true, true),
            new AIModel("Gemma-3n-E4B-it", "Gemma 3n E4B", "Gemma 3n E4B with text, vision, audio support", 4096, true, true),
            new AIModel("Gemma3-1B-IT", "Gemma3 1B", "Quantized Gemma3 1B Instruct model", 2048, false, false),
            new AIModel("Qwen2.5-1.5B-Instruct", "Qwen2.5 1.5B", "Qwen2.5 1.5B Instruct model for Android", 4096, false, false),
            new AIModel("DeepSeek-R1-Distill-Qwen-1.5B", "DeepSeek R1 Qwen 1.5B", "DeepSeek R1 distilled Qwen 1.5B model", 4096, false, false),
            new AIModel("TinyGarden-270M", "TinyGarden 270M", "Fine-tuned Function Gemma 270M for Tiny Garden", 1024, false, false),
            new AIModel("MobileActions-270M", "MobileActions 270M", "Fine-tuned Function Gemma 270M for Mobile Actions", 1024, false, false)
    };

    public LocalAIService() {
        this(UserConfig.selectedAccount);
    }

    public LocalAIService(int account) {
        super(account);
        this.appContext = ApplicationLoader.applicationContext;
        // Инициализируем карту моделей
        for (AIModel model : AVAILABLE_MODELS) {
            modelsMap.put(model.id, model);
        }
        // Создаём движок (но не инициализируем, пока не выбран путь к модели)
        this.inferenceEngine = new LiteRTEngine();
        ((LiteRTEngine) inferenceEngine).setContext(appContext);
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String modelId, Callback callback) {
        // Получаем настройки Local AI
        LocalAISettings settings = (LocalAISettings) getServiceSettings();
        if (!settings.validate()) {
            callback.onError("Настройки Local AI не заполнены. Укажите путь к модели или скачайте модель.");
            return;
        }

        // Получаем путь к модели из настроек
        String modelPath = settings.getModelPath();
        if (TextUtils.isEmpty(modelPath)) {
            callback.onError("Путь к модели не указан. Выберите или скачайте модель в настройках.");
            return;
        }
        File modelFile = new File(modelPath);
        if (!modelFile.exists()) {
            callback.onError("Файл модели не найден: " + modelPath);
            return;
        }

        // Создаём конфигурацию инференса из настроек
        InferenceConfig config = new InferenceConfig.Builder()
                .setNThreads(settings.getThreads())
                .setNPredict(settings.getMaxTokens())
                .setTopK(settings.getTopK())
                .setTopP(settings.getTopP())
                .setTemperature(settings.getTemperature())
                .setRepeatPenalty(settings.getRepeatPenalty())
                .setAccelerator(settings.getAccelerator())
                // Остальные параметры оставляем по умолчанию
                .build();

        // Инициализируем движок, если ещё не загружен
        if (!inferenceEngine.isLoaded() || !modelPath.equals(inferenceEngine.getModelPath())) {
            boolean initSuccess = inferenceEngine.init(modelPath, config);
            if (!initSuccess) {
                callback.onError("Не удалось инициализировать движок инференса для модели: " + modelPath);
                return;
            }
        }

        // Объединяем системный промпт и историю в один промпт для модели
        String fullPrompt = systemPrompt + "\n\n" + history;

        // Выполняем инференс в фоновом потоке
        new Thread(() -> {
            try {
                InferenceResult result = inferenceEngine.infer(fullPrompt, config);
                if (result.isSuccess()) {
                    String generatedText = result.getGeneratedText();
                    // Ожидаем JSON-ответ, как требует BaseAIService
                    JSONObject jsonResponse = cleanJsonResponse(generatedText);
                    if (jsonResponse == null) {
                        // Если ответ не JSON, пытаемся обернуть в JSON
                        jsonResponse = createFallbackResponse(generatedText);
                    }
                    JSONObject enhanced = enhanceSuggestions(jsonResponse);
                    callback.onSuccess(enhanced);
                } else {
                    callback.onError("Ошибка инференса: " + result.getErrorMessage());
                }
            } catch (Exception e) {
                FileLog.e(TAG + " Local AI inference error", e);
                callback.onError("Исключение при инференсе: " + e.getMessage());
            }
        }).start();
    }

    private JSONObject createFallbackResponse(String text) {
        try {
            JSONArray suggestions = new JSONArray();
            suggestions.put(text);

            JSONObject response = new JSONObject();
            response.put("suggestions", suggestions);
            return response;
        } catch (Exception e) {
            FileLog.e(TAG + " Error creating fallback response", e);
            // Возвращаем простой JSON с дефолтным предложением
            JSONObject response = new JSONObject();
            return response;
        }
    }

    @Override
    public String getServiceName() {
        return "Local AI";
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.LOCAL_AI;
    }

    @Override
    public AIModel[] getAvailableModels() {
        // Можно также добавить модели, скачанные через ModelManager
        return AVAILABLE_MODELS;
    }

    @Override
    public String getDefaultModelId() {
        return "Gemma-3n-E2B-it";
    }

    @Override
    public AIModel getModelById(String modelId) {
        if (TextUtils.isEmpty(modelId)) {
            return getModelById(getDefaultModelId());
        }
        AIModel model = modelsMap.get(modelId);
        if (model == null) {
            // Если модель не найдена, возвращаем дефолтную
            return getModelById(getDefaultModelId());
        }
        return model;
    }

    /**
     * Получить менеджер моделей для скачивания и управления.
     */
    public ModelManager getModelManager() {
        return new ModelManager(appContext);
    }

    /**
     * Получить список рекомендованных моделей из каталога.
     */
    public List<ModelItem> getRecommendedModels(String huggingFaceToken) {
        return ModelCatalog.getDefaultModels(huggingFaceToken);
    }

    /**
     * Сканировать локальную директорию на наличие моделей и добавить их в базу.
     */
    public List<ModelEntity> scanLocalModels(String directoryPath) {
        ModelManager manager = getModelManager();
        return manager.scanAndAddModels(directoryPath);
    }

    /**
     * Освободить ресурсы движка.
     */
    public void releaseEngine() {
        if (inferenceEngine != null) {
            inferenceEngine.release();
        }
    }
}