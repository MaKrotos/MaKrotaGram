package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LlamaService extends BaseAIService {

    private static final String API_URL = "https://api.together.xyz/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Map<String, AIModel> modelsMap = new HashMap<>();

    // Определяем доступные модели Llama
    private static final AIModel[] AVAILABLE_MODELS = {
            new AIModel(
                    "meta-llama/Llama-3.3-70B-Instruct-Turbo",
                    "Llama 3.3 70B Turbo",
                    "Самая быстрая модель Llama 3.3 с отличным качеством",
                    8192,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.2-11B-Vision-Instruct-Turbo",
                    "Llama 3.2 11B Vision",
                    "Мультимодальная модель с поддержкой изображений",
                    4096,
                    true,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.2-3B-Instruct-Turbo",
                    "Llama 3.2 3B",
                    "Легкая и быстрая модель для простых задач",
                    4096,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.2-90B-Vision-Instruct-Turbo",
                    "Llama 3.2 90B Vision",
                    "Крупнейшая мультимодальная модель",
                    8192,
                    true,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.1-405B-Instruct-Turbo",
                    "Llama 3.1 405B",
                    "Самая мощная модель Llama",
                    16384,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.1-70B-Instruct-Turbo",
                    "Llama 3.1 70B",
                    "Сбалансированная модель для сложных задач",
                    8192,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3.1-8B-Instruct-Turbo",
                    "Llama 3.1 8B",
                    "Оптимальная для большинства задач",
                    8192,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3-70B-Instruct-Turbo",
                    "Llama 3 70B",
                    "Стабильная версия Llama 3",
                    8192,
                    false,
                    true
            ),
            new AIModel(
                    "meta-llama/Llama-3-8B-Instruct-Turbo",
                    "Llama 3 8B",
                    "Быстрая версия Llama 3",
                    8192,
                    false,
                    true
            ),
            // Специализированные модели
            new AIModel(
                    "mistralai/Mixtral-8x7B-Instruct-v0.1",
                    "Mixtral 8x7B",
                    "Модель от Mistral с архитектурой MoE",
                    32768,
                    false,
                    true
            ),
            new AIModel(
                    "togethercomputer/CodeLlama-34B-Instruct",
                    "CodeLlama 34B",
                    "Специализирована для программирования",
                    16384,
                    false,
                    true
            )
    };

    public LlamaService() {
        this(UserConfig.selectedAccount);
    }

    public LlamaService(int account) {
        super(account);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS) // Llama может быть медленнее
                .build();

        // Инициализируем карту моделей
        for (AIModel model : AVAILABLE_MODELS) {
            modelsMap.put(model.id, model);
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model, Callback callback) {
        String apiKey = settings.getCurrentApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("API ключ Together.ai/Llama не установлен");
            return;
        }

        try {
            // Формируем запрос в формате OpenAI (совместимый с Together.ai)
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", 0.8);
            requestBody.put("max_tokens", 1500);
            requestBody.put("top_p", 0.95);
            requestBody.put("frequency_penalty", 0.3);
            requestBody.put("presence_penalty", 0.3);

            JSONArray messages = new JSONArray();

            // Системный промпт
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);

            // История и запрос пользователя
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", history);
            messages.put(userMessage);

            requestBody.put("messages", messages);

            // Опции для Together.ai
            requestBody.put("stop", new JSONArray()); // Пустой массив для использования стандартных стоп-токенов

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // Парсим ответ (формат OpenAI)
                        String extractedContent = extractContentFromResponse(jsonResponse);

                        try {
                            extractedContent = cleanJsonResponse(extractedContent);
                            JSONObject suggestions = new JSONObject(extractedContent);
                            suggestions = enhanceSuggestions(suggestions);
                            callback.onSuccess(suggestions);
                        } catch (Exception e) {
                            FileLog.e("Error parsing Llama response: " + e.getMessage());
                            callback.onSuccess(createDefaultResponse());
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("Llama request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating Llama request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    public boolean hasValidConfig() {
        return !TextUtils.isEmpty(settings.getCurrentApiKey());
    }

    @Override
    public String getServiceName() {
        return "Llama (Together.ai)";
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.LLAMA;
    }

    @Override
    public AIModel[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    public String getDefaultModelId() {
        return "meta-llama/Llama-3.3-70B-Instruct-Turbo";
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

    private String extractContentFromResponse(JSONObject response) throws Exception {
        // Проверяем на ошибки
        if (response.has("error")) {
            JSONObject error = response.getJSONObject("error");
            throw new Exception(error.optString("message", "Unknown error"));
        }

        if (response.has("choices") && response.getJSONArray("choices").length() > 0) {
            JSONObject choice = response.getJSONArray("choices").getJSONObject(0);
            
            if (choice.has("message")) {
                JSONObject message = choice.getJSONObject("message");
                if (message.has("content")) {
                    return message.getString("content");
                }
            }
            
            // Альтернативный формат (текст напрямую)
            if (choice.has("text")) {
                return choice.getString("text");
            }
        }
        
        throw new Exception("Невозможно извлечь текст из ответа Llama");
    }

    private void handleErrorResponse(Response response, Callback callback) throws IOException {
        String errorBody = response.body() != null ? response.body().string() : "";
        FileLog.e("Llama API error: " + response.code() + " - " + errorBody);

        String errorMessage;
        switch (response.code()) {
            case 400:
                try {
                    JSONObject error = new JSONObject(errorBody);
                    if (error.has("error")) {
                        errorMessage = "Ошибка Llama: " + error.getJSONObject("error").optString("message", "Неверный запрос");
                    } else {
                        errorMessage = "Неверный запрос к Llama API";
                    }
                } catch (Exception e) {
                    errorMessage = "Неверный запрос к Llama API";
                }
                break;
            case 401:
            case 403:
                errorMessage = "Неверный API ключ Together.ai";
                break;
            case 429:
                errorMessage = "Превышен лимит запросов";
                break;
            case 404:
                errorMessage = "Модель не найдена или недоступна";
                break;
            case 503:
                errorMessage = "Сервис временно недоступен";
                break;
            default:
                errorMessage = "Ошибка Llama API: " + response.code();
        }
        callback.onError(errorMessage);
    }

    /**
     * Проверяет доступность конкретной модели
     */
    public void checkModelAvailability(String modelId, ModelCheckCallback callback) {
        String apiKey = settings.getCurrentApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("API ключ не установлен");
            return;
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", modelId);
            requestBody.put("messages", new JSONArray().put(new JSONObject()
                    .put("role", "user")
                    .put("content", "test")));
            requestBody.put("max_tokens", 1);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        callback.onAvailable(true);
                    } else {
                        callback.onAvailable(false);
                    }
                } catch (Exception e) {
                    callback.onError(e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public interface ModelCheckCallback {
        void onAvailable(boolean available);
        void onError(String error);
    }
}