package org.telegram.messenger.openAI;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.models.AIStyle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AIStyleService {
    private static final String PREFS_NAME = "ai_styles";
    private static final String KEY_STYLES = "styles_data";
    private static final String KEY_SELECTED_STYLE_PREFIX = "selected_style_";

    private static volatile AIStyleService instance;
    private final SharedPreferences preferences;
    private final List<AIStyle> stylesCache = new ArrayList<>();

    private AIStyleService() {
        preferences = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadStyles();
        ensureDefaultStyles();
    }

    public static AIStyleService getInstance() {
        AIStyleService localInstance = instance;
        if (localInstance == null) {
            synchronized (AIStyleService.class) {
                localInstance = instance;
                if (localInstance == null) {
                    instance = localInstance = new AIStyleService();
                }
            }
        }
        return localInstance;
    }

    private void loadStyles() {
        try {
            String jsonString = preferences.getString(KEY_STYLES, "{}");
            JSONObject root = new JSONObject(jsonString);
            JSONArray stylesArray = root.optJSONArray("styles");
            stylesCache.clear();
            if (stylesArray != null) {
                for (int i = 0; i < stylesArray.length(); i++) {
                    JSONObject styleJson = stylesArray.getJSONObject(i);
                    AIStyle style = AIStyle.fromJson(styleJson);
                    stylesCache.add(style);
                }
            }
        } catch (Exception e) {
            FileLog.e("AIStyleService load error: " + e.getMessage());
        }
    }

    private void saveStyles() {
        try {
            JSONObject root = new JSONObject();
            JSONArray stylesArray = new JSONArray();
            for (AIStyle style : stylesCache) {
                stylesArray.put(style.toJson());
            }
            root.put("styles", stylesArray);
            preferences.edit().putString(KEY_STYLES, root.toString()).apply();
        } catch (Exception e) {
            FileLog.e("AIStyleService save error: " + e.getMessage());
        }
    }

    private void ensureDefaultStyles() {
        boolean hasOfficial = false;
        boolean hasFriendly = false;
        boolean hasCreative = false;
        boolean hasHumorous = false;
        for (AIStyle style : stylesCache) {
            if ("official".equals(style.getId())) hasOfficial = true;
            if ("friendly".equals(style.getId())) hasFriendly = true;
            if ("creative".equals(style.getId())) hasCreative = true;
            if ("humorous".equals(style.getId())) hasHumorous = true;
        }
        if (!hasOfficial) {
            addStyle(new AIStyle("official", "Официальный",
                    "Отвечай в официально-деловом стиле. Используй вежливые формулировки, избегай сленга и эмоций.", false, 0));
        }
        if (!hasFriendly) {
            addStyle(new AIStyle("friendly", "Дружеский",
                    "Отвечай в дружеском, неформальном тоне. Можно использовать смайлики, шутки, обращаться на \"ты\".", false, 1));
        }
        if (!hasCreative) {
            addStyle(new AIStyle("creative", "Креативный",
                    "Будь креативным, предлагай нестандартные варианты. Можно использовать метафоры, интересные формулировки.", false, 2));
        }
        if (!hasHumorous) {
            addStyle(new AIStyle("humorous", "Юмористический",
                    "Добавь юмора, иронии, сарказма (уместного). Ответ должен вызывать улыбку.", false, 3));
        }
        // Сортируем по order
        Collections.sort(stylesCache, Comparator.comparingInt(AIStyle::getOrder));
    }

    public List<AIStyle> getAllStyles() {
        return new ArrayList<>(stylesCache);
    }

    public List<AIStyle> getCustomStyles() {
        List<AIStyle> custom = new ArrayList<>();
        for (AIStyle style : stylesCache) {
            if (style.isCustom()) {
                custom.add(style);
            }
        }
        return custom;
    }

    public List<AIStyle> getBuiltInStyles() {
        List<AIStyle> builtIn = new ArrayList<>();
        for (AIStyle style : stylesCache) {
            if (!style.isCustom()) {
                builtIn.add(style);
            }
        }
        return builtIn;
    }

    public AIStyle getStyleById(String id) {
        for (AIStyle style : stylesCache) {
            if (style.getId().equals(id)) {
                return style;
            }
        }
        return null;
    }

    public void addStyle(AIStyle style) {
        stylesCache.add(style);
        Collections.sort(stylesCache, Comparator.comparingInt(AIStyle::getOrder));
        saveStyles();
    }

    public void updateStyle(AIStyle style) {
        for (int i = 0; i < stylesCache.size(); i++) {
            if (stylesCache.get(i).getId().equals(style.getId())) {
                stylesCache.set(i, style);
                saveStyles();
                return;
            }
        }
    }

    public void deleteStyle(String id) {
        for (int i = 0; i < stylesCache.size(); i++) {
            if (stylesCache.get(i).getId().equals(id)) {
                stylesCache.remove(i);
                saveStyles();
                return;
            }
        }
    }

    public String getSelectedStyleId(int account) {
        return preferences.getString(KEY_SELECTED_STYLE_PREFIX + account, "official"); // по умолчанию официальный
    }

    public void setSelectedStyleId(int account, String styleId) {
        preferences.edit().putString(KEY_SELECTED_STYLE_PREFIX + account, styleId).apply();
    }

    public AIStyle getSelectedStyle(int account) {
        String id = getSelectedStyleId(account);
        AIStyle style = getStyleById(id);
        if (style == null) {
            // fallback на первый встроенный стиль
            List<AIStyle> builtIn = getBuiltInStyles();
            if (!builtIn.isEmpty()) {
                style = builtIn.get(0);
                setSelectedStyleId(account, style.getId());
            }
        }
        return style;
    }

    public void setSelectedStyle(int account, AIStyle style) {
        setSelectedStyleId(account, style.getId());
    }

    public List<AIStyle> getPresetStyles() {
        return getBuiltInStyles();
    }

    public void addCustomStyle(AIStyle style) {
        // Генерируем уникальный ID, если не задан
        if (style.getId() == null) {
            style.setId("custom_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000));
        }
        style.setCustom(true);
        // Устанавливаем порядок после всех пользовательских стилей
        int maxOrder = 0;
        for (AIStyle s : stylesCache) {
            if (s.isCustom() && s.getOrder() > maxOrder) {
                maxOrder = s.getOrder();
            }
        }
        style.setOrder(maxOrder + 1);
        addStyle(style);
    }

    public void updateCustomStyle(AIStyle style) {
        if (!style.isCustom()) {
            return;
        }
        updateStyle(style);
    }

    public void deleteCustomStyle(AIStyle style) {
        if (!style.isCustom()) {
            return;
        }
        deleteStyle(style.getId());
    }
}