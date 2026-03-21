package com.fdw.sugar_pocketai.model;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Represents a downloadable model from Hugging Face.
 * Simplified version of gallery's AllowedModel.
 */
public class ModelItem {
    private final String name;
    private final String modelId;
    private final String modelFile;
    private final String commitHash;
    private final String description;
    private final long sizeInBytes;
    private final String downloadUrl;
    private final List<String> taskTypes;
    private final boolean llmSupportImage;
    private final boolean llmSupportAudio;
    private final int minDeviceMemoryInGb;
    private final String authToken;

    public ModelItem(String name, String modelId, String modelFile, String commitHash,
                     String description, long sizeInBytes, String downloadUrl,
                     List<String> taskTypes, boolean llmSupportImage, boolean llmSupportAudio,
                     int minDeviceMemoryInGb) {
        this(name, modelId, modelFile, commitHash, description, sizeInBytes, downloadUrl,
                taskTypes, llmSupportImage, llmSupportAudio, minDeviceMemoryInGb, null);
    }

    public ModelItem(String name, String modelId, String modelFile, String commitHash,
                     String description, long sizeInBytes, String downloadUrl,
                     List<String> taskTypes, boolean llmSupportImage, boolean llmSupportAudio,
                     int minDeviceMemoryInGb, String authToken) {
        this.name = name;
        this.modelId = modelId;
        this.modelFile = modelFile;
        this.commitHash = commitHash;
        this.description = description;
        this.sizeInBytes = sizeInBytes;
        this.downloadUrl = downloadUrl;
        this.taskTypes = taskTypes;
        this.llmSupportImage = llmSupportImage;
        this.llmSupportAudio = llmSupportAudio;
        this.minDeviceMemoryInGb = minDeviceMemoryInGb;
        this.authToken = authToken;
    }

    public String getName() {
        return name;
    }

    public String getModelId() {
        return modelId;
    }

    public String getModelFile() {
        return modelFile;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getDescription() {
        return description;
    }

    public long getSizeInBytes() {
        return sizeInBytes;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public List<String> getTaskTypes() {
        return taskTypes;
    }

    public boolean isLlmSupportImage() {
        return llmSupportImage;
    }

    public boolean isLlmSupportAudio() {
        return llmSupportAudio;
    }

    public int getMinDeviceMemoryInGb() {
        return minDeviceMemoryInGb;
    }

    public String getFormattedSize() {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.1f KB", sizeInBytes / 1024.0);
        } else if (sizeInBytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", sizeInBytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", sizeInBytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    public String getAuthToken() {
        return authToken;
    }

    @NonNull
    @Override
    public String toString() {
        return name + " (" + getFormattedSize() + ")";
    }
}