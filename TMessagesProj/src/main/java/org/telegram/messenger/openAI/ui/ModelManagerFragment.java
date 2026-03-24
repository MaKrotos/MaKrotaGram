package org.telegram.messenger.openAI;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
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

import com.fdw.sugar_pocketai.download.DownloadManager;
import com.fdw.sugar_pocketai.download.DownloadEntity;
import com.fdw.sugar_pocketai.download.DownloadStatus;
import com.fdw.sugar_pocketai.download.NetworkType;
import com.fdw.sugar_pocketai.model.ModelCatalog;
import com.fdw.sugar_pocketai.model.ModelItem;
import com.fdw.sugar_pocketai.model.ModelManager;
import com.fdw.sugar_pocketai.model.ModelEntity;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelManagerFragment extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private LocalAIService localAIService;
    private ModelManager modelManager;
    private DownloadManager downloadManager;
    private List<ModelItem> recommendedModels;
    private List<ModelEntity> downloadedModels;
    private List<DownloadEntity> activeDownloads;
    private LiveData<List<DownloadEntity>> downloadsLiveData;
    private Observer<List<DownloadEntity>> downloadsObserver;
    private Map<String, String> downloadIdToModelName = new HashMap<>();

    private int rowCount = 0;
    private int recommendedHeaderRow = -1;
    private int[] recommendedRows;
    private int downloadedHeaderRow = -1;
    private int[] downloadedRows;
    private int downloadsHeaderRow = -1;
    private int[] downloadRows;
    private int dividerRow = -1;
    private boolean selectionMode = false;

    public ModelManagerFragment() {
        super();
    }

    public ModelManagerFragment(Bundle args) {
        super(args);
    }

    public static ModelManagerFragment newInstanceForSelection() {
        Bundle args = new Bundle();
        args.putBoolean("selection_mode", true);
        return new ModelManagerFragment(args);
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        Bundle args = getArguments();
        if (args != null) {
            selectionMode = args.getBoolean("selection_mode", false);
        }
        int account = UserConfig.selectedAccount;
        localAIService = new LocalAIService(account);
        modelManager = localAIService.getModelManager();
        downloadManager = new DownloadManager(ApplicationLoader.applicationContext);
        loadData();
        subscribeToDownloads();

        // Принудительно обновляем список скачанных моделей
        refreshDownloadedModels();

        updateRows();
        return true;
    }

    private void loadData() {
        // Получить рекомендованные модели (используя токен из настроек)
        LocalAISettings settings = (LocalAISettings) new AISettings(UserConfig.selectedAccount).getServiceSettings(AISettings.AIServiceType.LOCAL_AI);
        String token = settings.getHfToken();
        if (token == null || token.isEmpty()) {
            // fallback to static token if available
            token = tw.fdw.makrotagram.Extra.HUGGINGFACETOKEN;
        }
        recommendedModels = ModelCatalog.getDefaultModels(token);
        // Получить скачанные модели из базы
        downloadedModels = modelManager.scanAndAddModels(null);
        // Активные загрузки будут обновлены через LiveData
        activeDownloads = new ArrayList<>();
    }

    private void refreshDownloadedModels() {
        new Thread(() -> {
            List<ModelEntity> allModels = new ArrayList<>();

            // Сканируем директорию models рекурсивно
            File modelsDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "models");
            scanDirectoryRecursive(modelsDir, allModels);

            AndroidUtilities.runOnUIThread(() -> {
                downloadedModels = allModels;
                updateRows();
                if (listAdapter != null) {
                    listAdapter.notifyDataSetChanged();
                }

                FileLog.d("ModelManagerFragment: найдено моделей: " + allModels.size());
                for (ModelEntity model : allModels) {
                    FileLog.d("ModelManagerFragment: " + model.getName() + " (" + model.getFormat() + ") -> " + model.getPath());
                }
            });
        }).start();
    }

    private void scanDirectoryRecursive(File dir, List<ModelEntity> models) {
        if (dir == null || !dir.exists()) {
            FileLog.d("ModelManagerFragment: директория не существует: " + (dir != null ? dir.getAbsolutePath() : "null"));
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            FileLog.d("ModelManagerFragment: не удалось прочитать содержимое: " + dir.getAbsolutePath());
            return;
        }

        FileLog.d("ModelManagerFragment: сканируем " + dir.getAbsolutePath() + ", найдено " + files.length + " элементов");

        for (File file : files) {
            if (file.isDirectory()) {
                // Рекурсивно сканируем поддиректории
                scanDirectoryRecursive(file, models);
            } else {
                String name = file.getName();
                String lowerName = name.toLowerCase();

                // Поддерживаемые форматы моделей
                boolean isModel = false;
                String format = null;

                if (lowerName.endsWith(".litertlm")) {
                    isModel = true;
                    format = "LiteRT LM";
                } else if (lowerName.endsWith(".gguf")) {
                    isModel = true;
                    format = "GGUF";
                } else if (lowerName.endsWith(".bin")) {
                    isModel = true;
                    format = "BIN";
                } else if (lowerName.endsWith(".onnx")) {
                    isModel = true;
                    format = "ONNX";
                } else if (lowerName.endsWith(".tflite")) {
                    isModel = true;
                    format = "TFLite";
                }

                if (isModel) {
                    // Создаем ModelEntity через конструктор
                    // Предполагаем, что конструктор принимает: id, name, path, size, format, type, timestamp
                    String id = file.getAbsolutePath();
                    String modelName = name;
                    String path = file.getAbsolutePath();
                    long size = file.length();
                    String modelFormat = format;
                    String type = "text-generation";
                    long timestamp = file.lastModified();

                    ModelEntity model = new ModelEntity(id, modelName, path, size, modelFormat, type, timestamp);
                    models.add(model);
                    FileLog.d("ModelManagerFragment: найдена модель: " + name + " (" + format + ")");
                }
            }
        }
    }

    private void subscribeToDownloads() {
        downloadsLiveData = downloadManager.getAllDownloads();
        downloadsObserver = downloads -> {
            if (downloads != null) {
                // Очищаем старые записи в Map для загрузок, которых больше нет в списке
                downloadIdToModelName.keySet().removeIf(id -> {
                    boolean found = false;
                    for (DownloadEntity d : downloads) {
                        if (d.getId().equals(id)) {
                            found = true;
                            break;
                        }
                    }
                    return !found;
                });
                boolean hasCompleted = false;
                activeDownloads.clear();
                for (DownloadEntity d : downloads) {
                    if (d.getStatus() == DownloadStatus.PENDING || d.getStatus() == DownloadStatus.RUNNING || d.getStatus() == DownloadStatus.PAUSED) {
                        activeDownloads.add(d);
                    } else if (d.getStatus() == DownloadStatus.COMPLETED) {
                        hasCompleted = true;
                        // Показать уведомление о завершении загрузки
                        AndroidUtilities.runOnUIThread(() -> {
                            String modelName = downloadIdToModelName.get(d.getId());
                            if (modelName == null) {
                                modelName = new File(d.getDestination()).getName();
                            }
                        });
                    }
                }

                // Если есть завершённые загрузки, обновляем список скачанных моделей
                if (hasCompleted) {
                    refreshDownloadedModels();
                } else {
                    // Обновляем UI для активных загрузок
                    updateRows();
                    if (listAdapter != null) {
                        listAdapter.notifyDataSetChanged();
                    }
                }
            }
        };
        downloadsLiveData.observeForever(downloadsObserver);
    }

    private void unsubscribeFromDownloads() {
        if (downloadsLiveData != null && downloadsObserver != null) {
            downloadsLiveData.removeObserver(downloadsObserver);
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        unsubscribeFromDownloads();
    }

    private void updateRows() {
        rowCount = 0;

        // В режиме выбора показываем ТОЛЬКО скачанные модели
        if (selectionMode) {
            recommendedHeaderRow = -1;
            recommendedRows = null;
            downloadsHeaderRow = -1;
            downloadRows = null;

            // Заголовок скачанных моделей
            if (downloadedModels != null && !downloadedModels.isEmpty()) {
                downloadedHeaderRow = rowCount++;
                downloadedRows = new int[downloadedModels.size()];
                for (int i = 0; i < downloadedModels.size(); i++) {
                    downloadedRows[i] = rowCount++;
                }
            } else {
                downloadedHeaderRow = -1;
                downloadedRows = null;
            }
        } else {
            // Обычный режим: показываем все
            // Заголовок рекомендованных моделей
            if (recommendedModels != null && !recommendedModels.isEmpty()) {
                recommendedHeaderRow = rowCount++;
                recommendedRows = new int[recommendedModels.size()];
                for (int i = 0; i < recommendedModels.size(); i++) {
                    recommendedRows[i] = rowCount++;
                }
            } else {
                recommendedHeaderRow = -1;
                recommendedRows = null;
            }

            // Заголовок скачанных моделей
            if (downloadedModels != null && !downloadedModels.isEmpty()) {
                downloadedHeaderRow = rowCount++;
                downloadedRows = new int[downloadedModels.size()];
                for (int i = 0; i < downloadedModels.size(); i++) {
                    downloadedRows[i] = rowCount++;
                }
            } else {
                downloadedHeaderRow = -1;
                downloadedRows = null;
            }

            // Заголовок активных загрузок
            if (activeDownloads != null && !activeDownloads.isEmpty()) {
                downloadsHeaderRow = rowCount++;
                downloadRows = new int[activeDownloads.size()];
                for (int i = 0; i < activeDownloads.size(); i++) {
                    downloadRows[i] = rowCount++;
                }
            } else {
                downloadsHeaderRow = -1;
                downloadRows = null;
            }
        }

        // Разделитель
        dividerRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (selectionMode) {
            actionBar.setTitle("Выберите модель");
        } else {
            actionBar.setTitle("Управление моделями");
        }
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
            if (recommendedRows != null) {
                for (int i = 0; i < recommendedRows.length; i++) {
                    if (position == recommendedRows[i]) {
                        onRecommendedModelClick(recommendedModels.get(i));
                        return;
                    }
                }
            }
            if (downloadedRows != null) {
                for (int i = 0; i < downloadedRows.length; i++) {
                    if (position == downloadedRows[i]) {
                        onDownloadedModelClick(downloadedModels.get(i));
                        return;
                    }
                }
            }
            if (downloadRows != null) {
                for (int i = 0; i < downloadRows.length; i++) {
                    if (position == downloadRows[i]) {
                        onDownloadClick(activeDownloads.get(i));
                        return;
                    }
                }
            }
        });

        return fragmentView;
    }

    private void onRecommendedModelClick(ModelItem model) {
        // Показать диалог с информацией о модели и кнопкой скачивания
        showDownloadDialog(model);
    }

    private void onDownloadedModelClick(ModelEntity model) {
        // Показать диалог с информацией о скачанной модели и кнопкой выбора
        showSelectDialog(model);
    }

    private void onDownloadClick(DownloadEntity download) {
        // Определяем доступные действия в зависимости от статуса
        List<String> items = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        DownloadStatus status = download.getStatus();
        if (status == DownloadStatus.RUNNING || status == DownloadStatus.PENDING) {
            items.add("Пауза");
            actions.add(() -> new Thread(() -> downloadManager.pauseDownload(download.getId())).start());
        } else if (status == DownloadStatus.PAUSED) {
            items.add("Возобновить");
            actions.add(() -> new Thread(() -> downloadManager.resumeDownload(download.getId())).start());
        }
        // Отмена доступна всегда
        items.add("Отменить");
        actions.add(() -> new Thread(() -> downloadManager.cancelDownload(download.getId(), true)).start());

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Управление загрузкой");
        builder.setItems(items.toArray(new String[0]), (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                actions.get(which).run();
            }
        });
        builder.show();
    }

    private void showDownloadDialog(ModelItem model) {
        // Диалог выбора типа сети
        String[] networkOptions = new String[]{"Только Wi-Fi", "Любая сеть"};
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Скачать модель " + model.getName());
        builder.setItems(networkOptions, (dialog, which) -> {
            NetworkType networkType = (which == 0) ? NetworkType.WIFI : NetworkType.ANY;
            startDownload(model, networkType);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void startDownload(ModelItem model, NetworkType networkType) {
        // Определяем путь назначения (в директорию моделей)
        File modelsDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "models");
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }

        // Создаем поддиректорию для модели, если нужно
        String modelId = model.getModelId();
        File modelDir = new File(modelsDir, modelId);
        if (!modelDir.exists()) {
            modelDir.mkdirs();
        }

        // Используем modelId как имя файла
        String fileName = modelId + ".litertlm";
        File destinationFile = new File(modelDir, fileName);
        String destinationPath = destinationFile.getAbsolutePath();

        // Получаем токен Hugging Face из настроек
        LocalAISettings settings = (LocalAISettings) new AISettings(UserConfig.selectedAccount).getServiceSettings(AISettings.AIServiceType.LOCAL_AI);
        String token = settings.getHfToken();
        if (token == null || token.isEmpty()) {
            // fallback to static token if available
            try {
                token = tw.fdw.makrotagram.Extra.HUGGINGFACETOKEN;
            } catch (Exception e) {
                token = "";
            }
        }
        final String finalToken = token;

        // Запускаем загрузку в фоновом потоке, чтобы избежать блокировки UI
        new Thread(() -> {
            try {
                String downloadId = downloadManager.startDownload(model.getDownloadUrl(), destinationPath, networkType, finalToken);
                // Сохраняем имя модели для отображения
                if (downloadId != null) {
                    downloadIdToModelName.put(downloadId, model.getName());
                }
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(getParentActivity(), "Загрузка модели " + model.getName() + " начата", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    Toast.makeText(getParentActivity(), "Ошибка при запуске загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void showSelectDialog(ModelEntity model) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Выбрать модель");
        builder.setMessage("Использовать модель " + model.getName() + " для работы с Local AI?");
        builder.setPositiveButton("Выбрать", (dialog, which) -> {
            // Установить путь к модели в настройках Local AI
            LocalAISettings settings = (LocalAISettings) new AISettings(UserConfig.selectedAccount).getServiceSettings(AISettings.AIServiceType.LOCAL_AI);
            settings.setModelPath(model.getPath());
            settings.save();

            Toast.makeText(getParentActivity(), "Модель " + model.getName() + " выбрана", Toast.LENGTH_SHORT).show();

            if (selectionMode) {
                // Закрыть фрагмент выбора
                finishFragment();
            }
        });
        builder.setNeutralButton("Удалить", (dialog, which) -> {
            showDeleteConfirmation(model);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void showDeleteConfirmation(ModelEntity model) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Удаление модели");
        builder.setMessage("Вы уверены, что хотите удалить модель " + model.getName() + "? Файл будет удален с устройства.");
        builder.setPositiveButton("Удалить", (dialog, which) -> {
            deleteModel(model);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void deleteModel(ModelEntity model) {
        // Проверим, является ли модель текущей выбранной
        LocalAISettings settings = (LocalAISettings) new AISettings(UserConfig.selectedAccount).getServiceSettings(AISettings.AIServiceType.LOCAL_AI);
        if (model.getPath().equals(settings.getModelPath())) {
            settings.setModelPath("");
            settings.save();
        }

        // Удалить файл модели
        File file = new File(model.getPath());
        boolean deleted = file.delete();
        if (deleted) {
            Toast.makeText(getParentActivity(), "Модель " + model.getName() + " удалена", Toast.LENGTH_SHORT).show();
            // Также удаляем родительскую директорию, если она пуста
            File parentDir = file.getParentFile();
            if (parentDir != null && parentDir.isDirectory() && parentDir.list().length == 0) {
                parentDir.delete();
            }
            // Обновляем список скачанных моделей
            refreshDownloadedModels();
        } else {
            Toast.makeText(getParentActivity(), "Ошибка удаления файла", Toast.LENGTH_SHORT).show();
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
            if (holder.getItemViewType() == 0) { // Header
                HeaderCell headerCell = (HeaderCell) holder.itemView;
                if (position == recommendedHeaderRow) {
                    headerCell.setText("Рекомендованные модели");
                } else if (position == downloadedHeaderRow) {
                    headerCell.setText("Скачанные модели");
                } else if (position == downloadsHeaderRow) {
                    headerCell.setText("Активные загрузки");
                }
            } else if (holder.getItemViewType() == 1) { // TextSettingsCell
                TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                if (recommendedRows != null) {
                    for (int i = 0; i < recommendedRows.length; i++) {
                        if (position == recommendedRows[i]) {
                            ModelItem model = recommendedModels.get(i);
                            String info = formatModelInfo(model);
                            textCell.setTextAndValue(model.getName(), info, true);
                            return;
                        }
                    }
                }
                if (downloadedRows != null) {
                    for (int i = 0; i < downloadedRows.length; i++) {
                        if (position == downloadedRows[i]) {
                            ModelEntity model = downloadedModels.get(i);
                            String info = formatModelInfo(model);
                            textCell.setTextAndValue(model.getName(), info, false);
                            return;
                        }
                    }
                }
                if (downloadRows != null) {
                    for (int i = 0; i < downloadRows.length; i++) {
                        if (position == downloadRows[i]) {
                            DownloadEntity download = activeDownloads.get(i);
                            String info = formatDownloadInfo(download);
                            textCell.setText(info, false);
                            return;
                        }
                    }
                }
            } else {
                // ShadowSectionCell - ничего не делаем
            }
        }

        private String formatModelInfo(ModelItem model) {
            StringBuilder sb = new StringBuilder();
            sb.append(model.getFormattedSize());
            if (model.isLlmSupportImage()) {
                sb.append(", 📷");
            }
            if (model.isLlmSupportAudio()) {
                sb.append(", 🎵");
            }
            if (model.getMinDeviceMemoryInGb() > 0) {
                sb.append(" ").append(model.getMinDeviceMemoryInGb()).append("GB RAM");
            }
            return sb.toString();
        }

        private String formatModelInfo(ModelEntity model) {
            long size = model.getSize();
            String formattedSize;
            if (size < 1024) {
                formattedSize = size + " B";
            } else if (size < 1024 * 1024) {
                formattedSize = String.format("%.1f KB", size / 1024.0);
            } else if (size < 1024 * 1024 * 1024) {
                formattedSize = String.format("%.1f MB", size / (1024.0 * 1024.0));
            } else {
                formattedSize = String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
            }
            return formattedSize + " • " + model.getFormat();
        }

        private String formatDownloadInfo(DownloadEntity download) {
            long total = download.getTotalBytes();
            long downloaded = download.getDownloadedBytes();
            String modelName = downloadIdToModelName.get(download.getId());
            if (modelName == null) {
                modelName = new File(download.getDestination()).getName();
            }
            if (total > 0) {
                int percent = (int) (downloaded * 100 / total);
                return modelName + " - " + percent + "% (" + formatBytes(downloaded) + " / " + formatBytes(total) + ")";
            } else {
                return modelName + " - " + formatBytes(downloaded) + " / ?";
            }
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.1f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (recommendedRows != null) {
                for (int r : recommendedRows) {
                    if (position == r) return true;
                }
            }
            if (downloadedRows != null) {
                for (int r : downloadedRows) {
                    if (position == r) return true;
                }
            }
            if (downloadRows != null) {
                for (int r : downloadRows) {
                    if (position == r) return true;
                }
            }
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == 0) {
                view = new HeaderCell(mContext);
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == 1) {
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
            if (position == recommendedHeaderRow || position == downloadedHeaderRow || position == downloadsHeaderRow) {
                return 0;
            }
            if ((recommendedRows != null && contains(recommendedRows, position)) ||
                    (downloadedRows != null && contains(downloadedRows, position)) ||
                    (downloadRows != null && contains(downloadRows, position))) {
                return 1;
            }
            return 2;
        }

        private boolean contains(int[] array, int value) {
            for (int v : array) {
                if (v == value) return true;
            }
            return false;
        }
    }
}