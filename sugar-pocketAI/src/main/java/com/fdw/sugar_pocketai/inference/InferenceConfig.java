package com.fdw.sugar_pocketai.inference;

public class InferenceConfig {
    private final int nThreads;
    private final int nPredict;
    private final int topK;
    private final float topP;
    private final float temperature;
    private final float repeatPenalty;
    private final int ctxSize;
    private final String accelerator;
    private final boolean supportImage;
    private final boolean supportAudio;
    private final int timeoutSeconds;

    private InferenceConfig(Builder builder) {
        this.nThreads = builder.nThreads;
        this.nPredict = builder.nPredict;
        this.topK = builder.topK;
        this.topP = builder.topP;
        this.temperature = builder.temperature;
        this.repeatPenalty = builder.repeatPenalty;
        this.ctxSize = builder.ctxSize;
        this.accelerator = builder.accelerator;
        this.supportImage = builder.supportImage;
        this.supportAudio = builder.supportAudio;
        this.timeoutSeconds = builder.timeoutSeconds;
    }

    public int getNThreads() {
        return nThreads;
    }

    public int getNPredict() {
        return nPredict;
    }

    public int getTopK() {
        return topK;
    }

    public float getTopP() {
        return topP;
    }

    public float getTemperature() {
        return temperature;
    }

    public float getRepeatPenalty() {
        return repeatPenalty;
    }

    public int getCtxSize() {
        return ctxSize;
    }

    public String getAccelerator() {
        return accelerator;
    }

    public boolean isSupportImage() {
        return supportImage;
    }

    public boolean isSupportAudio() {
        return supportAudio;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public static class Builder {
        private int nThreads = 4;
        private int nPredict = 256;
        private int topK = 40;
        private float topP = 0.9f;
        private float temperature = 0.8f;
        private float repeatPenalty = 1.1f;
        private int ctxSize = 2048;
        private String accelerator = "cpu";
        private boolean supportImage = false;
        private boolean supportAudio = false;
        private int timeoutSeconds = 1200;

        public Builder setNThreads(int nThreads) {
            this.nThreads = nThreads;
            return this;
        }

        public Builder setNPredict(int nPredict) {
            this.nPredict = nPredict;
            return this;
        }

        public Builder setTopK(int topK) {
            this.topK = topK;
            return this;
        }

        public Builder setTopP(float topP) {
            this.topP = topP;
            return this;
        }

        public Builder setTemperature(float temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder setRepeatPenalty(float repeatPenalty) {
            this.repeatPenalty = repeatPenalty;
            return this;
        }

        public Builder setCtxSize(int ctxSize) {
            this.ctxSize = ctxSize;
            return this;
        }

        public Builder setAccelerator(String accelerator) {
            this.accelerator = accelerator;
            return this;
        }

        public Builder setSupportImage(boolean supportImage) {
            this.supportImage = supportImage;
            return this;
        }

        public Builder setSupportAudio(boolean supportAudio) {
            this.supportAudio = supportAudio;
            return this;
        }

        public Builder setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
            return this;
        }

        public InferenceConfig build() {
            return new InferenceConfig(this);
        }
    }
}