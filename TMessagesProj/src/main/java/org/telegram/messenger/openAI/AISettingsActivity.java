package org.telegram.messenger.openAI;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.openAI.AIServiceFactory;
import org.telegram.messenger.openAI.AISettings;
import org.telegram.messenger.openAI.BaseAIService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.RadioCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextDetailSettingsCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AISettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private AISettings aiSettings;
    private AISettings.AISettingsData settingsData;

    private int rowCount = 0;

    // Ряды
    private int serviceHeaderRow;
    private int serviceOpenAIRow;
    private int serviceGeminiRow;
    private int serviceLlamaRow;
    private int serviceDividerRow;

    private int openaiHeaderRow;
    private int openaiApiKeyRow;
    private int openaiModelRow;
    private int openaiInfoRow;
    private int openaiDividerRow;

    private int geminiHeaderRow;
    private int geminiApiKeyRow;
    private int geminiModelRow;
    private int geminiInfoRow;
    private int geminiDividerRow;

    private int llamaHeaderRow;
    private int llamaApiKeyRow;
    private int llamaModelRow;
    private int llamaInfoRow;
    private int llamaDividerRow;

    private int testHeaderRow;
    private int testConnectionRow;
    private int testDividerRow;

    public AISettingsActivity() {
        super();
        aiSettings = new AISettings();
        settingsData = aiSettings.loadSettings();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;

        // Выбор сервиса
        serviceHeaderRow = rowCount++;
        serviceOpenAIRow = rowCount++;
        serviceGeminiRow = rowCount++;
        serviceLlamaRow = rowCount++;
        serviceDividerRow = rowCount++;

        // OpenAI секция
        openaiHeaderRow = rowCount++;
        openaiApiKeyRow = rowCount++;
        openaiModelRow = rowCount++;
        openaiInfoRow = rowCount++;
        openaiDividerRow = rowCount++;

        // Gemini секция
        geminiHeaderRow = rowCount++;
        geminiApiKeyRow = rowCount++;
        geminiModelRow = rowCount++;
        geminiInfoRow = rowCount++;
        geminiDividerRow = rowCount++;

        // Llama секция
        llamaHeaderRow = rowCount++;
        llamaApiKeyRow = rowCount++;
        llamaModelRow = rowCount++;
        llamaInfoRow = rowCount++;
        llamaDividerRow = rowCount++;

        // Тестовая секция
        testHeaderRow = rowCount++;
        testConnectionRow = rowCount++;
        testDividerRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("AISettings", R.string.AISettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter);
        listView.setVerticalScrollBarEnabled(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position == serviceOpenAIRow) {
                settingsData.selectedService = AISettings.AIServiceType.OPENAI;
                saveSettings();
                listAdapter.notifyDataSetChanged();
            } else if (position == serviceGeminiRow) {
                settingsData.selectedService = AISettings.AIServiceType.GEMINI;
                saveSettings();
                listAdapter.notifyDataSetChanged();
            } else if (position == serviceLlamaRow) {
                settingsData.selectedService = AISettings.AIServiceType.LLAMA;
                saveSettings();
                listAdapter.notifyDataSetChanged();
            } else if (position == openaiApiKeyRow) {
                showApiKeyDialog("OpenAI API Key", settingsData.openaiApiKey, "sk-", (key) -> {
                    settingsData.openaiApiKey = key;
                    saveSettings();
                    listAdapter.notifyItemChanged(openaiApiKeyRow);
                });
            } else if (position == openaiModelRow) {
                showOpenAIModelDialog();
            } else if (position == geminiApiKeyRow) {
                showApiKeyDialog("Gemini API Key", settingsData.geminiApiKey, "", (key) -> {
                    settingsData.geminiApiKey = key;
                    saveSettings();
                    listAdapter.notifyItemChanged(geminiApiKeyRow);
                });
            } else if (position == geminiModelRow) {
                showGeminiModelDialog();
            } else if (position == llamaApiKeyRow) {
                showApiKeyDialog("Together.ai API Key", settingsData.llamaApiKey, "tgp-", (key) -> {
                    settingsData.llamaApiKey = key;
                    saveSettings();
                    listAdapter.notifyItemChanged(llamaApiKeyRow);
                });
            } else if (position == llamaModelRow) {
                showLlamaModelDialog();
            } else if (position == testConnectionRow) {
                testConnection();
            }
        });

        return fragmentView;
    }

    private void saveSettings() {
        aiSettings.saveSettings(settingsData);
    }

    private void showApiKeyDialog(String title, String currentValue, String hint, OnValueSetListener listener) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(currentValue);
        if (!TextUtils.isEmpty(currentValue)) {
            editText.setSelection(currentValue.length());
        }
        editText.setHint(hint);
        editText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        editText.setTextSize(16);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            listener.onValueSet(editText.getText().toString());
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showOpenAIModelDialog() {
        BaseAIService service = AIServiceFactory.createService(AISettings.AIServiceType.OPENAI, currentAccount);
        BaseAIService.AIModel[] models = service.getAvailableModels();

        String[] displayNames = new String[models.length];
        String[] modelIds = new String[models.length];

        for (int i = 0; i < models.length; i++) {
            displayNames[i] = models[i].displayName;
            modelIds[i] = models[i].id;
        }

        showModelSelectionDialog("Выберите модель OpenAI",
                displayNames, modelIds, settingsData.openaiModel,
                (modelId) -> {
                    settingsData.openaiModel = modelId;
                    saveSettings();
                    listAdapter.notifyItemChanged(openaiModelRow);
                });
    }

    private void showGeminiModelDialog() {
        BaseAIService service = AIServiceFactory.createService(AISettings.AIServiceType.GEMINI, currentAccount);
        BaseAIService.AIModel[] models = service.getAvailableModels();

        String[] displayNames = new String[models.length];
        String[] modelIds = new String[models.length];
        String[] descriptions = new String[models.length];

        for (int i = 0; i < models.length; i++) {
            displayNames[i] = models[i].displayName;
            modelIds[i] = models[i].id;
            descriptions[i] = models[i].description;
        }

        showModelSelectionDialogWithDescriptions("Выберите модель Gemini",
                displayNames, descriptions, modelIds, settingsData.geminiModel,
                (modelId) -> {
                    settingsData.geminiModel = modelId;
                    saveSettings();
                    listAdapter.notifyItemChanged(geminiModelRow);
                });
    }

    private void showLlamaModelDialog() {
        BaseAIService service = AIServiceFactory.createService(AISettings.AIServiceType.LLAMA, currentAccount);
        BaseAIService.AIModel[] models = service.getAvailableModels();

        String[] displayNames = new String[models.length];
        String[] modelIds = new String[models.length];
        String[] descriptions = new String[models.length];

        for (int i = 0; i < models.length; i++) {
            displayNames[i] = models[i].displayName;
            modelIds[i] = models[i].id;
            descriptions[i] = models[i].description;
        }

        showModelSelectionDialogWithDescriptions("Выберите модель Llama",
                displayNames, descriptions, modelIds, settingsData.llamaModel,
                (modelId) -> {
                    settingsData.llamaModel = modelId;
                    saveSettings();
                    listAdapter.notifyItemChanged(llamaModelRow);
                });
    }

    private void showModelSelectionDialog(String title, String[] displayNames, String[] modelIds,
                                          String currentModel, OnModelSelectedListener listener) {
        int selectedIndex = 0;
        for (int i = 0; i < modelIds.length; i++) {
            if (modelIds[i].equals(currentModel)) {
                selectedIndex = i;
                break;
            }
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setSingleChoiceItems(displayNames, selectedIndex, (dialog, which) -> {
            listener.onModelSelected(modelIds[which]);
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showModelSelectionDialogWithDescriptions(String title, String[] displayNames,
                                                          String[] descriptions, String[] modelIds,
                                                          String currentModel, OnModelSelectedListener listener) {
        int selectedIndex = 0;
        for (int i = 0; i < modelIds.length; i++) {
            if (modelIds[i].equals(currentModel)) {
                selectedIndex = i;
                break;
            }
        }

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);

        // Создаем кастомный список с описаниями
        String[] items = new String[displayNames.length];
        for (int i = 0; i < displayNames.length; i++) {
            items[i] = displayNames[i] + "\n" + descriptions[i];
        }

        builder.setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
            listener.onModelSelected(modelIds[which]);
            dialog.dismiss();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void testConnection() {
        if (!aiSettings.hasValidConfig(settingsData.selectedService)) {
            showAlertWithOk("Ошибка", "Настройки для выбранного сервиса не заполнены");
            return;
        }

        // TODO: Реализовать тестовое соединение
        showAlertWithOk("Тест соединения", "Функция в разработке");
    }

    private void showAlertWithOk(String title, String message) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
    }

    private interface OnValueSetListener {
        void onValueSet(String value);
    }

    private interface OnModelSelectedListener {
        void onModelSelected(String modelId);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 0: // Header
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == serviceHeaderRow) {
                        headerCell.setText("Выберите AI сервис");
                    } else if (position == openaiHeaderRow) {
                        headerCell.setText("OpenAI");
                    } else if (position == geminiHeaderRow) {
                        headerCell.setText("Google Gemini");
                    } else if (position == llamaHeaderRow) {
                        headerCell.setText("Llama (Together.ai)");
                    } else if (position == testHeaderRow) {
                        headerCell.setText("Проверка соединения");
                    }
                    break;

                case 1: // RadioCell для выбора сервиса
                    RadioCell radioCell = (RadioCell) holder.itemView;
                    if (position == serviceOpenAIRow) {
                        radioCell.setText("OpenAI",
                                settingsData.selectedService == AISettings.AIServiceType.OPENAI,
                                position != serviceGeminiRow && position != serviceLlamaRow);
                    } else if (position == serviceGeminiRow) {
                        radioCell.setText("Google Gemini",
                                settingsData.selectedService == AISettings.AIServiceType.GEMINI,
                                false);
                    } else if (position == serviceLlamaRow) {
                        radioCell.setText("Llama (Together.ai)",
                                settingsData.selectedService == AISettings.AIServiceType.LLAMA,
                                false);
                    }
                    break;

                case 2: // TextSettingsCell для редактируемых полей
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == openaiApiKeyRow) {
                        String key = settingsData.openaiApiKey;
                        String display = TextUtils.isEmpty(key) ? "Не установлен" :
                                "••••••••" + key.substring(Math.max(0, key.length() - 4));
                        textCell.setTextAndValue("API Key", display, true);
                    } else if (position == openaiModelRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.OPENAI, currentAccount)
                                .getModelById(settingsData.openaiModel);
                        String display = model != null ? model.displayName : settingsData.openaiModel;
                        textCell.setTextAndValue("Модель", display, true);
                    } else if (position == openaiInfoRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.OPENAI, currentAccount)
                                .getModelById(settingsData.openaiModel);
                        String info = "Макс. токенов: " + (model != null ? model.maxTokens : "?") +
                                (model != null && model.supportsVision ? " | Поддержка изображений" : "");
                        textCell.setText(info, false);
                        textCell.setEnabled(false);
                    } else if (position == geminiApiKeyRow) {
                        String key = settingsData.geminiApiKey;
                        String display = TextUtils.isEmpty(key) ? "Не установлен" :
                                "••••••••" + key.substring(Math.max(0, key.length() - 4));
                        textCell.setTextAndValue("API Key", display, true);
                    } else if (position == geminiModelRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.GEMINI, currentAccount)
                                .getModelById(settingsData.geminiModel);
                        String display = model != null ? model.displayName : settingsData.geminiModel;
                        textCell.setTextAndValue("Модель", display, true);
                    } else if (position == geminiInfoRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.GEMINI, currentAccount)
                                .getModelById(settingsData.geminiModel);
                        String info = "Макс. токенов: " + (model != null ? model.maxTokens : "?") +
                                (model != null && model.supportsVision ? " | Поддержка изображений" : "");
                        textCell.setText(info, false);
                        textCell.setEnabled(false);
                    } else if (position == llamaApiKeyRow) {
                        String key = settingsData.llamaApiKey;
                        String display = TextUtils.isEmpty(key) ? "Не установлен" :
                                "••••••••" + key.substring(Math.max(0, key.length() - 4));
                        textCell.setTextAndValue("API Key", display, true);
                    } else if (position == llamaModelRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.LLAMA, currentAccount)
                                .getModelById(settingsData.llamaModel);
                        String display = model != null ? model.displayName : settingsData.llamaModel;
                        textCell.setTextAndValue("Модель", display, true);
                    } else if (position == llamaInfoRow) {
                        BaseAIService.AIModel model = AIServiceFactory.createService(
                                        AISettings.AIServiceType.LLAMA, currentAccount)
                                .getModelById(settingsData.llamaModel);
                        String info = "Макс. токенов: " + (model != null ? model.maxTokens : "?") +
                                (model != null && model.supportsVision ? " | Поддержка изображений" : "");
                        textCell.setText(info, false);
                        textCell.setEnabled(false);
                    } else if (position == testConnectionRow) {
                        textCell.setText("Проверить соединение",
                                aiSettings.hasValidConfig(settingsData.selectedService));
                    }
                    break;

                case 3: // ShadowSectionCell
                    // Ничего не делаем
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == serviceOpenAIRow || position == serviceGeminiRow ||
                    position == serviceLlamaRow ||
                    position == openaiApiKeyRow || position == openaiModelRow ||
                    position == geminiApiKeyRow || position == geminiModelRow ||
                    position == llamaApiKeyRow || position == llamaModelRow ||
                    position == testConnectionRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new RadioCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 3:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == serviceHeaderRow || position == openaiHeaderRow ||
                    position == geminiHeaderRow || position == llamaHeaderRow ||
                    position == testHeaderRow) {
                return 0; // Header
            } else if (position == serviceOpenAIRow || position == serviceGeminiRow ||
                    position == serviceLlamaRow) {
                return 1; // RadioCell
            } else if (position == openaiApiKeyRow || position == openaiModelRow ||
                    position == openaiInfoRow || position == geminiApiKeyRow ||
                    position == geminiModelRow || position == geminiInfoRow ||
                    position == llamaApiKeyRow || position == llamaModelRow ||
                    position == llamaInfoRow || position == testConnectionRow) {
                return 2; // TextSettingsCell
            } else {
                return 3; // ShadowSectionCell
            }
        }
    }
}