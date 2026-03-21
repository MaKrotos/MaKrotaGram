package com.fdw.sugar_pocketai.model;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ModelManager {
    private static final String TAG = "ModelManager";
    private static final String[] MODEL_EXTENSIONS = {".gguf", ".bin", ".ggml", ".model", ".litertlm", ".safetensors", ".pt", ".pth", ".onnx", ".h5", ".tflite", ".pb", ".mlmodel", ".ckpt", ".pkl", ".joblib", ".mar", ".pmml"};

    private final Context context;
    private final ModelDao modelDao;
    private final Executor executor;

    public ModelManager(Context context) {
        this.context = context;
        this.modelDao = ModelDatabase.getInstance(context).modelDao();
        this.executor = Executors.newSingleThreadExecutor();
    }

    /**
     * Scan a directory for model files and add them to the database.
     * @param directoryPath Path to scan (if null, uses default models directory)
     * @return List of newly added models
     */
    public List<ModelEntity> scanAndAddModels(String directoryPath) {
        List<ModelEntity> added = new ArrayList<>();
        File dir = directoryPath != null ? new File(directoryPath) : getDefaultModelsDir();
        Log.i(TAG, "Scanning directory: " + dir.getAbsolutePath());
        Log.i(TAG, "Directory exists? " + dir.exists());
        Log.i(TAG, "Is directory? " + dir.isDirectory());
        if (!dir.exists() || !dir.isDirectory()) {
            Log.w(TAG, "Directory does not exist: " + dir.getAbsolutePath());
            // Try to create directory
            if (dir.mkdirs()) {
                Log.i(TAG, "Directory created: " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create directory");
                return added;
            }
        }

        // Log all files for debugging
        File[] allFiles = dir.listFiles();
        if (allFiles == null) {
            Log.i(TAG, "No files found in directory (listFiles returned null)");
            // Check if specific test file exists
            File testFile = new File(dir, "lena.jpg");
            Log.i(TAG, "Test file lena.jpg exists? " + testFile.exists() + ", path: " + testFile.getAbsolutePath());
            return added;
        }
        Log.i(TAG, "Total files in directory: " + allFiles.length);
        for (File file : allFiles) {
            Log.i(TAG, "File: " + file.getName() + " (dir? " + file.isDirectory() + ")");
        }
        // If no files, check for lena.jpg specifically
        if (allFiles.length == 0) {
            File testFile = new File(dir, "lena.jpg");
            Log.i(TAG, "Directory empty but lena.jpg exists? " + testFile.exists() + ", path: " + testFile.getAbsolutePath());
        }

        File[] modelFiles = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.isDirectory()) {
                    Log.v(TAG, "Reject directory: " + file.getName());
                    return false;
                }
                String name = file.getName().toLowerCase();
                Log.v(TAG, "Checking file: " + file.getName() + " (lowercase: " + name + ")");
                for (String ext : MODEL_EXTENSIONS) {
                    if (name.endsWith(ext)) {
                        Log.v(TAG, "Matched extension: " + ext);
                        return true;
                    }
                }
                Log.v(TAG, "No extension matched");
                return false;
            }
        });

        if (modelFiles == null) {
            Log.i(TAG, "No model files after filtering (should not happen)");
            return added;
        }

        Log.i(TAG, "Found " + modelFiles.length + " potential model file(s)");
        for (File file : modelFiles) {
            Log.i(TAG, " - " + file.getName());
        }

        for (File file : modelFiles) {
            ModelEntity existing = modelDao.getByPath(file.getAbsolutePath());
            if (existing == null) {
                ModelEntity model = createModelEntity(file);
                modelDao.insert(model);
                added.add(model);
                Log.i(TAG, "Added model: " + model.getName());
            } else {
                Log.i(TAG, "Model already in database: " + file.getName());
            }
        }
        Log.i(TAG, "Total newly added: " + added.size());
        return added;
    }

    /**
     * Create a dummy model file for testing and add it to the database.
     * @return The created ModelEntity, or null if failed.
     */
    public ModelEntity createDummyModel() {
        // Create a dummy file in the app's internal storage (no permissions needed)
        File dummyDir = new File(context.getFilesDir(), "models");
        if (!dummyDir.exists()) {
            dummyDir.mkdirs();
        }
        File dummyFile = new File(dummyDir, "dummy.gguf");
        try {
            if (!dummyFile.exists()) {
                FileOutputStream fos = new FileOutputStream(dummyFile);
                fos.write("DUMMY MODEL CONTENT".getBytes());
                fos.close();
                Log.i(TAG, "Dummy model file created: " + dummyFile.getAbsolutePath());
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to create dummy model file", e);
            return null;
        }

        // Check if already in database
        ModelEntity existing = modelDao.getByPath(dummyFile.getAbsolutePath());
        if (existing != null) {
            return existing;
        }

        ModelEntity model = createModelEntity(dummyFile);
        executor.execute(() -> {
            modelDao.insert(model);
            Log.i(TAG, "Dummy model added to database: " + model.getName());
        });
        return model;
    }

    /**
     * Get all models from database as LiveData.
     */
    public LiveData<List<ModelEntity>> getAllModels() {
        return modelDao.getAll();
    }

    /**
     * Get model by ID.
     */
    public ModelEntity getModel(String id) {
        return modelDao.getById(id);
    }

    /**
     * Delete model by ID (and optionally delete the file).
     */
    public boolean deleteModel(String id, boolean deleteFile) {
        ModelEntity model = modelDao.getById(id);
        if (model == null) return false;
        if (deleteFile) {
            File file = new File(model.getPath());
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    Log.w(TAG, "Failed to delete file: " + model.getPath());
                }
            }
        }
        modelDao.deleteById(id);
        return true;
    }

    /**
     * Update model metadata.
     */
    public void updateModel(ModelEntity model) {
        modelDao.update(model);
    }

    private File getDefaultModelsDir() {
        // Use app's internal storage to avoid permission issues
        return new File(context.getFilesDir(), "models");
    }

    private ModelEntity createModelEntity(File file) {
        String id = UUID.randomUUID().toString();
        String name = file.getName();
        String path = file.getAbsolutePath();
        long size = file.length();
        String format = getFormatFromExtension(file);
        String parameters = "{}"; // placeholder
        long createdAt = file.lastModified();

        return new ModelEntity(id, name, path, size, format, parameters, createdAt);
    }

    private String getFormatFromExtension(File file) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".gguf")) return "GGUF";
        if (name.endsWith(".bin")) return "BIN";
        if (name.endsWith(".ggml")) return "GGML";
        if (name.endsWith(".litertlm")) return "LITERTLM";
        return "UNKNOWN";
    }
}