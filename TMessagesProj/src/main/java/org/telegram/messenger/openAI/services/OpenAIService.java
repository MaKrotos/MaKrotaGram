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

public class OpenAIService extends BaseAIService {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final Map<String, AIModel> modelsMap = new HashMap<>();

    // Определяем доступные модели OpenAI
    private static final AIModel[] AVAILABLE_MODELS = {
            new AIModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "Самая быстрая модель для повседневных задач", 4096, false, false, true),
            new AIModel("gpt-3.5-turbo-16k", "GPT-3.5 Turbo 16K", "Увеличенный контекст до 16K токенов", 16384, false, false, true),
            new AIModel("gpt-4", "GPT-4", "Более мощная модель для сложных задач", 8192, false, false, true),
            new AIModel("gpt-4-turbo-preview", "GPT-4 Turbo", "Самая современная и быстрая GPT-4", 128000, false, false, true),
            new AIModel("gpt-4o", "GPT-4o", "Оптимизированная версия GPT-4", 128000, true, true, true),
            new AIModel("gpt-4o-mini", "GPT-4o Mini", "Облегченная версия GPT-4o", 128000, true, true, true),
            new AIModel("gpt-4-vision-preview", "GPT-4 Vision", "С поддержкой анализа изображений", 128000, true, false, true)
    };

    public OpenAIService() {
        this(UserConfig.selectedAccount);
    }

    public OpenAIService(int account) {
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
            callback.onError("API ключ OpenAI не установлен");
            return;
        }

        OpenAISettings openAISettings = (OpenAISettings) getServiceSettings();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", openAISettings.getTemperature());
            requestBody.put("max_tokens", openAISettings.getMaxTokens());
            requestBody.put("top_p", openAISettings.getTopP());
            requestBody.put("frequency_penalty", openAISettings.getFrequencyPenalty());
            requestBody.put("presence_penalty", openAISettings.getPresencePenalty());

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

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

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
                                FileLog.e("Error parsing OpenAI response: " + e.getMessage());
                            }
                        } else {
                            callback.onError("Пустой ответ от API");
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("OpenAI request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating OpenAI request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model,
                               List<ImageAttachment> images, Callback callback) {
        // Если изображений нет или модель не поддерживает vision, вызываем старый метод
        if (images == null || images.isEmpty() || !modelSupportsVision(model)) {
            makeRequest(systemPrompt, history, model, callback);
            return;
        }
        String apiKey = getApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("API ключ OpenAI не установлен");
            return;
        }

        OpenAISettings openAISettings = (OpenAISettings) getServiceSettings();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", openAISettings.getTemperature());
            requestBody.put("max_tokens", openAISettings.getMaxTokens());
            requestBody.put("top_p", openAISettings.getTopP());
            requestBody.put("frequency_penalty", openAISettings.getFrequencyPenalty());
            requestBody.put("presence_penalty", openAISettings.getPresencePenalty());

            JSONArray messages = new JSONArray();

            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            messages.put(systemMessage);

            // Создаём пользовательское сообщение с мультимодальным контентом
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            JSONArray contentArray = new JSONArray();
            // Текстовый элемент
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", history);
            contentArray.put(textPart);
            // Добавляем изображения
            for (ImageAttachment image : images) {
                JSONObject imagePart = new JSONObject();
                imagePart.put("type", "image_url");
                JSONObject imageUrl = new JSONObject();
                // Формат: data:image/jpeg;base64,{base64Data}
                imageUrl.put("url", "data:" + image.mimeType + ";base64," + image.base64Data);
                imagePart.put("image_url", imageUrl);
                contentArray.put(imagePart);
                // Если у изображения есть подпись, добавляем как отдельный текстовый элемент
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

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

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
                                FileLog.e("Error parsing OpenAI response: " + e.getMessage());
                            }
                        } else {
                            callback.onError("Пустой ответ от API");
                        }
                    } else {
                        handleErrorResponse(response, callback);
                    }
                } catch (Exception e) {
                    FileLog.e("OpenAI request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating OpenAI request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    @Override
    protected void makeRequest(String systemPrompt, String history, String model,
                               List<ImageAttachment> images, List<AudioAttachment> audio, Callback callback) {
        // Если есть аудио и модель поддерживает аудио, добавляем транскрипции в текст
        StringBuilder enhancedHistory = new StringBuilder(history);
        if (audio != null && !audio.isEmpty() && modelSupportsAudio(model)) {
            for (AudioAttachment audioAttachment : audio) {
                if (audioAttachment.transcription != null && !audioAttachment.transcription.isEmpty()) {
                    enhancedHistory.append("\n[Аудио транскрипция: ").append(audioAttachment.transcription).append("]");
                }
                if (audioAttachment.caption != null && !audioAttachment.caption.isEmpty()) {
                    enhancedHistory.append("\n[Подпись аудио: ").append(audioAttachment.caption).append("]");
                }
            }
        }
        // Если есть изображения, вызываем метод с изображениями (он уже обрабатывает vision)
        if (images != null && !images.isEmpty()) {
            makeRequest(systemPrompt, enhancedHistory.toString(), model, images, callback);
        } else {
            // Иначе вызываем обычный метод
            makeRequest(systemPrompt, enhancedHistory.toString(), model, callback);
        }
    }

    private boolean modelSupportsVision(String modelId) {
        AIModel model = getModelById(modelId);
        return model != null && model.supportsVision;
    }

    private boolean modelSupportsAudio(String modelId) {
        AIModel model = getModelById(modelId);
        return model != null && model.supportsAudio;
    }

    @Override
    protected void makeStreamingRequest(String systemPrompt, String history, String model, StreamCallback callback) {
        String apiKey = getApiKey();
        if (TextUtils.isEmpty(apiKey)) {
            callback.onError("API ключ OpenAI не установлен");
            return;
        }

        OpenAISettings openAISettings = (OpenAISettings) getServiceSettings();

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", openAISettings.getTemperature());
            requestBody.put("max_tokens", openAISettings.getMaxTokens());
            requestBody.put("top_p", openAISettings.getTopP());
            requestBody.put("frequency_penalty", openAISettings.getFrequencyPenalty());
            requestBody.put("presence_penalty", openAISettings.getPresencePenalty());
            requestBody.put("stream", true); // Включаем стриминг

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

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "text/event-stream")
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (!response.isSuccessful()) {
                        handleErrorResponse(response, new Callback() {
                            @Override
                            public void onSuccess(JSONObject response) {}
                            @Override
                            public void onError(String error) {
                                callback.onError(error);
                            }
                        });
                        return;
                    }

                    // Парсим SSE поток
                    String responseBody = response.body().string();
                    // Упрощённая обработка: разбиваем на строки и эмулируем чанки
                    // В реальности нужно парсить события "data: {...}"
                    // Для простоты отправим весь ответ одним чанком
                    // TODO: реализовать настоящий парсинг SSE
                    String fullResponse = extractContentFromStream(responseBody);
                    if (fullResponse != null && !fullResponse.isEmpty()) {
                        // Эмуляция посимвольного вывода
                        int chunkSize = 10;
                        for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                            int end = Math.min(fullResponse.length(), i + chunkSize);
                            String chunk = fullResponse.substring(i, end);
                            callback.onChunk(chunk);
                            try {
                                Thread.sleep(50); // небольшая задержка для эффекта стриминга
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        callback.onComplete();
                    } else {
                        callback.onError("Пустой ответ от API");
                    }
                } catch (Exception e) {
                    FileLog.e("OpenAI streaming request error: " + e.getMessage());
                    callback.onError("Ошибка сети: " + e.getMessage());
                }
            }).start();

        } catch (Exception e) {
            FileLog.e("Error creating OpenAI streaming request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    private String extractContentFromStream(String streamData) {
        // Упрощённая реализация: ищем поле "content" в JSON объектах
        StringBuilder content = new StringBuilder();
        String[] lines = streamData.split("\n");
        for (String line : lines) {
            if (line.startsWith("data: ")) {
                String jsonStr = line.substring(6).trim();
                if (jsonStr.equals("[DONE]")) {
                    continue;
                }
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
                } catch (Exception e) {
                    // игнорируем некорректные JSON
                }
            }
        }
        return content.toString();
    }

    @Override
    public String getServiceName() {
        return "OpenAI";
    }

    @Override
    public AISettings.AIServiceType getServiceType() {
        return AISettings.AIServiceType.OPENAI;
    }

    @Override
    public AIModel[] getAvailableModels() {
        return AVAILABLE_MODELS;
    }

    @Override
    public String getDefaultModelId() {
        return "gpt-3.5-turbo";
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

    private void handleErrorResponse(Response response, Callback callback) throws IOException {
        String errorBody = response.body() != null ? response.body().string() : "";
        FileLog.e("OpenAI API error: " + response.code() + " - " + errorBody);

        String errorMessage;
        if (response.code() == 401) {
            errorMessage = "Неверный API ключ OpenAI";
        } else if (response.code() == 429) {
            errorMessage = "Превышен лимит запросов OpenAI";
        } else if (response.code() == 404) {
            errorMessage = "Модель не найдена или недоступна";
        } else {
            errorMessage = "Ошибка OpenAI API: " + response.code();
        }
        callback.onError(errorMessage);
    }
}