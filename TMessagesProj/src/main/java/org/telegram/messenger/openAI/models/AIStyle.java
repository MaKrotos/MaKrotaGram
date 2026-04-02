package org.telegram.messenger.openAI.models;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;

public class AIStyle {
    private String id;
    private String name;
    private String prompt;
    private boolean isCustom;
    private int order;

    public AIStyle() {
        this.id = "";
        this.name = "";
        this.prompt = "";
        this.isCustom = false;
        this.order = 0;
    }

    public AIStyle(String id, String name, String prompt, boolean isCustom, int order) {
        this.id = id;
        this.name = name;
        this.prompt = prompt;
        this.isCustom = isCustom;
        this.order = order;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("prompt", prompt);
            json.put("isCustom", isCustom);
            json.put("order", order);
        } catch (JSONException e) {
            FileLog.e("AIStyle toJson error: " + e.getMessage());
        }
        return json;
    }

    public static AIStyle fromJson(JSONObject json) {
        AIStyle style = new AIStyle();
        try {
            style.id = json.optString("id", "");
            style.name = json.optString("name", "");
            style.prompt = json.optString("prompt", "");
            style.isCustom = json.optBoolean("isCustom", false);
            style.order = json.optInt("order", 0);
        } catch (Exception e) {
            FileLog.e("AIStyle fromJson error: " + e.getMessage());
        }
        return style;
    }

    // Геттеры и сеттеры
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setCustom(boolean custom) {
        isCustom = custom;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}