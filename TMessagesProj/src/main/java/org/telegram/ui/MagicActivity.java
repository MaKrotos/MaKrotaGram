package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.AIServiceFactory;
import org.telegram.messenger.openAI.AISettings;
import org.telegram.messenger.openAI.AISettingsActivity;
import org.telegram.messenger.openAI.BaseAIService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;

public class MagicActivity extends BaseFragment {

    private ArrayList<MessageObject> selectedMessages;
    private String promptText;
    private BaseAIService aiService;
    private AISettings aiSettings;
    private LinearLayout suggestionsContainer;
    private TextView loadingTextView;
    private TextView errorTextView;
    private View noApiKeyView;
    private RadialProgressView progressView;
    private FrameLayout progressContainer;
    private TextView serviceInfoView;
    private View explanationView;
    private boolean isGenerating = false;

    public MagicActivity() {
        super();
        selectedMessages = new ArrayList<>();
        promptText = "";
        aiSettings = new AISettings();
        updateService();
    }

    private void updateService() {
        aiService = AIServiceFactory.createService(currentAccount);
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

        // Добавляем иконку настроек в экшн бар
        actionBar.createMenu().addItem(100, R.drawable.filled_profile_settings);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 100) {
                    openAISettings();
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

        // Заголовок с анимацией
        TextView titleView = new TextView(context);
        titleView.setText("✨ Магия AI ✨");
        titleView.setTextSize(24);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
        titleView.setAlpha(0f);
        titleView.setTranslationY(-AndroidUtilities.dp(20));
        contentLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Информация о выбранном сервисе
        serviceInfoView = new TextView(context);
        updateServiceInfo();
        serviceInfoView.setTextSize(13);
        serviceInfoView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        serviceInfoView.setGravity(Gravity.CENTER);
        serviceInfoView.setPadding(0, 0, 0, AndroidUtilities.dp(10));
        serviceInfoView.setAlpha(0f);
        serviceInfoView.setTranslationY(-AndroidUtilities.dp(20));
        contentLayout.addView(serviceInfoView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Информация о количестве выбранных сообщений
        TextView countView = new TextView(context);
        countView.setText(LocaleController.formatString("SelectedMessages", R.string.SelectedMessages, selectedMessages.size()));
        countView.setTextSize(14);
        countView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        countView.setGravity(Gravity.CENTER);
        countView.setPadding(0, 0, 0, AndroidUtilities.dp(5));
        countView.setAlpha(0f);
        countView.setTranslationY(-AndroidUtilities.dp(20));
        contentLayout.addView(countView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // Информация о промпте
        if (promptText != null && !promptText.isEmpty()) {
            FrameLayout promptContainer = new FrameLayout(context);
            promptContainer.setBackgroundDrawable(Theme.createRoundRectDrawable(
                    AndroidUtilities.dp(12),
                    Theme.getColor(Theme.key_windowBackgroundWhite)
            ));
            promptContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                    AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            LinearLayout.LayoutParams promptContainerParams = LayoutHelper.createLinear(
                    LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.CENTER, 20, 10, 20, 15
            );
            contentLayout.addView(promptContainer, promptContainerParams);

            TextView promptIcon = new TextView(context);
            promptIcon.setText("📝");
            promptIcon.setTextSize(16);
            promptContainer.addView(promptIcon, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            TextView promptView = new TextView(context);
            promptView.setText(promptText);
            promptView.setTextSize(14);
            promptView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            promptView.setMaxLines(3);
            promptView.setEllipsize(TextUtils.TruncateAt.END);
            promptView.setPadding(AndroidUtilities.dp(28), 0, 0, 0);
            promptContainer.addView(promptView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

            promptContainer.setAlpha(0f);
            promptContainer.setTranslationX(-AndroidUtilities.dp(20));
        }

        // View для отображения отсутствия API ключа
        noApiKeyView = createNoApiKeyView(context);
        contentLayout.addView(noApiKeyView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 20, 20, 0));
        noApiKeyView.setAlpha(0f);
        noApiKeyView.setScaleX(0.9f);
        noApiKeyView.setScaleY(0.9f);

        // Контейнер для прогресса
        progressContainer = new FrameLayout(context);
        progressContainer.setVisibility(View.GONE);
        contentLayout.addView(progressContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 100));

        // RadialProgressView для загрузки
        progressView = new RadialProgressView(context);
        progressView.setSize(AndroidUtilities.dp(40));
        progressView.setProgressColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        progressContainer.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        // TextView для загрузки
        loadingTextView = new TextView(context);
        loadingTextView.setText(LocaleController.getString("Thinking", R.string.Thinking));
        loadingTextView.setTextSize(15);
        loadingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        loadingTextView.setGravity(Gravity.CENTER);
        loadingTextView.setPadding(0, AndroidUtilities.dp(70), 0, 0);
        loadingTextView.setVisibility(View.GONE);
        progressContainer.addView(loadingTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // TextView для ошибок
        errorTextView = new TextView(context);
        errorTextView.setTextSize(15);
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

        // Запускаем анимацию появления
        animateViewsIn(titleView, serviceInfoView, countView,
                promptText != null && !promptText.isEmpty() ? ((FrameLayout) contentLayout.getChildAt(4)) : null);

        // Генерация будет запущена в onResume

        return fragmentView;
    }

    private void updateServiceInfo() {
        if (serviceInfoView != null) {
            String serviceName = aiSettings.getServiceName();
            String modelName = "Unknown";

            BaseAIService.AIModel model = aiService.getModelById(aiSettings.getCurrentModel());
            if (model != null) {
                modelName = model.displayName;
            }

            serviceInfoView.setText("⚡ " + serviceName + " • " + modelName);
        }
    }

    private void animateViewsIn(View titleView, View serviceInfo, View countView, View promptContainer) {
        // Анимация заголовка
        titleView.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Анимация информации о сервисе
        serviceInfo.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(300)
                .setStartDelay(50)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Анимация счетчика
        countView.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(300)
                .setStartDelay(100)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // Анимация промпт контейнера
        if (promptContainer != null) {
            promptContainer.animate()
                    .alpha(1f)
                    .translationX(0)
                    .setDuration(350)
                    .setStartDelay(200)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }

        // Анимация noApiKeyView (если видим)
        if (noApiKeyView.getVisibility() == View.VISIBLE) {
            noApiKeyView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(350)
                    .setStartDelay(250)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();
        }
    }

    private View createNoApiKeyView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(20),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        layout.setVisibility(aiSettings.hasValidConfig() ? View.GONE : View.VISIBLE);

        // Иконка с анимацией пульсации
        FrameLayout iconContainer = new FrameLayout(context);
        TextView iconView = new TextView(context);
        iconView.setText("🔑");
        iconView.setTextSize(32);
        iconContainer.addView(iconView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        layout.addView(iconContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 10));

        // Запускаем анимацию пульсации для иконки
        iconView.post(() -> {
            ObjectAnimator pulseAnim = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.1f, 1f);
            pulseAnim.setDuration(1500);
            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnim.start();

            ObjectAnimator pulseAnimY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.1f, 1f);
            pulseAnimY.setDuration(1500);
            pulseAnimY.setRepeatCount(ValueAnimator.INFINITE);
            pulseAnimY.setInterpolator(new AccelerateDecelerateInterpolator());
            pulseAnimY.start();
        });

        TextView titleView = new TextView(context);
        titleView.setText(LocaleController.getString("ApiKeyNotFound", R.string.ApiKeyNotFound));
        titleView.setTextSize(16);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setGravity(Gravity.CENTER);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        layout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView descView = new TextView(context);
        descView.setText(LocaleController.getString("ApiKeyNotFoundDesc", R.string.ApiKeyNotFoundDesc));
        descView.setTextSize(14);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setGravity(Gravity.CENTER);
        descView.setPadding(0, 0, 0, AndroidUtilities.dp(20));
        layout.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка перехода в настройки с анимацией нажатия
        FrameLayout settingsButton = new FrameLayout(context);
        settingsButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton)
        ));
        settingsButton.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
        );
        layout.addView(settingsButton, buttonParams);

        TextView buttonText = new TextView(context);
        buttonText.setText(LocaleController.getString("GoToSettings", R.string.GoToSettings));
        buttonText.setTextSize(15);
        buttonText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        settingsButton.addView(buttonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        settingsButton.setOnClickListener(v -> {
            // Анимация нажатия
            v.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction(() -> v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start())
                    .start();

            // Открываем настройки AI
            openAISettings();
        });

        return layout;
    }

    private void openAISettings() {
        AISettingsActivity settingsActivity = new AISettingsActivity();
        presentFragment(settingsActivity);
    }

    private void checkAndGenerateSuggestions() {
        if (isGenerating) {
            return;
        }
        if (!aiSettings.hasValidConfig()) {
            // Показываем сообщение об отсутствии ключа с анимацией
            noApiKeyView.setVisibility(View.VISIBLE);
            noApiKeyView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(350)
                    .setInterpolator(new OvershootInterpolator(1.2f))
                    .start();

            progressContainer.setVisibility(View.GONE);
            suggestionsContainer.setVisibility(View.GONE);
            errorTextView.setVisibility(View.GONE);
            return;
        }

        noApiKeyView.setVisibility(View.GONE);
        isGenerating = true;
        generateSuggestions();
    }

    private void generateSuggestions() {
        progressContainer.setVisibility(View.VISIBLE);
        progressContainer.setAlpha(0f);
        progressContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        loadingTextView.setVisibility(View.VISIBLE);
        suggestionsContainer.setVisibility(View.GONE);
        suggestionsContainer.setAlpha(0f);
        errorTextView.setVisibility(View.GONE);
        if (suggestionsContainer.getChildCount() == 0) {
            suggestionsContainer.removeAllViews();
        }

        // Анимация вращения прогресса
        progressView.setProgress(0f);
        ValueAnimator progressAnim = ValueAnimator.ofFloat(0f, 1f);
        progressAnim.setDuration(2000);
        progressAnim.setRepeatCount(ValueAnimator.INFINITE);
        progressAnim.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            progressView.setProgress(progress);
        });
        progressAnim.start();

        aiService.generateSuggestions(selectedMessages, promptText, new BaseAIService.Callback() {
            @Override
            public void onSuccess(JSONObject response) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressAnim.cancel();
                    handleSuccess(response);
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    progressAnim.cancel();
                    showError(error);
                });
            }
        });
    }

    private void handleSuccess(JSONObject response) {
        try {
            // Анимация скрытия прогресса
            progressContainer.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> progressContainer.setVisibility(View.GONE))
                    .start();

            suggestionsContainer.setVisibility(View.VISIBLE);
            suggestionsContainer.setAlpha(0f);
            suggestionsContainer.setTranslationY(AndroidUtilities.dp(30));

            // Добавляем предложения
            if (response.has("suggestions")) {
                JSONArray suggestions = response.getJSONArray("suggestions");
                // Вычисляем количество уже существующих предложений
                int existingCount = 0;
                for (int j = 0; j < suggestionsContainer.getChildCount(); j++) {
                    View child = suggestionsContainer.getChildAt(j);
                    if (child != explanationView) {
                        existingCount++;
                    }
                }
                for (int i = 0; i < suggestions.length(); i++) {
                    String text;
                    // Элемент может быть строкой или объектом
                    Object item = suggestions.get(i);
                    if (item instanceof String) {
                        text = (String) item;
                    } else if (item instanceof JSONObject) {
                        JSONObject suggestion = (JSONObject) item;
                        text = suggestion.optString("text", "");
                    } else {
                        continue;
                    }
                    if (text.isEmpty()) {
                        continue;
                    }
                    addSuggestionView(
                            text,
                            existingCount + i,
                            existingCount + suggestions.length()
                    );
                }
            }

            // Анимация появления контейнера с предложениями
            suggestionsContainer.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(300)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

        } catch (Exception e) {
            showError(LocaleController.getString("ResponseProcessingError", R.string.ResponseProcessingError) + ": " + e.getMessage());
        } finally {
            isGenerating = false;
        }
    }

    private void addExplanationView(String explanation) {
        Context context = getParentActivity();
        if (context == null) return;

        // Если explanation уже есть, не добавляем повторно
        if (explanationView != null && explanationView.getParent() == suggestionsContainer) {
            return;
        }

        LinearLayout explanationLayout = new LinearLayout(context);
        explanationLayout.setOrientation(LinearLayout.HORIZONTAL);
        explanationLayout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        explanationLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        LinearLayout.LayoutParams layoutParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, 8, 16, 8
        );
        suggestionsContainer.addView(explanationLayout, layoutParams);

        TextView emojiView = new TextView(context);
        emojiView.setText("💡");
        emojiView.setTextSize(20);
        explanationLayout.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        TextView explanationTextView = new TextView(context);
        explanationTextView.setText(explanation);
        explanationTextView.setTextSize(14);
        explanationTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        explanationTextView.setPadding(AndroidUtilities.dp(10), 0, 0, 0);
        explanationTextView.setMaxLines(3);
        explanationTextView.setEllipsize(TextUtils.TruncateAt.END);
        explanationLayout.addView(explanationTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        // Сохраняем ссылку на explanation view
        explanationView = explanationLayout;

        // Анимация появления
        explanationLayout.setAlpha(0f);
        explanationLayout.setTranslationX(-AndroidUtilities.dp(20));
        explanationLayout.animate()
                .alpha(1f)
                .translationX(0)
                .setDuration(300)
                .setStartDelay(100)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void addSuggestionView(String text, int index, int totalCount) {
        Context context = getParentActivity();
        if (context == null) return;

        LinearLayout suggestionCard = new LinearLayout(context);
        suggestionCard.setOrientation(LinearLayout.VERTICAL);
        suggestionCard.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        suggestionCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        LinearLayout.LayoutParams cardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, index == 0 ? 4 : 2, 16, 2
        );
        suggestionsContainer.addView(suggestionCard, cardParams);

        // Текст предложения
        TextView messageText = new TextView(context);
        messageText.setText(text);
        messageText.setTextSize(15);
        messageText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        messageText.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        messageText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        suggestionCard.addView(messageText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Разделитель
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams dividerParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 0, 0, 10);
        dividerParams.topMargin = AndroidUtilities.dp(4);
        suggestionCard.addView(divider, dividerParams);

        // Нижняя строка с кнопками
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setPadding(0, AndroidUtilities.dp(6), 0, 0);
        suggestionCard.addView(bottomRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка копирования
        FrameLayout copyButton = createActionButton(context, "📋", LocaleController.getString("Copy", R.string.Copy), Theme.getColor(Theme.key_featuredStickers_addButton));
        bottomRow.addView(copyButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 4, 0));

        copyButton.setOnClickListener(v -> {
            // Анимация нажатия
            animateButtonPress(copyButton);

            // Копирование текста
            android.content.ClipboardManager clipboard =
                    (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip =
                    android.content.ClipData.newPlainText("suggested_message", text);
            clipboard.setPrimaryClip(clip);



            // Анимация успешного копирования
            copyButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                    AndroidUtilities.dp(10),
                    0xFF4CAF50
            ));
            copyButton.postDelayed(() -> {
                copyButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                        AndroidUtilities.dp(10),
                        Theme.getColor(Theme.key_featuredStickers_addButton)
                ));
            }, 500);
        });

        // Кнопка "Использовать"
        FrameLayout useButton = createActionButton(context, "✏️", LocaleController.getString("Use", R.string.Use), Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        bottomRow.addView(useButton, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 0, 0));

        useButton.setOnClickListener(v -> {
            animateButtonPress(useButton);
            Toast.makeText(context, LocaleController.getString("FeatureComingSoon", R.string.FeatureComingSoon), Toast.LENGTH_SHORT).show();
        });

        // Анимация появления карточки
        suggestionCard.setAlpha(0f);
        suggestionCard.setTranslationY(AndroidUtilities.dp(30));
        suggestionCard.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(350)
                .setStartDelay(150 + index * 100)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();
    }

    private FrameLayout createActionButton(Context context, String emoji, String text, int backgroundColor) {
        FrameLayout button = new FrameLayout(context);
        button.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(10),
                backgroundColor
        ));
        button.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(10), AndroidUtilities.dp(8));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER);

        TextView emojiView = new TextView(context);
        emojiView.setText(emoji);
        emojiView.setTextSize(14);
        content.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(14);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        textView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        textView.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
        content.addView(textView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        button.addView(content, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        return button;
    }

    private void animateButtonPress(View button) {
        button.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction(() -> button.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start())
                .start();
    }

    private void showError(String error) {
        progressContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> progressContainer.setVisibility(View.GONE))
                .start();

        suggestionsContainer.setVisibility(View.GONE);
        errorTextView.setVisibility(View.VISIBLE);
        errorTextView.setText("❌ " + error);
        errorTextView.setAlpha(0f);
        errorTextView.setScaleX(0.8f);
        errorTextView.setScaleY(0.8f);

        errorTextView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(new OvershootInterpolator())
                .start();
        
        isGenerating = false;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Обновляем сервис при возвращении из настроек
        updateService();
        updateServiceInfo();
        // Перепроверяем наличие ключа
        if (!selectedMessages.isEmpty()) {
            checkAndGenerateSuggestions();
        }
    }

    public String getPromptText() {
        return promptText;
    }

    public ArrayList<MessageObject> getSelectedMessages() {
        return selectedMessages;
    }
}