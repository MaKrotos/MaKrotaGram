package com.fdw.sugar_pocketai.inference;

/**
 * Callback for receiving streaming inference results.
 */
public interface InferenceStreamCallback {
    /**
     * Called when a new token or piece of text is generated.
     * @param partialText The newly generated text (could be a token, a word, or a chunk).
     */
    void onPartialResult(String partialText);

    /**
     * Called when inference completes successfully.
     * @param fullText The complete generated text.
     * @param inferenceTimeMs Total inference time in milliseconds.
     * @param tokensGenerated Approximate number of tokens generated.
     */
    void onSuccess(String fullText, long inferenceTimeMs, int tokensGenerated);

    /**
     * Called when inference fails.
     * @param errorMessage Description of the error.
     */
    void onError(String errorMessage);
}