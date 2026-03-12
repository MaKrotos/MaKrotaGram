package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import tw.nekomimi.nekogram.NekoConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenAIService {

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private static final String MODEL_STANDARD = "gpt-3.5-turbo";

    private static final String PREFS_NAME = "user_prompts";
    private static final String KEY_PROMPT_PREFIX = "prompt_";

    // Системный промпт для генерации ответов ОТ ИМЕНИ ПОЛЬЗОВАТЕЛЯ
    private static final String DEFAULT_SYSTEM_PROMPT =
            "Ты - креативный помощник, который помогает пользователю мессенджера придумать, ЧТО ОТВЕТИТЬ в чате. " +
                    "Твоя задача - проанализировать историю переписки и предложить пользователю НЕСКОЛЬКО РАЗНЫХ вариантов того, что он МОЖЕТ НАПИСАТЬ ДАЛЬШЕ.\n\n" +
                    "❗️❗️❗️ КРИТИЧЕСКИ ВАЖНО ❗️❗️❗️\n" +
                    "1. Ты НЕ отвечаешь от своего имени. Ты предлагаешь варианты, которые пользователь (Я) может отправить собеседнику.\n" +
                    "2. ВНИМАТЕЛЬНО СМОТРИ КТО НАПИСАЛ ПОСЛЕДНЕЕ СООБЩЕНИЕ:\n" +
                    "   - Если последнее сообщение от СОБЕСЕДНИКА → мне нужно ОТВЕТИТЬ ему\n" +
                    "   - Если последнее сообщение от МЕНЯ → значит я уже что-то написал, и теперь нужно ПРОДОЛЖИТЬ разговор (добавить еще сообщение)\n\n" +
                    "ВАЖНЫЕ ТРЕБОВАНИЯ:\n" +
                    "1. Всегда предлагай МИНИМУМ 3-5 различных вариантов ответа\n" +
                    "2. Варианты должны быть РАЗНЫМИ по стилю и содержанию\n" +
                    "3. Учитывай контекст беседы и отношения между собеседниками\n" +
                    "4. Если есть изображения, просто учитывай их наличие в контексте\n" +
                    "5. Варианты должны звучать естественно, как будто их пишет реальный человек\n" +
                    "6. Варианты должны быть более человечными, в обычном разговорном стиле. Не ставь точки в конце предложений, если это не вопрос или восклицание. Используй естественный язык, как в мессенджерах.\n\n" +
                    "Отвечай ТОЛЬКО в формате JSON со следующими полями:\n" +
                    "{\n" +
                    "  \"suggestions\": [\n" +
                    "    {\n" +
                    "      \"text\": \"текст, который Я МОГУ ОТПРАВИТЬ\",\n" +
                    "      \"confidence\": 0.95 (число от 0 до 1, насколько этот вариант уместен),\n" +
                    "      \"type\": \"answer\" (прямой ответ), \"question\" (вопрос), \"continuation\" (продолжение темы), \"humor\" (с юмором), \"emoji\" (с эмодзи)\n" +
                    "    },\n" +
                    "    ... (еще минимум 2-4 варианта)\n" +
                    "  ],\n" +
                    "  \"explanation\": \"краткое объяснение контекста и почему эти варианты уместны\"\n" +
                    "}\n\n" +
                    "ПРИМЕР хорошего ответа (обрати внимание - варианты от лица пользователя):\n" +
                    "{\n" +
                    "  \"suggestions\": [\n" +
                    "    {\"text\": \"Да, я тоже так думаю\", \"confidence\": 0.9, \"type\": \"answer\"},\n" +
                    "    {\"text\": \"А что ты думаешь по этому поводу?\", \"confidence\": 0.85, \"type\": \"question\"},\n" +
                    "    {\"text\": \"Кстати, это напомнило мне одну историю...\", \"confidence\": 0.7, \"type\": \"continuation\"},\n" +
                    "    {\"text\": \"😄 Классно звучит!\", \"confidence\": 0.8, \"type\": \"emoji\"}\n" +
                    "  ],\n" +
                    "  \"explanation\": \"Разные варианты: согласие, вопрос для продолжения, переход к истории и эмоциональная реакция\"\n" +
                    "}\n\n" +
                    "Важно: НИКОГДА не возвращай меньше 3 вариантов. Ответ должен быть только в JSON формате.";

    private final OkHttpClient client;
    private int currentAccount = UserConfig.selectedAccount;

    public interface Callback {
        void onSuccess(JSONObject response);
        void onError(String error);
    }

    public OpenAIService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    public OpenAIService(int account) {
        this();
        this.currentAccount = account;
    }

    private String getApiKey() {
        String apiKey = NekoConfig.openaiApiKey;
        if (TextUtils.isEmpty(apiKey)) {
            return null;
        }
        return apiKey;
    }

    /**
     * Получает базовый промпт из глобальных настроек NekoConfig
     * @return Текст базового промпта или null
     */
    private String getBasicPrompt() {
        String basicPrompt = NekoConfig.OpenAIBasicPropmpt;
        if (!TextUtils.isEmpty(basicPrompt)) {
            return basicPrompt;
        }
        return null;
    }

    /**
     * Получает ID собеседника из списка сообщений
     * @param messages список сообщений
     * @return ID собеседника или 0, если не найден
     */
    private long getInterlocutorId(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return 0;

        long myId = UserConfig.getInstance(currentAccount).getClientUserId();

        // Ищем первое сообщение не от меня
        for (MessageObject msg : messages) {
            long senderId = msg.getSenderId();
            if (senderId != myId && senderId > 0) {
                return senderId;
            }
        }

        return 0;
    }

    public void generateSuggestions(ArrayList<MessageObject> messages, String userPrompt, Callback callback) {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            callback.onError("API ключ не установлен. Пожалуйста, добавьте его в настройках.");
            return;
        }

        try {
            JSONObject requestBody = new JSONObject();

            // Всегда используем стандартную модель, без vision
            requestBody.put("model", MODEL_STANDARD);

            JSONArray messagesArray = new JSONArray();

            // Системный промпт
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", DEFAULT_SYSTEM_PROMPT);
            messagesArray.put(systemMessage);

            // Получаем ID текущего пользователя (бот)
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();

            // Получаем ID собеседника (кому помогаем отвечать)
            long interlocutorId = getInterlocutorId(messages);

            // Получаем пользовательский промпт для СОБЕСЕДНИКА, если он есть
            String userSpecificPrompt = interlocutorId > 0 ? UserPromptService.getInstance(currentAccount).getPrompt(interlocutorId) : null;

            // Получаем базовый промпт из глобальных настроек
            String basicPrompt = getBasicPrompt();

            // Формируем комбинированный промпт
            StringBuilder combinedPrompt = new StringBuilder();

            // Добавляем переданный промпт (из интерфейса)
            if (userPrompt != null && !userPrompt.isEmpty()) {
                combinedPrompt.append("Инструкция от пользователя: ").append(userPrompt).append("\n\n");
            }

            // Добавляем базовый промпт из настроек (только если нет пользовательского)
            if (userSpecificPrompt == null && basicPrompt != null) {
                combinedPrompt.append("Базовая инструкция: ").append(basicPrompt).append("\n\n");
            }
            // Добавляем сохраненный промпт для СОБЕСЕДНИКА (он имеет приоритет)
            else if (userSpecificPrompt != null) {
                combinedPrompt.append("Персональная инструкция для собеседника: ").append(userSpecificPrompt).append("\n\n");
            }

            // Если есть какие-то промпты, добавляем их в запрос
            if (combinedPrompt.length() > 0) {
                combinedPrompt.append("(Не забудь предложить минимум 3-5 разных вариантов ответа ОТ ИМЕНИ СОБЕСЕДНИКА, учитывая эти инструкции. Используй обычный разговорный стиль, не ставь точки в конце предложений, если это не вопрос или восклицание.)");

                JSONObject userPromptMessage = new JSONObject();
                userPromptMessage.put("role", "user");
                userPromptMessage.put("content", combinedPrompt.toString());
                messagesArray.put(userPromptMessage);
            }

            // Формируем историю переписки
            StringBuilder conversationHistory = new StringBuilder();

            // ИНФОРМАЦИЯ О ПОЛЬЗОВАТЕЛЯХ
            TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
            String myName = currentUser != null ? getDisplayName(currentUser) : "Я (бот)";
            long myId = UserConfig.getInstance(currentAccount).getClientUserId();

            // Получаем имя собеседника
            String interlocutorName = "СОБЕСЕДНИК";
            if (interlocutorId > 0) {
                TLRPC.User interlocutor = MessagesController.getInstance(currentAccount).getUser(interlocutorId);
                if (interlocutor != null) {
                    interlocutorName = getDisplayName(interlocutor);
                }
            }

            // Определяем, кто написал последнее сообщение
            MessageObject lastMessage = messages.get(messages.size() - 1);
            long lastMessageSenderId = lastMessage.getSenderId();
            boolean lastMessageIsFromMe = lastMessageSenderId == myId;
            boolean lastMessageIsFromInterlocutor = lastMessageSenderId == interlocutorId;

            String lastMessageSenderName;
            if (lastMessageIsFromMe) {
                lastMessageSenderName = myName + " (Я)";
            } else if (lastMessageIsFromInterlocutor) {
                lastMessageSenderName = interlocutorName + " (СОБЕСЕДНИК)";
            } else {
                lastMessageSenderName = getSenderNameFromId(lastMessageSenderId) + " (УЧАСТНИК)";
            }

            conversationHistory.append("========== ПОМОЩЬ В ОТВЕТЕ ==========\n\n");

            // ЧЕТКОЕ ОПРЕДЕЛЕНИЕ РОЛЕЙ
            conversationHistory.append("🔴 РОЛИ УЧАСТНИКОВ:\n");
            conversationHistory.append("   👤 Я (бот): ").append(myName).append("\n");
            conversationHistory.append("   🗣 КОМУ ПОМОГАЕМ: ").append(interlocutorName).append("\n");
            if (!lastMessageIsFromInterlocutor && !lastMessageIsFromMe) {
                conversationHistory.append("   👥 ДРУГОЙ УЧАСТНИК: ").append(lastMessageSenderName).append("\n");
            }
            conversationHistory.append("\n");

            // Добавляем информацию о промптах, если они есть
            if (userSpecificPrompt != null) {
                conversationHistory.append("📝 ПЕРСОНАЛЬНЫЕ ИНСТРУКЦИИ ДЛЯ ").append(interlocutorName.toUpperCase()).append(":\n");
                conversationHistory.append(userSpecificPrompt).append("\n\n");
            } else if (basicPrompt != null) {
                conversationHistory.append("📝 БАЗОВЫЕ ИНСТРУКЦИИ:\n");
                conversationHistory.append(basicPrompt).append("\n\n");
            }

            // Определяем тип чата
            boolean isGroupChat = isGroupChat(messages);
            addChatContext(conversationHistory, messages, myName, isGroupChat);

            conversationHistory.append("\n========== ИСТОРИЯ ПЕРЕПИСКИ ==========\n");
            conversationHistory.append("(Сообщения идут от старых к новым)\n\n");

            int messageCount = messages.size();

            for (int i = 0; i < messageCount; i++) {
                MessageObject message = messages.get(i);

                // Получаем имя отправителя с четкой маркировкой
                String sender = getSenderName(message, myId, myName, interlocutorName, interlocutorId);

                // Получаем текст сообщения
                String text = getMessageText(message);

                // Маркировка важных сообщений
                if (i == messageCount - 1) {
                    conversationHistory.append("🔴🔴🔴 [ПОСЛЕДНЕЕ СООБЩЕНИЕ] 🔴🔴🔴\n");
                } else if (i == messageCount - 2) {
                    conversationHistory.append("🟠🟠🟠 [ПРЕДПОСЛЕДНЕЕ] 🟠🟠🟠\n");
                } else if (i == messageCount - 3) {
                    conversationHistory.append("🟡🟡🟡 [ТРЕТЬЕ С КОНЦА] 🟡🟡🟡\n");
                }

                conversationHistory.append("👉 ").append(sender).append(": ").append(text).append("\n");

                // Добавляем дополнительную информацию о медиа
                if (message.isPhoto()) {
                    conversationHistory.append("      📸 [ФОТОГРАФИЯ]\n");
                } else if (message.isVideo()) {
                    conversationHistory.append("      🎥 [ВИДЕО]\n");
                } else if (message.getDocument() != null) {
                    conversationHistory.append("      📎 [ФАЙЛ: ").append(message.getDocumentName()).append("]\n");
                } else if (message.isVoice()) {
                    conversationHistory.append("      🎤 [ГОЛОСОВОЕ]\n");
                } else if (message.isSticker()) {
                    conversationHistory.append("      🎯 [СТИКЕР]\n");
                } else if (message.isGif()) {
                    conversationHistory.append("      🎞 [GIF]\n");
                }

                conversationHistory.append("\n");
            }

            // АНАЛИЗ СИТУАЦИИ - КРИТИЧЕСКИ ВАЖНО
            conversationHistory.append("\n========== ⚠️ АНАЛИЗ ТЕКУЩЕЙ СИТУАЦИИ ⚠️ ==========\n");
            conversationHistory.append("📨 ПОСЛЕДНЕЕ СООБЩЕНИЕ НАПИСАЛ: ").append(lastMessageSenderName.toUpperCase()).append("\n\n");

            if (lastMessageIsFromInterlocutor) {
                conversationHistory.append("❗️ СИТУАЦИЯ: ").append(interlocutorName).append(" УЖЕ НАПИСАЛ СООБЩЕНИЕ\n");
                conversationHistory.append("👉 ").append(interlocutorName).append(" нужно ПРОДОЛЖИТЬ разговор - добавить еще одно сообщение\n");
                conversationHistory.append("👉 Задача: развить тему, задать вопрос, отреагировать на что-то\n");
            } else {
                conversationHistory.append("❗️ СИТУАЦИЯ: ").append(interlocutorName).append(" должен ОТВЕТИТЬ на сообщение\n");
                conversationHistory.append("👉 ").append(interlocutorName).append(" нужно отреагировать на то, что написал ").append(lastMessageSenderName).append("\n");
                conversationHistory.append("👉 Задача: дать прямой ответ на последнее сообщение\n");
            }

            conversationHistory.append("\n========== ЧТО НУЖНО СДЕЛАТЬ ==========\n");
            conversationHistory.append("👤 ОТ ИМЕНИ КОГО ОТВЕЧАЕМ: ").append(interlocutorName.toUpperCase()).append("\n");

            if (lastMessageIsFromInterlocutor) {
                conversationHistory.append("📝 ТИП ОТВЕТА: ПРОДОЛЖЕНИЕ (добавить сообщение после своего же)\n");
                conversationHistory.append("🎯 ЦЕЛЬ: предложить варианты того, что ").append(interlocutorName).append(" МОЖЕТ ДОБАВИТЬ\n");
            } else {
                conversationHistory.append("📝 ТИП ОТВЕТА: ОТВЕТ НА СООБЩЕНИЕ\n");
                conversationHistory.append("🎯 ЦЕЛЬ: предложить варианты того, что ").append(interlocutorName).append(" МОЖЕТ ОТВЕТИТЬ\n");
            }

            conversationHistory.append("\n========== ТРЕБОВАНИЯ К ОТВЕТУ ==========\n");
            conversationHistory.append("✅ Предложи 3-5 РАЗНЫХ вариантов того, что ").append(interlocutorName.toUpperCase()).append(" МОЖЕТ НАПИСАТЬ\n");
            conversationHistory.append("✅ Варианты должны быть ОТ ИМЕНИ ").append(interlocutorName.toUpperCase()).append(" (используй 'я', 'мне', 'моё')\n");
            conversationHistory.append("✅ НЕ предлагай варианты от имени AI или от третьего лица\n");
            conversationHistory.append("\n");
            conversationHistory.append("❌ НЕПРАВИЛЬНО (от третьего лица): 'Пользователь может написать \"Привет\"'\n");
            conversationHistory.append("✅ ПРАВИЛЬНО (от первого лица): 'Привет, как дела?'\n");
            conversationHistory.append("\n");
            conversationHistory.append("❌ НЕПРАВИЛЬНО (AI предлагает): 'Я бы посоветовал спросить \"Как погода?\"'\n");
            conversationHistory.append("✅ ПРАВИЛЬНО (прямой текст): 'Как погода?'\n");

            // Добавляем напоминание о промптах, если они есть
            if (userSpecificPrompt != null) {
                conversationHistory.append("\n🔔 НЕ ЗАБУДЬ УЧЕСТЬ ПЕРСОНАЛЬНЫЕ ИНСТРУКЦИИ ДЛЯ ").append(interlocutorName.toUpperCase()).append(":\n");
                conversationHistory.append(userSpecificPrompt).append("\n");
            } else if (basicPrompt != null) {
                conversationHistory.append("\n🔔 НЕ ЗАБУДЬ УЧЕСТЬ БАЗОВЫЕ ИНСТРУКЦИИ:\n");
                conversationHistory.append(basicPrompt).append("\n");
            }

            conversationHistory.append("\n");
            conversationHistory.append("=".repeat(60));

            JSONObject historyMessage = new JSONObject();
            historyMessage.put("role", "user");
            historyMessage.put("content", conversationHistory.toString());

            messagesArray.put(historyMessage);

            // Настройки для вариативности
            requestBody.put("messages", messagesArray);
            requestBody.put("temperature", 0.9);
            requestBody.put("max_tokens", 1500);
            requestBody.put("top_p", 0.95);
            requestBody.put("frequency_penalty", 0.5);
            requestBody.put("presence_penalty", 0.6);

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
                            JSONObject choice = choices.getJSONObject(0);
                            JSONObject message = choice.getJSONObject("message");
                            String content = message.getString("content");

                            try {
                                content = cleanJsonResponse(content);
                                JSONObject suggestions = new JSONObject(content);

                                // Проверяем количество предложений
                                if (suggestions.has("suggestions")) {
                                    JSONArray suggestionsArray = suggestions.getJSONArray("suggestions");
                                    if (suggestionsArray.length() < 3) {
                                        suggestions = enhanceSuggestions(suggestions);
                                    }
                                }

                                callback.onSuccess(suggestions);
                            } catch (Exception e) {
                                FileLog.e("Error parsing OpenAI response: " + e.getMessage());
                                JSONObject fallback = createMultipleSuggestionsFromText(content);
                                callback.onSuccess(fallback);
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

    public void generateSuggestions(ArrayList<MessageObject> messages, Callback callback) {
        generateSuggestions(messages, null, callback);
    }

    public boolean hasApiKey() {
        return !TextUtils.isEmpty(NekoConfig.openaiApiKey);
    }

    // Функция для определения группового чата
    private boolean isGroupChat(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return false;

        Set<Long> uniqueSenders = new HashSet<>();
        for (MessageObject msg : messages) {
            uniqueSenders.add(msg.getSenderId());
        }

        // Если больше 2 уникальных отправителей - это группа
        // Или если есть сообщения от чата (отрицательный ID)
        for (MessageObject msg : messages) {
            if (msg.getSenderId() < 0) {
                return true; // Сообщение от канала/чата
            }
        }

        return uniqueSenders.size() > 2;
    }

    // Функция добавления контекста чата
    private void addChatContext(StringBuilder history, ArrayList<MessageObject> messages, String myName, boolean isGroupChat) {
        if (messages == null || messages.isEmpty()) return;

        try {
            if (isGroupChat) {
                history.append("👥👥👥 ЭТО ГРУППОВОЙ ЧАТ 👥👥👥\n");

                // Собираем информацию об участниках
                Set<Long> participants = new HashSet<>();
                for (MessageObject msg : messages) {
                    participants.add(msg.getSenderId());
                }

                history.append("УЧАСТНИКИ ПЕРЕПИСКИ:\n");
                for (Long id : participants) {
                    String name = getSenderNameFromId(id);
                    if (name != null) {
                        history.append("  • ").append(name);
                        if (id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            history.append(" (ЭТО Я - БОТ)");
                        }
                        history.append("\n");
                    }
                }
            } else {
                // Приватный чат - ищем собеседника
                long myId = UserConfig.getInstance(currentAccount).getClientUserId();
                for (MessageObject msg : messages) {
                    long senderId = msg.getSenderId();
                    if (senderId != myId && senderId > 0) {
                        String interlocutor = getSenderNameFromId(senderId);
                        history.append("💬💬💬 ПРИВАТНЫЙ ЧАТ С ").append(interlocutor.toUpperCase()).append(" 💬💬💬\n");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("Error adding chat context: " + e.getMessage());
        }
    }

    // ОБНОВЛЕННАЯ функция получения имени отправителя с четкой маркировкой
    private String getSenderName(MessageObject message, long myId, String myName, String interlocutorName, long interlocutorId) {
        try {
            long senderId = message.getSenderId();

            if (senderId == myId) {
                // Это наше сообщение (бота)
                return "👤 " + myName + " (Я - бот)";
            } else if (senderId == interlocutorId) {
                // Это сообщение от собеседника (кому помогаем)
                return "🗣 " + interlocutorName + " (СОБЕСЕДНИК - ему помогаем)";
            } else {
                // Сообщение от другого участника
                return "👥 " + getSenderNameFromId(senderId) + " (УЧАСТНИК)";
            }
        } catch (Exception e) {
            FileLog.e("Error getting sender name: " + e.getMessage());
            return message.isOut() ? "👤 Я (бот)" : "🗣 СОБЕСЕДНИК";
        }
    }

    private String getSenderNameFromId(long id) {
        try {
            if (id > 0) {
                // Это пользователь
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(id);
                if (user != null) {
                    return getDisplayName(user);
                }
            } else {
                // Это чат/канал
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-(long) id);
                if (chat != null) {
                    return chat.title;
                }
            }
        } catch (Exception e) {
            FileLog.e("Error getting name from ID: " + e.getMessage());
        }
        return "Пользователь";
    }

    private String getDisplayName(TLRPC.User user) {
        if (user == null) return "Пользователь";

        String name = user.first_name;
        if (!TextUtils.isEmpty(user.last_name)) {
            name += " " + user.last_name;
        }
        if (TextUtils.isEmpty(name)) {
            name = user.username;
        }
        if (TextUtils.isEmpty(name)) {
            name = "Пользователь";
        }
        return name;
    }

    private JSONObject enhanceSuggestions(JSONObject original) {
        try {
            JSONArray suggestions = original.getJSONArray("suggestions");
            JSONArray enhanced = new JSONArray();

            // Копируем оригинальные предложения
            for (int i = 0; i < suggestions.length(); i++) {
                enhanced.put(suggestions.get(i));
            }

            // Добавляем стандартные варианты если нужно
            if (enhanced.length() < 3) {
                JSONObject default1 = new JSONObject();
                default1.put("text", "Интересно, а что ты думаешь по этому поводу?");
                default1.put("confidence", 0.7);
                default1.put("type", "question");
                enhanced.put(default1);

                if (enhanced.length() < 3) {
                    JSONObject default2 = new JSONObject();
                    default2.put("text", "😊 Понятно, спасибо");
                    default2.put("confidence", 0.6);
                    default2.put("type", "emoji");
                    enhanced.put(default2);
                }
            }

            original.put("suggestions", enhanced);
            return original;
        } catch (Exception e) {
            return original;
        }
    }

    private JSONObject createMultipleSuggestionsFromText(String text) {
        try {
            JSONObject result = new JSONObject();
            JSONArray suggestions = new JSONArray();

            // Разбиваем текст на предложения
            String[] sentences = text.split("[.!?]");

            for (int i = 0; i < Math.min(sentences.length, 5); i++) {
                if (!TextUtils.isEmpty(sentences[i].trim())) {
                    JSONObject suggestion = new JSONObject();
                    suggestion.put("text", sentences[i].trim() + ".");
                    suggestion.put("confidence", 0.8 - (i * 0.1));
                    suggestion.put("type", i == 0 ? "answer" : (i == 1 ? "question" : "continuation"));
                    suggestions.put(suggestion);
                }
            }

            // Если предложений мало, добавляем варианты
            if (suggestions.length() < 3) {
                addDefaultSuggestions(suggestions);
            }

            result.put("suggestions", suggestions);
            result.put("explanation", "Автоматически сгенерированные варианты");

            return result;
        } catch (Exception e) {
            return createDefaultResponse();
        }
    }

    private void addDefaultSuggestions(JSONArray suggestions) throws Exception {
        JSONObject default1 = new JSONObject();
        default1.put("text", "Можешь рассказать подробнее?");
        default1.put("confidence", 0.7);
        default1.put("type", "question");
        suggestions.put(default1);

        JSONObject default2 = new JSONObject();
        default2.put("text", "Понятно, спасибо");
        default2.put("confidence", 0.6);
        default2.put("type", "answer");
        suggestions.put(default2);
    }

    private JSONObject createDefaultResponse() {
        try {
            JSONObject result = new JSONObject();
            JSONArray suggestions = new JSONArray();

            JSONObject suggestion1 = new JSONObject();
            suggestion1.put("text", "Да, я согласен");
            suggestion1.put("confidence", 0.9);
            suggestion1.put("type", "answer");
            suggestions.put(suggestion1);

            JSONObject suggestion2 = new JSONObject();
            suggestion2.put("text", "А что ты думаешь?");
            suggestion2.put("confidence", 0.8);
            suggestion2.put("type", "question");
            suggestions.put(suggestion2);

            JSONObject suggestion3 = new JSONObject();
            suggestion3.put("text", "Интересно! 😊");
            suggestion3.put("confidence", 0.7);
            suggestion3.put("type", "emoji");
            suggestions.put(suggestion3);

            result.put("suggestions", suggestions);
            result.put("explanation", "Стандартные варианты ответа");

            return result;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void handleErrorResponse(Response response, Callback callback) throws IOException {
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

    private boolean checkForImages(ArrayList<MessageObject> messages) {
        for (MessageObject message : messages) {
            if (message.isPhoto()) {
                return true;
            }
        }
        return false;
    }

    private String getMessageText(MessageObject message) {
        if (message.messageText != null && !TextUtils.isEmpty(message.messageText.toString())) {
            return message.messageText.toString();
        } else if (message.isPhoto()) {
            return "[Фото]";
        } else if (message.isVideo()) {
            return "[Видео]";
        } else if (message.isVoice()) {
            return "[Голосовое сообщение]";
        } else if (message.isSticker()) {
            return "[Стикер]";
        } else if (message.isGif()) {
            return "[GIF]";
        } else if (message.getDocument() != null) {
            return "[Файл: " + message.getDocumentName() + "]";
        } else {
            return "[Медиа]";
        }
    }

    private String cleanJsonResponse(String response) {
        response = response.replaceAll("```json\\s*", "");
        response = response.replaceAll("```\\s*", "");
        return response.trim();
    }
}