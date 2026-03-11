package org.telegram.messenger.openAI;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import tw.nekomimi.nekogram.NekoConfig;
import org.telegram.ui.MagicActivity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIService {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String DEFAULT_SYSTEM_PROMPT =
            "Ты - помощник для генерации ответов в мессенджере. " +
                    "Твоя задача - проанализировать историю переписки и придумать, что написать дальше. " +
                    "Отвечай ТОЛЬКО в формате JSON со следующими полями:\n" +
                    "{\n" +
                    "  \"suggestions\": [\n" +
                    "    {\n" +
                    "      \"text\": \"текст предполагаемого ответа\",\n" +
                    "      \"confidence\": 0.95,\n" +
                    "      \"type\": \"answer\" или \"question\" или \"continuation\"\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"explanation\": \"краткое объяснение почему это уместно\"\n" +
                    "}\n" +
                    "Важно: ответ должен быть только в JSON формате, без дополнительного текста.";

    private final OkHttpClient client;

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String error);
    }

    public OpenAIService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private String getApiKey() {
        String apiKey = NekoConfig.openaiApiKey;
        if (TextUtils.isEmpty(apiKey)) {
            return null;
        }
        return apiKey;
    }

    public void generateSuggestions(ArrayList<MessageObject> messages, String userPrompt, Callback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API ключ не установлен. Пожалуйста, добавьте его в настройках.");
            return;
        }

        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "gpt-3.5-turbo");

            JSONArray messagesArray = new JSONArray();

            // Системный промпт
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", DEFAULT_SYSTEM_PROMPT);
            messagesArray.put(systemMessage);

            // Добавляем пользовательский промпт, если он есть
            if (userPrompt != null && !userPrompt.isEmpty()) {
                JSONObject userPromptMessage = new JSONObject();
                userPromptMessage.put("role", "user");
                userPromptMessage.put("content", "Дополнительные инструкции: " + userPrompt);
                messagesArray.put(userPromptMessage);
            }

            // Формируем историю переписки
            StringBuilder conversationHistory = new StringBuilder();
            conversationHistory.append("Вот история переписки:\n\n");

            for (int i = 0; i < messages.size(); i++) {
                MessageObject message = messages.get(i);
                String sender = message.isOut() ? "Я" : "Собеседник";
                String text = message.messageText != null ? (String) message.messageText : "[Медиа файл]";
                conversationHistory.append(sender).append(": ").append(text).append("\n");
            }

            conversationHistory.append("\nНа основе этой переписки, что мне написать дальше?");

            JSONObject historyMessage = new JSONObject();
            historyMessage.put("role", "user");
            historyMessage.put("content", conversationHistory.toString());
            messagesArray.put(historyMessage);

            requestBody.put("messages", messagesArray);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 500);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody.toString(), JSON))
                    .build();

            // Выполняем запрос в отдельном потоке
            new Thread(() -> {
                try {
                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);

                        // Извлекаем ответ ассистента
                        JSONArray choices = jsonResponse.getJSONArray("choices");
                        if (choices.length() > 0) {
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject message = choice.getJSONObject("message");
                            String content = message.getString("content");

                            // Пытаемся распарсить JSON из ответа
                            try {
                                // Очищаем ответ от возможных markdown-блоков
                                content = cleanJsonResponse(content);
                                JSONObject suggestions = new JSONObject(content);
                                callback.onSuccess(suggestions);
                            } catch (Exception e) {
                                FileLog.e("Error parsing OpenAI response: " + e.getMessage());
                                // Если не удалось распарсить JSON, создаем простую структуру
                                JSONObject fallback = new JSONObject();
                                JSONArray suggestionsArray = new JSONArray();
                                JSONObject suggestion = new JSONObject();
                                suggestion.put("text", content);
                                suggestion.put("confidence", 1.0);
                                suggestion.put("type", "answer");
                                suggestionsArray.put(suggestion);
                                fallback.put("suggestions", suggestionsArray);
                                fallback.put("explanation", "Ответ от API (не в JSON формате)");
                                callback.onSuccess(fallback);
                            }
                        } else {
                            callback.onError("Пустой ответ от API");
                        }
                    } else {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        FileLog.e("OpenAI API error: " + response.code() + " - " + errorBody);

                        if (response.code() == 401) {
                            callback.onError("Неверный API ключ. Проверьте настройки.");
                        } else if (response.code() == 429) {
                            callback.onError("Превышен лимит запросов. Попробуйте позже.");
                        } else {
                            callback.onError("Ошибка API: " + response.code());
                        }
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

    private String cleanJsonResponse(String response) {
        // Удаляем markdown-блоки ```json и ```
        response = response.replaceAll("```json\\s*", "");
        response = response.replaceAll("```\\s*", "");
        return response.trim();
    }

    // Упрощенный метод для быстрого использования
    public void generateSuggestions(ArrayList<MessageObject> messages, Callback callback) {
        generateSuggestions(messages, null, callback);
    }

    // Метод для проверки наличия API ключа
    public boolean hasApiKey() {
        return !TextUtils.isEmpty(NekoConfig.openaiApiKey);
    }
}