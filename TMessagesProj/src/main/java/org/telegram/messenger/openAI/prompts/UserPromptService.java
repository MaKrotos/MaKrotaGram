package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserPromptService extends BaseController {
    private static final String PREFS_NAME = "user_prompts";
    private static final String KEY_PROMPTS = "prompts_data";
    private static final String KEY_USER_PREFIX = "user_";

    private static volatile UserPromptService[] Instance = new UserPromptService[UserConfig.MAX_ACCOUNT_COUNT];
    private final Map<Long, UserPrompt> promptsCache = new ConcurrentHashMap<>();
    private final SharedPreferences preferences;
    private final int currentAccount;

    private UserPromptService(int account) {
        super(account);
        this.currentAccount = account;
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME + "_" + account, Context.MODE_PRIVATE);
        loadAllPrompts();
    }

    public static UserPromptService getInstance(int account) {
        UserPromptService localInstance = Instance[account];
        if (localInstance == null) {
            synchronized (UserPromptService.class) {
                localInstance = Instance[account];
                if (localInstance == null) {
                    Instance[account] = localInstance = new UserPromptService(account);
                }
            }
        }
        return localInstance;
    }

    public static UserPromptService getCurrentInstance() {
        return getInstance(UserConfig.selectedAccount);
    }

    /**
     * Сохранить промпт для пользователя
     */
    public void savePrompt(long userId, String promptText) {
        if (userId == 0) return;

        UserPrompt prompt = promptsCache.get(userId);
        if (prompt == null) {
            prompt = new UserPrompt(userId, promptText);
        } else {
            prompt.setPromptText(promptText);
        }

        promptsCache.put(userId, prompt);
        saveAllPrompts();

        // Уведомляем подписчиков об изменении
        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.userPromptUpdated, userId, promptText);
    }

    /**
     * Сохранить промпт для пользователя с категорией
     */
    public void savePrompt(long userId, String promptText, String category) {
        if (userId == 0) return;

        UserPrompt prompt = promptsCache.get(userId);
        if (prompt == null) {
            prompt = new UserPrompt(userId, promptText);
        } else {
            prompt.setPromptText(promptText);
        }
        prompt.setCategory(category);

        promptsCache.put(userId, prompt);
        saveAllPrompts();

        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.userPromptUpdated, userId, promptText);
    }

    /**
     * Получить промпт для пользователя
     */
    public String getPrompt(long userId) {
        UserPrompt prompt = promptsCache.get(userId);
        return prompt != null ? prompt.getPromptText() : "";
    }

    /**
     * Получить полный объект промпта
     */
    public UserPrompt getUserPrompt(long userId) {
        return promptsCache.get(userId);
    }

    /**
     * Получить промпт для текущего пользователя
     */
    public String getCurrentUserPrompt() {
        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        return getPrompt(currentUserId);
    }

    /**
     * Удалить промпт для пользователя
     */
    public void deletePrompt(long userId) {
        promptsCache.remove(userId);
        saveAllPrompts();

        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.userPromptDeleted, userId);
    }

    /**
     * Проверить, существует ли промпт для пользователя
     */
    public boolean hasPrompt(long userId) {
        UserPrompt prompt = promptsCache.get(userId);
        return prompt != null && !prompt.getPromptText().isEmpty();
    }

    /**
     * Получить все промпты
     */
    public List<UserPrompt> getAllPrompts() {
        return new ArrayList<>(promptsCache.values());
    }

    /**
     * Получить все промпты для указанного аккаунта
     */
    public Map<Long, String> getAllPromptsForAccount(int account) {
        Map<Long, String> result = new HashMap<>();
        long currentUserId = UserConfig.getInstance(account).getClientUserId();

        for (Map.Entry<Long, UserPrompt> entry : promptsCache.entrySet()) {
            // Здесь можно добавить логику фильтрации по аккаунту
            result.put(entry.getKey(), entry.getValue().getPromptText());
        }
        return result;
    }


    /**
     * Поиск промптов по тексту
     */
    public List<UserPrompt> searchPrompts(String query) {
        List<UserPrompt> results = new ArrayList<>();
        if (query == null || query.isEmpty()) {
            return results;
        }

        String lowerQuery = query.toLowerCase();
        for (UserPrompt prompt : promptsCache.values()) {
            if (prompt.getPromptText().toLowerCase().contains(lowerQuery)) {
                results.add(prompt);
            }
        }
        return results;
    }

    /**
     * Загрузить все промпты из SharedPreferences
     */
    private void loadAllPrompts() {
        try {
            String jsonString = preferences.getString(KEY_PROMPTS, "{}");
            JSONObject root = new JSONObject(jsonString);

            promptsCache.clear();

            // Загружаем все промпты
            JSONArray promptsArray = root.optJSONArray("prompts");
            if (promptsArray != null) {
                for (int i = 0; i < promptsArray.length(); i++) {
                    JSONObject promptJson = promptsArray.getJSONObject(i);
                    UserPrompt prompt = UserPrompt.fromJson(promptJson);
                    promptsCache.put(prompt.getUserId(), prompt);
                }
            }

            // Поддерживаем старый формат для обратной совместимости
            if (promptsArray == null) {
                loadLegacyPrompts(root);
            }

        } catch (Exception e) {
            FileLog.e("UserPromptService load error: " + e.getMessage());
        }
    }

    /**
     * Загрузка в старом формате (для обратной совместимости)
     */
    private void loadLegacyPrompts(JSONObject root) {
        try {
            JSONArray userIds = root.names();
            if (userIds != null) {
                for (int i = 0; i < userIds.length(); i++) {
                    String userIdStr = userIds.getString(i);
                    try {
                        long userId = Long.parseLong(userIdStr);
                        String promptText = root.getString(userIdStr);
                        promptsCache.put(userId, new UserPrompt(userId, promptText));
                    } catch (NumberFormatException e) {
                        // Пропускаем некорректные ключи
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e("UserPromptService loadLegacy error: " + e.getMessage());
        }
    }

    /**
     * Сохранить все промпты в SharedPreferences
     */
    private void saveAllPrompts() {
        try {
            JSONObject root = new JSONObject();
            JSONArray promptsArray = new JSONArray();

            for (UserPrompt prompt : promptsCache.values()) {
                promptsArray.put(prompt.toJson());
            }

            root.put("prompts", promptsArray);
            preferences.edit().putString(KEY_PROMPTS, root.toString()).apply();

        } catch (Exception e) {
            FileLog.e("UserPromptService save error: " + e.getMessage());
        }
    }

    /**
     * Очистить все промпты
     */
    public void clearAllPrompts() {
        promptsCache.clear();
        preferences.edit().remove(KEY_PROMPTS).apply();

        NotificationCenter.getInstance(currentAccount).postNotificationName(
                NotificationCenter.userPromptCleared);
    }
}