package com.fdw.sugar_pocketai.inference;

import android.graphics.Bitmap;
import java.util.List;

public interface InferenceEngine {
    /**
     * Initialize the engine with a model file.
     * @param modelPath Path to the model file (e.g., .gguf)
     * @param config Configuration for inference
     * @return true if initialization succeeded
     */
    boolean init(String modelPath, InferenceConfig config);

    /**
     * Perform inference on a prompt.
     * @param prompt Input text
     * @return InferenceResult containing generated text and metrics
     */
    InferenceResult infer(String prompt);

    /**
     * Perform inference with custom configuration (overrides initial config).
     * @param prompt Input text
     * @param config Override configuration
     * @return InferenceResult
     */
    InferenceResult infer(String prompt, InferenceConfig config);

    /**
     * Perform inference with images and/or audio.
     * @param prompt Input text (may be empty if images/audio provide enough context)
     * @param config Override configuration
     * @param images List of Bitmap images (can be null or empty)
     * @param audioClips List of audio byte arrays (can be null or empty)
     * @return InferenceResult
     */
    default InferenceResult infer(String prompt, InferenceConfig config,
                                  List<Bitmap> images, List<byte[]> audioClips) {
        // Default implementation falls back to text-only inference
        // Override in engine that supports multimodal input
        return infer(prompt, config);
    }

    /**
     * Perform streaming inference with a callback for partial results.
     * @param prompt Input text
     * @param config Override configuration (or null to use default)
     * @param callback Callback to receive streaming results
     */
    void streamInfer(String prompt, InferenceConfig config, InferenceStreamCallback callback);

    /**
     * Perform streaming inference with images and/or audio.
     * @param prompt Input text
     * @param config Override configuration (or null to use default)
     * @param images List of Bitmap images (can be null or empty)
     * @param audioClips List of audio byte arrays (can be null or empty)
     * @param callback Callback to receive streaming results
     */
    default void streamInfer(String prompt, InferenceConfig config,
                             List<Bitmap> images, List<byte[]> audioClips,
                             InferenceStreamCallback callback) {
        // Default implementation falls back to text-only streaming
        streamInfer(prompt, config, callback);
    }

    /**
     * Release resources and unload model.
     */
    void release();

    /**
     * Check if engine is loaded and ready.
     */
    boolean isLoaded();

    /**
     * Get current model path.
     */
    String getModelPath();
}