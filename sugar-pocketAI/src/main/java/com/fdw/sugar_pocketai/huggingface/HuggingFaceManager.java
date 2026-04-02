package com.fdw.sugar_pocketai.huggingface;

import android.content.Context;
import com.fdw.sugar_pocketai.download.DownloadManager;
import com.fdw.sugar_pocketai.download.NetworkType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HuggingFaceManager {
    private final Context context;
    private final HuggingFaceClient client;
    private final DownloadManager downloadManager;
    private String authToken;

    public HuggingFaceManager(Context context) {
        this.context = context.getApplicationContext();
        this.client = new HuggingFaceClient();
        this.downloadManager = new DownloadManager(context);
    }

    public void setAuthToken(String token) {
        this.authToken = token;
        client.setAuthToken(token);
    }

    /**
     * Search for models matching query and filter by GGUF format.
     */
    public List<HuggingFaceClient.ModelInfo> searchGGUFModels(String query, int limit) throws IOException {
        try {
            List<HuggingFaceClient.ModelInfo> allModels = client.searchModels(query, limit, "gguf,conversational");
            List<HuggingFaceClient.ModelInfo> filtered = new ArrayList<>();
            for (HuggingFaceClient.ModelInfo model : allModels) {
                if (isLikelyGGUFModel(model)) {
                    filtered.add(model);
                }
            }
            return filtered;
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error", e);
        }
    }

    /**
     * Heuristic to detect if a model likely contains GGUF files.
     */
    private boolean isLikelyGGUFModel(HuggingFaceClient.ModelInfo model) {
        String tags = model.getTags().toLowerCase(Locale.US);
        return tags.contains("gguf") || tags.contains("llama") || tags.contains("ggml");
    }

    /**
     * Get GGUF files from a model.
     */
    public List<String> getGGUFFiles(String modelId) throws IOException {
        try {
            List<String> allFiles = client.getModelFiles(modelId);
            List<String> ggufFiles = new ArrayList<>();
            for (String file : allFiles) {
                if (file.toLowerCase(Locale.US).endsWith(".gguf")) {
                    ggufFiles.add(file);
                }
            }
            return ggufFiles;
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error", e);
        }
    }

    /**
     * Start downloading a GGUF file from Hugging Face.
     * @param modelId Full model ID (e.g., "TheBloke/Llama-2-7B-GGUF")
     * @param filename File name (e.g., "llama-2-7b.Q4_K_M.gguf")
     * @param destination Local file path
     * @return Download ID
     */
    public String downloadGGUFFile(String modelId, String filename, String destination) {
        String url = client.getFileDownloadUrl(modelId, filename);
        return downloadManager.startDownload(url, destination, NetworkType.ANY, authToken);
    }

    /**
     * Convenience method to download the first GGUF file found in a model.
     */
    public String downloadFirstGGUFFile(String modelId, String destination) throws IOException {
        List<String> files = getGGUFFiles(modelId);
        if (files.isEmpty()) {
            throw new IOException("No GGUF files found in model " + modelId);
        }
        String filename = files.get(0);
        return downloadGGUFFile(modelId, filename, destination);
    }

    /**
     * Search for models matching query and filter by LiteRT format.
     * Uses the "litert-lm" tag filter supported by Hugging Face API.
     */
    public List<HuggingFaceClient.ModelInfo> searchLiteRTModels(String query, int limit) throws IOException {
        try {
            // Use the official tag filter for LiteRT-LM models
            List<HuggingFaceClient.ModelInfo> allModels = client.searchModels(query, limit, "litert-lm");
            // Return all results; the API already filtered by tag.
            return allModels;
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error", e);
        }
    }

    /**
     * Heuristic to detect if a model likely contains LiteRT files.
     * Kept for compatibility but not used in searchLiteRTModels.
     */
    private boolean isLikelyLiteRTModel(HuggingFaceClient.ModelInfo model) {
        String tags = model.getTags().toLowerCase(Locale.US);
        return tags.contains("litert-lm") || tags.contains("litertlm") || tags.contains("litert");
    }

    /**
     * Get LiteRT files from a model.
     */
    public List<String> getLiteRTFiles(String modelId) throws IOException {
        try {
            List<String> allFiles = client.getModelFiles(modelId);
            List<String> litertFiles = new ArrayList<>();
            for (String file : allFiles) {
                if (file.toLowerCase(Locale.US).endsWith(".litertlm")) {
                    litertFiles.add(file);
                }
            }
            return litertFiles;
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error", e);
        }
    }

    /**
     * Start downloading a LiteRT file from Hugging Face.
     * @param modelId Full model ID (e.g., "google/gemma-3n-E2B-it-litert-lm")
     * @param filename File name (e.g., "gemma-3n-E2B-it-int4.litertlm")
     * @param destination Local file path
     * @return Download ID
     */
    public String downloadLiteRTFile(String modelId, String filename, String destination) {
        String url = client.getFileDownloadUrl(modelId, filename);
        return downloadManager.startDownload(url, destination, NetworkType.ANY, authToken);
    }

    /**
     * Convenience method to download the first LiteRT file found in a model.
     */
    public String downloadFirstLiteRTFile(String modelId, String destination) throws IOException {
        List<String> files = getLiteRTFiles(modelId);
        if (files.isEmpty()) {
            throw new IOException("No LiteRT files found in model " + modelId);
        }
        String filename = files.get(0);
        return downloadLiteRTFile(modelId, filename, destination);
    }

    /**
     * Get detailed model info.
     */
    public HuggingFaceClient.ModelDetail getModelDetail(String modelId) throws IOException {
        try {
            return client.getModelDetail(modelId);
        } catch (org.json.JSONException e) {
            throw new IOException("JSON parsing error", e);
        }
    }
}