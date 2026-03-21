package com.fdw.sugar_pocketai.speech;

/**
 * Callback for speech recognition events.
 */
public interface SpeechRecognitionCallback {
    /**
     * Called when speech recognition results are available.
     * @param text Recognized text.
     */
    void onRecognitionResult(String text);

    /**
     * Called when an error occurs during recognition.
     * @param errorCode Error code from SpeechRecognizer.
     * @param errorMessage Human-readable error message.
     */
    void onRecognitionError(int errorCode, String errorMessage);

    /**
     * Called when the audio amplitude changes (for visual feedback).
     * @param amplitude Amplitude value (0-100).
     */
    default void onAmplitudeChanged(int amplitude) {
        // Optional, default does nothing
    }

    /**
     * Called when recognition starts.
     */
    default void onRecognitionStarted() {}

    /**
     * Called when recognition ends (either by completion or cancellation).
     */
    default void onRecognitionEnded() {}
}