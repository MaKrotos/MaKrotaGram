package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.openAI.UserPromptService;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class PromptSettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private int currentAccount = UserConfig.selectedAccount; // Добавляем переменную для текущего аккаунта

    private int settingsSectionRow;
    private int promptTextRow;
    private int promptEnabledRow;
    private int settingsSectionEndRow;
    private int infoRow;
    private int rowCount;

    private String currentPromptText = "";

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        // Загружаем текущий промпт с указанием аккаунта
        long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
        currentPromptText = UserPromptService.getInstance(currentAccount).getPrompt(currentUserId); // Исправлено: передаем currentAccount

        // Подписываемся на уведомления
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.userPromptUpdated);

        updateRows();
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Отписываемся от уведомлений
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.userPromptUpdated);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.userPromptUpdated) {
            long userId = (Long) args[0];
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            if (userId == currentUserId) {
                // Обновляем текст промпта
                currentPromptText = UserPromptService.getInstance(currentAccount).getPrompt(currentUserId);
                if (adapter != null) {
                    adapter.notifyItemChanged(promptTextRow);
                }
            }
        }
    }

    private void updateRows() {
        rowCount = 0;
        settingsSectionRow = rowCount++;
        promptTextRow = rowCount++;
        promptEnabledRow = rowCount++;
        settingsSectionEndRow = rowCount++;
        infoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString("UserPromptSettings", R.string.UserPromptSettings));
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

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position == promptTextRow) {
                // Открываем диалог редактирования промпта
                showPromptEditDialog();
            } else if (position == promptEnabledRow) {
                // Переключаем включение/выключение промпта
                TextCheckCell checkCell = (TextCheckCell) view;
                long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                boolean newState = !checkCell.isChecked();
                checkCell.setChecked(newState);
            }
        });

        return fragmentView;
    }

    private void showPromptEditDialog() {
        EditTextBoldCursor editText = new EditTextBoldCursor(getContext());
        editText.setText(currentPromptText);
        editText.setSelection(editText.length());
        editText.setHint(LocaleController.getString("PromptHint", R.string.PromptHint));
        editText.setSingleLine(false);
        editText.setMaxLines(5);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(getContext());
        builder.setTitle(LocaleController.getString("EditPrompt", R.string.EditPrompt));
        builder.setView(editText);
        builder.setPositiveButton(LocaleController.getString("Save", R.string.Save), (dialog, which) -> {
            String newPrompt = editText.getText().toString().trim();
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();

            if (!newPrompt.isEmpty()) {
                UserPromptService.getInstance(currentAccount).savePrompt(currentUserId, newPrompt); // Исправлено: передаем currentAccount
                currentPromptText = newPrompt;
                updateAdapter();
            } else {
                UserPromptService.getInstance(currentAccount).deletePrompt(currentUserId); // Исправлено: передаем currentAccount
                currentPromptText = "";
                updateAdapter();
            }
        });
        builder.setNeutralButton(LocaleController.getString("Clear", R.string.Clear), (dialog, which) -> {
            long currentUserId = UserConfig.getInstance(currentAccount).getClientUserId();
            UserPromptService.getInstance(currentAccount).deletePrompt(currentUserId); // Исправлено: передаем currentAccount
            currentPromptText = "";
            updateAdapter();
        });
        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);

        android.app.AlertDialog dialog = builder.create();
        dialog.show();

        // Настраиваем цвета кнопок
        if (dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE) != null) {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        }
        if (dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL) != null) {
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }

        // Показываем клавиатуру
        editText.requestFocus();
        AndroidUtilities.showKeyboard(editText);
    }

    private void updateAdapter() {
        if (adapter != null) {
            adapter.notifyItemChanged(promptTextRow);
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
            switch (holder.getItemViewType()) {
                case 0: {
                    if (position == promptTextRow) {
                        TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                        textCell.setTextAndValue(LocaleController.getString("PromptText", R.string.PromptText),
                                currentPromptText.isEmpty() ? LocaleController.getString("NotSet", R.string.NotSet) : currentPromptText,
                                true);
                    }
                    break;
                }
                case 1: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == infoRow) {
                        cell.setText(LocaleController.getString("PromptInfo", R.string.PromptInfo));
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == settingsSectionEndRow) {
                        cell.setText("");
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
                case 2: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == settingsSectionRow) {
                        cell.setText(LocaleController.getString("PromptSettingsSection", R.string.PromptSettingsSection), false);
                        cell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position == promptTextRow || position == promptEnabledRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                default:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    ((TextSettingsCell) view).setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public int getItemViewType(int position) {
            if (position == infoRow || position == settingsSectionEndRow) {
                return 1;
            } else if (position == settingsSectionRow) {
                return 2;
            }
            return 0;
        }
    }
}