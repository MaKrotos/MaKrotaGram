package com.fdw.sugar_pocketai.download;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.File;
import java.util.List;
import java.util.UUID;

public class DownloadManager {
    private static final String TAG = "DownloadManager";
    
    private final Context context;
    private final WorkManager workManager;
    private final DownloadDatabase database;
    
    public DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.workManager = WorkManager.getInstance(this.context);
        this.database = DownloadDatabase.getInstance(this.context);
    }
    
    public String startDownload(String url, String destinationPath, 
                                NetworkType networkType, String authToken) {
        
        Log.d(TAG, "=== startDownload ===");
        Log.d(TAG, "URL: " + url);
        Log.d(TAG, "Destination: " + destinationPath);
        Log.d(TAG, "NetworkType: " + networkType);
        Log.d(TAG, "AuthToken: " + (authToken != null ? "present (length: " + authToken.length() + ")" : "null"));
        
        // Создаём уникальный ID для загрузки
        String downloadId = UUID.randomUUID().toString();
        
        // Создаём запись в БД
        DownloadEntity entity = new DownloadEntity(
            downloadId,
            url,
            destinationPath,
            0L, // totalBytes
            0L, // downloadedBytes
            DownloadStatus.PENDING,
            0, // priority
            networkType,
            System.currentTimeMillis(),
            null, // error
            authToken
        );
        
        // Сохраняем в БД
        database.downloadDao().insertDownload(entity);
        Log.d(TAG, "Download entity saved with ID: " + downloadId);
        
        // Создаём Constraints для WorkManager
        Constraints.Builder constraintsBuilder = new Constraints.Builder();
        
        // Конвертируем ваш NetworkType в androidx.work.NetworkType
        androidx.work.NetworkType workNetworkType;
        if (networkType == NetworkType.WIFI) {
            workNetworkType = androidx.work.NetworkType.UNMETERED;
        } else if (networkType == NetworkType.CELLULAR) {
            workNetworkType = androidx.work.NetworkType.METERED;
        } else {
            workNetworkType = androidx.work.NetworkType.CONNECTED;
        }
        
        constraintsBuilder.setRequiredNetworkType(workNetworkType);
        Constraints constraints = constraintsBuilder.build();
        
        // Создаём Data с параметрами для Worker
        Data.Builder dataBuilder = new Data.Builder()
                .putString("downloadId", downloadId)
                .putString("url", url)
                .putString("destinationPath", destinationPath)
                .putString("networkType", networkType.name());
        
        // Добавляем токен, если есть
        if (authToken != null && !authToken.isEmpty()) {
            dataBuilder.putString("authToken", authToken);
            Log.d(TAG, "Auth token added to work data");
        } else {
            Log.d(TAG, "No auth token provided");
        }
        
        Data workData = dataBuilder.build();
        
        // Создаём WorkRequest
        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DownloadWorker.class)
                .setInputData(workData)
                .setConstraints(constraints)
                .addTag(downloadId)
                .build();
        
        // Запускаем работу
        workManager.enqueue(workRequest);
        Log.d(TAG, "Work enqueued for download ID: " + downloadId);
        
        return downloadId;
    }
    
    // НОВЫЙ МЕТОД: получить все загрузки как LiveData
    public LiveData<List<DownloadEntity>> getAllDownloads() {
        return database.downloadDao().getAllDownloads();
    }
    
    // НОВЫЙ МЕТОД: получить конкретную загрузку
    public DownloadEntity getDownload(String downloadId) {
        return database.downloadDao().getDownload(downloadId);
    }
    
    // НОВЫЙ МЕТОД: приостановить загрузку
    public void pauseDownload(String downloadId) {
        Log.d(TAG, "Pausing download: " + downloadId);
        
        // Отменяем WorkManager задачу
        workManager.cancelAllWorkByTag(downloadId);
        
        // Обновляем статус в БД
        database.downloadDao().updateStatus(downloadId, DownloadStatus.PAUSED, "Paused by user");
    }
    
    // НОВЫЙ МЕТОД: возобновить загрузку
    public void resumeDownload(String downloadId) {
        Log.d(TAG, "Resuming download: " + downloadId);
        
        // Получаем информацию о загрузке
        DownloadEntity entity = database.downloadDao().getDownload(downloadId);
        
        if (entity != null) {
            // Перезапускаем загрузку с тем же ID
            String newDownloadId = startDownload(
                entity.getUrl(),
                entity.getDestination(),
                entity.getNetworkType(),
                entity.getAuthToken()
            );
            
            Log.d(TAG, "Resumed with new ID: " + newDownloadId + " (old ID: " + downloadId + ")");
        }
    }
    
    // ИСПРАВЛЕННЫЙ МЕТОД: отмена загрузки (без второго параметра)
    public void cancelDownload(String downloadId) {
        Log.d(TAG, "Cancelling download: " + downloadId);
        
        // Отменяем WorkManager задачу
        workManager.cancelAllWorkByTag(downloadId);
        
        // Обновляем статус в БД
        database.downloadDao().updateStatus(downloadId, DownloadStatus.CANCELLED, "Cancelled by user");
        
        // Удаляем временные файлы
        DownloadEntity entity = database.downloadDao().getDownload(downloadId);
        if (entity != null) {
            String destinationPath = entity.getDestination();
            File tempFile = new File(destinationPath + ".tmp");
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    // Дополнительный метод: отмена с удалением файла
    public void cancelDownload(String downloadId, boolean deleteFile) {
        Log.d(TAG, "Cancelling download with deleteFile=" + deleteFile + ": " + downloadId);
        
        // Отменяем WorkManager задачу
        workManager.cancelAllWorkByTag(downloadId);
        
        // Обновляем статус в БД
        database.downloadDao().updateStatus(downloadId, DownloadStatus.CANCELLED, "Cancelled by user");
        
        if (deleteFile) {
            DownloadEntity entity = database.downloadDao().getDownload(downloadId);
            if (entity != null) {
                String destinationPath = entity.getDestination();
                File file = new File(destinationPath);
                File tempFile = new File(destinationPath + ".tmp");
                
                if (file.exists()) {
                    file.delete();
                }
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                
                Log.d(TAG, "Deleted files for download: " + downloadId);
            }
        }
    }
    
    // Метод для повторной попытки загрузки
    public String retryDownload(String downloadId) {
        DownloadEntity entity = database.downloadDao().getDownload(downloadId);
        if (entity != null) {
            return startDownload(
                entity.getUrl(),
                entity.getDestination(),
                entity.getNetworkType(),
                entity.getAuthToken()
            );
        }
        return null;
    }
}