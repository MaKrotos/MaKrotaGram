package com.fdw.sugar_pocketai.inference;

import android.content.Context;
import android.util.Log;

public class InferenceEngineFactory {
    private static final String TAG = "InferenceEngineFactory";

    public enum EngineType {
        LITERT
    }

    /**
     * Create an inference engine of the specified type.
     */
    public static InferenceEngine createEngine(EngineType type) {
        return createEngine(type, null);
    }

    /**
     * Create an inference engine of the specified type with optional context.
     * Context is required for LiteRT engine.
     */
    public static InferenceEngine createEngine(EngineType type, Context context) {
        // Only LITERT is supported
        if (type != EngineType.LITERT) {
            throw new IllegalArgumentException("Unsupported engine type: " + type);
        }
        LiteRTEngine engine = new LiteRTEngine();
        if (context != null) {
            engine.setContext(context);
        } else {
            Log.w(TAG, "Context is null for LiteRT engine, may fail during init");
        }
        return engine;
    }

    /**
     * Create an engine based on model file extension.
     * @param modelPath path to model file
     * @param context Android context (required for LiteRT)
     * @return appropriate inference engine
     */
    public static InferenceEngine createEngineForModel(String modelPath, Context context) {
        if (modelPath == null) {
            Log.w(TAG, "Model path is null, creating LiteRT engine as default");
            return createEngine(EngineType.LITERT, context);
        }
        String lowerPath = modelPath.toLowerCase();
        Log.d(TAG, "createEngineForModel: path=" + modelPath + ", lower=" + lowerPath);
        if (lowerPath.endsWith(".litertlm")) {
            Log.i(TAG, "Detected LiteRT model, creating LiteRT engine");
            return createEngine(EngineType.LITERT, context);
        } else {
            Log.w(TAG, "Unknown model extension '" + lowerPath + "', creating LiteRT engine (may not support this format)");
            return createEngine(EngineType.LITERT, context);
        }
    }

    /**
     * Create a default engine (LiteRT).
     */
    public static InferenceEngine createDefaultEngine() {
        return createEngine(EngineType.LITERT);
    }
}