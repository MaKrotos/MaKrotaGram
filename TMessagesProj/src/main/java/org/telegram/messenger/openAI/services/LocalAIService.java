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
import org.telegram.messenger.openAI.services.EngineLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LocalAIService extends BaseAIService {

    private static final String TAG = "LocalAIService";

    private final Context appContext;
    private EngineLoader engineLoader;
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
        // Создаём загрузчик движка
        this.engineLoader = new EngineLoader(appContext);
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

        // Проверяем, загружен ли уже движок с этой моделью
        InferenceEngine engine = engineLoader.getCurrentEngine();
        if (engine != null && engine.isLoaded() && modelPath.equals(engine.getModelPath())) {
            // Движок уже загружен, выполняем инференс
            performInference(engine, systemPrompt, history, config, callback);
            return;
        }

        // Запускаем асинхронную загрузку движка
        engineLoader.loadEngineAsync(modelPath, config, new EngineLoader.LoadCallback() {
            @Override
            public void onLoadStart() {
                Log.d(TAG, "Начало загрузки движка");
                notifyEngineLoadingStarted();
            }

            @Override
            public void onLoadSuccess(InferenceEngine engine) {
                notifyEngineLoadingFinished();
                // Загрузка успешна, выполняем инференс
                performInference(engine, systemPrompt, history, config, callback);
            }

            @Override
            public void onLoadError(String error) {
                notifyEngineLoadingError(error);
                callback.onError("Ошибка загрузки движка: " + error);
            }

            @Override
            public void onLoadProgress(int percent) {
                // Опционально: обновить прогресс-бар
                notifyEngineLoadingProgress(percent / 100.0f);
            }
        });
    }

    @Override
    protected void makeStreamingRequest(String systemPrompt, String history, String modelId, StreamCallback callback) {
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

        // Проверяем, загружен ли уже движок с этой моделью
        InferenceEngine engine = engineLoader.getCurrentEngine();
        if (engine != null && engine.isLoaded() && modelPath.equals(engine.getModelPath())) {
            // Движок уже загружен, выполняем стриминговый инференс
            performStreamingInference(engine, systemPrompt, history, config, callback);
            return;
        }

        // Запускаем асинхронную загрузку движка
        engineLoader.loadEngineAsync(modelPath, config, new EngineLoader.LoadCallback() {
            @Override
            public void onLoadStart() {
                Log.d(TAG, "Начало загрузки движка для стриминга");
                notifyEngineLoadingStarted();
            }

            @Override
            public void onLoadSuccess(InferenceEngine engine) {
                notifyEngineLoadingFinished();
                performStreamingInference(engine, systemPrompt, history, config, callback);
            }

            @Override
            public void onLoadError(String error) {
                notifyEngineLoadingError(error);
                callback.onError("Ошибка загрузки движка: " + error);
            }

            @Override
            public void onLoadProgress(int percent) {
                // Опционально
                notifyEngineLoadingProgress(percent / 100.0f);
            }
        });
    }

    private void performInference(InferenceEngine engine, String systemPrompt, String history,
                                  InferenceConfig config, Callback callback) {
        // Объединяем системный промпт и историю в один промпт для модели
        String fullPrompt = systemPrompt + "\n\n" + history;

        // Выполняем инференс в фоновом потоке
        new Thread(() -> {
            try {
                InferenceResult result = engine.infer(fullPrompt, config);
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

    private void performStreamingInference(InferenceEngine engine, String systemPrompt, String history,
                                           InferenceConfig config, StreamCallback callback) {
        // Объединяем системный промпт и историю в один промпт для модели
        String fullPrompt = systemPrompt + "\n\n" + history;

        // Выполняем инференс в фоновом потоке
        new Thread(() -> {
            try {
                InferenceResult result = engine.infer(fullPrompt, config);
                if (result.isSuccess()) {
                    String generatedText = result.getGeneratedText();
                    // Для анализа диалога ожидаем plain text, а не JSON
                    // Эмуляция стриминга: разбиваем текст на чанки по 10 символов
                    int chunkSize = 10;
                    for (int i = 0; i < generatedText.length(); i += chunkSize) {
                        int end = Math.min(generatedText.length(), i + chunkSize);
                        String chunk = generatedText.substring(i, end);
                        callback.onChunk(chunk);
                        // Небольшая задержка для имитации реального стриминга
                        Thread.sleep(50);
                    }
                    callback.onComplete();
                } else {
                    callback.onError("Ошибка инференса: " + result.getErrorMessage());
                }
            } catch (Exception e) {
                FileLog.e(TAG + " Local AI streaming inference error", e);
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
        engineLoader.release();
    }

    /**
     * Получить загрузчик движка (для управления загрузкой из UI).
     */
    public EngineLoader getEngineLoader() {
        return engineLoader;
    }

    @Override
    public boolean isEngineLoading() {
        return engineLoader.isLoading();
    }
}