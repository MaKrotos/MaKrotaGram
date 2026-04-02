package com.fdw.sugar_pocketai.speech;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Helper class for speech recognition using Android's SpeechRecognizer.
 * Provides a simple API to start/stop recognition and receive results via callback.
 */
public class SpeechRecognizerHelper implements RecognitionListener {
    private static final String TAG = "SpeechRecognizerHelper";

    private final Context context;
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private SpeechRecognitionCallback callback;
    private boolean isRecognizing = false;

    public SpeechRecognizerHelper(Context context) {
        this.context = context.getApplicationContext();
        initialize();
    }

    private void initialize() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(this);

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
    }

    /**
     * Start speech recognition.
     * @param callback Callback to receive results and errors.
     */
    public void startRecognition(SpeechRecognitionCallback callback) {
        if (isRecognizing) {
            Log.w(TAG, "Recognition already in progress");
            return;
        }
        this.callback = callback;
        try {
            speechRecognizer.startListening(recognizerIntent);
            isRecognizing = true;
            callback.onRecognitionStarted();
        } catch (SecurityException e) {
            Log.e(TAG, "RECORD_AUDIO permission not granted", e);
            callback.onRecognitionError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
                    "RECORD_AUDIO permission required");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start recognition", e);
            callback.onRecognitionError(SpeechRecognizer.ERROR_CLIENT, e.getMessage());
        }
    }

    /**
     * Stop recognition and deliver final results.
     */
    public void stopRecognition() {
        if (!isRecognizing) {
            return;
        }
        try {
            speechRecognizer.stopListening();
        } catch (Exception e) {
            Log.e(TAG, "Error stopping recognition", e);
        }
        // onEndOfSpeech and onResults will be called, which will set isRecognizing = false
    }

    /**
     * Cancel recognition without delivering results.
     */
    public void cancelRecognition() {
        if (!isRecognizing) {
            return;
        }
        try {
            speechRecognizer.cancel();
            isRecognizing = false;
            if (callback != null) {
                callback.onRecognitionEnded();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error canceling recognition", e);
        }
    }

    /**
     * Release resources. Call this when the helper is no longer needed.
     */
    public void release() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        callback = null;
    }

    /**
     * Check if recognition is currently active.
     */
    public boolean isRecognizing() {
        return isRecognizing;
    }

    // RecognitionListener implementation

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.d(TAG, "onReadyForSpeech");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech");
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        if (callback != null) {
            // Convert dB to amplitude (0-100) for simplicity
            int amplitude = (int) ((rmsdB + 2) * 10); // rough approximation
            if (amplitude < 0) amplitude = 0;
            if (amplitude > 100) amplitude = 100;
            callback.onAmplitudeChanged(amplitude);
        }
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        // Not used
    }

    @Override
    public void onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech");
    }

    @Override
    public void onError(int error) {
        Log.e(TAG, "Recognition error: " + error);
        isRecognizing = false;
        if (callback != null) {
            String errorMessage = getErrorMessage(error);
            callback.onRecognitionError(error, errorMessage);
            callback.onRecognitionEnded();
        }
    }

    @Override
    public void onResults(Bundle results) {
        Log.d(TAG, "onResults");
        isRecognizing = false;
        if (callback != null) {
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                callback.onRecognitionResult(text);
            } else {
                callback.onRecognitionResult("");
            }
            callback.onRecognitionEnded();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (callback != null) {
            ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (matches != null && !matches.isEmpty()) {
                String text = matches.get(0);
                callback.onRecognitionResult(text);
            }
        }
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
        // Not used
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognizer busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error";
        }
    }
}