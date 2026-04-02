package com.fdw.sugar_pocketai.download;

import androidx.lifecycle.LiveData;
import androidx.room.*;

import java.util.List;

@Dao
public interface DownloadDao {
    @Query("SELECT * FROM downloads")
    LiveData<List<DownloadEntity>> getAllDownloads();

    @Query("SELECT * FROM downloads WHERE id = :downloadId")
    DownloadEntity getDownload(String downloadId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDownload(DownloadEntity download);

    @Update
    void updateDownload(DownloadEntity download);

    @Delete
    void deleteDownload(DownloadEntity download);

    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :totalBytes, status = :status WHERE id = :downloadId")
    void updateProgress(String downloadId, long bytes, long totalBytes, DownloadStatus status);

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :downloadId")
    void updateStatus(String downloadId, DownloadStatus status, String error);

    // New methods for resume support
    @Query("UPDATE downloads SET resumable = :resumable WHERE id = :downloadId")
    void updateResumable(String downloadId, boolean resumable);

    @Query("UPDATE downloads SET retryCount = :retryCount WHERE id = :downloadId")
    void updateRetryCount(String downloadId, int retryCount);

    @Query("UPDATE downloads SET lastError = :lastError WHERE id = :downloadId")
    void updateLastError(String downloadId, String lastError);

    @Query("UPDATE downloads SET downloadedBytesAtPause = :bytes WHERE id = :downloadId")
    void updateDownloadedBytesAtPause(String downloadId, long bytes);

    // Combined update for pause
    @Query("UPDATE downloads SET status = :status, downloadedBytesAtPause = :bytes WHERE id = :downloadId")
    void updatePauseStatus(String downloadId, DownloadStatus status, long bytes);

    // Increment retry count
    @Query("UPDATE downloads SET retryCount = retryCount + 1 WHERE id = :downloadId")
    void incrementRetryCount(String downloadId);
}