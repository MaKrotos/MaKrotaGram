
package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.UserPromptService;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseAIService {

    protected static final String SYSTEM_PROMPT =
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

    protected int currentAccount;
    protected AISettings aiSettings;

    protected String getSystemPrompt() {
        String custom = aiSettings.getSystemPrompt();
        if (custom != null && !custom.isEmpty()) {
            return SYSTEM_PROMPT + "\n\n" + custom;
        }
        return SYSTEM_PROMPT;
    }

    public interface Callback {
        void onSuccess(JSONObject response);

        void onError(String error);
    }

    // Модель для представления доступной модели AI
    public static class AIModel {
        public final String id;
        public final String displayName;
        public final String description;
        public final int maxTokens;
        public final boolean supportsVision;
        public final boolean supportsFunctions;

        public AIModel(String id, String displayName, String description,
                       int maxTokens, boolean supportsVision, boolean supportsFunctions) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.maxTokens = maxTokens;
            this.supportsVision = supportsVision;
            this.supportsFunctions = supportsFunctions;
        }
    }

    public BaseAIService(int account) {
        this.currentAccount = account;
        this.aiSettings = new AISettings(account);
    }

    public BaseAIService() {
        this(UserConfig.selectedAccount);
    }

    // Абстрактные методы, которые должны реализовать конкретные сервисы
    protected abstract void makeRequest(String systemPrompt, String history, String model, Callback callback);

    public abstract String getServiceName();

    public abstract AISettings.AIServiceType getServiceType();

    // Новые абстрактные методы для моделей
    public abstract AIModel[] getAvailableModels();

    public abstract String getDefaultModelId();

    public abstract AIModel getModelById(String modelId);

    /**
     * Returns the service-specific settings for this service.
     */
    protected BaseServiceSettings getServiceSettings() {
        return aiSettings.getServiceSettings(getServiceType());
    }

    /**
     * Check if this service has valid configuration (API key set).
     */
    public boolean hasValidConfig() {
        return getServiceSettings().validate();
    }

    /**
     * Get the API key for this service.
     */
    protected String getApiKey() {
        return getServiceSettings().getApiKey();
    }

    /**
     * Get the model ID for this service.
     */
    protected String getModel() {
        return getServiceSettings().getModel();
    }

    // Общая логика генерации запроса
    public void generateSuggestions(ArrayList<MessageObject> messages, String userPrompt, Callback callback) {
        if (!hasValidConfig()) {
            callback.onError(getServiceName() + " не настроен. Пожалуйста, проверьте настройки.");
            return;
        }

        try {
            // Получаем выбранную модель из настроек
            String modelId = getModel();
            AIModel model = getModelById(modelId);
            if (model == null) {
                model = getModelById(getDefaultModelId());
            }

            // Формируем историю переписки
            String conversationHistory = buildConversationHistory(messages, userPrompt);

            // Отправляем запрос в конкретный сервис с указанием модели
            makeRequest(getSystemPrompt(), conversationHistory, model.id, callback);

        } catch (Exception e) {
            FileLog.e("Error creating request: " + e.getMessage());
            callback.onError("Ошибка при создании запроса: " + e.getMessage());
        }
    }

    public void generateSuggestions(ArrayList<MessageObject> messages, Callback callback) {
        generateSuggestions(messages, null, callback);
    }

    // Формирование истории переписки (без изменений)
    protected String buildConversationHistory(ArrayList<MessageObject> messages, String userPrompt) {
        StringBuilder history = new StringBuilder();

        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        long interlocutorId = getInterlocutorId(messages);

        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        String myName = currentUser != null ? getDisplayName(currentUser) : "Я (бот)";

        String interlocutorName = "СОБЕСЕДНИК";
        if (interlocutorId > 0) {
            TLRPC.User interlocutor = MessagesController.getInstance(currentAccount).getUser(interlocutorId);
            if (interlocutor != null) {
                interlocutorName = getDisplayName(interlocutor);
            }
        }

        // Добавляем информацию о используемом сервисе и модели
        history.append("🤖 ИСПОЛЬЗУЕТСЯ: ").append(getServiceName()).append("\n");
        history.append("📊 МОДЕЛЬ: ").append(getModelById(getModel()).displayName).append("\n\n");

        // Добавляем пользовательский промпт если есть
        if (!TextUtils.isEmpty(userPrompt)) {
            history.append("📝 ИНСТРУКЦИЯ ОТ ПОЛЬЗОВАТЕЛЯ: ").append(userPrompt).append("\n\n");
        }

        // Добавляем промпты из UserPromptService
        UserPromptService promptService = UserPromptService.getInstance(currentAccount);
        if (interlocutorId > 0) {
            String interlocutorPrompt = promptService.getPrompt(interlocutorId);
            if (!TextUtils.isEmpty(interlocutorPrompt)) {
                history.append("🎯 ПРОМПТ ДЛЯ СОБЕСЕДНИКА: ").append(interlocutorPrompt).append("\n\n");
            }
        }
        String myPrompt = promptService.getCurrentUserPrompt();
        if (!TextUtils.isEmpty(myPrompt)) {
            history.append("👤 МОЙ ПРОМПТ: ").append(myPrompt).append("\n\n");
        }

        // Добавляем информацию о чате
        history.append("========== ИНФОРМАЦИЯ О ЧАТЕ ==========\n");
        history.append("👤 Я (бот): ").append(myName).append("\n");
        history.append("🗣 КОМУ ПОМОГАЕМ: ").append(interlocutorName).append("\n");

        // Определяем тип чата
        boolean isGroupChat = isGroupChat(messages);
        if (isGroupChat) {
            history.append("👥 ТИП ЧАТА: Групповой\n");
            addGroupParticipants(history, messages);
        } else {
            history.append("💬 ТИП ЧАТА: Личный\n");
        }

        // Анализ последнего сообщения
        MessageObject lastMessage = messages.get(messages.size() - 1);
        boolean lastMessageIsFromInterlocutor = lastMessage.getSenderId() == interlocutorId;
        boolean lastMessageIsFromMe = lastMessage.getSenderId() == currentUserId;

        history.append("\n========== ТЕКУЩАЯ СИТУАЦИЯ ==========\n");
        if (lastMessageIsFromInterlocutor) {
            history.append("📨 ").append(interlocutorName).append(" написал последнее сообщение\n");
            history.append("👉 Задача: предложить варианты ПРОДОЛЖЕНИЯ разговора\n");
        } else {
            history.append("📨 ").append(getSenderNameFromId(lastMessage.getSenderId())).append(" написал последнее сообщение\n");
            history.append("👉 Задача: предложить варианты ОТВЕТА от лица ").append(interlocutorName).append("\n");
        }

        // История сообщений
        history.append("\n========== ИСТОРИЯ ПЕРЕПИСКИ ==========\n");
        history.append("(Сообщения идут от старых к новым)\n\n");

        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            String sender = getSenderName(msg, currentUserId, myName, interlocutorName, interlocutorId);
            String text = getMessageText(msg);

            if (i == messages.size() - 1) {
                history.append("🔴 [ПОСЛЕДНЕЕ] ");
            } else if (i == messages.size() - 2) {
                history.append("🟠 [ПРЕДПОСЛЕДНЕЕ] ");
            }

            history.append(sender).append(": ").append(text).append("\n");

            // Добавляем инфо о медиа
            if (msg.isPhoto()) history.append("   📸 [ФОТО]\n");
            else if (msg.isVideo()) history.append("   🎥 [ВИДЕО]\n");
            else if (msg.isVoice()) history.append("   🎤 [ГОЛОСОВОЕ]\n");
            else if (msg.isSticker()) history.append("   🎯 [СТИКЕР]\n");
        }

        history.append("\n========== ТРЕБОВАНИЯ К ОТВЕТУ ==========\n");
        history.append("✅ Предложи 3-5 РАЗНЫХ вариантов от лица ").append(interlocutorName).append("\n");
        history.append("✅ Используй 'я', 'мне', 'моё' (от первого лица)\n");
        history.append("✅ Ответ только в JSON формате\n");

        return history.toString();
    }

    // Остальные вспомогательные методы остаются без изменений
    protected long getInterlocutorId(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        long myId = UserConfig.getInstance(currentAccount).getClientUserId();
        for (MessageObject msg : messages) {
            long senderId = msg.getSenderId();
            if (senderId != myId && senderId > 0) {
                return senderId;
            }
        }
        return 0;
    }

    protected boolean isGroupChat(ArrayList<MessageObject> messages) {
        if (messages == null || messages.isEmpty()) return false;
        Set<Long> uniqueSenders = new HashSet<>();
        for (MessageObject msg : messages) {
            uniqueSenders.add(msg.getSenderId());
            if (msg.getSenderId() < 0) return true;
        }
        return uniqueSenders.size() > 2;
    }

    protected String getSenderName(MessageObject message, long myId, String myName, String interlocutorName, long interlocutorId) {
        long senderId = message.getSenderId();
        if (senderId == myId) {
            return "👤 " + myName + " (Я)";
        } else if (senderId == interlocutorId) {
            return "🗣 " + interlocutorName + " (СОБЕСЕДНИК)";
        } else {
            return "👥 " + getSenderNameFromId(senderId) + " (УЧАСТНИК)";
        }
    }

    protected String getSenderNameFromId(long id) {
        try {
            if (id > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(id);
                if (user != null) return getDisplayName(user);
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-id);
                if (chat != null) return chat.title;
            }
        } catch (Exception e) {
            FileLog.e("Error getting name: " + e.getMessage());
        }
        return "Пользователь";
    }

    protected String getDisplayName(TLRPC.User user) {
        if (user == null) return "Пользователь";
        String name = user.first_name;
        if (!TextUtils.isEmpty(user.last_name)) name += " " + user.last_name;
        if (TextUtils.isEmpty(name)) name = user.username;
        return TextUtils.isEmpty(name) ? "Пользователь" : name;
    }

    protected String getMessageText(MessageObject message) {
        if (message.messageText != null && !TextUtils.isEmpty(message.messageText.toString())) {
            return message.messageText.toString();
        } else if (message.isPhoto()) return "[Фото]";
        else if (message.isVideo()) return "[Видео]";
        else if (message.isVoice()) return "[Голосовое]";
        else if (message.isSticker()) return "[Стикер]";
        else if (message.isGif()) return "[GIF]";
        else return "[Медиа]";
    }

    protected void addGroupParticipants(StringBuilder history, ArrayList<MessageObject> messages) {
        Set<Long> participants = new HashSet<>();
        for (MessageObject msg : messages) {
            participants.add(msg.getSenderId());
        }
        history.append("УЧАСТНИКИ:\n");
        for (Long id : participants) {
            history.append("  • ").append(getSenderNameFromId(id)).append("\n");
        }
    }

    // Методы для обработки ответов
    protected JSONObject enhanceSuggestions(JSONObject original) {
        try {
            JSONArray suggestions = original.getJSONArray("suggestions");
            if (suggestions.length() >= 3) return original;

            JSONArray enhanced = new JSONArray();
            for (int i = 0; i < suggestions.length(); i++) {
                enhanced.put(suggestions.get(i));
            }

            while (enhanced.length() < 3) {
                JSONObject fallback = createDefaultResponse();
                enhanced.put(fallback);
            }

            original.put("suggestions", enhanced);
            return original;
        } catch (Exception e) {
            FileLog.e("Error enhancing suggestions: " + e.getMessage());
            return original;
        }
    }

    public JSONObject createDefaultResponse() {
        JSONObject suggestion = new JSONObject();
        try {
            suggestion.put("text", "Да, я согласен");
            suggestion.put("confidence", 0.8);
            suggestion.put("type", "answer");
        } catch (Exception e) {
            FileLog.e("Error creating default response: " + e.getMessage());
        }
        return suggestion;
    }

    protected JSONObject cleanJsonResponse(String raw) {
        // Удаляем возможные лишние символы в начале/конце
        String cleaned = raw.trim();
        // Ищем начало JSON
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return new JSONObject(cleaned);
        } catch (Exception e) {
            FileLog.e("Error parsing JSON: " + e.getMessage());
            return null;
        }
    }
}