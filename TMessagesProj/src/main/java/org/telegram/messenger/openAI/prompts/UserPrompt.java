package org.telegram.messenger.openAI;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

public class UserPrompt {
    private long userId;
    private String promptText;
    private long updatedTime;
    private String category;

    public UserPrompt() {
        this.userId = 0;
        this.promptText = "";
        this.updatedTime = System.currentTimeMillis();
        this.category = "default";
    }

    public UserPrompt(long userId, String promptText) {
        this.userId = userId;
        this.promptText = promptText;
        this.updatedTime = System.currentTimeMillis();
        this.category = "default";
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("userId", userId);
            json.put("promptText", promptText);
            json.put("updatedTime", updatedTime);
            json.put("category", category);
        } catch (JSONException e) {
            FileLog.e("UserPrompt toJson error: " + e.getMessage());
        }
        return json;
    }

    public static UserPrompt fromJson(JSONObject json) {
        UserPrompt prompt = new UserPrompt();
        try {
            prompt.userId = json.optLong("userId", 0);
            prompt.promptText = json.optString("promptText", "");
            prompt.updatedTime = json.optLong("updatedTime", System.currentTimeMillis());
            prompt.category = json.optString("category", "default");
        } catch (Exception e) {
            FileLog.e("UserPrompt fromJson error: " + e.getMessage());
        }
        return prompt;
    }

    // Геттеры и сеттеры
    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
        this.updatedTime = System.currentTimeMillis();
    }

    public long getUpdatedTime() {
        return updatedTime;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

}