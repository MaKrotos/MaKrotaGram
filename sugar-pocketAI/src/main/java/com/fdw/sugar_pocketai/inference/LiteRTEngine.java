package com.fdw.sugar_pocketai.inference;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalApi;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Inference engine using LiteRT-LM for .litertlm models.
 * Real integration with the LiteRT LM SDK.
 */
public class LiteRTEngine implements InferenceEngine {
    private static final String TAG = "LiteRTEngine";
    private String modelPath;
    private InferenceConfig config;
    private boolean loaded = false;
    private Context appContext;
    private Engine engine;
    private final Object inferenceLock = new Object();
    private int currentNPredict = -1;
    private String currentAccelerator = null;
    private boolean currentSupportImage = false;
    private boolean currentSupportAudio = false;

    // Need application context for engine initialization
    public void setContext(Context context) {
        this.appContext = context.getApplicationContext();
    }

    private byte[] bitmapToPngByteArray(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        return stream.toByteArray();
    }

    private Contents createContents(String prompt, List<Bitmap> images, List<byte[]> audioClips) {
        List<Content> contents = new java.util.ArrayList<>();
        if (images != null) {
            for (Bitmap image : images) {
                contents.add(new Content.ImageBytes(bitmapToPngByteArray(image)));
            }
        }
        if (audioClips != null) {
            for (byte[] audioClip : audioClips) {
                contents.add(new Content.AudioBytes(audioClip));
            }
        }
        if (prompt != null && !prompt.trim().isEmpty()) {
            contents.add(new Content.Text(prompt));
        }
        return Contents.Companion.of(contents);
    }

    private String extractTextFromMessage(Message message) {
        // Message.getContents() returns Contents, which has a toString() that concatenates text content.
        Contents contents = message.getContents();
        return contents.toString();
    }

    @Override
    public boolean init(String modelPath, InferenceConfig config) {
        if (modelPath == null || modelPath.isEmpty()) {
            Log.e(TAG, "Invalid model path: " + modelPath);
            loaded = false;
            return false;
        }
        if (config == null) {
            Log.w(TAG, "Config is null, using default");
            config = new InferenceConfig.Builder().build();
        }
        this.modelPath = modelPath;
        this.config = config;

        if (appContext == null) {
            Log.e(TAG, "Context not set, cannot initialize LiteRT engine");
            loaded = false;
            return false;
        }

        try {
            // Map accelerator to backend
            Backend preferredBackend;
            String accelerator = config.getAccelerator();
            if (accelerator == null || accelerator.equalsIgnoreCase("cpu")) {
                preferredBackend = new Backend.CPU();
            } else if (accelerator.equalsIgnoreCase("gpu")) {
                preferredBackend = new Backend.GPU();
            } else if (accelerator.equalsIgnoreCase("npu")) {
                preferredBackend = new Backend.NPU();
                // Set NPU libraries directory (private access, may need reflection)
                // ExperimentalFlags.npuLibrariesDir = appContext.getApplicationInfo().nativeLibraryDir;
                Log.w(TAG, "NPU backend selected but npuLibrariesDir not set (may cause issues)");
            } else {
                Log.w(TAG, "Unknown accelerator '" + accelerator + "', defaulting to CPU");
                preferredBackend = new Backend.CPU();
            }

            boolean supportImage = config.isSupportImage();
            boolean supportAudio = config.isSupportAudio();

            EngineConfig engineConfig = new EngineConfig(
                    modelPath,
                    preferredBackend,
                    supportImage ? new Backend.GPU() : null, // vision backend must be GPU for Gemma 3n
                    supportAudio ? new Backend.CPU() : null, // audio backend must be CPU for Gemma 3n
                    config.getNPredict(), // maxNumTokens
                    null // cacheDir (optional)
            );

            Log.i(TAG, "EngineConfig: modelPath=" + modelPath + ", backend=" + preferredBackend +
                    ", visionBackend=" + (supportImage ? "GPU" : "null") +
                    ", audioBackend=" + (supportAudio ? "CPU" : "null") +
                    ", maxNumTokens=" + config.getNPredict());

            // Create engine
            engine = new Engine(engineConfig);
            engine.initialize();

            loaded = true;
            currentNPredict = config.getNPredict();
            currentAccelerator = config.getAccelerator();
            currentSupportImage = config.isSupportImage();
            currentSupportAudio = config.isSupportAudio();
            Log.i(TAG, "LiteRT engine initialized successfully with model: " + modelPath);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize LiteRT engine", e);
            loaded = false;
            return false;
        }
    }

    private boolean needEngineReinit(InferenceConfig newConfig) {
        if (newConfig == null) return false;
        return (newConfig.getNPredict() != currentNPredict) ||
                !newConfig.getAccelerator().equals(currentAccelerator) ||
                (newConfig.isSupportImage() != currentSupportImage) ||
                (newConfig.isSupportAudio() != currentSupportAudio);
    }

