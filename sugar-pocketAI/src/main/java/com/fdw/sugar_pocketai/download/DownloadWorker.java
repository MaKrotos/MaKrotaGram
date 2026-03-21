package com.fdw.sugar_pocketai.download;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadWorker extends Worker {
    private static final String TAG = "DownloadWorker";
    private static final int MAX_RETRY_COUNT = 5;
    
    private final DownloadDatabase database;
    private final OkHttpClient client;
    
    public DownloadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.database = DownloadDatabase.getInstance(context);
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }
    
    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "=== DownloadWorker started ===");
        
        // Получаем параметры
        String downloadId = getInputData().getString("downloadId");
        String url = getInputData().getString("url");
        String destinationPath = getInputData().getString("destinationPath");
        String authToken = getInputData().getString("authToken");
        String networkTypeStr = getInputData().getString("networkType");
        
        Log.d(TAG, "Download ID: " + downloadId);
        Log.d(TAG, "URL: " + url);
        Log.d(TAG, "Destination: " + destinationPath);
        Log.d(TAG, "Auth token present: " + (authToken != null && !authToken.isEmpty()));
        Log.d(TAG, "Network type: " + networkTypeStr);
        
        if (downloadId == null || url == null || destinationPath == null) {
            Log.e(TAG, "Missing required parameters");
            return Result.failure();
        }
        
        // Обновляем статус на RUNNING
        updateStatus(downloadId, DownloadStatus.RUNNING, null);
        
        // Проверяем, не существует ли уже конечный файл
        File destinationFile = new File(destinationPath);
        if (destinationFile.exists()) {
            long fileSize = destinationFile.length();
            Log.d(TAG, "File already exists: " + destinationPath + " (" + fileSize + " bytes)");
            // Если файл уже существует, считаем загрузку завершенной?
            // Для простоты удаляем и начинаем заново (или можно пропустить).
            // Но лучше проверить, соответствует ли размер ожидаемому.
            // Пока удаляем.
            if (!destinationFile.delete()) {
                Log.w(TAG, "Could not delete existing file");
            }
        }
        
        // Временный файл для докачки
        File tempFile = new File(destinationPath + ".tmp");
        long existingBytes = 0;
        boolean resumable = false;
        
        if (tempFile.exists()) {
            existingBytes = tempFile.length();
            Log.d(TAG, "Temporary file exists, size: " + existingBytes + " bytes");
            // Проверим, поддерживает ли сервер докачку
            resumable = checkServerSupport(url, authToken);
            Log.d(TAG, "Server supports resume: " + resumable);
            if (!resumable) {
                // Сервер не поддерживает, начинаем заново
                if (!tempFile.delete()) {
                    Log.w(TAG, "Could not delete temp file");
                }
                existingBytes = 0;
            }
        }
        
        try {
            // Создаём директорию, если нужно
            File parentDir = destinationFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    Log.e(TAG, "Failed to create directory: " + parentDir);
                    updateStatus(downloadId, DownloadStatus.FAILED, "Failed to create directory");
                    return Result.failure();
                }
            }
            
            // Выполняем запрос с возможным Range-заголовком
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "sugar-pocketAI/1.0");
            
            if (authToken != null && !authToken.isEmpty()) {
                String authHeader = "Bearer " + authToken;
                requestBuilder.header("Authorization", authHeader);
                Log.d(TAG, "Added Authorization header: Bearer " + maskToken(authToken));
            }
            
            if (resumable && existingBytes > 0) {
                requestBuilder.header("Range", "bytes=" + existingBytes + "-");
                Log.d(TAG, "Added Range header: bytes=" + existingBytes + "-");
            }
            
            Request request = requestBuilder.build();
            Log.d(TAG, "Executing network request for URL: " + url);
            
            Response response = client.newCall(request).execute();
            
            int responseCode = response.code();
            Log.d(TAG, "Response code: " + responseCode);
            
            if (responseCode == 416) { // Range Not Satisfiable
                // Возможно, файл уже полностью скачан
                Log.w(TAG, "Range not satisfiable, maybe already downloaded");
                if (tempFile.renameTo(destinationFile)) {
                    updateStatus(downloadId, DownloadStatus.COMPLETED, null);
                    return Result.success();
                }
            }
            
            if (!response.isSuccessful() && responseCode != 206) {
                // Ошибка сервера (не 2xx и не 206 Partial Content)
                String message = response.message();
                Log.e(TAG, "Server error: " + responseCode + " - " + message);
                
                String errorBody = null;
                if (response.body() != null) {
                    errorBody = response.body().string();
                    Log.e(TAG, "Error body: " + errorBody);
                }
                
                String errorMsg = "HTTP " + responseCode + ": " + message;
                if (responseCode == 401) {
                    errorMsg = "Authentication failed - token is invalid";
                } else if (responseCode == 403) {
                    errorMsg = "Forbidden - you don't have access to this model. Make sure you've accepted the license on Hugging Face website";
                } else if (responseCode == 404) {
                    errorMsg = "Model or file not found";
                }
                
                updateStatus(downloadId, DownloadStatus.FAILED, errorMsg);
                // Для 4xx ошибок не повторяем
                return Result.failure();
            }
            
            // Определяем общий размер файла
            long contentLength = response.body() != null ? response.body().contentLength() : -1;
            String contentRange = response.header("Content-Range");
            if (contentRange != null && contentRange.contains("/")) {
                String total = contentRange.substring(contentRange.indexOf('/') + 1);
                try {
                    contentLength = Long.parseLong(total);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse Content-Range total: " + total);
                }
            }
            if (contentLength <= 0) {
                // Если размер неизвестен, используем значение из заголовка Content-Length
                contentLength = response.body().contentLength();
            }
            Log.d(TAG, "Content length: " + contentLength + " bytes");
            
            // Если это докачка, общий размер может быть больше existingBytes + contentLength
            long totalBytes = contentLength;
            if (responseCode == 206 && existingBytes > 0) {
                totalBytes = existingBytes + contentLength;
                Log.d(TAG, "Resuming download, total will be: " + totalBytes);
            }
            
            // Скачиваем файл
            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFile, existingBytes > 0)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = existingBytes;
                long lastProgressUpdate = System.currentTimeMillis();
                
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Обновляем прогресс каждые 500мс
                    long now = System.currentTimeMillis();
                    if (now - lastProgressUpdate > 500) {
                        updateProgress(downloadId, totalBytesRead, totalBytes);
                        setProgressAsync(new Data.Builder()
                            .putLong("downloadedBytes", totalBytesRead)
                            .putLong("totalBytes", totalBytes)
                            .build());
                        Log.d(TAG, "Progress: " + totalBytesRead + "/" + totalBytes);
                        lastProgressUpdate = now;
                    }
                }
                
                outputStream.flush();
                Log.d(TAG, "Download finished, total bytes read: " + totalBytesRead);
                
                // Переименовываем временный файл в конечный
                if (tempFile.renameTo(destinationFile)) {
                    Log.d(TAG, "File saved successfully: " + destinationPath);
                    Log.d(TAG, "Final file size: " + destinationFile.length() + " bytes");
                    
                    updateProgress(downloadId, totalBytesRead, totalBytes);
                    updateStatus(downloadId, DownloadStatus.COMPLETED, null);
                    return Result.success();
                } else {
                    Log.e(TAG, "Failed to rename temp file");
                    updateStatus(downloadId, DownloadStatus.FAILED, "Failed to rename temp file");
                    return Result.failure();
                }
                
            } catch (IOException e) {
                Log.e(TAG, "IO Error during download", e);
                // Увеличиваем счетчик попыток
                database.downloadDao().incrementRetryCount(downloadId);
                int retryCount = database.downloadDao().getDownload(downloadId).getRetryCount();
                if (retryCount >= MAX_RETRY_COUNT) {
                    updateStatus(downloadId, DownloadStatus.FAILED, "IO Error after " + retryCount + " retries: " + e.getMessage());
                    return Result.failure();
                }
                // Возвращаем retry для повторной попытки
                Log.d(TAG, "Retrying download, attempt " + retryCount);
                return Result.retry();
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error", e);
            updateStatus(downloadId, DownloadStatus.FAILED, "Unexpected error: " + e.getMessage());
            // Для неожиданных ошибок также пробуем повторить, если это сетевой сбой
            if (e instanceof IOException) {
                database.downloadDao().incrementRetryCount(downloadId);
                int retryCount = database.downloadDao().getDownload(downloadId).getRetryCount();
                if (retryCount < MAX_RETRY_COUNT) {
                    return Result.retry();
                }
            }
            return Result.failure();
        }
    }
    
    private boolean checkServerSupport(String url, String authToken) {
        // Простая проверка: отправляем HEAD запрос и смотрим заголовок Accept-Ranges
        try {
            Request.Builder requestBuilder = new Request.Builder()
                    .url(url)
                    .head()
                    .header("User-Agent", "sugar-pocketAI/1.0");
            if (authToken != null && !authToken.isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + authToken);
            }
            Request request = requestBuilder.build();
            Response response = client.newCall(request).execute();
            String acceptRanges = response.header("Accept-Ranges");
            boolean supports = "bytes".equalsIgnoreCase(acceptRanges);
            response.close();
            return supports;
        } catch (Exception e) {
            Log.w(TAG, "Failed to check server support, assuming false", e);
            return false;
        }
    }
    
    private void updateStatus(String downloadId, DownloadStatus status, String error) {
        try {
            database.downloadDao().updateStatus(downloadId, status, error);
            Log.d(TAG, "Status updated - ID: " + downloadId + ", Status: " + status + ", Error: " + error);
        } catch (Exception e) {
            Log.e(TAG, "Error updating status", e);
        }
    }
    
    private void updateProgress(String downloadId, long downloadedBytes, long totalBytes) {
        try {
            database.downloadDao().updateProgress(downloadId, downloadedBytes, totalBytes, DownloadStatus.RUNNING);
            Log.d(TAG, "Progress updated - ID: " + downloadId + ", Downloaded: " + downloadedBytes + "/" + totalBytes);
        } catch (Exception e) {
            Log.e(TAG, "Error updating progress", e);
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "****";
        return token.substring(0, 5) + "..." + token.substring(token.length() - 4);
    }
}