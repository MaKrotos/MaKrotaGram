package com.fdw.sugar_pocketai.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalog of pre‑defined models available for download.
 * This is a static catalog; in the future it could be extended to load from a remote source.
 */
public final class ModelCatalog {

    private ModelCatalog() {
        // No instance
    }

    /**
     * Returns the default list of models with the provided authentication token.
     * The token will be attached to each model (required for downloading from Hugging Face).
     *
     * @param authToken Hugging Face authentication token (can be null, but downloads will fail)
     * @return List of pre‑defined ModelItem instances
     */
    @NonNull
    public static List<ModelItem> getDefaultModels(String authToken) {
        List<ModelItem> list = new ArrayList<>();

        // Gemma-3n-E2B-it
        list.add(new ModelItem(
                "Gemma-3n-E2B-it",
                "google/gemma-3n-E2B-it-litert-lm",
                "gemma-3n-E2B-it-int4.litertlm",
                "ba9ca88da013b537b6ed38108be609b8db1c3a16",
                "Gemma 3n E2B with text, vision, audio support, 4096 context length.",
                3655827456L,
                "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/ba9ca88da013b537b6ed38108be609b8db1c3a16/gemma-3n-E2B-it-int4.litertlm?download=true",
                List.of("llm_chat", "llm_prompt_lab", "llm_ask_image", "llm_ask_audio"),
                true,
                true,
                8,
                authToken
        ));

        // Gemma-3n-E4B-it
        list.add(new ModelItem(
                "Gemma-3n-E4B-it",
                "google/gemma-3n-E4B-it-litert-lm",
                "gemma-3n-E4B-it-int4.litertlm",
                "297ed75955702dec3503e00c2c2ecbbf475300bc",
                "Gemma 3n E4B with text, vision, audio support, 4096 context length.",
                4919541760L,
                "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm?download=true",
                List.of("llm_chat", "llm_prompt_lab", "llm_ask_image", "llm_ask_audio"),
                true,
                true,
                12,
                authToken
        ));

        // Gemma3-1B-IT
        list.add(new ModelItem(
                "Gemma3-1B-IT",
                "litert-community/Gemma3-1B-IT",
                "gemma3-1b-it-int4.litertlm",
                "42d538a932e8d5b12e6b3b455f5572560bd60b2c",
                "Quantized Gemma3 1B Instruct model.",
                584417280L,
                "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/42d538a932e8d5b12e6b3b455f5572560bd60b2c/gemma3-1b-it-int4.litertlm?download=true",
                List.of("llm_chat", "llm_prompt_lab"),
                false,
                false,
                6,
                authToken
        ));

        // Qwen2.5-1.5B-Instruct
        list.add(new ModelItem(
                "Qwen2.5-1.5B-Instruct",
                "litert-community/Qwen2.5-1.5B-Instruct",
                "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
                "19edb84c69a0212f29a6ef17ba0d6f278b6a1614",
                "Qwen2.5 1.5B Instruct model for Android.",
                1597931520L,
                "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/19edb84c69a0212f29a6ef17ba0d6f278b6a1614/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                List.of("llm_chat", "llm_prompt_lab"),
                false,
                false,
                6,
                authToken
        ));

        // DeepSeek-R1-Distill-Qwen-1.5B
        list.add(new ModelItem(
                "DeepSeek-R1-Distill-Qwen-1.5B",
                "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
                "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm",
                "e34bb88632342d1f9640bad579a45134eb1cf988",
                "DeepSeek R1 distilled Qwen 1.5B model.",
                1833451520L,
                "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/e34bb88632342d1f9640bad579a45134eb1cf988/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                List.of("llm_chat", "llm_prompt_lab"),
                false,
                false,
                6,
                authToken
        ));

        // TinyGarden-270M
        list.add(new ModelItem(
                "TinyGarden-270M",
                "litert-community/functiongemma-270m-ft-tiny-garden",
                "tiny_garden_q8_ekv1024.litertlm",
                "c205853ff82da86141a1105faa2344a8b176dfe7",
                "Fine-tuned Function Gemma 270M model for Tiny Garden.",
                288964608L,
                "https://huggingface.co/litert-community/functiongemma-270m-ft-tiny-garden/resolve/c205853ff82da86141a1105faa2344a8b176dfe7/tiny_garden_q8_ekv1024.litertlm?download=true",
                List.of("llm_tiny_garden"),
                false,
                false,
                6,
                authToken
        ));

        // MobileActions-270M
        list.add(new ModelItem(
                "MobileActions-270M",
                "litert-community/functiongemma-270m-ft-mobile-actions",
                "mobile_actions_q8_ekv1024.litertlm",
                "38942192c9b723af836d489074823ff33d4a3e7a",
                "Fine-tuned Function Gemma 270M model for Mobile Actions.",
                288964608L,
                "https://huggingface.co/litert-community/functiongemma-270m-ft-mobile-actions/resolve/38942192c9b723af836d489074823ff33d4a3e7a/mobile_actions_q8_ekv1024.litertlm?download=true",
                List.of("llm_mobile_actions"),
                false,
                false,
                6,
                authToken
        ));

        return list;
    }

    /**
     * Returns the default list of models without an authentication token.
     * Useful for display purposes; downloading will require setting a token later.
     */
    @NonNull
    public static List<ModelItem> getDefaultModels() {
        return getDefaultModels(null);
    }
}