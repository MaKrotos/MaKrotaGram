package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Service for interacting with Ollama AI.
 * Ollama provides an OpenAI-compatible API at /v1/chat/completions.
 */
public class OllamaService extends BaseAIService {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Map<String, AIModel> modelsMap = new HashMap<>();

    // Default models for Ollama. 
    // Since Ollama models are dynamic, we provide a few common ones as defaults.
    private static final AIModel[] DEFAULT_MODELS = {
            new AIModel("llama3", "Llama 3", "Meta's latest powerful model", 8192, false, false, true),
            new AIModel("mistral", "Mistral", "Efficient and capable model", 8192, false, false, true),
            new AIModel("phi3", "Phi-3", "Microsoft's lightweight model", 4096, false, false, true),
            new AIModel("gemma", "Gemma", "Google's open model", 8192, false, false, true)
    };

    public OllamaService() {
        this(UserConfig.selectedAccount);
    }

    public OllamaService(int account) {
        super(account);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        for (AIModel model : DEFAULT_MODELS) {
            modelsMap.put(model.id, model);
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model, Callback callback) {
        OllamaSettings settings = (OllamaSettings) getServiceSettings();
        String apiUrl = settings.getApiUrl();
        
        // Ollama OpenAI-compatible endpoint
        String fullUrl = apiUrl.trim();
        if (!fullUrl.endsWith("/v1/chat/completions")) {
            // Ensure the URL ends with the correct path
            if (fullUrl.endsWith("/")) {
                fullUrl += "v1/chat/completions";
            } else {
                fullUrl += "/v1/chat/completions";
            }
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", settings.getTemperature());
            requestBody.put("max_tokens", settings.getMaxTokens());
            requestBody.put("top_p", settings.getTopP());
            requestBody.put("frequency_penalty", settings.getFrequencyPenalty());
            requestBody.put("presence_penalty", settings.getPresencePenalty());

            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", history);
            messages.put(userMessage);

            requestBody.put("messages", messages);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(fullUrl)
                    .post(RequestBody.create(requestBody.toString(), JSON));

            String apiKey = getApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = requestBuilder.build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String content = choices.getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");

                            try {
                                JSONObject cleanedJson = cleanJsonResponse(content);
                                if (cleanedJson == null) {
                                    throw new Exception("Failed to parse JSON response");
                                }
                                JSONObject suggestions = enhanceSuggestions(cleanedJson);
                                callback.onSuccess(suggestions);
                            } catch (Exception e) {
                                FileLog.e("Error parsing Ollama response: " + e.getMessage());
                                callback.onError("Ошибка парсинга ответа: " + e.getMessage());
                            }
                        } else {
                            callback.onError("Пустой ответ от Ollama");
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("Ollama request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating Ollama request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model,
                               List<ImageAttachment> images, Callback callback) {
        // Ollama's OpenAI-compatible API supports vision if the model does.
        // For now, we'll use the same logic as OpenAI: if images are present, we send them.
        if (images == null || images.isEmpty()) {
            makeRequest(systemPrompt, history, model, callback);
            return;
        }

        OllamaSettings settings = (OllamaSettings) getServiceSettings();
        String apiUrl = settings.getApiUrl();
        String fullUrl = apiUrl.trim();
        if (!fullUrl.endsWith("/v1/chat/completions")) {
            fullUrl += (fullUrl.endsWith("/") ? "" : "/") + "v1/chat/completions";
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", settings.getTemperature());
            requestBody.put("max_tokens", settings.getMaxTokens());
            requestBody.put("top_p", settings.getTopP());
            requestBody.put("frequency_penalty", settings.getFrequencyPenalty());
            requestBody.put("presence_penalty", settings.getPresencePenalty());

            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            JSONArray contentArray = new JSONArray();
            
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", history);
            contentArray.put(textPart);

            for (ImageAttachment image : images) {
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", "data:" + image.mimeType + ";base64," + image.base64Data);
                imagePart.put("image_url", imageUrl);
                contentArray.put(imagePart);
                if (image.caption != null && !image.caption.isEmpty()) {
                    JSONObject captionPart = new JSONObject();
                    captionPart.put("type", "text");
                    captionPart.put("text", image.caption);
                    contentArray.put(captionPart);
                }
            }
            userMessage.put("content", contentArray);
            messages.put(userMessage);

            requestBody.put("messages", messages);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(fullUrl)
                    .post(RequestBody.create(requestBody.toString(), JSON));

            String apiKey = getApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = requestBuilder.build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        if (choices.length() > 0) {
                            String content = choices.getJSONObject(0).getJSONObject("message").getString("content");
                            JSONObject cleanedJson = cleanJsonResponse(content);
                            if (cleanedJson == null) throw new Exception("Failed to parse JSON response");
                            callback.onSuccess(enhanceSuggestions(cleanedJson));
                        } else {
                            callback.onError("Пустой ответ от Ollama");
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model,
                               List<ImageAttachment> images, List<AudioAttachment> audio, Callback callback) {
        StringBuilder enhancedHistory = new StringBuilder(history);
        if (audio != null && !audio.isEmpty()) {
            for (AudioAttachment audioAttachment : audio) {
                if (audioAttachment.transcription != null && !audioAttachment.transcription.isEmpty()) {
                    enhancedHistory.append("\n[Аудио транскрипция: ").append(audioAttachment.transcription).append("]");
                }
                if (audioAttachment.caption != null && !audioAttachment.caption.isEmpty()) {
                    enhancedHistory.append("\n[Подпись аудио: ").append(audioAttachment.caption).append("]");
                }
            }
        }
        if (images != null && !images.isEmpty()) {
            makeRequest(systemPrompt, enhancedHistory.toString(), model, images, callback);
        } else {
            makeRequest(systemPrompt, enhancedHistory.toString(), model, callback);
        }
    }

    @Override
    protected void makeStreamingRequest(String systemPrompt, String history, String model, StreamCallback callback) {
        OllamaSettings settings = (OllamaSettings) getServiceSettings();
        String apiUrl = settings.getApiUrl();
        String fullUrl = apiUrl.trim();
        if (!fullUrl.endsWith("/v1/chat/completions")) {
            fullUrl += (fullUrl.endsWith("/") ? "" : "/") + "v1/chat/completions";
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", settings.getTemperature());
            requestBody.put("max_tokens", settings.getMaxTokens());
            requestBody.put("top_p", settings.getTopP());
            requestBody.put("frequency_penalty", settings.getFrequencyPenalty());
            requestBody.put("presence_penalty", settings.getPresencePenalty());
            requestBody.put("stream", true);

            JSONArray messages = new JSONArray();
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", history);
            messages.put(userMessage);

            requestBody.put("messages", messages);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(fullUrl)
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBody.toString(), JSON));

            String apiKey = getApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }

            Request request = requestBuilder.build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, new Callback() {
                            @Override public void onSuccess(JSONObject response) {}
                            @Override public void onError(String error) { callback.onError(error); }
                        });
                        return;
                    }

                    String responseBody = response.body().string();
                    String fullResponse = extractContentFromStream(responseBody);
                    if (fullResponse != null && !fullResponse.isEmpty()) {
                        int chunkSize = 10;
                        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                            int end = Math.min(fullResponse.length(), i + chunkSize);
                            callback.onChunk(fullResponse.substring(i, end));
                            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        }
                        callback.onComplete();
                    } else {
                        callback.onError("Пустой ответ от Ollama");
                    }
                } catch (Exception e) {
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    private String extractContentFromStream(String streamData) {
        StringBuilder content = new StringBuilder();
        String[] lines = streamData.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String jsonStr = line.substring(6).trim();
                if (jsonStr.equals("[DONE]")) continue;
                try {
                    JSONObject json = new JSONObject(jsonStr);
                    JSONArray choices = json.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        JSONObject delta = choice.optJSONObject("delta");
                        if (delta != null && delta.has("content")) {
                            content.append(delta.getString("content"));
                        }
                    }
                } catch (Exception e) {}
            }
        }
        return content.toString();
    }

    private void handleErrorResponse(Response response, Callback callback) throws IOException {
        String errorBody = response.body() != null ? response.body().string() : "";
        FileLog.e("Ollama API error: " + response.code() + " - " + errorBody);
        String errorMessage;
        if (response.code() == 401) {
            errorMessage = "Неверный API ключ Ollama";
        } else if (response.code() == 404) {
            errorMessage = "Модель не найдена в Ollama";
        } else {
            errorMessage = "Ошибка Ollama API: " + response.code();
        }
        callback.onError(errorMessage);
    }

    @Override
    public String getServiceName() {
        return "Ollama";
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.OLLAMA;
    }

    @Override
    public AIModel[] getAvailableModels() {
        return DEFAULT_MODELS;
    }

    /**
     * Запрашивает список моделей, доступных на сервере Ollama.
     * @param callback колбэк для получения списка моделей.
     */
    public void fetchRemoteModels(Callback callback) {
        OllamaSettings settings = (OllamaSettings) getServiceSettings();
        String apiUrl = settings.getApiUrl();
        String fullUrl = apiUrl.trim();
        if (!fullUrl.endsWith("/api/tags")) {
            if (fullUrl.endsWith("/")) {
                fullUrl += "api/tags";
            } else {
                fullUrl += "/api/tags";
            }
        }

        try {
            Request.Builder requestBuilder = new Request.Builder().url(fullUrl).get();
            String apiKey = getApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            Request request = requestBuilder.build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        FileLog.d("Ollama fetch models response: " + responseBody);
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        
                        JSONArray modelsArray = jsonResponse.optJSONArray("models");
                        if (modelsArray == null) {
                            callback.onError("В ответе сервера отсутствует массив 'models'");
                            return;
                        }
                        
                        JSONArray resultSuggestions = new JSONArray();
                        for (int i = 0; i < modelsArray.length(); i++) {
                            JSONObject modelObj = modelsArray.optJSONObject(i);
                            if (modelObj != null) {
                                String name = modelObj.optString("name");
                                if (!TextUtils.isEmpty(name)) {
                                    resultSuggestions.put(name);
                                }
                            }
                        }
                        
                        JSONObject finalResponse = new JSONObject();
                        finalResponse.put("models", resultSuggestions);
                        callback.onSuccess(finalResponse);
                    } else {
                        callback.onError("Ошибка при получении списка моделей: " + response.code());
                    }
                } catch (Exception e) {
                    FileLog.e("Ollama fetch models error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    public void testConnection(Callback callback) {
        OllamaSettings settings = (OllamaSettings) getServiceSettings();
        String apiUrl = settings.getApiUrl();
        if (TextUtils.isEmpty(apiUrl)) {
            callback.onError("URL сервера не указан");
            return;
        }

        String fullUrl = apiUrl.trim();
        if (!fullUrl.endsWith("/api/tags")) {
            fullUrl += (fullUrl.endsWith("/") ? "" : "/") + "api/tags";
        }

        try {
            Request.Builder requestBuilder = new Request.Builder().url(fullUrl).get();
            String apiKey = getApiKey();
            if (!TextUtils.isEmpty(apiKey)) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            Request request = requestBuilder.build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        callback.onSuccess(new JSONObject());
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("Ollama test connection error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();
        } catch (Exception e) {
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    public String getDefaultModelId() {
        return OllamaSettings.DEFAULT_MODEL;
    }

    @Override
    public AIModel getModelById(String modelId) {
        if (TextUtils.isEmpty(modelId)) {
            return getModelById(getDefaultModelId());
        }
        AIModel model = modelsMap.get(modelId);
        if (model == null) {
            // Create a generic model if not in our default list
            return new AIModel(modelId, modelId, "Ollama Model", 8192, false, false, true);
        }
        return model;
    }
}
