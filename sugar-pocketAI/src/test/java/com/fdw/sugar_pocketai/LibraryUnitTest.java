package com.fdw.sugar_pocketai;

import com.fdw.sugar_pocketai.download.DownloadEntity;
import com.fdw.sugar_pocketai.download.DownloadStatus;
import com.fdw.sugar_pocketai.download.NetworkType;
import com.fdw.sugar_pocketai.hardware.CpuInfo;
import com.fdw.sugar_pocketai.hardware.GpuInfo;
import com.fdw.sugar_pocketai.hardware.HardwareInfo;
import com.fdw.sugar_pocketai.inference.InferenceConfig;
import com.fdw.sugar_pocketai.inference.InferenceEngine;
import com.fdw.sugar_pocketai.inference.InferenceEngineFactory;
import com.fdw.sugar_pocketai.inference.InferenceResult;
import com.fdw.sugar_pocketai.model.ModelEntity;

import org.junit.Test;

import static org.junit.Assert.*;

public class LibraryUnitTest {
    @Test
    public void testDownloadEntity() {
        DownloadEntity entity = new DownloadEntity(
                "test-id",
                "http://example.com/model.gguf",
                "/storage/model.gguf",
                1000L, // totalBytes
                0L,    // downloadedBytes
                DownloadStatus.QUEUED,
                0,     // priority
                NetworkType.WIFI,
                System.currentTimeMillis(),
                null,  // error
                null   // authToken
        );
        assertEquals("test-id", entity.getId());
        assertEquals(DownloadStatus.QUEUED, entity.getStatus());
        assertEquals(NetworkType.WIFI, entity.getNetworkType());
    }

    @Test
    public void testHardwareInfo() {
        // These methods may return null or empty on JVM, but we can at least call them
        CpuInfo cpu = HardwareInfo.getCpuInfo();
        assertNotNull(cpu);
        // CPU info may be empty in unit test environment, but we can still test structure
        GpuInfo gpu = HardwareInfo.getGpuInfo();
        assertNotNull(gpu);
        String chipset = HardwareInfo.getChipset();
        assertNotNull(chipset);
        // getAvailableMemory requires Context, skip in unit test
        // long memory = HardwareInfo.getAvailableMemory(null);
        // assertTrue(memory >= 0);
    }

    @Test
    public void testModelEntity() {
        ModelEntity model = new ModelEntity(
                "model-123",
                "test-model",
                "/path/to/model.gguf",
                1024 * 1024,
                "GGUF",
                "{}",
                System.currentTimeMillis()
        );
        assertEquals("model-123", model.getId());
        assertEquals("test-model", model.getName());
        assertEquals("/path/to/model.gguf", model.getPath());
    }

    @Test
    public void testInferenceConfigBuilder() {
        InferenceConfig config = new InferenceConfig.Builder()
                .setNThreads(2)
                .setNPredict(128)
                .setTemperature(0.7f)
                .build();
        assertEquals(2, config.getNThreads());
        assertEquals(128, config.getNPredict());
        assertEquals(0.7f, config.getTemperature(), 0.001);
    }

    @Test
    public void testLiteRTEngine() {
        InferenceEngine engine = InferenceEngineFactory.createEngine(
                InferenceEngineFactory.EngineType.LITERT);
        assertNotNull(engine);
        // LiteRTEngine requires context, but we can't provide in unit test.
        // For now, skip init test.
        // Instead, test that engine can be created and infer returns something.
        InferenceResult result = engine.infer("Hello");
        assertNotNull(result);
        // Since engine is not loaded, infer should return error
        assertFalse(result.isSuccess());
        // Alternatively, we could set up a mock context, but that's complex.
        // We'll just ensure no crash.
    }
}