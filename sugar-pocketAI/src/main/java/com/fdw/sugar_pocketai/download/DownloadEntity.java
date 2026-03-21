package com.fdw.sugar_pocketai.download;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Ignore;

@Entity(tableName = "downloads")
public class DownloadEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;
    
    @NonNull
    @ColumnInfo(name = "url")
    private String url;
    
    @NonNull
    @ColumnInfo(name = "destination")
    private String destination;
    
    @ColumnInfo(name = "totalBytes")
    private long totalBytes;
    
    @ColumnInfo(name = "downloadedBytes")
    private long downloadedBytes;
    
    @NonNull
    @ColumnInfo(name = "status")
    private DownloadStatus status;
    
    @ColumnInfo(name = "priority")
    private int priority;
    
    @NonNull
    @ColumnInfo(name = "networkType")
    private NetworkType networkType;
    
    @ColumnInfo(name = "createdAt")
    private long createdAt;
    
    @Nullable
    @ColumnInfo(name = "error")
    private String error;
    
    @Nullable
    @ColumnInfo(name = "authToken")
    private String authToken;
    
    // New fields for resume and retry
    @ColumnInfo(name = "resumable", defaultValue = "1")
    private boolean resumable;
    
    @ColumnInfo(name = "retryCount", defaultValue = "0")
    private int retryCount;
    
    @Nullable
    @ColumnInfo(name = "lastError")
    private String lastError;
    
    @ColumnInfo(name = "downloadedBytesAtPause", defaultValue = "0")
    private long downloadedBytesAtPause;

    // Constructor used by Room (must match column names)
    public DownloadEntity(@NonNull String id, @NonNull String url, @NonNull String destination,
                          long totalBytes, long downloadedBytes, @NonNull DownloadStatus status,
                          int priority, @NonNull NetworkType networkType, long createdAt,
                          @Nullable String error, @Nullable String authToken,
                          boolean resumable, int retryCount, @Nullable String lastError,
                          long downloadedBytesAtPause) {
        this.id = id;
        this.url = url;
        this.destination = destination;
        this.totalBytes = totalBytes;
        this.downloadedBytes = downloadedBytes;
        this.status = status;
        this.priority = priority;
        this.networkType = networkType;
        this.createdAt = createdAt;
        this.error = error;
        this.authToken = authToken;
        this.resumable = resumable;
        this.retryCount = retryCount;
        this.lastError = lastError;
        this.downloadedBytesAtPause = downloadedBytesAtPause;
    }

    // Convenience constructor for backward compatibility (used by existing code)
    @Ignore
    public DownloadEntity(@NonNull String id, @NonNull String url, @NonNull String destination,
                          long totalBytes, long downloadedBytes, @NonNull DownloadStatus status,
                          int priority, @NonNull NetworkType networkType, long createdAt,
                          @Nullable String error, @Nullable String authToken) {
        this(id, url, destination, totalBytes, downloadedBytes, status, priority, networkType,
             createdAt, error, authToken, true, 0, null, 0);
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    public void setUrl(@NonNull String url) {
        this.url = url;
    }

    @NonNull
    public String getDestination() {
        return destination;
    }

    public void setDestination(@NonNull String destination) {
        this.destination = destination;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    @NonNull
    public DownloadStatus getStatus() {
        return status;
    }

    public void setStatus(@NonNull DownloadStatus status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    @NonNull
    public NetworkType getNetworkType() {
        return networkType;
    }

    public void setNetworkType(@NonNull NetworkType networkType) {
        this.networkType = networkType;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Nullable
    public String getError() {
        return error;
    }

    public void setError(@Nullable String error) {
        this.error = error;
    }

    @Nullable
    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(@Nullable String authToken) {
        this.authToken = authToken;
    }

    public boolean isResumable() {
        return resumable;
    }

    public void setResumable(boolean resumable) {
        this.resumable = resumable;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    @Nullable
    public String getLastError() {
        return lastError;
    }

    public void setLastError(@Nullable String lastError) {
        this.lastError = lastError;
    }

    public long getDownloadedBytesAtPause() {
        return downloadedBytesAtPause;
    }

    public void setDownloadedBytesAtPause(long downloadedBytesAtPause) {
        this.downloadedBytesAtPause = downloadedBytesAtPause;
    }
}