    private boolean reinitEngineIfNeeded(InferenceConfig newConfig) {
        if (needEngineReinit(newConfig)) {
            Log.i(TAG, "Engine parameters changed, reinitializing...");
            // Release current engine
            if (engine != null) {
                try {
                    engine.close();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to close engine during reinit", e);
                }
                engine = null;
            }
            loaded = false;
            // Re-init with new config
            boolean success = init(modelPath, newConfig);
            if (success) {
                Log.i(TAG, "Engine reinitialized successfully");
                return true;
            } else {
                Log.e(TAG, "Engine reinitialization failed");
                return false;
            }
        }
        return true; // no reinit needed
    }

    @Override
    public InferenceResult infer(String prompt) {
        return infer(prompt, null);
    }

    @Override
    public InferenceResult infer(String prompt, InferenceConfig overrideConfig) {
        synchronized (inferenceLock) {
            if (!loaded || engine == null) {
                Log.e(TAG, "Engine not loaded, cannot infer");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Engine not loaded")
                        .build();
            }
            if (prompt == null || prompt.trim().isEmpty()) {
                Log.w(TAG, "Empty prompt provided");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Prompt is empty")
                        .build();
            }

            InferenceConfig usedConfig = overrideConfig != null ? overrideConfig : this.config;
            if (usedConfig == null) {
                usedConfig = new InferenceConfig.Builder().build();
            }

            // Reinitialize engine if parameters changed
            if (!reinitEngineIfNeeded(usedConfig)) {
                Log.e(TAG, "Failed to reinitialize engine with new parameters");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Engine reinitialization failed")
                        .build();
            }

            Log.i(TAG, "Inferring with LiteRT, prompt length: " + prompt.length());
            long startTime = System.currentTimeMillis();

            // Prepare sampler config from usedConfig
            SamplerConfig samplerConfig = new SamplerConfig(
                    usedConfig.getTopK(),
                    (double) usedConfig.getTopP(),
                    (double) usedConfig.getTemperature(),
                    0 // seed
            );
            ConversationConfig conversationConfig = new ConversationConfig(
                    null, // systemInstruction
                    Collections.emptyList(), // initialMessages
                    Collections.emptyList(), // tools
                    samplerConfig,
                    null, // systemMessage
                    false  // automaticToolCalling
            );
            Conversation conversation = null;
            try {
                conversation = engine.createConversation(conversationConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create conversation", e);
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Failed to create conversation: " + e.getMessage())
                        .build();
            }

            // Prepare contents (text only, no image/audio for now)
            Content textContent = new Content.Text(prompt);
            Contents contents = Contents.Companion.of(textContent);

            final Conversation finalConversation = conversation;

            try {
                // Synchronous inference
                Message response = conversation.sendMessage(contents, Collections.emptyMap());
                String generatedText = extractTextFromMessage(response);
                long endTime = System.currentTimeMillis();
                long inferenceTimeMs = endTime - startTime;

                // Estimate tokens (rough approximation)
                int tokens = generatedText.split("\\s+").length;

                closeConversation(finalConversation);

                return new InferenceResult.Builder()
                        .setGeneratedText(generatedText)
                        .setInferenceTimeMs(inferenceTimeMs)
                        .setTokensGenerated(tokens)
                        .setTokensPerSecond(tokens > 0 ? (int) (tokens / (inferenceTimeMs / 1000.0)) : 0)
                        .setSuccess(true)
                        .build();

            } catch (Exception e) {
                Log.e(TAG, "Inference error", e);
                closeConversation(finalConversation);
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .build();
            }
        }
    }

    @Override
    public InferenceResult infer(String prompt, InferenceConfig config,
                                 List<Bitmap> images, List<byte[]> audioClips) {
        synchronized (inferenceLock) {
            if (!loaded || engine == null) {
                Log.e(TAG, "Engine not loaded, cannot infer");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Engine not loaded")
                        .build();
            }
            // Allow empty prompt if images/audio are provided
            if ((prompt == null || prompt.trim().isEmpty()) &&
                    (images == null || images.isEmpty()) &&
                    (audioClips == null || audioClips.isEmpty())) {
                Log.w(TAG, "No input provided (prompt, images, audio)");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("No input provided")
                        .build();
            }

            InferenceConfig usedConfig = config != null ? config : this.config;
            if (usedConfig == null) {
                usedConfig = new InferenceConfig.Builder().build();
            }

            // Reinitialize engine if parameters changed
            if (!reinitEngineIfNeeded(usedConfig)) {
                Log.e(TAG, "Failed to reinitialize engine with new parameters");
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Engine reinitialization failed")
                        .build();
            }

            Log.i(TAG, "Inferring with LiteRT, prompt length: " + (prompt != null ? prompt.length() : 0)
                    + ", images: " + (images != null ? images.size() : 0)
                    + ", audio clips: " + (audioClips != null ? audioClips.size() : 0));
            long startTime = System.currentTimeMillis();

            // Prepare sampler config from usedConfig
            SamplerConfig samplerConfig = new SamplerConfig(
                    usedConfig.getTopK(),
                    (double) usedConfig.getTopP(),
                    (double) usedConfig.getTemperature(),
                    0 // seed
            );
            ConversationConfig conversationConfig = new ConversationConfig(
                    null, // systemInstruction
                    Collections.emptyList(), // initialMessages
                    Collections.emptyList(), // tools
                    samplerConfig,
                    null, // systemMessage
                    false  // automaticToolCalling
            );
            Conversation conversation = null;
            try {
                conversation = engine.createConversation(conversationConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create conversation", e);
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage("Failed to create conversation: " + e.getMessage())
                        .build();
            }

            // Prepare contents using images/audio
            Contents contents = createContents(prompt, images, audioClips);

            final Conversation finalConversation = conversation;

            try {
                // Synchronous inference
                Message response = conversation.sendMessage(contents, Collections.emptyMap());
                String generatedText = extractTextFromMessage(response);
                long endTime = System.currentTimeMillis();
                long inferenceTimeMs = endTime - startTime;

                // Estimate tokens (rough approximation)
                int tokens = generatedText.split("\\s+").length;

                closeConversation(finalConversation);

                return new InferenceResult.Builder()
                        .setGeneratedText(generatedText)
                        .setInferenceTimeMs(inferenceTimeMs)
                        .setTokensGenerated(tokens)
                        .setTokensPerSecond(tokens > 0 ? (int) (tokens / (inferenceTimeMs / 1000.0)) : 0)
                        .setSuccess(true)
                        .build();

            } catch (Exception e) {
                Log.e(TAG, "Inference error", e);
                closeConversation(finalConversation);
                return new InferenceResult.Builder()
                        .setSuccess(false)
                        .setErrorMessage(e.getMessage())
                        .build();
            }
        }
    }

    private void closeConversation(Conversation conversation) {
        if (conversation != null) {
            Log.i(TAG, "Closing conversation " + conversation.hashCode());
            try {
                conversation.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close conversation", e);
            }
        }
    }

    @Override
    public void streamInfer(String prompt, InferenceConfig config, InferenceStreamCallback callback) {
        synchronized (inferenceLock) {
            if (!loaded || engine == null) {
                Log.e(TAG, "Engine not loaded, cannot stream infer");
                callback.onError("Engine not loaded");
                return;
            }
            if (prompt == null || prompt.trim().isEmpty()) {
                Log.w(TAG, "Empty prompt provided");
                callback.onError("Prompt is empty");
                return;
            }

            InferenceConfig usedConfig = config != null ? config : this.config;
            if (usedConfig == null) {
                usedConfig = new InferenceConfig.Builder().build();
            }

            // Reinitialize engine if parameters changed
            if (!reinitEngineIfNeeded(usedConfig)) {
                Log.e(TAG, "Failed to reinitialize engine with new parameters");
                callback.onError("Engine reinitialization failed");
                return;
            }

            Log.i(TAG, "Stream inferring with LiteRT, prompt length: " + prompt.length());
            long startTime = System.currentTimeMillis();

            // Prepare sampler config from usedConfig
            SamplerConfig samplerConfig = new SamplerConfig(
                    usedConfig.getTopK(),
                    (double) usedConfig.getTopP(),
                    (double) usedConfig.getTemperature(),
                    0 // seed
            );
            ConversationConfig conversationConfig = new ConversationConfig(
                    null, // systemInstruction
                    Collections.emptyList(), // initialMessages
                    Collections.emptyList(), // tools
                    samplerConfig,
                    null, // systemMessage
                    false  // automaticToolCalling
            );
            Conversation conversation = null;
            try {
                conversation = engine.createConversation(conversationConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create conversation", e);
                callback.onError("Failed to create conversation: " + e.getMessage());
                return;
            }

            // Prepare contents (text only, no image/audio for now)
            Content textContent = new Content.Text(prompt);
            Contents contents = Contents.Companion.of(textContent);

            final Conversation finalConversation = conversation;

            try {
                // Create a MessageCallback to receive streaming responses
                MessageCallback messageCallback = new MessageCallback() {
                    private final StringBuilder accumulatedText = new StringBuilder();
                    private int tokenCount = 0;

                    @Override
                    public void onMessage(@NonNull Message message) {
                        // Called for each incremental message (partial result)
                        String text = extractTextFromMessage(message);
                        if (text != null && !text.isEmpty()) {
                            accumulatedText.append(text);
                            tokenCount += text.split("\\s+").length;
                            callback.onPartialResult(text);
                        }
                    }

                    @Override
                    public void onDone() {
                        // Called when inference is complete
                        long inferenceTimeMs = System.currentTimeMillis() - startTime;
                        callback.onSuccess(accumulatedText.toString(), inferenceTimeMs, tokenCount);
                        closeConversation(finalConversation);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        Log.e(TAG, "Stream inference error", throwable);
                        callback.onError(throwable.getMessage());
                        closeConversation(finalConversation);
                    }
                };

                // Start asynchronous streaming inference with empty extra context
                conversation.sendMessageAsync(contents, messageCallback, Collections.emptyMap());

            } catch (Exception e) {
                Log.e(TAG, "Inference error", e);
                callback.onError(e.getMessage());
                closeConversation(finalConversation);
            }
        }
    }

    @Override
    public void streamInfer(String prompt, InferenceConfig config,
                            List<Bitmap> images, List<byte[]> audioClips,
                            InferenceStreamCallback callback) {
        synchronized (inferenceLock) {
            if (!loaded || engine == null) {
                Log.e(TAG, "Engine not loaded, cannot stream infer");
                callback.onError("Engine not loaded");
                return;
            }
            // Allow empty prompt if images/audio are provided
            if ((prompt == null || prompt.trim().isEmpty()) &&
                    (images == null || images.isEmpty()) &&
                    (audioClips == null || audioClips.isEmpty())) {
                Log.w(TAG, "No input provided (prompt, images, audio)");
                callback.onError("No input provided");
                return;
            }

            InferenceConfig usedConfig = config != null ? config : this.config;
            if (usedConfig == null) {
                usedConfig = new InferenceConfig.Builder().build();
            }

            // Reinitialize engine if parameters changed
            if (!reinitEngineIfNeeded(usedConfig)) {
                Log.e(TAG, "Failed to reinitialize engine with new parameters");
                callback.onError("Engine reinitialization failed");
                return;
            }

            Log.i(TAG, "Stream inferring with LiteRT, prompt length: " + (prompt != null ? prompt.length() : 0)
                    + ", images: " + (images != null ? images.size() : 0)
                    + ", audio clips: " + (audioClips != null ? audioClips.size() : 0));
            long startTime = System.currentTimeMillis();

            // Prepare sampler config from usedConfig
            SamplerConfig samplerConfig = new SamplerConfig(
                    usedConfig.getTopK(),
                    (double) usedConfig.getTopP(),
                    (double) usedConfig.getTemperature(),
                    0 // seed
            );
            ConversationConfig conversationConfig = new ConversationConfig(
                    null, // systemInstruction
                    Collections.emptyList(), // initialMessages
                    Collections.emptyList(), // tools
                    samplerConfig,
                    null, // systemMessage
                    false  // automaticToolCalling
            );
            Conversation conversation = null;
            try {
                conversation = engine.createConversation(conversationConfig);
            } catch (Exception e) {
                Log.e(TAG, "Failed to create conversation", e);
                callback.onError("Failed to create conversation: " + e.getMessage());
                return;
            }

            // Prepare contents using images/audio
            Contents contents = createContents(prompt, images, audioClips);

            final Conversation finalConversation = conversation;

            try {
                // Create a MessageCallback to receive streaming responses
                MessageCallback messageCallback = new MessageCallback() {
                    private final StringBuilder accumulatedText = new StringBuilder();
                    private int tokenCount = 0;

                    @Override
                    public void onMessage(@NonNull Message message) {
                        // Called for each incremental message (partial result)
                        String text = extractTextFromMessage(message);
                        if (text != null && !text.isEmpty()) {
                            accumulatedText.append(text);
                            tokenCount += text.split("\\s+").length;
                            callback.onPartialResult(text);
                        }
                    }

                    @Override
                    public void onDone() {
                        // Called when inference is complete
                        long inferenceTimeMs = System.currentTimeMillis() - startTime;
                        callback.onSuccess(accumulatedText.toString(), inferenceTimeMs, tokenCount);
                        closeConversation(finalConversation);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        Log.e(TAG, "Stream inference error", throwable);
                        callback.onError(throwable.getMessage());
                        closeConversation(finalConversation);
                    }
                };

                // Start asynchronous streaming inference with empty extra context
                conversation.sendMessageAsync(contents, messageCallback, Collections.emptyMap());

            } catch (Exception e) {
                Log.e(TAG, "Inference error", e);
                callback.onError(e.getMessage());
                closeConversation(finalConversation);
            }
        }
    }

    @Override
    public void release() {
        Log.i(TAG, "Releasing LiteRT resources");
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close engine", e);
            }
            engine = null;
        }
        loaded = false;
        modelPath = null;
        config = null;
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public String getModelPath() {
        return modelPath;
    }
}