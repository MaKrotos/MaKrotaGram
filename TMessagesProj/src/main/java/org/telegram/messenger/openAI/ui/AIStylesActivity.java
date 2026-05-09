package org.telegram.messenger.openAI;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.models.AIStyle;
import org.telegram.messenger.openAI.AIStyleService;
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

public class AIStylesActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private AIStyleService styleService;
    private int currentAccount = -1;

    private int rowCount = 0;
    private int presetHeaderRow;
    private int[] presetStyleRows;
    private int customHeaderRow;
    private int[] customStyleRows;
    private int addStyleRow;
    private int dividerRow;

    private List<AIStyle> presetStyles;
    private List<AIStyle> customStyles;

    public AIStylesActivity() {
        super();
        currentAccount = UserConfig.selectedAccount;
        styleService = AIStyleService.getInstance();
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadStyles();
        updateRows();
        return true;
    }

    private void loadStyles() {
        presetStyles = styleService.getPresetStyles();
        customStyles = styleService.getCustomStyles();
    }

    private void updateRows() {
        rowCount = 0;
        presetHeaderRow = rowCount++;
        presetStyleRows = new int[presetStyles.size()];
        for (int i = 0; i < presetStyles.size(); i++) {
            presetStyleRows[i] = rowCount++;
        }
        customHeaderRow = rowCount++;
        customStyleRows = new int[customStyles.size()];
        for (int i = 0; i < customStyles.size(); i++) {
            customStyleRows[i] = rowCount++;
        }
        addStyleRow = rowCount++;
        dividerRow = rowCount++;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadStyles();
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Стили AI");
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
            if (position == addStyleRow) {
                showAddStyleDialog();
                return;
            }
            for (int i = 0; i < presetStyles.size(); i++) {
                if (position == presetStyleRows[i]) {
                    showEditStyleDialog(presetStyles.get(i));
                    return;
                }
            }
            for (int i = 0; i < customStyles.size(); i++) {
                if (position == customStyleRows[i]) {
                    showEditStyleDialog(customStyles.get(i));
                    return;
                }
            }
        });

        return fragmentView;
    }

    private void showAddStyleDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(LocaleController.getStringResId("AddStyle")));

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        org.telegram.ui.Components.EditTextBoldCursor nameEdit = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        nameEdit.setHint(LocaleController.getString(LocaleController.getStringResId("StyleName")));
        nameEdit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        nameEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));

        org.telegram.ui.Components.EditTextBoldCursor promptEdit = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        promptEdit.setHint(LocaleController.getString(LocaleController.getStringResId("StylePrompt")));
        promptEdit.setMinLines(3);
        promptEdit.setMaxLines(6);
        promptEdit.setVerticalScrollBarEnabled(true);
        promptEdit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        promptEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        promptEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));

        container.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        container.addView(promptEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String name = nameEdit.getText().toString().trim();
            String prompt = promptEdit.getText().toString().trim();
            if (!name.isEmpty() && !prompt.isEmpty()) {
                AIStyle style = new AIStyle(null, name, prompt, true, customStyles.size());
                styleService.addCustomStyle(style);
                loadStyles();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        builder.show();
    }

    private void showEditStyleDialog(AIStyle style) {
        boolean isCustom = style.isCustom();
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(isCustom ? LocaleController.getString(LocaleController.getStringResId("EditStyle")) : LocaleController.getString(LocaleController.getStringResId("ViewStyle")));

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        org.telegram.ui.Components.EditTextBoldCursor nameEdit = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        nameEdit.setHint(LocaleController.getString(LocaleController.getStringResId("StyleName")));
        nameEdit.setText(style.getName());
        nameEdit.setEnabled(isCustom);
        nameEdit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        nameEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));

        org.telegram.ui.Components.EditTextBoldCursor promptEdit = new org.telegram.ui.Components.EditTextBoldCursor(getParentActivity());
        promptEdit.setHint(LocaleController.getString(LocaleController.getStringResId("StylePrompt")));
        promptEdit.setText(style.getPrompt());
        promptEdit.setEnabled(isCustom);
        promptEdit.setMinLines(3);
        promptEdit.setMaxLines(6);
        promptEdit.setVerticalScrollBarEnabled(true);
        promptEdit.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        promptEdit.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        promptEdit.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));

        container.addView(nameEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        container.addView(promptEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0));

        builder.setView(container);
        if (isCustom) {
            builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
                String newName = nameEdit.getText().toString().trim();
                String newPrompt = promptEdit.getText().toString().trim();
                if (!newName.isEmpty() && !newPrompt.isEmpty()) {
                    style.setName(newName);
                    style.setPrompt(newPrompt);
                    styleService.updateCustomStyle(style);
                    loadStyles();
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                }
            });
            builder.setNeutralButton(LocaleController.getString(LocaleController.getStringResId("Delete")), (dialog, which) -> {
                styleService.deleteCustomStyle(style);
                loadStyles();
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }
            });
        } else {
            builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        }
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
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
                    if (position == presetHeaderRow) {
                        headerCell.setText("Предустановленные стили");
                    } else if (position == customHeaderRow) {
                        headerCell.setText("Пользовательские стили");
                    }
                    break;

                case 1: // TextSettingsCell для стилей
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    if (position == addStyleRow) {
                        textCell.setText("Добавить стиль", false);
                    } else {
                        for (int i = 0; i < presetStyles.size(); i++) {
                            if (position == presetStyleRows[i]) {
                                AIStyle style = presetStyles.get(i);
                                textCell.setText(style.getName(), true);
                                break;
                            }
                        }
                        for (int i = 0; i < customStyles.size(); i++) {
                            if (position == customStyleRows[i]) {
                                AIStyle style = customStyles.get(i);
                                textCell.setText(style.getName(), true);
                                break;
                            }
                        }
                    }
                    break;

                case 2: // ShadowSectionCell
                    // Ничего не делаем
                    break;
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == addStyleRow) {
                return true;
            }
            for (int i = 0; i < presetStyles.size(); i++) {
                if (position == presetStyleRows[i]) {
                    return true;
                }
            }
            for (int i = 0; i < customStyles.size(); i++) {
                if (position == customStyleRows[i]) {
                    return true;
                }
            }
            return false;
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
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 2:
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
            if (position == presetHeaderRow || position == customHeaderRow) {
                return 0; // Header
            }
            if (position == addStyleRow) {
                return 1; // TextSettingsCell
            }
            for (int i = 0; i < presetStyles.size(); i++) {
                if (position == presetStyleRows[i]) {
                    return 1;
                }
            }
            for (int i = 0; i < customStyles.size(); i++) {
                if (position == customStyleRows[i]) {
                    return 1;
                }
            }
            return 2; // ShadowSectionCell
        }
    }
}