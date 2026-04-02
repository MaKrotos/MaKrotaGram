package org.telegram.messenger.openAI;

import org.telegram.messenger.UserConfig;
import java.util.HashMap;
import java.util.Map;

public class AIServiceFactory {

    private static final Map<AISettings.AIServiceType, BaseAIService> serviceInstances = new HashMap<>();

    public static BaseAIService createService() {
        return createService(UserConfig.selectedAccount);
    }

    public static BaseAIService createService(int account) {
        AISettings settings = new AISettings(account);
        AISettings.AIServiceType type = settings.getSelectedServiceType();
        return createService(type, account);
    }

    public static BaseAIService createService(AISettings.AIServiceType type, int account) {
        switch (type) {
            case OPENAI:
                return new OpenAIService(account);
            case GEMINI:
                return new GeminiService(account);
            case LOCAL_AI:
                return new LocalAIService(account);
            default:
                return new OpenAIService(account);
        }
    }


    public static boolean hasValidConfig() {
        AISettings settings = new AISettings();
        return settings.hasValidConfig();
    }

    public static boolean hasValidConfig(AISettings.AIServiceType type) {
        AISettings settings = new AISettings();
        return settings.hasValidConfig(type);
    }

    public static String getServiceName() {
        AISettings settings = new AISettings();
        return settings.getServiceName();
    }

    public static BaseAIService.AIModel[] getAvailableModels(AISettings.AIServiceType type) {
        BaseAIService service = createService(type, UserConfig.selectedAccount);
        return service.getAvailableModels();
    }

    public static String getModelDisplayName(AISettings.AIServiceType type, String modelId) {
        BaseAIService service = createService(type, UserConfig.selectedAccount);
        BaseAIService.AIModel model = service.getModelById(modelId);
        return model != null ? model.displayName : modelId;
    }

    // Очистка кэша сервисов (при смене аккаунта)
    public static void clearCache() {
        serviceInstances.clear();
    }
}