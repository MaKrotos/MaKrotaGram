package org.telegram.messenger.openAI;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry of available AI services.
 * Provides a central place to enumerate services and retrieve their metadata.
 */
public class AIServiceRegistry {

    /**
     * Represents metadata about an AI service.
     */
    public static class ServiceInfo {
        public final AISettings.AIServiceType type;
        public final String displayName;
        public final String description;
        public final int iconResId; // optional

        public ServiceInfo(AISettings.AIServiceType type, String displayName, String description, int iconResId) {
            this.type = type;
            this.displayName = displayName;
            this.description = description;
            this.iconResId = iconResId;
        }

        public ServiceInfo(AISettings.AIServiceType type, String displayName, String description) {
            this(type, displayName, description, 0);
        }
    }

    private static final List<ServiceInfo> SERVICES = new ArrayList<>();

    static {
        // Register built-in services
        SERVICES.add(new ServiceInfo(
                AISettings.AIServiceType.OPENAI,
                "OpenAI",
                "Использует модели GPT от OpenAI (GPT‑3.5, GPT‑4, etc.)"
        ));
        SERVICES.add(new ServiceInfo(
                AISettings.AIServiceType.GEMINI,
                "Google Gemini",
                "Использует модели Gemini от Google AI"
        ));
        SERVICES.add(new ServiceInfo(
                AISettings.AIServiceType.LOCAL_AI,
                "Local AI",
                "Локальные модели через sugar-pocketAI (Gemma, Qwen и др.)"
        ));
    }

    /**
     * @return list of all registered service infos.
     */
    public static List<ServiceInfo> getAvailableServices() {
        return new ArrayList<>(SERVICES);
    }

    /**
     * @return list of service types.
     */
    public static List<AISettings.AIServiceType> getAvailableServiceTypes() {
        List<AISettings.AIServiceType> types = new ArrayList<>();
        for (ServiceInfo info : SERVICES) {
            types.add(info.type);
        }
        return types;
    }

    /**
     * Get service info by type.
     */
    public static ServiceInfo getServiceInfo(AISettings.AIServiceType type) {
        for (ServiceInfo info : SERVICES) {
            if (info.type == type) {
                return info;
            }
        }
        return null;
    }

    /**
     * Register a new service dynamically (for future extensibility).
     */
    public static void registerService(ServiceInfo info) {
        SERVICES.add(info);
    }
}