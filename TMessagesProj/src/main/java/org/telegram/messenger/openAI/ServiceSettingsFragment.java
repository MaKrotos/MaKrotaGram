package org.telegram.messenger.openAI;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.os.Build;
import android.content.res.ColorStateList;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.Console;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class ServiceSettingsFragment extends BaseFragment {

    private static final String ARG_SERVICE_TYPE = "service_type";

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private AISettings aiSettings;
    private BaseServiceSettings serviceSettings;
    private AISettings.AIServiceType serviceType;

    private int rowCount = 0;
    private List<SettingDefinition> definitions;
    private int[] definitionRows; // mapping from definition index to row number
    private int currentAccount = -1;

    private static float parseFloatWithComma(String str) throws NumberFormatException {
        return Float.parseFloat(str.replace(',', '.'));
    }

    private static int parseIntWithComma(String str) throws NumberFormatException {
        // Удаляем все нецифровые символы, кроме минуса в начале
        String cleaned = str.replace(',', '.');
        // Парсим как float, затем в int (для случаев, когда введено "1,0")
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            // Если содержит точку или запятую, парсим как float и округляем
            float f = Float.parseFloat(cleaned);
            return Math.round(f);
        }
    }
    public ServiceSettingsFragment() {
        super();
    }

    public ServiceSettingsFragment(Bundle args) {
        super(args);
    }

    public static ServiceSettingsFragment newInstance(AISettings.AIServiceType serviceType) {
        Bundle args = new Bundle();
        args.putString(ARG_SERVICE_TYPE, serviceType.name());
        return new ServiceSettingsFragment(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        Bundle args = getArguments();
        if (args != null) {
            String typeName = args.getString(ARG_SERVICE_TYPE);
            serviceType = AISettings.AIServiceType.fromString(typeName);
        }
        if (serviceType == null) {
            serviceType = AISettings.AIServiceType.OPENAI; // fallback
        }
        currentAccount = UserConfig.selectedAccount;
        aiSettings = new AISettings(currentAccount);
        serviceSettings = aiSettings.getServiceSettings(serviceType);
        definitions = serviceSettings.getSettingDefinitions();
        updateRows();
        return true;
    }

    private void reloadSettings() {
        int newAccount = UserConfig.selectedAccount;
        if (newAccount != currentAccount) {
            currentAccount = newAccount;
            aiSettings = new AISettings(currentAccount);
            serviceSettings = aiSettings.getServiceSettings(serviceType);
            definitions = serviceSettings.getSettingDefinitions();
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadSettings();
    }

    private void updateRows() {
        rowCount = 0;
        definitionRows = new int[definitions.size()];
        for (int i = 0; i < definitions.size(); i++) {
            definitionRows[i] = rowCount++;
        }
        // Add a divider after all settings
        rowCount++; // divider
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(serviceSettings.getServiceDisplayName());
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
            for (int i = 0; i < definitions.size(); i++) {
                if (position == definitionRows[i]) {
                    onSettingClick(definitions.get(i));
                    break;
                }
            }
        });

        return fragmentView;
    }

    private void onSettingClick(SettingDefinition definition) {
        switch (definition.getType()) {
            case STRING:
                showStringDialog(definition);
                break;
            case INT:
                showNumberDialog(definition, true);
                break;
            case FLOAT:
                showNumberDialog(definition, false);
                break;
            case BOOLEAN:
                toggleBoolean(definition);
                break;
            case CHOICE:
                showChoiceDialog(definition);
                break;
        }
    }

    private void showStringDialog(SettingDefinition definition) {
        String currentValue = (String) serviceSettings.getValue(definition.getKey());
        if (currentValue == null) {
            currentValue = definition.getStringDefault();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(definition.getTitle());

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(currentValue);
        if (!TextUtils.isEmpty(currentValue)) {
            editText.setSelection(currentValue.length());
        }
        editText.setHint(definition.getDescription());
        editText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        editText.setTextSize(16);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        if (definition.isMasked()) {
            editText.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String newValue = editText.getText().toString();
            serviceSettings.setValue(definition.getKey(), newValue);
            listAdapter.notifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showNumberDialog(SettingDefinition definition, boolean isInteger) {
        float min = definition.getMin();
        float max = definition.getMax();
        // Проверяем, есть ли ограничения min/max (не равные дефолтным значениям)
        boolean hasConstraints = (min != Float.MIN_VALUE && max != Float.MAX_VALUE);
        if (hasConstraints) {
            showSliderDialog(definition, isInteger);
        } else {
            showPlainNumberDialog(definition, isInteger);
        }
    }

    private void showPlainNumberDialog(SettingDefinition definition, boolean isInteger) {
        Number currentValue = (Number) serviceSettings.getValue(definition.getKey());
        String currentStr = currentValue != null ? currentValue.toString() : "";
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(definition.getTitle());

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(currentStr);
        editText.setHint(definition.getDescription());
        editText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        editText.setTextSize(16);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        if (isInteger) {
            editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        } else {
            editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String str = editText.getText().toString();
            try {
                if (isInteger) {
                    int val = parseIntWithComma(str);
                    // Apply constraints
                    Map<String, Object> constraints = definition.getConstraints();
                    if (constraints != null) {
                        if (constraints.containsKey("min")) {
                            int min = ((Number) constraints.get("min")).intValue();
                            val = Math.max(min, val);
                        }
                        if (constraints.containsKey("max")) {
                            int max = ((Number) constraints.get("max")).intValue();
                            val = Math.min(max, val);
                        }
                    }
                    serviceSettings.setValue(definition.getKey(), val);
                } else {
                    float val = parseFloatWithComma(str);
                    Map<String, Object> constraints = definition.getConstraints();
                    if (constraints != null) {
                        if (constraints.containsKey("min")) {
                            float min = ((Number) constraints.get("min")).floatValue();
                            val = Math.max(min, val);
                        }
                        if (constraints.containsKey("max")) {
                            float max = ((Number) constraints.get("max")).floatValue();
                            val = Math.min(max, val);
                        }
                    }
                    serviceSettings.setValue(definition.getKey(), val);
                }
                listAdapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Log.e("TAG", "Ошибка парсинга числа: " + e.getMessage());
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showSliderDialog(SettingDefinition definition, boolean isInteger) {
        float min = definition.getMin();
        float max = definition.getMax();
        Number currentValue = (Number) serviceSettings.getValue(definition.getKey());
        float currentFloat;
        if (currentValue != null) {
            currentFloat = currentValue.floatValue();
        } else {
            currentFloat = isInteger ? definition.getIntDefault() : definition.getFloatDefault();
        }
        // Ограничиваем текущее значение
        if (currentFloat < min) currentFloat = min;
        if (currentFloat > max) currentFloat = max;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(definition.getTitle());

        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        // Текстовое поле для отображения значения
        TextView valueText = new TextView(getParentActivity());
        valueText.setTextSize(18);
        valueText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        valueText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        valueText.setText(isInteger ? String.valueOf((int) currentFloat) : String.format("%.2f", currentFloat));

        // SeekBar (работает только с целыми числами, преобразуем диапазон)
        SeekBar seekBar = new SeekBar(getParentActivity());
        seekBar.setMax(1000); // достаточно точности
        // Преобразуем значение в прогресс
        int progress = (int) ((currentFloat - min) / (max - min) * 1000);
        seekBar.setProgress(progress);
        seekBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_player_progress)));
            seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(Theme.getColor(Theme.key_player_progressBackground)));
        }

        // Поле ввода для ручного ввода
        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setText(isInteger ? String.valueOf((int) currentFloat) : String.format("%.2f", currentFloat));
        editText.setHint(definition.getDescription());
        editText.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        editText.setTextSize(16);
        editText.setBackgroundDrawable(Theme.createEditTextDrawable(getParentActivity(), true));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        if (isInteger) {
            editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        } else {
            editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        }

        // Обработка изменения SeekBar
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float value = min + (progress / 1000f) * (max - min);
                    if (isInteger) {
                        value = Math.round(value);
                    }
                    valueText.setText(isInteger ? String.valueOf((int) value) : String.format("%.2f", value));
                    editText.setText(isInteger ? String.valueOf((int) value) : String.format("%.2f", value));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Обработка изменения текста
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float value;
                    if (isInteger) {
                        value = parseIntWithComma(s.toString());
                    } else {
                        value = parseFloatWithComma(s.toString());
                    }
                    // Ограничиваем
                    if (value < min) value = min;
                    if (value > max) value = max;
                    // Обновляем SeekBar
                    int progress = (int) ((value - min) / (max - min) * 1000);
                    seekBar.setProgress(progress);
                    valueText.setText(isInteger ? String.valueOf((int) value) : String.format("%.2f", value));
                } catch (NumberFormatException e) {
                    Log.e("TAG", "Ошибка парсинга числа: " + e.getMessage());
                }
            }
        });

        layout.addView(valueText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(seekBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            String str = editText.getText().toString();
            try {
                if (isInteger) {
                    int val = parseIntWithComma(str);
                    // Применяем ограничения
                    val = (int) Math.max(min, Math.min(max, val));
                    serviceSettings.setValue(definition.getKey(), val);
                } else {
                    float val = parseFloatWithComma(str);
                    val = Math.max(min, Math.min(max, val));
                    serviceSettings.setValue(definition.getKey(), val);
                }
                listAdapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Log.e("TAG", "Ошибка парсинга числа: " + e.getMessage());
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void toggleBoolean(SettingDefinition definition) {
        Boolean current = (Boolean) serviceSettings.getValue(definition.getKey());
        if (current == null) {
            current = definition.getBooleanDefault();
        }
        boolean newValue = !current;
        serviceSettings.setValue(definition.getKey(), newValue);
        listAdapter.notifyDataSetChanged();
    }

    private void showChoiceDialog(SettingDefinition definition) {
        List<String> choices = definition.getChoices();
        if (choices == null || choices.isEmpty()) {
            // Fallback to string input
            showStringDialog(definition);
            return;
        }
        String currentValue = (String) serviceSettings.getValue(definition.getKey());
        if (currentValue == null) {
            currentValue = definition.getStringDefault();
        }
        int selectedIndex = choices.indexOf(currentValue);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        final int[] selectedIndexHolder = new int[]{selectedIndex};

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(definition.getTitle());

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(8), AndroidUtilities.dp(24), AndroidUtilities.dp(8));

        RecyclerListView listView = new RecyclerListView(getParentActivity());
        ChoiceAdapter adapter = new ChoiceAdapter(choices, selectedIndex);
        listView.setLayoutManager(new LinearLayoutManager(getParentActivity(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(adapter);
        listView.setVerticalScrollBarEnabled(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
        listView.setOnItemClickListener((view, position) -> {
            // Update selected index
            selectedIndexHolder[0] = position;
            // Highlight? We'll notify adapter
            adapter.setSelectedIndex(position);
        });

        container.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), (dialog, which) -> {
            if (selectedIndexHolder[0] >= 0 && selectedIndexHolder[0] < choices.size()) {
                String newValue = choices.get(selectedIndexHolder[0]);
                serviceSettings.setValue(definition.getKey(), newValue);
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private class ChoiceAdapter extends RecyclerListView.SelectionAdapter {
        private List<String> choices;
        private int selectedIndex;

        public ChoiceAdapter(List<String> choices, int selectedIndex) {
            this.choices = choices;
            this.selectedIndex = selectedIndex;
        }

        public void setSelectedIndex(int index) {
            selectedIndex = index;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return choices.size();
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TextSettingsCell cell = (TextSettingsCell) holder.itemView;
            cell.setText(choices.get(position), selectedIndex == position);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TextSettingsCell cell = new TextSettingsCell(parent.getContext());
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            return new RecyclerListView.Holder(cell);
        }
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
            if (position < definitions.size()) {
                SettingDefinition def = definitions.get(position);
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                Object value = serviceSettings.getValue(def.getKey());
                String displayValue = formatValue(def, value);
                textCell.setTextAndValue(def.getTitle(), displayValue, true);
            } else {
                // Divider row, nothing to bind
            }
        }

        private String formatValue(SettingDefinition def, Object value) {
            if (value == null) {
                return "Не установлено";
            }
            switch (def.getType()) {
                case STRING:
                    String str = (String) value;
                    if (def.isMasked() && !TextUtils.isEmpty(str)) {
                        return "••••••••" + str.substring(Math.max(0, str.length() - 4));
                    }
                    return str;
                case INT:
                    return String.valueOf(value);
                case FLOAT:
                    return String.format("%.2f", value);
                case BOOLEAN:
                    return (Boolean) value ? "Включено" : "Выключено";
                case CHOICE:
                    return (String) value;
                default:
                    return String.valueOf(value);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position < definitions.size();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new TextSettingsCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new ShadowSectionCell(mContext);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            return position < definitions.size() ? 0 : 1;
        }
    }
}