package com.fdw.sugar_pocketai.huggingface;

import android.util.Log;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HuggingFaceClient {
    private static final String TAG = "HuggingFaceClient";
    private static final String BASE_URL = "https://huggingface.co/api";
    private static final String DOWNLOAD_BASE_URL = "https://huggingface.co";

    private final OkHttpClient client;
    private String authToken;

    public HuggingFaceClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void setAuthToken(String token) {
        this.authToken = token;
    }

    /**
     * Search models by query.
     * @param query Search term
     * @param limit Maximum number of results
     * @return List of model info
     */
    public List<ModelInfo> searchModels(String query, int limit) throws IOException, JSONException {
        return searchModels(query, limit, "");
    }

    /**
     * Search models by query with optional filter.
     * @param query Search term
     * @param limit Maximum number of results
     * @param filter Comma-separated tags to filter by (e.g., "gguf,conversational")
     * @return List of model info
     */
    public List<ModelInfo> searchModels(String query, int limit, String filter) throws IOException, JSONException {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/models").newBuilder();
        urlBuilder.addQueryParameter("search", query);
        urlBuilder.addQueryParameter("limit", String.valueOf(limit));
        urlBuilder.addQueryParameter("sort", "downloads");
        urlBuilder.addQueryParameter("direction", "-1");
        if (filter != null && !filter.trim().isEmpty()) {
            urlBuilder.addQueryParameter("filter", filter);
        }
        String url = urlBuilder.build().toString();

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        Request request = requestBuilder.build();

        Log.d(TAG, "Searching models: " + query + (filter != null && !filter.isEmpty() ? " filter=" + filter : ""));
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code());
            }
            String jsonData = response.body().string();
            JSONArray jsonArray = new JSONArray(jsonData);
            List<ModelInfo> models = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                ModelInfo info = parseModelInfo(obj);
                models.add(info);
            }
            Log.d(TAG, "Found " + models.size() + " models");
            return models;
        }
    }

    /**
     * Get detailed model info including files.
     * @param modelId Full model ID (e.g., "TheBloke/Llama-2-7B-GGUF")
     * @return Model details
     */
    public ModelDetail getModelDetail(String modelId) throws IOException, JSONException {
        String url = BASE_URL + "/models/" + modelId;
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        Request request = requestBuilder.build();

        Log.d(TAG, "Fetching model detail: " + modelId);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code());
            }
            String jsonData = response.body().string();
            JSONObject obj = new JSONObject(jsonData);
            return parseModelDetail(obj);
        }
    }

    /**
     * Get download URL for a specific file in a model.
     * @param modelId Full model ID
     * @param filename File name (e.g., "llama-2-7b.Q4_K_M.gguf")
     * @return Direct download URL
     */
    public String getFileDownloadUrl(String modelId, String filename) {
        return DOWNLOAD_BASE_URL + "/" + modelId + "/resolve/main/" + filename;
    }

    /**
     * Get list of files in a model.
     * @param modelId Full model ID
     * @return List of file names
     */
    public List<String> getModelFiles(String modelId) throws IOException, JSONException {
        String url = BASE_URL + "/models/" + modelId + "/tree/main";
        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        Request request = requestBuilder.build();

        Log.d(TAG, "Fetching model files: " + modelId);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code());
            }
            String jsonData = response.body().string();
            JSONArray jsonArray = new JSONArray(jsonData);
            List<String> files = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String type = obj.getString("type");
                if ("file".equals(type)) {
                    files.add(obj.getString("path"));
                }
            }
            return files;
        }
    }

    private ModelInfo parseModelInfo(JSONObject obj) throws JSONException {
        ModelInfo info = new ModelInfo();
        info.setId(obj.getString("id"));
        info.setAuthor(obj.optString("author", ""));
        info.setDownloads(obj.optLong("downloads", 0));
        info.setLikes(obj.optLong("likes", 0));
        info.setTags(obj.optString("tags", ""));
        info.setPipelineTag(obj.optString("pipeline_tag", ""));
        info.setPrivate(obj.optBoolean("private", false));
        return info;
    }

    private ModelDetail parseModelDetail(JSONObject obj) throws JSONException {
        ModelDetail detail = new ModelDetail();
        detail.setId(obj.getString("id"));
        detail.setAuthor(obj.optString("author", ""));
        detail.setDownloads(obj.optLong("downloads", 0));
        detail.setLikes(obj.optLong("likes", 0));
        detail.setTags(obj.optString("tags", ""));
        detail.setPipelineTag(obj.optString("pipeline_tag", ""));
        detail.setPrivate(obj.optBoolean("private", false));
        detail.setLastModified(obj.optString("lastModified", ""));
        detail.setCreatedAt(obj.optString("createdAt", ""));
        return detail;
    }

    public static class ModelInfo {
        private String id;
        private String author;
        private long downloads;
        private long likes;
        private String tags;
        private String pipelineTag;
        private boolean isPrivate;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public long getDownloads() { return downloads; }
        public void setDownloads(long downloads) { this.downloads = downloads; }
        public long getLikes() { return likes; }
        public void setLikes(long likes) { this.likes = likes; }
        public String getTags() { return tags; }
        public void setTags(String tags) { this.tags = tags; }
        public String getPipelineTag() { return pipelineTag; }
        public void setPipelineTag(String pipelineTag) { this.pipelineTag = pipelineTag; }
        public boolean isPrivate() { return isPrivate; }
        public void setPrivate(boolean aPrivate) { isPrivate = aPrivate; }

        @Override
        public String toString() {
            return "ModelInfo{" +
                    "id='" + id + '\'' +
                    ", downloads=" + downloads +
                    '}';
        }
    }

    public static class ModelDetail extends ModelInfo {
        private String lastModified;
        private String createdAt;

        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
}