
package org.telegram.messenger.openAI;

import android.text.TextUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.UserPromptService;
import org.telegram.tgnet.TLRPC;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public abstract class BaseAIService {

    protected static final String SYSTEM_PROMPT =
            "You are an AI assistant that suggests reply options in a chat. Your task is to analyze the conversation history and suggest 3-5 different response options that the user can send. All options must be in first person (I, me, my) and ready to be sent.\n\n" +
                    "Important rules:\n" +
                    "1. If the last message is from the interlocutor, suggest replies to them.\n" +
                    "2. If the last message is from the user, suggest continuations.\n" +
                    "3. Options must be diverse in style and content.\n" +
                    "4. Consider the conversation context and relationship between interlocutors.\n" +
                    "5. If there are images, stickers, voice messages, etc., simply consider their presence in the context.\n" +
                    "6. Options should sound natural, as if written by a real person. Use casual, conversational style as in everyday messengers.\n" +
                    "7. Write in the same language as the chat (or app language if unclear).\n\n" +
                    "Respond ONLY in JSON format with the following structure:\n" +
                    "{\n" +
                    "  \"suggestions\": [\n" +
                    "    \"text of first suggestion\",\n" +
                    "    \"text of second suggestion\",\n" +
                    "    \"text of third suggestion\"\n" +
                    "  ]\n" +
                    "}\n\n" +
                    "Never return fewer than 3 options. The response must be only JSON.";

    protected int currentAccount;
    protected AISettings aiSettings;

    protected String getSystemPrompt() {
        String custom = aiSettings.getSystemPrompt();
        if (custom != null && !custom.isEmpty()) {
            return SYSTEM_PROMPT + "\n\n" + custom;
        }
        return SYSTEM_PROMPT;
    }

    protected String getEnhancedSystemPrompt(long interlocutorId) {
        StringBuilder enhanced = new StringBuilder();
        enhanced.append(SYSTEM_PROMPT);

        String custom = aiSettings.getSystemPrompt();
        if (custom != null && !custom.isEmpty()) {
            enhanced.append("\n\n").append("=== BASE USER PROMPT ===\n").append(custom);
        }

        UserPromptService promptService = UserPromptService.getInstance(currentAccount);

        // Prompt for myself
        String myPrompt = promptService.getCurrentUserPrompt();
        if (!TextUtils.isEmpty(myPrompt)) {
            enhanced.append("\n\n").append("=== MY PROMPT ===\n").append(myPrompt);
        }

        // Prompt for interlocutor (if any)
        if (interlocutorId > 0) {
            String interlocutorPrompt = promptService.getPrompt(interlocutorId);
            if (!TextUtils.isEmpty(interlocutorPrompt)) {
                enhanced.append("\n\n").append("=== INTERLOCUTOR PROMPT ===\n").append(interlocutorPrompt);
            }
        }

        return enhanced.toString();
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

    /**
     * Get the maximum tokens limit for this service.
     */
    public int getMaxTokens() {
        return getServiceSettings().getMaxTokens();
    }

    /**
     * Get the number of tokens generated so far (if available).
     * Default implementation returns -1 (unknown).
     */
    public int getGeneratedTokens() {
        return -1;
    }

    // Общая логика генерации запроса
    public void generateSuggestions(ArrayList<MessageObject> messages, String userPrompt, Callback callback) {
        if (!hasValidConfig()) {
            callback.onError(getServiceName() + " is not configured. Please check settings.");
            return;
        }

        try {
            // Получаем выбранную модель из настроек
            String modelId = getModel();
            AIModel model = getModelById(modelId);
            if (model == null) {
                model = getModelById(getDefaultModelId());
            }

            // Вычисляем ID собеседника для enhanced системного промпта
            long interlocutorId = getInterlocutorId(messages);
            String systemPrompt = getEnhancedSystemPrompt(interlocutorId);

            // Формируем историю переписки (без дублирования промптов)
            String conversationHistory = buildConversationHistory(messages, userPrompt, interlocutorId);

            // Отправляем запрос в конкретный сервис с указанием модели
            makeRequest(systemPrompt, conversationHistory, model.id, callback);

        } catch (Exception e) {
            FileLog.e("Error creating request: " + e.getMessage());
            callback.onError("Error creating request: " + e.getMessage());
        }
    }

    public void generateSuggestions(ArrayList<MessageObject> messages, Callback callback) {
        generateSuggestions(messages, null, callback);
    }

    // Формирование истории переписки (без дублирования промптов)
    protected String buildConversationHistory(ArrayList<MessageObject> messages, String userPrompt, long interlocutorId) {
        StringBuilder history = new StringBuilder();

        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();

        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        String myName = currentUser != null ? getDisplayName(currentUser) : "Me (bot)";

        String interlocutorName = "INTERLOCUTOR";
        if (interlocutorId > 0) {
            TLRPC.User interlocutor = MessagesController.getInstance(currentAccount).getUser(interlocutorId);
            if (interlocutor != null) {
                interlocutorName = getDisplayName(interlocutor);
            }
        }

        // Add user prompt if any
        if (!TextUtils.isEmpty(userPrompt)) {
            history.append("USER INSTRUCTION: ").append(userPrompt).append("\n\n");
        }

        // Промпты из UserPromptService больше не добавляются здесь, они включены в системный промпт

        // Add chat information
        history.append("========== CHAT INFO ==========\n");
        history.append("Me (bot): ").append(myName).append("\n");
        history.append("HELPING: ").append(interlocutorName).append("\n");
        // Add app language
        Locale currentLocale = LocaleController.getInstance().getCurrentLocale();
        String appLanguage = currentLocale.getDisplayLanguage(Locale.ENGLISH);
        history.append("APP LANGUAGE: ").append(appLanguage).append("\n");

        // Determine chat type
        boolean isGroupChat = isGroupChat(messages);
        if (isGroupChat) {
            history.append("CHAT TYPE: Group\n");
            addGroupParticipants(history, messages);
        } else {
            history.append("CHAT TYPE: Private\n");
        }

        // Анализ последнего сообщения
        MessageObject lastMessage = messages.get(messages.size() - 1);
        boolean lastMessageIsFromInterlocutor = lastMessage.getSenderId() == interlocutorId;
        boolean lastMessageIsFromMe = lastMessage.getSenderId() == currentUserId;

        history.append("\n========== CURRENT SITUATION ==========\n");
        if (lastMessageIsFromInterlocutor) {
            history.append("").append(interlocutorName).append(" wrote the last message\n");
            history.append("Task: suggest CONTINUATION options\n");
        } else {
            history.append("").append(getSenderNameFromId(lastMessage.getSenderId())).append(" wrote the last message\n");
            history.append("Task: suggest REPLY options on behalf of ").append(interlocutorName).append("\n");
        }

        // Message history
        history.append("\n========== CONVERSATION HISTORY ==========\n");
        history.append("(Messages from oldest to newest)\n\n");

        for (int i = 0; i < messages.size(); i++) {
            MessageObject msg = messages.get(i);
            String sender = getSenderName(msg, currentUserId, myName, interlocutorName, interlocutorId);
            String text = getMessageText(msg);

            if (i == messages.size() - 1) {
                history.append("[LAST] ");
            } else if (i == messages.size() - 2) {
                history.append("[SECOND LAST] ");
            }

            history.append(sender).append(": ").append(text).append("\n");
        }

        history.append("\n========== RESPONSE REQUIREMENTS ==========\n");
        history.append("SUGGEST 3-5 DIFFERENT OPTIONS ON BEHALF OF ").append(interlocutorName).append("\n");
        history.append("MUST use 'I', 'me', 'my' (first person)\n");
        history.append("RESPONSE ONLY IN JSON FORMAT\n");

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
            return myName + " (Me)";
        } else if (senderId == interlocutorId) {
            return interlocutorName + " (INTERLOCUTOR)";
        } else {
            return getSenderNameFromId(senderId) + " (PARTICIPANT)";
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
        return "User";
    }

    protected String getDisplayName(TLRPC.User user) {
        if (user == null) return "User";
        String name = user.first_name;
        if (!TextUtils.isEmpty(user.last_name)) name += " " + user.last_name;
        if (TextUtils.isEmpty(name)) name = user.username;
        return TextUtils.isEmpty(name) ? "User" : name;
    }

    protected String getMessageText(MessageObject message) {
        String caption = null;
        if (message.messageText != null && !TextUtils.isEmpty(message.messageText.toString())) {
            caption = message.messageText.toString();
        }

        if (message.isPhoto()) {
            return caption != null ? "[Photo] Caption: " + caption : "[Photo]";
        } else if (message.isVideo()) {
            return caption != null ? "[Video] Caption: " + caption : "[Video]";
        } else if (message.isVoice()) {
            // Проверяем наличие расшифровки голосового сообщения
            String transcription = getVoiceTranscription(message);
            if (transcription != null && !transcription.isEmpty()) {
                return "[Voice] Transcription: " + transcription;
            } else {
                return "[Voice]";
            }
        } else if (message.isSticker()) {
            // Get sticker emoji directly from MessageObject
            String stickerEmoji = getStickerEmoji(message);
            if (stickerEmoji != null && !stickerEmoji.isEmpty()) {
                return stickerEmoji;
            } else {
                return "[Sticker]";
            }
        } else if (message.isGif()) {
            return "[GIF]";
        } else if (caption != null) {
            return caption;
        } else {
            return "[Media]";
        }
    }



    // Метод для получения эмодзи стикера
    private String getStickerEmoji(MessageObject message) {
        try {
            // Получаем документ стикера
            TLRPC.Document document = message.getDocument();
            if (document != null) {
                // Ищем атрибут стикера
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                        TLRPC.TL_documentAttributeSticker stickerAttr = (TLRPC.TL_documentAttributeSticker) attribute;

                        // В TL_documentAttributeSticker есть поле alt - это и есть закрепленный эмодзи
                        // Также может быть поле emoji в некоторых версиях
                        if (stickerAttr.alt != null && !stickerAttr.alt.isEmpty()) {
                            return stickerAttr.alt;
                        }

                        // Проверяем также поле emoji, если оно существует
                        // (зависит от версии TL схемы)
                        try {
                            java.lang.reflect.Field emojiField = stickerAttr.getClass().getField("emoji");
                            if (emojiField != null) {
                                String emoji = (String) emojiField.get(stickerAttr);
                                if (emoji != null && !emoji.isEmpty()) {
                                    return emoji;
                                }
                            }
                        } catch (Exception e) {
                            // Поле emoji может отсутствовать - игнорируем
                        }

                        break;
                    }
                }

                // Альтернативный способ через getStickerEmoji из MessageObject
                // если такой метод существует
                try {
                    java.lang.reflect.Method getStickerEmojiMethod = message.getClass().getMethod("getStickerEmoji");
                    if (getStickerEmojiMethod != null) {
                        String emoji = (String) getStickerEmojiMethod.invoke(message);
                        if (emoji != null && !emoji.isEmpty()) {
                            return emoji;
                        }
                    }
                } catch (Exception e) {
                    // Метод может отсутствовать - игнорируем
                }
            }
        } catch (Exception e) {
            FileLog.e("Error getting sticker emoji: " + e.getMessage());
        }
        return null;
    }


    private String getVoiceTranscription(MessageObject message) {
        try {
            // Пытаемся получить расшифровку через рефлексию, так как прямой метод может отсутствовать
            try {
                // Метод 1: getTranscription (если существует)
                java.lang.reflect.Method getTranscriptionMethod = message.getClass().getMethod("getTranscription");
                if (getTranscriptionMethod != null) {
                    Object transcription = getTranscriptionMethod.invoke(message);
                    if (transcription != null && transcription instanceof CharSequence) {
                        String text = transcription.toString();
                        if (!TextUtils.isEmpty(text)) {
                            return text;
                        }
                    }
                }
            } catch (Exception e) {
                // Метод может отсутствовать - пробуем следующий способ
            }

            // Метод 2: проверяем через messageObject.getDocument() и атрибуты
            TLRPC.Document document = message.getDocument();
            if (document != null) {
                // Ищем атрибут с транскрипцией (если такой есть в вашей версии Telegram)
                for (TLRPC.DocumentAttribute attribute : document.attributes) {
                    // В некоторых версиях может быть специальный атрибут для транскрипции
                    // Проверяем наличие поля transcriptionText через рефлексию
                    try {
                        java.lang.reflect.Field transcriptionField = attribute.getClass().getField("transcriptionText");
                        if (transcriptionField != null) {
                            String transcription = (String) transcriptionField.get(attribute);
                            if (transcription != null && !transcription.isEmpty()) {
                                return transcription;
                            }
                        }
                    } catch (Exception e) {
                        // Поле может отсутствовать - игнорируем
                    }
                }
            }

            // Метод 3: проверяем через getMedia() если есть
            try {
                java.lang.reflect.Method getMediaMethod = message.getClass().getMethod("getMedia");
                if (getMediaMethod != null) {
                    Object media = getMediaMethod.invoke(message);
                    if (media != null) {
                        // Пытаемся найти транскрипцию в media
                        java.lang.reflect.Method getTranscriptionMethod = media.getClass().getMethod("getTranscription");
                        if (getTranscriptionMethod != null) {
                            Object transcription = getTranscriptionMethod.invoke(media);
                            if (transcription != null && transcription instanceof CharSequence) {
                                String text = transcription.toString();
                                if (!TextUtils.isEmpty(text)) {
                                    return text;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // Метод может отсутствовать
            }

            // Метод 4: проверяем наличие расшифровки в messageObject.transcription (если такое поле существует)
            try {
                java.lang.reflect.Field transcriptionField = message.getClass().getField("transcription");
                if (transcriptionField != null) {
                    Object transcription = transcriptionField.get(message);
                    if (transcription != null && transcription instanceof CharSequence) {
                        String text = transcription.toString();
                        if (!TextUtils.isEmpty(text)) {
                            return text;
                        }
                    }
                }
            } catch (Exception e) {
                // Поле может отсутствовать
            }

            // Метод 5: проверяем наличие расшифровки в атрибутах документа через getAttributes()
            try {
                java.lang.reflect.Method getAttributesMethod = document.getClass().getMethod("getAttributes");
                if (getAttributesMethod != null) {
                    ArrayList<?> attributes = (ArrayList<?>) getAttributesMethod.invoke(document);
                    for (Object attr : attributes) {
                        // Проверяем, есть ли поле transcriptionText в атрибуте
                        try {
                            java.lang.reflect.Field textField = attr.getClass().getField("text");
                            if (textField != null) {
                                String text = (String) textField.get(attr);
                                if (text != null && !text.isEmpty()) {
                                    return text;
                                }
                            }
                        } catch (Exception e) {
                            // Поле может отсутствовать
                        }
                    }
                }
            } catch (Exception e) {
                // Метод может отсутствовать
            }

        } catch (Exception e) {
            FileLog.e("Error getting voice transcription: " + e.getMessage());
        }
        return null;
    }



    protected void addGroupParticipants(StringBuilder history, ArrayList<MessageObject> messages) {
        Set<Long> participants = new HashSet<>();
        for (MessageObject msg : messages) {
            participants.add(msg.getSenderId());
        }
        history.append("PARTICIPANTS:\n");
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
                Object item = suggestions.get(i);
                // Если элемент - объект, извлекаем поле "text"
                if (item instanceof JSONObject) {
                    JSONObject obj = (JSONObject) item;
                    if (obj.has("text")) {
                        enhanced.put(obj.getString("text"));
                    } else {
                        // fallback
                        enhanced.put(item.toString());
                    }
                } else {
                    enhanced.put(item);
                }
            }

            original.put("suggestions", enhanced);
            return original;
        } catch (Exception e) {
            FileLog.e("Error enhancing suggestions: " + e.getMessage());
            return original;
        }
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