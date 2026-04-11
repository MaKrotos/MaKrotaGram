package org.telegram.messenger.openAI;

import android.os.Handler;
import android.os.Looper;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.openAI.AISettings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Менеджер автоответов с таймером для каждого диалога.
 * Обрабатывает входящие сообщения, запускает таймер и автоматически генерирует ответ через AI.
 */
public class AutoReplyManager implements NotificationCenter.NotificationCenterDelegate {

    private static final Map<Integer, AutoReplyManager> instances = new HashMap<>();

    private final int account;
    private final AISettings aiSettings;
    private final Handler mainHandler;
    private final Map<Long, Runnable> timerRunnables = new HashMap<>();
    private final Map<Long, Long> timerStartTimes = new HashMap<>();
    private final Map<Long, ArrayList<MessageObject>> pendingMessages = new HashMap<>();
    private final Map<Long, Long> lastReplyTimes = new HashMap<>();
    private static final long MIN_REPLY_INTERVAL_MS = TimeUnit.MINUTES.toMillis(5); // минимальный интервал между автоответами в одном диалоге

    private AutoReplyManager(int account) {
        this.account = account;
        this.aiSettings = new AISettings(account);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public static AutoReplyManager getInstance(int account) {
        AutoReplyManager instance = instances.get(account);
        if (instance == null) {
            instance = new AutoReplyManager(account);
            instances.put(account, instance);
        }
        return instance;
    }

    public void init() {
        NotificationCenter.getInstance(account).addObserver(this, NotificationCenter.didReceiveNewMessages);
    }

    public void dispose() {
        NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.didReceiveNewMessages);
        cancelAllTimers();
        instances.remove(account);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (account != this.account) return;
        if (id == NotificationCenter.didReceiveNewMessages) {
            if (args.length < 3) {
                FileLog.e("AutoReplyManager: didReceiveNewMessages args length < 3");
                return;
            }
            long dialogId;
            try {
                dialogId = (Long) args[0];
            } catch (ClassCastException e) {
                FileLog.e("AutoReplyManager: dialogId cast error, args[0]=" + args[0] + " type=" + (args[0] != null ? args[0].getClass().getName() : "null"));
                return;
            }
            ArrayList<MessageObject> messages = null;
            try {
                messages = (ArrayList<MessageObject>) args[1];
            } catch (ClassCastException e) {
                FileLog.e("AutoReplyManager: messages cast error, args[1] type=" + (args[1] != null ? args[1].getClass().getName() : "null"));
                // Попробуем преобразовать, если это ArrayList<TLRPC.Message>
                if (args[1] instanceof ArrayList) {
                    ArrayList<?> rawList = (ArrayList<?>) args[1];
                    if (!rawList.isEmpty() && rawList.get(0) instanceof TLRPC.Message) {
                        FileLog.d("AutoReplyManager: converting TLRPC.Message to MessageObject");
                        messages = new ArrayList<>();
                        for (Object obj : rawList) {
                            TLRPC.Message tlMessage = (TLRPC.Message) obj;
                            messages.add(new MessageObject(account, tlMessage, false, false));
                        }
                    }
                }
                if (messages == null) {
                    return;
                }
            }
            boolean scheduled = (Boolean) args[2];
            // int mode = (Integer) args[3]; // не используется
            if (scheduled) {
                return; // не обрабатываем scheduled сообщения
            }
            if (messages != null) {
                for (MessageObject message : messages) {
                    if (message != null && !message.isOut() && !(message.messageOwner instanceof TLRPC.TL_messageService)) {
                        onMessageReceived(message);
                    }
                }
            }
        }
    }

    private void onMessageReceived(MessageObject message) {
        if (!aiSettings.isAutoReplyEnabled()) {
            return;
        }
        if (!aiSettings.hasValidConfig()) {
            FileLog.d("AutoReplyManager: AI service not configured, skipping auto-reply");
            return;
        }
        long dialogId = message.getDialogId();
        // Проверяем, не является ли диалог каналом или заблокированным
        if (isDialogRestricted(dialogId)) {
            return;
        }

        // Добавляем сообщение в pending для этого диалога
        ArrayList<MessageObject> messages = pendingMessages.get(dialogId);
        if (messages == null) {
            messages = new ArrayList<>();
            pendingMessages.put(dialogId, messages);
        }
        messages.add(message);

        // Запускаем или перезапускаем таймер
        startTimer(dialogId);
    }

