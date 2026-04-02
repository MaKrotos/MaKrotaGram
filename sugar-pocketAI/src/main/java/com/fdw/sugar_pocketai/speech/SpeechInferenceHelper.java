package com.fdw.sugar_pocketai.speech;

import android.content.Context;

import com.fdw.sugar_pocketai.inference.InferenceEngine;
import com.fdw.sugar_pocketai.inference.InferenceResult;

/**
 * Helper that combines speech recognition with inference.
 * Listens for speech, converts to text, and runs inference on the recognized text.
 */
public class SpeechInferenceHelper {
    private final Context context;
    private final InferenceEngine inferenceEngine;
    private SpeechRecognizerHelper speechHelper;
    private SpeechInferenceCallback callback;

    public SpeechInferenceHelper(Context context, InferenceEngine inferenceEngine) {
        this.context = context.getApplicationContext();
        this.inferenceEngine = inferenceEngine;
    }

    /**
     * Start listening for speech and perform inference on the result.
     * @param callback Callback to receive inference results or errors.
     */
    public void startListening(SpeechInferenceCallback callback) {
        this.callback = callback;
        if (speechHelper == null) {
            speechHelper = new SpeechRecognizerHelper(context);
        }
        speechHelper.startRecognition(new SpeechRecognitionCallback() {
            @Override
            public void onRecognitionResult(String text) {
                if (text == null || text.trim().isEmpty()) {
                    if (callback != null) {
                        callback.onInferenceError("No speech recognized");
                    }
                    return;
                }
                // Run inference on the recognized text
                runInference(text);
            }

            @Override
            public void onRecognitionError(int errorCode, String errorMessage) {
                if (callback != null) {
                    callback.onInferenceError("Speech recognition failed: " + errorMessage);
                }
            }

            @Override
            public void onAmplitudeChanged(int amplitude) {
                if (callback != null) {
                    callback.onAmplitudeChanged(amplitude);
                }
            }

            @Override
            public void onRecognitionStarted() {
                if (callback != null) {
                    callback.onListeningStarted();
                }
            }

            @Override
            public void onRecognitionEnded() {
                if (callback != null) {
                    callback.onListeningEnded();
                }
            }
        });
    }

    /**
     * Stop listening without performing inference.
     */
    public void stopListening() {
        if (speechHelper != null) {
            speechHelper.stopRecognition();
        }
    }

    /**
     * Cancel listening.
     */
    public void cancelListening() {
        if (speechHelper != null) {
            speechHelper.cancelRecognition();
        }
    }

    /**
     * Release resources.
     */
    public void release() {
        if (speechHelper != null) {
            speechHelper.release();
            speechHelper = null;
        }
        callback = null;
    }

    private void runInference(String text) {
        if (!inferenceEngine.isLoaded()) {
            if (callback != null) {
                callback.onInferenceError("Inference engine not loaded");
            }
            return;
        }
        try {
            InferenceResult result = inferenceEngine.infer(text);
            if (callback != null) {
                callback.onInferenceResult(text, result);
            }
        } catch (Exception e) {
            if (callback != null) {
                callback.onInferenceError("Inference failed: " + e.getMessage());
            }
        }
    }

    public interface SpeechInferenceCallback {
        /**
         * Called when inference completes successfully.
         * @param recognizedText The text that was recognized from speech.
         * @param result The inference result.
         */
        void onInferenceResult(String recognizedText, InferenceResult result);

        /**
         * Called when an error occurs during speech recognition or inference.
         * @param errorMessage Description of the error.
         */
        void onInferenceError(String errorMessage);

        /**
         * Called when the audio amplitude changes (for visual feedback).
         * @param amplitude Amplitude value (0-100).
         */
        default void onAmplitudeChanged(int amplitude) {}

        /**
         * Called when listening for speech starts.
         */
        default void onListeningStarted() {}

        /**
         * Called when listening for speech ends.
         */
        default void onListeningEnded() {}
    }
}