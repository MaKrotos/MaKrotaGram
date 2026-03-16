package org.telegram.messenger.openAI;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import org.telegram.messenger.AndroidUtilities;
import android.widget.LinearLayout;
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
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.List;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AISettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private AISettings aiSettings;

    private int rowCount = 0;
    private int selectedServiceHeaderRow;
    private int selectedServiceRow;
    private int headerRow;
    private int[] serviceRows;
    private int promptHeaderRow;
    private int promptRow;
    private int dividerRow;
    private int testHeaderRow;
    private int testConnectionRow;
    private int testDividerRow;

    private List<AIServiceRegistry.ServiceInfo> serviceInfos;
    private int currentAccount = -1;

    public AISettingsActivity() {
        super();
        currentAccount = UserConfig.selectedAccount;
        aiSettings = new AISettings(currentAccount);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        serviceInfos = AIServiceRegistry.getAvailableServices();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        selectedServiceHeaderRow = rowCount++;
        selectedServiceRow = rowCount++;
        headerRow = rowCount++;
        serviceRows = new int[serviceInfos.size()];
        for (int i = 0; i < serviceInfos.size(); i++) {
            serviceRows[i] = rowCount++;
        }
        promptHeaderRow = rowCount++;
        promptRow = rowCount++;
        dividerRow = rowCount++;
        testHeaderRow = rowCount++;
        testConnectionRow = rowCount++;
        testDividerRow = rowCount++;
    }

    private void reloadSettings() {
        int newAccount = UserConfig.selectedAccount;
        if (newAccount != currentAccount) {
            currentAccount = newAccount;
            aiSettings = new AISettings(currentAccount);
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
            if (position == selectedServiceRow) {
                showServiceSelectionDialog();
                return;
            }
            for (int i = 0; i < serviceInfos.size(); i++) {
                if (position == serviceRows[i]) {
                    AISettings.AIServiceType serviceType = serviceInfos.get(i).type;
                    presentFragment(ServiceSettingsFragment.newInstance(serviceType));
                    break;
                }
            }
            if (position == promptRow) {
                editPrompt();
                return;
            }
            if (position == testConnectionRow) {
                testConnection();
            }
        });

        return fragmentView;
    }

    private void testConnection() {
        AISettings.AIServiceType selected = aiSettings.getSelectedServiceType();
        if (!aiSettings.hasValidConfig(selected)) {
            showAlertWithOk("Ошибка", "Настройки для выбранного сервиса не заполнены");
            return;
        }

        // TODO: Реализовать тестовое соединение
        showAlertWithOk("Тест соединения", "Функция в разработке");
    }

    private void editPrompt() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Редактировать промпт");

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        EditText editText = new EditText(getParentActivity());
        editText.setHint("Введите ваш промпт...");
        editText.setText(aiSettings.getSystemPrompt() != null ? aiSettings.getSystemPrompt() : "");
        editText.setMinLines(3);
        editText.setMaxLines(6);
        editText.setVerticalScrollBarEnabled(true);
        editText.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        editText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));

        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String newPrompt = editText.getText().toString().trim();
            aiSettings.setSystemPrompt(newPrompt);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showServiceSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Выберите сервис");

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));

        RecyclerListView listView = new RecyclerListView(getParentActivity());
        listView.setLayoutManager(new LinearLayoutManager(getParentActivity(), LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new RecyclerView.Adapter() {
            private final int VIEW_TYPE_ITEM = 0;
            private int selectedIndex = -1;
            {
                AISettings.AIServiceType current = aiSettings.getSelectedServiceType();
                for (int i = 0; i < serviceInfos.size(); i++) {
                    if (serviceInfos.get(i).type == current) {
                        selectedIndex = i;
                        break;
                    }
                }
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                TextSettingsCell cell = new TextSettingsCell(parent.getContext());
                cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                return new RecyclerListView.Holder(cell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                AIServiceRegistry.ServiceInfo info = serviceInfos.get(position);
                cell.setText(info.displayName, position == selectedIndex);
            }

            @Override
            public int getItemCount() {
                return serviceInfos.size();
            }

            @Override
            public int getItemViewType(int position) {
                return VIEW_TYPE_ITEM;
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            AISettings.AIServiceType newType = serviceInfos.get(position).type;
            aiSettings.setSelectedService(newType);
            listAdapter.notifyDataSetChanged();
            builder.getDismissRunnable().run();
        });

        container.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));
        builder.setView(container);
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showAlertWithOk(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.show();
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
                    if (position == selectedServiceHeaderRow) {
                        headerCell.setText("Выбранный сервис");
                    } else if (position == headerRow) {
                        headerCell.setText("Выберите AI сервис");
                    } else if (position == promptHeaderRow) {
                        headerCell.setText("Промпт");
                    } else if (position == testHeaderRow) {
                        headerCell.setText("Проверка соединения");
                    }
                    break;

                case 1: // TextSettingsCell для сервисов и выбранного сервиса
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == selectedServiceRow) {
                        AISettings.AIServiceType selected = aiSettings.getSelectedServiceType();
                        String displayName = selected.getDisplayName();
                        textCell.setText(displayName, false);
                    } else if (position == promptRow) {
                        String prompt = aiSettings.getSystemPrompt();
                        if (prompt == null || prompt.isEmpty()) {
                            textCell.setText("Редактировать промпт", false);
                        } else {
                            textCell.setText("Промпт: " + (prompt.length() > 20 ? prompt.substring(0, 20) + "..." : prompt), false);
                        }
                    } else {
                        for (int i = 0; i < serviceInfos.size(); i++) {
                            if (position == serviceRows[i]) {
                                AIServiceRegistry.ServiceInfo info = serviceInfos.get(i);
                                textCell.setText(info.displayName, true);
                                break;
                            }
                        }
                    }
                    break;

                case 2: // TextSettingsCell для теста
                    TextSettingsCell testCell = (TextSettingsCell) holder.itemView;
                    if (position == testConnectionRow) {
                        testCell.setText("Проверить соединение",
                                aiSettings.hasValidConfig(aiSettings.getSelectedServiceType()));
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
            if (position == selectedServiceRow) {
                return true;
            }
            for (int i = 0; i < serviceInfos.size(); i++) {
                if (position == serviceRows[i]) {
                    return true;
                }
            }
            if (position == promptRow) {
                return true;
            }
            return position == testConnectionRow;
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
            if (position == selectedServiceHeaderRow || position == headerRow || position == testHeaderRow || position == promptHeaderRow) {
                return 0; // Header
            }
            if (position == selectedServiceRow) {
                return 1; // TextSettingsCell для выбранного сервиса
            }
            for (int i = 0; i < serviceInfos.size(); i++) {
                if (position == serviceRows[i]) {
                    return 1; // Service row
                }
            }
            if (position == promptRow) {
                return 1; // TextSettingsCell для промпта
            }
            if (position == testConnectionRow) {
                return 2; // Test row
            }
            return 3; // ShadowSectionCell
        }
    }
}