    private boolean isDialogRestricted(long dialogId) {
        MessagesController messagesController = MessagesController.getInstance(account);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User user = messagesController.getUser(dialogId);
            if (user == null || user.deleted) {
                return true;
            }
            // Разрешаем ответы ботам (user.bot == true)
        } else if (DialogObject.isChatDialog(dialogId)) {
            TLRPC.Chat chat = messagesController.getChat(-dialogId);
            if (chat == null || chat.left || chat.kicked || chat.deactivated) {
                return true;
            }
            if (ChatObject.isChannel(chat) && !chat.megagroup) {
                // Не отвечаем в каналах (не группах)
                return true;
            }
        }
        // Проверяем настройки "Не беспокоить"
        if (messagesController.isDialogMuted(dialogId, 0)) {
            return true;
        }
        return false;
    }

    private void startTimer(long dialogId) {
        // Отменяем предыдущий таймер
        cancelTimer(dialogId);

        int delayMinutes = aiSettings.getAutoReplyDelayMinutes();
        long delayMillis = TimeUnit.MINUTES.toMillis(delayMinutes);

        Runnable timerRunnable = new Runnable() {
            @Override
            public void run() {
                onTimerExpired(dialogId);
            }
        };

        timerRunnables.put(dialogId, timerRunnable);
        timerStartTimes.put(dialogId, System.currentTimeMillis());
        mainHandler.postDelayed(timerRunnable, delayMillis);

        FileLog.d("AutoReplyManager: Timer started for dialog " + dialogId + " delay " + delayMinutes + " min");
    }

    private void cancelTimer(long dialogId) {
        Runnable runnable = timerRunnables.remove(dialogId);
        if (runnable != null) {
            mainHandler.removeCallbacks(runnable);
            timerStartTimes.remove(dialogId);
            FileLog.d("AutoReplyManager: Timer cancelled for dialog " + dialogId);
        }
    }

    private void cancelAllTimers() {
        for (Runnable runnable : timerRunnables.values()) {
            mainHandler.removeCallbacks(runnable);
        }
        timerRunnables.clear();
        timerStartTimes.clear();
        pendingMessages.clear();
    }

    private void onTimerExpired(long dialogId) {
        FileLog.d("AutoReplyManager: Timer expired for dialog " + dialogId);
        timerRunnables.remove(dialogId);
        timerStartTimes.remove(dialogId);

        // Проверяем, не слишком ли часто отвечали в этом диалоге
        Long lastReplyTime = lastReplyTimes.get(dialogId);
        long now = System.currentTimeMillis();
        if (lastReplyTime != null && (now - lastReplyTime) < MIN_REPLY_INTERVAL_MS) {
            FileLog.d("AutoReplyManager: Skipping auto-reply for dialog " + dialogId + " due to rate limit");
            pendingMessages.remove(dialogId); // очищаем pending, чтобы не копились
            return;
        }

        // Собираем контекст
        ArrayList<MessageObject> recentMessages = pendingMessages.remove(dialogId);
        if (recentMessages == null || recentMessages.isEmpty()) {
            FileLog.d("AutoReplyManager: No pending messages for dialog " + dialogId);
            return;
        }

        // Получаем дополнительные сообщения из истории
        ArrayList<MessageObject> historyMessages = loadHistoryMessages(dialogId, aiSettings.getAutoReplyIncludeHistoryCount());
        ArrayList<MessageObject> allMessages = new ArrayList<>();
        if (historyMessages != null) {
            allMessages.addAll(historyMessages);
        }
        allMessages.addAll(recentMessages);

        // Генерируем и отправляем ответ
        generateAndSendReply(dialogId, allMessages);
    }

    private ArrayList<MessageObject> loadHistoryMessages(long dialogId, int count) {
        // Упрощённая реализация: используем MessagesStorage для получения последних сообщений
        // В реальности нужно асинхронно загружать историю через MessagesController
        // Здесь возвращаем null, чтобы не усложнять; можно доработать позже.
        return null;
    }

    private void generateAndSendReply(long dialogId, ArrayList<MessageObject> messages) {
        org.telegram.messenger.openAI.BaseAIService aiService = org.telegram.messenger.openAI.AIServiceFactory.createService(account);
        if (aiService == null) {
            FileLog.e("AutoReplyManager: AI service is null, cannot generate reply");
            return;
        }
        aiService.generateSingleResponse(messages, new org.telegram.messenger.openAI.BaseAIService.SingleResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                FileLog.d("AutoReplyManager: AI generated response: " + responseText);
                // Обновляем время последнего ответа
                lastReplyTimes.put(dialogId, System.currentTimeMillis());
                sendMessage(dialogId, responseText);
            }

            @Override
            public void onError(String error) {
                FileLog.e("AutoReplyManager: Failed to generate response: " + error);
            }
        });
    }

    private void sendMessage(long dialogId, String text) {
        if (text == null || text.trim().isEmpty()) {
            FileLog.e("AutoReplyManager: Empty response text, not sending");
            return;
        }
        // Ограничиваем длину ответа (Telegram имеет лимит на длину сообщения)
        final int MAX_LENGTH = 4000;
        if (text.length() > MAX_LENGTH) {
            FileLog.d("AutoReplyManager: Truncating response from " + text.length() + " to " + MAX_LENGTH + " characters");
            text = text.substring(0, MAX_LENGTH);
        }
        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                text,
                dialogId,
                null, // replyToMsg
                null, // replyToTopMsg
                null, // entities
                true, // clearInput
                null, // scheduleDate
                null, // scheduleRepeatPeriod
                null, // parentObject
                true, // silent
                0, // ttl
                0, // payStars
                null, // effect
                false // invertMedia
        );
        SendMessagesHelper.getInstance(account).sendMessage(params);
        FileLog.d("AutoReplyManager: Sent auto-reply to dialog " + dialogId);
    }

    // Публичные методы для управления
    public void setEnabled(boolean enabled) {
        if (enabled) {
            init();
        } else {
            dispose();
        }
    }

    public boolean isEnabled() {
        return aiSettings.isAutoReplyEnabled();
    }
}