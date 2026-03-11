package org.telegram.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.openAI.OpenAIService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

public class MagicActivity extends BaseFragment {

    private ArrayList<MessageObject> selectedMessages;
    private String promptText;
    private OpenAIService openAIService;
    private LinearLayout suggestionsContainer;
    private TextView loadingTextView;
    private TextView errorTextView;
    private View noApiKeyView;

    public MagicActivity() {
        super();
        selectedMessages = new ArrayList<>();
        promptText = "";
        openAIService = new OpenAIService();
    }

    public void setSelectedMessages(ArrayList<MessageObject> messages) {
        this.selectedMessages = messages;
    }

    public void setPromptText(String prompt) {
        this.promptText = prompt != null ? prompt : "";
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("Magic", R.string.Magic));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // Основной контейнер с прокруткой
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        frameLayout.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20));
        scrollView.addView(contentLayout, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));

        // Заголовок
        TextView titleView = new TextView(context);
        titleView.setText("✨ Магия AI ✨");
        titleView.setTextSize(24);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, AndroidUtilities.dp(10));
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Информация о количестве выбранных сообщений
        TextView countView = new TextView(context);
        countView.setText("Выбрано сообщений: " + selectedMessages.size());
        countView.setTextSize(16);
        countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
        contentLayout.addView(countView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Информация о промпте
        if (promptText != null && !promptText.isEmpty()) {
            TextView promptView = new TextView(context);
            promptView.setText("📝 " + promptText);
            promptView.setTextSize(14);
            promptView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
            promptView.setGravity(Gravity.CENTER);
            promptView.setMaxLines(3);
            promptView.setEllipsize(TextUtils.TruncateAt.END);
            promptView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(15));
            contentLayout.addView(promptView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // View для отображения отсутствия API ключа
        noApiKeyView = createNoApiKeyView(context);
        contentLayout.addView(noApiKeyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 20, 20, 0));

        // TextView для загрузки
        loadingTextView = new TextView(context);
        loadingTextView.setText("🤔 Думаю...");
        loadingTextView.setTextSize(18);
        loadingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        loadingTextView.setGravity(Gravity.CENTER);
        loadingTextView.setPadding(0, AndroidUtilities.dp(30), 0, 0);
        loadingTextView.setVisibility(View.GONE);
        contentLayout.addView(loadingTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // TextView для ошибок
        errorTextView = new TextView(context);
        errorTextView.setTextSize(16);
        errorTextView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
        errorTextView.setGravity(Gravity.CENTER);
        errorTextView.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(30), AndroidUtilities.dp(20), 0);
        errorTextView.setVisibility(View.GONE);
        contentLayout.addView(errorTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Контейнер для предложений
        suggestionsContainer = new LinearLayout(context);
        suggestionsContainer.setOrientation(LinearLayout.VERTICAL);
        suggestionsContainer.setVisibility(View.GONE);
        contentLayout.addView(suggestionsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Запускаем генерацию при создании представления
        if (!selectedMessages.isEmpty()) {
            checkAndGenerateSuggestions();
        }

        return fragmentView;
    }

    private View createNoApiKeyView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        layout.setVisibility(openAIService.hasApiKey() ? View.GONE : View.VISIBLE);

        TextView titleView = new TextView(context);
        titleView.setText("🔑 API ключ не найден");
        titleView.setTextSize(18);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView descView = new TextView(context);
        descView.setText("Для использования магии AI необходимо добавить OpenAI API ключ в настройках.");
        descView.setTextSize(14);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setGravity(Gravity.CENTER);
        descView.setPadding(0, 0, 0, AndroidUtilities.dp(16));
        layout.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка перехода в настройки
        FrameLayout settingsButton = new FrameLayout(context);
        settingsButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton)
        ));
        settingsButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10),
                AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
        );
        layout.addView(settingsButton, buttonParams);

        TextView buttonText = new TextView(context);
        buttonText.setText("⚙️ Перейти в настройки");
        buttonText.setTextSize(14);
        buttonText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        settingsButton.addView(buttonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        settingsButton.setOnClickListener(v -> {
            // Закрываем текущий фрагмент и открываем настройки
            finishFragment();
            // TODO: Открыть нужный раздел настроек
            // Например: presentFragment(new SettingsActivity());
            Toast.makeText(context, "Откройте настройки и добавьте API ключ", Toast.LENGTH_LONG).show();
        });

        return layout;
    }

    private void checkAndGenerateSuggestions() {
        if (!openAIService.hasApiKey()) {
            // Показываем сообщение об отсутствии ключа
            noApiKeyView.setVisibility(View.VISIBLE);
            loadingTextView.setVisibility(View.GONE);
            suggestionsContainer.setVisibility(View.GONE);
            errorTextView.setVisibility(View.GONE);
            return;
        }

        noApiKeyView.setVisibility(View.GONE);
        generateSuggestions();
    }

    private void generateSuggestions() {
        loadingTextView.setVisibility(View.VISIBLE);
        suggestionsContainer.setVisibility(View.GONE);
        errorTextView.setVisibility(View.GONE);
        suggestionsContainer.removeAllViews();

        openAIService.generateSuggestions(selectedMessages, promptText, new OpenAIService.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        loadingTextView.setVisibility(View.GONE);
                        suggestionsContainer.setVisibility(View.VISIBLE);
                        errorTextView.setVisibility(View.GONE);

                        // Добавляем объяснение, если есть
                        if (response.has("explanation")) {
                            addExplanationView(response.getString("explanation"));
                        }

                        // Добавляем предложения
                        if (response.has("suggestions")) {
                            JSONArray suggestions = response.getJSONArray("suggestions");
                            for (int i = 0; i < suggestions.length(); i++) {
                                JSONObject suggestion = suggestions.getJSONObject(i);
                                addSuggestionView(
                                        suggestion.getString("text"),
                                        suggestion.getString("type"),
                                        suggestion.getDouble("confidence"),
                                        i
                                );
                            }
                        }

                    } catch (Exception e) {
                        showError("Ошибка при обработке ответа: " + e.getMessage());
                    }
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    showError(error);
                });
            }
        });
    }

    private void addExplanationView(String explanation) {
        Context context = getParentActivity();
        if (context == null) return;

        LinearLayout explanationLayout = new LinearLayout(context);
        explanationLayout.setOrientation(LinearLayout.HORIZONTAL);
        explanationLayout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        explanationLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout.LayoutParams layoutParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, 16, 16, 8
        );
        suggestionsContainer.addView(explanationLayout, layoutParams);

        TextView emojiView = new TextView(context);
        emojiView.setText("💡");
        emojiView.setTextSize(20);
        explanationLayout.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        TextView explanationView = new TextView(context);
        explanationView.setText(explanation);
        explanationView.setTextSize(14);
        explanationView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        explanationView.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        explanationView.setMaxLines(3);
        explanationView.setEllipsize(TextUtils.TruncateAt.END);
        explanationLayout.addView(explanationView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
    }

    private void addSuggestionView(String text, String type, double confidence, int index) {
        Context context = getParentActivity();
        if (context == null) return;

        LinearLayout suggestionCard = new LinearLayout(context);
        suggestionCard.setOrientation(LinearLayout.VERTICAL);
        suggestionCard.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        suggestionCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        LinearLayout.LayoutParams cardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, index == 0 ? 8 : 4, 16, 4
        );
        suggestionsContainer.addView(suggestionCard, cardParams);

        // Верхняя строка с типом и уверенностью
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        suggestionCard.addView(topRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        String typeEmoji;
        String typeText;
        switch (type) {
            case "question":
                typeEmoji = "❓";
                typeText = "Вопрос";
                break;
            case "continuation":
                typeEmoji = "➡️";
                typeText = "Продолжение";
                break;
            default:
                typeEmoji = "💬";
                typeText = "Ответ";
        }

        TextView typeView = new TextView(context);
        typeView.setText(typeEmoji + " " + typeText);
        typeView.setTextSize(14);
        typeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        typeView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        topRow.addView(typeView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView confidenceView = new TextView(context);
        confidenceView.setText((int)(confidence * 100) + "%");
        confidenceView.setTextSize(14);
        confidenceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        topRow.addView(confidenceView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Текст предложения
        TextView messageText = new TextView(context);
        messageText.setText(text);
        messageText.setTextSize(16);
        messageText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        messageText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        suggestionCard.addView(messageText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Нижняя строка с кнопками
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, AndroidUtilities.dp(4), 0, 0);
        suggestionCard.addView(bottomRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка копирования
        FrameLayout copyButton = new FrameLayout(context);
        copyButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_featuredStickers_addButton)
        ));
        copyButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        LinearLayout.LayoutParams copyButtonParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.LEFT | Gravity.CENTER_VERTICAL
        );
        copyButtonParams.rightMargin = AndroidUtilities.dp(8);
        bottomRow.addView(copyButton, copyButtonParams);

        TextView copyText = new TextView(context);
        copyText.setText("📋 Копировать");
        copyText.setTextSize(14);
        copyText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        copyText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        copyButton.addView(copyText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        copyButton.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip =
                    android.content.ClipData.newPlainText("suggested_message", text);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show();

            // Визуальная обратная связь
            copyButton.setAlpha(0.7f);
            copyButton.postDelayed(() -> copyButton.setAlpha(1.0f), 200);
        });

        // Кнопка "Использовать" (можно будет потом реализовать отправку сообщения)
        FrameLayout useButton = new FrameLayout(context);
        useButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(8),
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText)
        ));
        useButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        bottomRow.addView(useButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        TextView useText = new TextView(context);
        useText.setText("✏️ Использовать");
        useText.setTextSize(14);
        useText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        useText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        useButton.addView(useText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        useButton.setOnClickListener(v -> {
            Toast.makeText(context, "Функция будет доступна позже", Toast.LENGTH_SHORT).show();
            // TODO: Реализовать вставку сообщения в поле ввода
        });
    }

    private void showError(String error) {
        loadingTextView.setVisibility(View.GONE);
        suggestionsContainer.setVisibility(View.GONE);
        errorTextView.setVisibility(View.VISIBLE);
        errorTextView.setText("❌ " + error);
    }

    public String getPromptText() {
        return promptText;
    }

    public ArrayList<MessageObject> getSelectedMessages() {
        return selectedMessages;
    }
}