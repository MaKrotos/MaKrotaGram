package org.telegram.messenger.openAI;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Base64;
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
    private static AIModel[] AVAILABLE_MODELS = null;

    private static synchronized AIModel[] loadModels() {
        if (AVAILABLE_MODELS != null) {
            return AVAILABLE_MODELS;
        }
        List<ModelItem> modelItems = ModelCatalog.getDefaultModels();
        List<AIModel> models = new ArrayList<>();
        for (ModelItem item : modelItems) {
            // Берем maxTokens из ModelItem
            int maxTokens = item.getMaxTokens();
            // Определяем поддержку функций: если модель поддерживает изображения или аудио, считаем что поддерживает функции
            boolean supportsFunctions = item.isLlmSupportImage() || item.isLlmSupportAudio();
            AIModel model = new AIModel(
                    item.getName(),
                    item.getName(), // displayName можно взять из name, но лучше использовать description?
                    item.getDescription(),
                    maxTokens,
                    item.isLlmSupportImage(),
                    item.isLlmSupportAudio(),
                    supportsFunctions
            );
            models.add(model);
        }
        AVAILABLE_MODELS = models.toArray(new AIModel[0]);
        return AVAILABLE_MODELS;
    }

    private static AIModel[] getAvailableModelsInternal() {
        return loadModels();
    }

    public LocalAIService() {
        this(UserConfig.selectedAccount);
    }

    public LocalAIService(int account) {
        super(account);
        this.appContext = ApplicationLoader.applicationContext;
        // Инициализируем карту моделей
        AIModel[] models = getAvailableModelsInternal();
        for (AIModel model : models) {
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

    private boolean modelSupportsVision(String modelId) {
        org.telegram.messenger.openAI.BaseAIService.AIModel model = modelsMap.get(modelId);
        return model != null && model.supportsVision;
    }

    private boolean modelSupportsAudio(String modelId) {
        org.telegram.messenger.openAI.BaseAIService.AIModel model = modelsMap.get(modelId);
        return model != null && model.supportsAudio;
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String modelId,
                               List<BaseAIService.ImageAttachment> images, List<BaseAIService.AudioAttachment> audio, Callback callback) {
        // Если модель не поддерживает мультимодальность или медиа нет, вызываем обычный запрос
        boolean hasImages = images != null && !images.isEmpty();
        boolean hasAudio = audio != null && !audio.isEmpty();
        boolean modelSupportsVision = modelSupportsVision(modelId);
        boolean modelSupportsAudio = modelSupportsAudio(modelId);
        
        if ((!hasImages && !hasAudio) || (!modelSupportsVision && !modelSupportsAudio)) {
            makeRequest(systemPrompt, history, modelId, callback);
            return;
        }

        // Получаем настройки Local AI
        LocalAISettings settings = (org.telegram.messenger.openAI.LocalAISettings) getServiceSettings();
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
                .build();

        // Проверяем, загружен ли уже движок с этой моделью
        InferenceEngine engine = engineLoader.getCurrentEngine();
        if (engine != null && engine.isLoaded() && modelPath.equals(engine.getModelPath())) {
            // Движок уже загружен, выполняем инференс с медиа
            performInferenceWithMedia(engine, systemPrompt, history, config, callback, images, audio);
            return;
        }

        // Запускаем асинхронную загрузку движка
        engineLoader.loadEngineAsync(modelPath, config, new EngineLoader.LoadCallback() {
            @Override
            public void onLoadStart() {
                Log.d(TAG, "Начало загрузки движка для мультимодального запроса");
                notifyEngineLoadingStarted();
            }

            @Override
            public void onLoadSuccess(InferenceEngine engine) {
                notifyEngineLoadingFinished();
                performInferenceWithMedia(engine, systemPrompt, history, config, callback, images, audio);
            }

            @Override
            public void onLoadError(String error) {
                notifyEngineLoadingError(error);
                callback.onError("Ошибка загрузки движка: " + error);
            }

            @Override
            public void onLoadProgress(int percent) {
                notifyEngineLoadingProgress(percent / 100.0f);
            }
        });
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String modelId, List<BaseAIService.ImageAttachment> images, Callback callback) {
        // Вызываем новый метод с audio = null для обратной совместимости
        makeRequest(systemPrompt, history, modelId, images, null, callback);
    }

    private void performInferenceWithImages(InferenceEngine engine, String systemPrompt, String history,
                                            InferenceConfig config, Callback callback, List<BaseAIService.ImageAttachment> images) {
        // Вызываем новый метод с audio = null
        performInferenceWithMedia(engine, systemPrompt, history, config, callback, images, null);
    }

    private void performInferenceWithMedia(InferenceEngine engine, String systemPrompt, String history,
                                           InferenceConfig config, Callback callback,
                                           List<BaseAIService.ImageAttachment> images, List<BaseAIService.AudioAttachment> audio) {
        // Объединяем системный промпт и историю в один промпт для модели
        String fullPrompt = systemPrompt + "\n\n" + history;

        // Добавляем транскрипции аудио в промпт (если есть)
        if (audio != null && !audio.isEmpty()) {
            StringBuilder audioTranscripts = new StringBuilder("\n\n[Аудио транскрипции]:\n");
            for (BaseAIService.AudioAttachment aud : audio) {
                if (aud.transcription != null && !aud.transcription.isEmpty()) {
                    audioTranscripts.append("- ").append(aud.transcription).append("\n");
                } else if (aud.caption != null && !aud.caption.isEmpty()) {
                    audioTranscripts.append("- [Аудио с подписью: ").append(aud.caption).append("]\n");
                } else {
                    audioTranscripts.append("- [Аудио файл]\n");
                }
            }
            fullPrompt += audioTranscripts.toString();
        }

        // Преобразуем ImageAttachment в Bitmap
        List<Bitmap> bitmaps = new ArrayList<>();
        if (images != null) {
            for (BaseAIService.ImageAttachment img : images) {
                try {
                    byte[] imageBytes = Base64.decode(img.base64Data, Base64.DEFAULT);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                    if (bitmap != null) {
                        bitmaps.add(bitmap);
                    } else {
                        Log.w(TAG, "Не удалось декодировать изображение из base64");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Ошибка преобразования изображения", e);
                }
            }
        }

        // Выполняем инференс в фоновом потоке
        String finalFullPrompt = fullPrompt;
        new Thread(() -> {
            try {
                InferenceResult result = engine.infer(finalFullPrompt, config, bitmaps, null);
                if (result.isSuccess()) {
                    String generatedText = result.getGeneratedText();
                    JSONObject jsonResponse = cleanJsonResponse(generatedText);
                    if (jsonResponse == null) {
                        throw new Exception("Failed to parse JSON response");
                    }
                    JSONObject suggestions = enhanceSuggestions(jsonResponse);
                    callback.onSuccess(suggestions);
                } else {
                    callback.onError("Ошибка инференса: " + result.getErrorMessage());
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка инференса с медиа", e);
                callback.onError("Ошибка инференса: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void makeStreamingRequest(String systemPrompt, String history, String modelId, StreamCallback callback) {
        // Получаем настройки Local AI
        org.telegram.messenger.openAI.LocalAISettings settings = (LocalAISettings) getServiceSettings();
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
        // Возвращаем модели из каталога
        return getAvailableModelsInternal();
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