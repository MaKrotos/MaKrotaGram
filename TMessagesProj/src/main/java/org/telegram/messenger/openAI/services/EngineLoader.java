package org.telegram.messenger.openAI.services;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.fdw.sugar_pocketai.inference.InferenceConfig;
import com.fdw.sugar_pocketai.inference.InferenceEngine;
import com.fdw.sugar_pocketai.inference.LiteRTEngine;

import org.telegram.messenger.FileLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Асинхронный загрузчик движка LiteRT.
 * Позволяет загружать модель в фоновом потоке с уведомлением о результате.
 */
public class EngineLoader {

    public interface LoadCallback {
        void onLoadStart();
        void onLoadSuccess(InferenceEngine engine);
        void onLoadError(String error);
        void onLoadProgress(int percent); // опционально, если есть прогресс
    }

    private static final String TAG = "EngineLoader";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isLoading = new AtomicBoolean(false);
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);

    private final Context appContext;
    private InferenceEngine currentEngine;
    private String currentModelPath;
    private InferenceConfig currentConfig;

    public EngineLoader(Context context) {
        this.appContext = context;
    }

    /**
     * Запускает асинхронную загрузку движка.
     * Если движок уже загружен с теми же modelPath и config, возвращает его сразу.
     *
     * @param modelPath путь к файлу модели
     * @param config конфигурация инференса
     * @param callback колбэк для уведомлений
     */
    public void loadEngineAsync(String modelPath, InferenceConfig config, LoadCallback callback) {
        if (isLoading.get()) {
            callback.onLoadError("Загрузка уже выполняется");
            return;
        }
        // Проверяем, не загружен ли уже тот же движок
        if (currentEngine != null && currentEngine.isLoaded() &&
                modelPath.equals(currentModelPath) && configEquals(config, currentConfig)) {
            callback.onLoadSuccess(currentEngine);
            return;
        }

        isLoading.set(true);
        isCancelled.set(false);
        callback.onLoadStart();

        executor.submit(() -> {
            try {
                // Создаём новый движок
                LiteRTEngine engine = new LiteRTEngine();
                // Устанавливаем контекст
                engine.setContext(appContext);
                // Инициализация движка
                boolean success = engine.init(modelPath, config);
                mainHandler.post(() -> {
                    if (isCancelled.get()) {
                        // Загрузка отменена, освобождаем ресурсы
                        engine.release();
                        return;
                    }
                    isLoading.set(false);
                    if (success) {
                        currentEngine = engine;
                        currentModelPath = modelPath;
                        currentConfig = config;
                        callback.onLoadSuccess(engine);
                    } else {
                        callback.onLoadError("Не удалось инициализировать движок");
                    }
                });
            } catch (Exception e) {
                FileLog.e(TAG + " loadEngineAsync error", e);
                mainHandler.post(() -> {
                    isLoading.set(false);
                    callback.onLoadError("Исключение: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Отменяет текущую загрузку.
     */
    public void cancel() {
        isCancelled.set(true);
    }

    /**
     * Освобождает ресурсы движка.
     */
    public void release() {
        if (currentEngine != null) {
            currentEngine.release();
            currentEngine = null;
            currentModelPath = null;
            currentConfig = null;
        }
    }

    public boolean isLoading() {
        return isLoading.get();
    }

    public InferenceEngine getCurrentEngine() {
        return currentEngine;
    }

    public boolean isLoaded() {
        return currentEngine != null && currentEngine.isLoaded();
    }

    private boolean configEquals(InferenceConfig c1, InferenceConfig c2) {
        if (c1 == c2) return true;
        if (c1 == null || c2 == null) return false;
        // Упрощённое сравнение, можно расширить при необходимости
        return c1.getNThreads() == c2.getNThreads() &&
                c1.getNPredict() == c2.getNPredict() &&
                Float.compare(c1.getTemperature(), c2.getTemperature()) == 0;
    }
}