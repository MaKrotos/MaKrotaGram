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

public class GeminiService extends BaseAIService {

    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Map<String, AIModel> modelsMap = new HashMap<>();

    // Определяем доступные модели Gemini с полными характеристиками
    private static final AIModel[] AVAILABLE_MODELS = {
            new AIModel(
                    "gemini-pro",
                    "Gemini Pro",
                    "Базовая модель для текстовых задач",
                    30720,
                    false,
                    true
            ),
            new AIModel(
                    "gemini-pro-vision",
                    "Gemini Pro Vision",
                    "Мультимодальная модель с поддержкой изображений",
                    12288,
                    true,
                    false
            ),
            new AIModel(
                    "gemini-1.5-pro",
                    "Gemini 1.5 Pro",
                    "Улучшенная версия с большим контекстом",
                    128000,
                    true,
                    true
            ),
            new AIModel(
                    "gemini-1.5-flash",
                    "Gemini 1.5 Flash",
                    "Быстрая и экономичная модель",
                    128000,
                    true,
                    true
            ),
            new AIModel(
                    "gemini-1.0-pro",
                    "Gemini 1.0 Pro",
                    "Стабильная версия Gemini Pro",
                    30720,
                    false,
                    true
            ),
            new AIModel(
                    "gemini-1.0-pro-vision",
                    "Gemini 1.0 Pro Vision",
                    "Стабильная версия с поддержкой изображений",
                    12288,
                    true,
                    false
            ),
            // Экспериментальные модели
            new AIModel(
                    "gemini-1.5-pro-exp",
                    "Gemini 1.5 Pro Experimental",
                    "Экспериментальная версия с новейшими функциями",
                    128000,
                    true,
                    true
            ),
            new AIModel(
                    "gemini-1.5-flash-exp",
                    "Gemini 1.5 Flash Experimental",
                    "Экспериментальная быстрая версия",
                    128000,
                    true,
                    true
            )
    };

    public GeminiService() {
        this(UserConfig.selectedAccount);
    }

    public GeminiService(int account) {
        super(account);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // Инициализируем карту моделей
        for (AIModel model : AVAILABLE_MODELS) {
            modelsMap.put(model.id, model);
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model, Callback callback) {
        String apiKey = getApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("API ключ Gemini не установлен");
            return;
        }

        GeminiSettings geminiSettings = (GeminiSettings) getServiceSettings();

        try {
            // Формируем URL с учетом выбранной модели
            String url = API_URL + model + ":generateContent?key=" + apiKey;

            JSONObject requestBody = new JSONObject();

            JSONArray contents = new JSONArray();

            // Объединяем системный промпт с историей
            String fullPrompt = systemPrompt + "\n\n" + history;

            JSONObject content = new JSONObject();
            content.put("role", "user");

            JSONArray parts = new JSONArray();
            JSONObject part = new JSONObject();
            part.put("text", fullPrompt);
            parts.put(part);
            content.put("parts", parts);

            contents.put(content);
            requestBody.put("contents", contents);

            // Настройки генерации из настроек
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", geminiSettings.getTemperature());
            generationConfig.put("maxOutputTokens", geminiSettings.getMaxOutputTokens());
            generationConfig.put("topP", geminiSettings.getTopP());
            generationConfig.put("topK", geminiSettings.getTopK());
            requestBody.put("generationConfig", generationConfig);

            // Настройки безопасности из настроек
            JSONArray safetySettings = new JSONArray();
            Map<String, String> safetyMap = geminiSettings.getSafetySettings();
            for (Map.Entry<String, String> entry : safetyMap.entrySet()) {
                addSafetySetting(safetySettings, entry.getKey(), entry.getValue());
            }
            requestBody.put("safetySettings", safetySettings);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // Парсим ответ Gemini
                        String contentsd = extractContentFromGeminiResponse(jsonResponse);

                        try {
                            JSONObject cleanedJson = cleanJsonResponse(contentsd);
                            if (cleanedJson == null) {
                                throw new Exception("Failed to parse JSON response");
                            }
                            JSONObject suggestions = enhanceSuggestions(cleanedJson);
                            callback.onSuccess(suggestions);
                        } catch (Exception e) {
                            FileLog.e("Error parsing Gemini response: " + e.getMessage());
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("Gemini request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating Gemini request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    private void addSafetySetting(JSONArray settings, String category, String threshold) throws Exception {
        JSONObject setting = new JSONObject();
        setting.put("category", category);
        setting.put("threshold", threshold);
        settings.put(setting);
    }


    @Override
    public String getServiceName() {
        return "Google Gemini";
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.GEMINI;
    }

    @Override
    public AIModel[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    public String getDefaultModelId() {
        return "gemini-pro";
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

    private String extractContentFromGeminiResponse(JSONObject response) throws Exception {
        // Проверяем на ошибки
        if (response.has("error")) {
            JSONObject error = response.getJSONObject("error");
            throw new Exception(error.getString("message"));
        }

        if (response.has("candidates")) {
            JSONArray candidates = response.getJSONArray("candidates");
            if (candidates.length() > 0) {
                JSONObject candidate = candidates.getJSONObject(0);

                // Проверяем на блокировку из-за safety settings
                if (candidate.has("finishReason")) {
                    String finishReason = candidate.getString("finishReason");
                    if (finishReason.equals("SAFETY")) {
                        throw new Exception("Ответ заблокирован системой безопасности");
                    }
                }

                if (candidate.has("content")) {
                    JSONObject content = candidate.getJSONObject("content");
                    if (content.has("parts")) {
                        JSONArray parts = content.getJSONArray("parts");
                        if (parts.length() > 0) {
                            return parts.getJSONObject(0).getString("text");
                        }
                    }
                }
            }
        }
        throw new Exception("Невозможно извлечь текст из ответа Gemini");
    }

    private void handleErrorResponse(Response response, Callback callback) throws IOException {
        String errorBody = response.body() != null ? response.body().string() : "";
        FileLog.e("Gemini API error: " + response.code() + " - " + errorBody);

        String errorMessage;
        if (response.code() == 400) {
            // Пытаемся извлечь более подробную ошибку
            try {
                JSONObject error = new JSONObject(errorBody);
                if (error.has("error")) {
                    JSONObject errorObj = error.getJSONObject("error");
                    errorMessage = "Ошибка Gemini: " + errorObj.optString("message", "Неверный запрос");
                } else {
                    errorMessage = "Неверный запрос к Gemini API";
                }
            } catch (Exception e) {
                errorMessage = "Неверный запрос к Gemini API";
            }
        } else if (response.code() == 403) {
            errorMessage = "Неверный API ключ Gemini";
        } else if (response.code() == 429) {
            errorMessage = "Превышен лимит запросов Gemini";
        } else if (response.code() == 404) {
            errorMessage = "Модель не найдена или недоступна в вашем регионе";
        } else {
            errorMessage = "Ошибка Gemini API: " + response.code();
        }
        callback.onError(errorMessage);
    }
}