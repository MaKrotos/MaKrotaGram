package com.fdw.sugar_pocketai.inference;

public class InferenceResult {
    private final String generatedText;
    private final long inferenceTimeMs;
    private final int tokensGenerated;
    private final double tokensPerSecond;
    private final boolean isSuccess;
    private final String errorMessage;

    private InferenceResult(Builder builder) {
        this.generatedText = builder.generatedText;
        this.inferenceTimeMs = builder.inferenceTimeMs;
        this.tokensGenerated = builder.tokensGenerated;
        this.tokensPerSecond = builder.tokensPerSecond;
        this.isSuccess = builder.isSuccess;
        this.errorMessage = builder.errorMessage;
    }

    public String getGeneratedText() {
        return generatedText;
    }

    public long getInferenceTimeMs() {
        return inferenceTimeMs;
    }

    public int getTokensGenerated() {
        return tokensGenerated;
    }

    public double getTokensPerSecond() {
        return tokensPerSecond;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public static class Builder {
        private String generatedText = "";
        private long inferenceTimeMs = 0;
        private int tokensGenerated = 0;
        private double tokensPerSecond = 0.0;
        private boolean isSuccess = true;
        private String errorMessage = null;

        public Builder setGeneratedText(String generatedText) {
            this.generatedText = generatedText;
            return this;
        }

        public Builder setInferenceTimeMs(long inferenceTimeMs) {
            this.inferenceTimeMs = inferenceTimeMs;
            return this;
        }

        public Builder setTokensGenerated(int tokensGenerated) {
            this.tokensGenerated = tokensGenerated;
            return this;
        }

        public Builder setTokensPerSecond(double tokensPerSecond) {
            this.tokensPerSecond = tokensPerSecond;
            return this;
        }

        public Builder setSuccess(boolean success) {
            isSuccess = success;
            return this;
        }

        public Builder setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public InferenceResult build() {
            return new InferenceResult(this);
        }
    }
}