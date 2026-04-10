package org.telegram.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;

import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
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
import org.telegram.messenger.openAI.AIStyleService;
import org.telegram.messenger.openAI.models.AIStyle;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;

import java.util.ArrayList;
import java.util.List;

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
    private boolean hasGenerated = false;
    private TextView tokensProgressView;
    private String selectedStyleId = null;
    private LinearLayout styleChipsContainer;
    private FrameLayout generateButton;
    private boolean userRequestedGeneration = false;
    private boolean engineLoading = false;
    private ValueAnimator engineProgressAnimator = null;
    private boolean generateAfterAnalysis = false;

    // Новые поля для анализа диалога
    private android.widget.EditText userQuestionEditText;
    private TextView analysisResultTextView; // сохраняем для обратной совместимости, но будем скрывать
    private LinearLayout analysisContainer;
    private FrameLayout analyzeButton;
    private java.util.List<BaseAIService.ChatMessage> chatHistory;
    
    // Поля для чат-интерфейса
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;
    private TextView currentBotMessageView; // текущее сообщение бота для потокового обновления

    // Интерфейс для передачи текста предложения обратно в ChatActivity
    public interface OnUseSuggestionListener {
        void onUseSuggestion(String text);
    }
    private OnUseSuggestionListener onUseSuggestionListener;

    public void setOnUseSuggestionListener(OnUseSuggestionListener listener) {
        this.onUseSuggestionListener = listener;
    }

    public MagicActivity() {
        super();
        selectedMessages = new ArrayList<>();
        promptText = "";
        aiSettings = new AISettings();
        chatHistory = new java.util.ArrayList<>();
        updateService();
    }

    private void updateService() {
        aiService = AIServiceFactory.createService(currentAccount);
        hasGenerated = false;
        loadSelectedStyleId();
        // Устанавливаем слушатель загрузки движка (если сервис поддерживает)
        if (aiService != null) {
            aiService.setEngineLoadingListener(new BaseAIService.EngineLoadingListener() {
                @Override
                public void onEngineLoadingStarted() {
                    setEngineLoading(true, "Загрузка движка...");
                }

                @Override
                public void onEngineLoadingProgress(float progress) {
                    updateEngineLoadingProgress(progress);
                }

                @Override
                public void onEngineLoadingFinished() {
                    setEngineLoading(false, null);
                }

                @Override
                public void onEngineLoadingError(String error) {
                    showEngineLoadingError(error);
                }
            });
        }
    }

    private void loadSelectedStyleId() {
        selectedStyleId = AIStyleService.getInstance().getSelectedStyleId(currentAccount);
    }

    private void createStyleChips(Context context) {
        styleChipsContainer.removeAllViews();
        List<AIStyle> styles = AIStyleService.getInstance().getAllStyles();
        for (AIStyle style : styles) {
            // Создаём кастомный TextView вместо Chip
            TextView chip = new TextView(context);
            chip.setText(style.getName());
            chip.setTextSize(14);
            chip.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chip.setTag(style.getId()); // Сохраняем id стиля в теге
            boolean selected = style.getId().equals(selectedStyleId);
            // Начальные цвета
            int backgroundColor = selected ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_windowBackgroundWhite);
            int textColor = selected ? Theme.getColor(Theme.key_featuredStickers_buttonText) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            int strokeColor = Theme.getColor(Theme.key_featuredStickers_addButton);
            // Создаём фон с закруглёнными углами и обводкой
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.RECTANGLE);
            background.setCornerRadius(AndroidUtilities.dp(20));
            background.setColor(backgroundColor);
            background.setStroke(AndroidUtilities.dp(1), strokeColor);
            chip.setBackground(background);
            chip.setTextColor(textColor);
            chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            chip.setId(View.generateViewId());
            // Добавляем margins между chips
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, AndroidUtilities.dp(4), 0);
            styleChipsContainer.addView(chip, params);

            chip.setOnClickListener(v -> {
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

                // Обновляем выбранный стиль
                selectedStyleId = style.getId();
                AIStyleService.getInstance().setSelectedStyleId(currentAccount, selectedStyleId);
                // Анимируем изменение цвета всех chips
                updateChipColorsWithAnimation(styleChipsContainer, selectedStyleId);
            });
        }
    }

    private void updateChipColorsWithAnimation(ViewGroup chipContainer, String selectedStyleId) {
        for (int i = 0; i < chipContainer.getChildCount(); i++) {
            View child = chipContainer.getChildAt(i);
            if (child instanceof TextView) {
                TextView chip = (TextView) child;
                String styleId = (String) chip.getTag();
                boolean selected = styleId != null && styleId.equals(selectedStyleId);
                int targetBackground = selected ? Theme.getColor(Theme.key_featuredStickers_addButton) : Theme.getColor(Theme.key_windowBackgroundWhite);
                int targetText = selected ? Theme.getColor(Theme.key_featuredStickers_buttonText) : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
                // Анимация цвета
                animateChipColor(chip, targetBackground, targetText);
            }
        }
    }

    private void animateChipColor(TextView chip, int targetBackgroundColor, int targetTextColor) {
        GradientDrawable background = (GradientDrawable) chip.getBackground();
        int startBackground = background.getColor() != null ? background.getColor().getDefaultColor() : Theme.getColor(Theme.key_windowBackgroundWhite);
        int startText = chip.getTextColors().getDefaultColor();
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(200);
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            int bgColor = blendColors(startBackground, targetBackgroundColor, fraction);
            int textColor = blendColors(startText, targetTextColor, fraction);
            background.setColor(bgColor);
            chip.setTextColor(textColor);
        });
        animator.start();
    }

    private int blendColors(int startColor, int endColor, float fraction) {
        int startA = (startColor >> 24) & 0xff;
        int startR = (startColor >> 16) & 0xff;
        int startG = (startColor >> 8) & 0xff;
        int startB = startColor & 0xff;
        int endA = (endColor >> 24) & 0xff;
        int endR = (endColor >> 16) & 0xff;
        int endG = (endColor >> 8) & 0xff;
        int endB = endColor & 0xff;
        int a = (int) (startA + (endA - startA) * fraction);
        int r = (int) (startR + (endR - startR) * fraction);
        int g = (int) (startG + (endG - startG) * fraction);
        int b = (int) (startB + (endB - startB) * fraction);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void setSelectedMessages(ArrayList<MessageObject> messages) {
        this.selectedMessages = messages;
        hasGenerated = false;
    }

    public void setPromptText(String prompt) {
        this.promptText = prompt != null ? prompt : "";
        hasGenerated = false;
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

        // Контейнер для выбора стилей (chips)
        LinearLayout styleContainer = new LinearLayout(context);
        styleContainer.setOrientation(LinearLayout.VERTICAL);
        styleContainer.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        styleContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        contentLayout.addView(styleContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 8, 12, 8, 12));

        TextView styleLabel = new TextView(context);
        styleLabel.setText("Стиль ответа:");
        styleLabel.setTextSize(15);
        styleLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        styleLabel.setGravity(Gravity.START);
        styleLabel.setPadding(0, 0, 0, AndroidUtilities.dp(8));
        styleContainer.addView(styleLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        HorizontalScrollView horizontalScrollView = new HorizontalScrollView(context);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        styleChipsContainer = new LinearLayout(context);
        styleChipsContainer.setOrientation(LinearLayout.HORIZONTAL);
        styleChipsContainer.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        horizontalScrollView.addView(styleChipsContainer, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));
        styleContainer.addView(horizontalScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Заполняем chips
        createStyleChips(context);

        // Раздел анализа диалога
        analysisContainer = new LinearLayout(context);
        analysisContainer.setOrientation(LinearLayout.VERTICAL);
        analysisContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        analysisContainer.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(16),
                Theme.getColor(Theme.key_windowBackgroundWhite)
        ));
        LinearLayout.LayoutParams analysisParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, 12, 16, 12
        );
        contentLayout.addView(analysisContainer, analysisParams);

        TextView analysisLabel = new TextView(context);
        analysisLabel.setText("Анализ диалога");
        analysisLabel.setTextSize(16);
        analysisLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        analysisLabel.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        analysisLabel.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        analysisContainer.addView(analysisLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        userQuestionEditText = new android.widget.EditText(context);
        userQuestionEditText.setHint("Задайте вопрос о диалоге...");
        userQuestionEditText.setTextSize(14);
        userQuestionEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        userQuestionEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        userQuestionEditText.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_windowBackgroundGray)
        ));
        userQuestionEditText.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        userQuestionEditText.setSingleLine(false);
        userQuestionEditText.setMaxLines(3);
        analysisContainer.addView(userQuestionEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        analyzeButton = new FrameLayout(context);
        analyzeButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton)
        ));
        analyzeButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        LinearLayout.LayoutParams analyzeButtonParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER
        );
        analysisContainer.addView(analyzeButton, analyzeButtonParams);

        LinearLayout analyzeButtonContent = new LinearLayout(context);
        analyzeButtonContent.setOrientation(LinearLayout.HORIZONTAL);
        analyzeButtonContent.setGravity(Gravity.CENTER);

        TextView analyzeIcon = new TextView(context);
        analyzeIcon.setText("🔍");
        analyzeIcon.setTextSize(16);
        analyzeButtonContent.addView(analyzeIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView analyzeText = new TextView(context);
        analyzeText.setText("Анализировать диалог");
        analyzeText.setTextSize(14);
        analyzeText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        analyzeText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        analyzeText.setPadding(AndroidUtilities.dp(6), 0, 0, 0);
        analyzeButtonContent.addView(analyzeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        analyzeButton.addView(analyzeButtonContent, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        analyzeButton.setOnClickListener(v -> {
            animateButtonPress(analyzeButton);
            performAnalysis();
        });

        // Контейнер истории чата (заменяет analysisResultTextView)
        chatScrollView = new ScrollView(context);
        chatScrollView.setVerticalScrollBarEnabled(false);
        chatScrollView.setFillViewport(true);
        LinearLayout.LayoutParams scrollParams = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        scrollParams.topMargin = AndroidUtilities.dp(16);
        scrollParams.bottomMargin = AndroidUtilities.dp(8);
        chatScrollView.setLayoutParams(scrollParams);
        
        chatMessagesContainer = new LinearLayout(context);
        chatMessagesContainer.setOrientation(LinearLayout.VERTICAL);
        chatMessagesContainer.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        chatScrollView.addView(chatMessagesContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        ));
        analysisContainer.addView(chatScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        
        // Старый TextView оставляем скрытым для обратной совместимости
        analysisResultTextView = new TextView(context);
        analysisResultTextView.setTextSize(14);
        analysisResultTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        analysisResultTextView.setPadding(0, AndroidUtilities.dp(16), 0, 0);
        analysisResultTextView.setVisibility(View.GONE);
        analysisContainer.addView(analysisResultTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Кнопка генерации
        generateButton = new FrameLayout(context);
        generateButton.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton)
        ));
        generateButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        LinearLayout.LayoutParams buttonParams = LayoutHelper.createLinear(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 0, 16, 0, 16
        );
        contentLayout.addView(generateButton, buttonParams);

        LinearLayout buttonContent = new LinearLayout(context);
        buttonContent.setOrientation(LinearLayout.HORIZONTAL);
        buttonContent.setGravity(Gravity.CENTER);

        TextView buttonIcon = new TextView(context);
        buttonIcon.setText("✨");
        buttonIcon.setTextSize(18);
        buttonContent.addView(buttonIcon, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView buttonText = new TextView(context);
        buttonText.setText("Сгенерировать предложения");
        buttonText.setTextSize(16);
        buttonText.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        buttonText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        buttonText.setPadding(AndroidUtilities.dp(8), 0, 0, 0);
        buttonContent.addView(buttonText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        generateButton.addView(buttonContent, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        generateButton.setOnClickListener(v -> {
            animateButtonPress(generateButton);
            userRequestedGeneration = true;
            checkAndGenerateSuggestions();
        });

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

        // TextView для прогресса токенов
        tokensProgressView = new TextView(context);
        tokensProgressView.setTextSize(13);
        tokensProgressView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        tokensProgressView.setGravity(Gravity.CENTER);
        tokensProgressView.setPadding(0, AndroidUtilities.dp(110), 0, 0);
        tokensProgressView.setVisibility(View.GONE);
        progressContainer.addView(tokensProgressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

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

    /**
     * Добавляет сообщение пользователя в историю чата (пузырёк справа).
     */
    private void addUserMessage(String text) {
        if (chatMessagesContainer == null || getParentActivity() == null) return;
        Context context = getParentActivity();
        
        FrameLayout messageLayout = new FrameLayout(context);
        messageLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        
        LinearLayout bubbleLayout = new LinearLayout(context);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_chat_outBubble)
        ));
        bubbleLayout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.END;
        bubbleLayout.setLayoutParams(bubbleParams);
        
        TextView messageText = new TextView(context);
        messageText.setText(text);
        messageText.setTextSize(14);
        messageText.setTextColor(Theme.getColor(Theme.key_chat_messageTextOut));
        messageText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        bubbleLayout.addView(messageText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        
        messageLayout.addView(bubbleLayout);
        chatMessagesContainer.addView(messageLayout);
        
        // Анимация появления
        bubbleLayout.setAlpha(0f);
        bubbleLayout.setScaleX(0.8f);
        bubbleLayout.setScaleY(0.8f);
        bubbleLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator())
                .start();
        
        scrollToBottom();
    }

    /**
     * Добавляет сообщение бота в историю чата (пузырёк слева) и возвращает TextView для обновления текста.
     */
    private TextView addBotMessage(String initialText) {
        if (chatMessagesContainer == null || getParentActivity() == null) return null;
        Context context = getParentActivity();
        
        FrameLayout messageLayout = new FrameLayout(context);
        messageLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        messageLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        
        LinearLayout bubbleLayout = new LinearLayout(context);
        bubbleLayout.setOrientation(LinearLayout.VERTICAL);
        bubbleLayout.setBackgroundDrawable(Theme.createRoundRectDrawable(
                AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_chat_inBubble)
        ));
        bubbleLayout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        bubbleParams.gravity = Gravity.START;
        bubbleLayout.setLayoutParams(bubbleParams);
        
        TextView messageText = new TextView(context);
        messageText.setText(initialText);
        messageText.setTextSize(14);
        messageText.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
        messageText.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        bubbleLayout.addView(messageText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        
        messageLayout.addView(bubbleLayout);
        chatMessagesContainer.addView(messageLayout);
        
        // Анимация появления
        bubbleLayout.setAlpha(0f);
        bubbleLayout.setScaleX(0.8f);
        bubbleLayout.setScaleY(0.8f);
        bubbleLayout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(new OvershootInterpolator())
                .start();
        
        scrollToBottom();
        return messageText;
    }

    /**
     * Прокручивает контейнер чата вниз.
     */
    private void scrollToBottom() {
        if (chatScrollView != null) {
            chatScrollView.post(() -> {
                chatScrollView.fullScroll(View.FOCUS_DOWN);
            });
        }
    }

    /**
     * Устанавливает состояние загрузки движка и обновляет UI.
     */
    private void setEngineLoading(boolean loading, String message) {
        if (engineLoading == loading && message == null) {
            return;
        }
        engineLoading = loading;
        AndroidUtilities.runOnUIThread(() -> {
            if (loading) {
                // Показываем прогресс-контейнер, если он скрыт
                progressContainer.setVisibility(View.VISIBLE);
                progressContainer.setAlpha(1f);
                loadingTextView.setVisibility(View.VISIBLE);
                loadingTextView.setText(message != null ? message : "Загрузка движка... 0%");
                tokensProgressView.setVisibility(View.GONE);
                // Запускаем анимацию прогресса (индетерминантную)
                if (engineProgressAnimator != null) {
                    engineProgressAnimator.cancel();
                }
                engineProgressAnimator = ValueAnimator.ofFloat(0f, 1f);
                engineProgressAnimator.setDuration(2000);
                engineProgressAnimator.setRepeatCount(ValueAnimator.INFINITE);
                engineProgressAnimator.addUpdateListener(animation -> {
                    float progress = (float) animation.getAnimatedValue();
                    progressView.setProgress(progress);
                });
                engineProgressAnimator.start();
            } else {
                // Останавливаем анимацию
                if (engineProgressAnimator != null) {
                    engineProgressAnimator.cancel();
                    engineProgressAnimator = null;
                }
                // Показываем сообщение об успешной загрузке и плавно скрываем прогресс-контейнер
                showEngineLoadedSuccess();
            }
        });
    }

    /**
     * Обновляет прогресс загрузки движка (0..1).
     */
    private void updateEngineLoadingProgress(float progress) {
        AndroidUtilities.runOnUIThread(() -> {
            if (engineLoading && progressView != null) {
                progressView.setProgress(progress);
                if (loadingTextView != null) {
                    int percent = (int) (progress * 100);
                    loadingTextView.setText("Загрузка движка... " + percent + "%");
                }
            }
        });
    }

    /**
     * Показывает ошибку загрузки движка.
     */
    private void showEngineLoadingError(String error) {
        AndroidUtilities.runOnUIThread(() -> {
            setEngineLoading(false, null);
            // Можно показать тост или обновить errorTextView
            if (errorTextView != null) {
                errorTextView.setVisibility(View.VISIBLE);
                errorTextView.setText("❌ Ошибка загрузки движка: " + error);
                errorTextView.setAlpha(0f);
                errorTextView.animate().alpha(1f).setDuration(300).start();
            }
        });
    }

    /**
     * Показывает сообщение об успешной загрузке движка и модели.
     */
    private void showEngineLoadedSuccess() {
        AndroidUtilities.runOnUIThread(() -> {
            // Определяем, является ли модель локальной
            boolean isLocalModel = aiSettings.getSelectedServiceType() == AISettings.AIServiceType.LOCAL_AI;
            String successMessage;
            if (isLocalModel) {
                successMessage = "✅ Движок и модель успешно загружены!";
            } else {
                successMessage = "✅ Движок успешно загружен!";
            }
            
            // Обновляем текст загрузки на успешное сообщение
            if (loadingTextView != null) {
                loadingTextView.setText(successMessage);
                loadingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGreenText));
            }
            
            // Плавно скрываем прогресс-контейнер через 1.5 секунды
            progressContainer.postDelayed(() -> {
                if (progressContainer != null) {
                    progressContainer.animate()
                            .alpha(0f)
                            .setDuration(500)
                            .setInterpolator(new DecelerateInterpolator())
                            .withEndAction(() -> {
                                if (progressContainer != null) {
                                    progressContainer.setVisibility(View.GONE);
                                    // Восстанавливаем цвет текста загрузки
                                    if (loadingTextView != null) {
                                        loadingTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                                    }
                                }
                            })
                            .start();
                }
            }, 1500);
        });
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

    private void updateTokensProgress() {
        if (tokensProgressView == null || aiService == null) return;
        int maxTokens = aiService.getMaxTokens();
        int generated = aiService.getGeneratedTokens();
        String text;
        if (maxTokens <= 0) {
            text = "Токены: неизвестно";
        } else if (generated >= 0) {
            text = "Токены: " + generated + " / " + maxTokens;
        } else {
            text = "Макс токенов: " + maxTokens;
        }
        tokensProgressView.setText(text);
    }

    private void openAISettings() {
        AISettingsActivity settingsActivity = new AISettingsActivity();
        presentFragment(settingsActivity);
    }

    private void checkAndGenerateSuggestions() {
        if (isGenerating) {
            return;
        }
        if (hasGenerated) {
            // Уже сгенерировано для текущих сообщений и промпта
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
        generateSuggestionsWithHistory(null);
    }

    private void generateSuggestionsWithHistory(List<BaseAIService.ChatMessage> chatHistory) {
        progressContainer.setVisibility(View.VISIBLE);
        progressContainer.setAlpha(0f);
        progressContainer.animate()
                .alpha(1f)
                .setDuration(200)
                .start();

        loadingTextView.setVisibility(View.VISIBLE);
        tokensProgressView.setVisibility(View.VISIBLE);
        updateTokensProgress();
        suggestionsContainer.setVisibility(View.GONE);
        suggestionsContainer.setAlpha(0f);
        errorTextView.setVisibility(View.GONE);
        suggestionsContainer.removeAllViews();
        explanationView = null;

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

        aiService.generateSuggestions(selectedMessages, promptText, selectedStyleId, chatHistory, new BaseAIService.Callback() {
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
            hasGenerated = true;
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
        explanationLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

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
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        LinearLayout.LayoutParams cardParams = LayoutHelper.createLinear(
                LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER, 16, 8, 16, 8
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
            if (onUseSuggestionListener != null) {
                onUseSuggestionListener.onUseSuggestion(text);
                // Закрываем фрагмент после использования предложения
                finishFragment();
            } else {
                // Fallback: показать тост, если колбэк не установлен (для отладки)
                Toast.makeText(context, LocaleController.getString("FeatureComingSoon", R.string.FeatureComingSoon), Toast.LENGTH_SHORT).show();
            }
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
        hasGenerated = false;
    }

    private void performAnalysis() {
        if (isGenerating) {
            return;
        }
        String question = userQuestionEditText.getText().toString().trim();
        if (question.isEmpty()) {
            Toast.makeText(getParentActivity(), "Введите вопрос", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!aiSettings.hasValidConfig()) {
            Toast.makeText(getParentActivity(), "Настройте API ключ", Toast.LENGTH_SHORT).show();
            return;
        }

        // Добавляем вопрос в историю
        chatHistory.add(new BaseAIService.ChatMessage("user", question));
        // Очищаем поле ввода
        AndroidUtilities.runOnUIThread(() -> userQuestionEditText.setText(""));

        // Добавляем пузырёк пользователя
        addUserMessage(question);

        // Добавляем пузырёк бота с индикатором загрузки
        currentBotMessageView = addBotMessage("Анализирую...");
        analyzeButton.setEnabled(false);

        isGenerating = true;

        // Вызываем анализ с поддержкой стриминга и историей чата
        aiService.analyzeConversationStreaming(selectedMessages, question, null, chatHistory, new BaseAIService.StreamCallback() {
            private final StringBuilder accumulated = new StringBuilder();

            @Override
            public void onChunk(String chunk) {
                AndroidUtilities.runOnUIThread(() -> {
                    // Игнорируем пустые чанки
                    if (chunk == null || chunk.trim().isEmpty()) {
                        return;
                    }
                    // Всегда добавляем чанк в накопленный текст
                    accumulated.append(chunk);
                    // Проверяем наличие команды [GENERATE] в накопленном тексте
                    String accumulatedStr = accumulated.toString();
                    if (accumulatedStr.contains("[GENERATE]")) {
                        generateAfterAnalysis = true;
                        // Удаляем маркер из текста, чтобы не отображать пользователю
                        String cleaned = accumulatedStr.replace("[GENERATE]", "").trim();
                        accumulated.setLength(0);
                        accumulated.append(cleaned);
                        accumulatedStr = cleaned;
                    }
                    // Обновляем текст текущего сообщения бота
                    if (currentBotMessageView != null) {
                        currentBotMessageView.setText(accumulatedStr);
                        currentBotMessageView.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
                    }
                    // Прокручиваем вниз
                    scrollToBottom();
                });
            }

            @Override
            public void onComplete() {
                AndroidUtilities.runOnUIThread(() -> {
                    // Добавляем ответ ассистента в историю
                    String fullResponse = accumulated.toString();
                    chatHistory.add(new BaseAIService.ChatMessage("assistant", fullResponse));
                    // Обновляем финальный текст (если ещё не обновлён)
                    if (currentBotMessageView != null) {
                        currentBotMessageView.setText(fullResponse);
                        currentBotMessageView.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
                    }
                    analyzeButton.setEnabled(true);
                    isGenerating = false;
                    // Если была команда [GENERATE], запускаем генерацию предложений с историей чата
                    if (generateAfterAnalysis) {
                        generateAfterAnalysis = false;
                        generateSuggestionsWithHistory(chatHistory);
                    }
                    currentBotMessageView = null;
                });
            }

            @Override
            public void onError(String error) {
                AndroidUtilities.runOnUIThread(() -> {
                    // Показываем ошибку в пузырьке бота
                    if (currentBotMessageView != null) {
                        currentBotMessageView.setText("Ошибка: " + error);
                        currentBotMessageView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                    }
                    analyzeButton.setEnabled(true);
                    isGenerating = false;
                    generateAfterAnalysis = false;
                    currentBotMessageView = null;
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Обновляем сервис при возвращении из настроек
        updateService();
        updateServiceInfo();
        // Обновляем chips стилей
        if (styleChipsContainer != null && getParentActivity() != null) {
            createStyleChips(getParentActivity());
        }
        // Не запускаем генерацию автоматически - ждём нажатия кнопки
    }

    public String getPromptText() {
        return promptText;
    }

    public ArrayList<MessageObject> getSelectedMessages() {
        return selectedMessages;
    }
}
