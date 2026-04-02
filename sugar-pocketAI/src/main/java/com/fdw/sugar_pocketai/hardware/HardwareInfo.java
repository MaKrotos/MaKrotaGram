package com.fdw.sugar_pocketai.hardware;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.opengl.GLES20;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

import java.io.File;
import java.util.*;
import java.util.regex.Pattern;

public class HardwareInfo {

    public static String getChipset() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (Build.SOC_MODEL != null && !Build.SOC_MODEL.isEmpty()) {
                return Build.SOC_MODEL;
            }
        }
        if (Build.HARDWARE != null && !Build.HARDWARE.isEmpty()) {
            return Build.HARDWARE;
        }
        return Build.BOARD;
    }

    private static boolean isOpenCLSupported() {
        String[] libPaths = {
            "/system/lib/libOpenCL.so",
            "/system/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so"
        };
        for (String path : libPaths) {
            if (new File(path).exists()) {
                return true;
            }
        }
        return false;
    }

    public static GpuInfo getGpuInfo() {
        String renderer = "";
        String vendor = "";
        String version = "";

        try {
            EGL10 egl = (EGL10) EGLContext.getEGL();
            EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

            if (display != EGL10.EGL_NO_DISPLAY) {
                int[] versionArray = new int[2];
                egl.eglInitialize(display, versionArray);

                int[] configsCount = new int[1];
                EGLConfig[] configs = new EGLConfig[1];
                int[] configSpec = {
                    EGL10.EGL_RENDERABLE_TYPE, 4,
                    EGL10.EGL_NONE
                };

                egl.eglChooseConfig(display, configSpec, configs, 1, configsCount);

                if (configsCount[0] > 0) {
                    EGLContext context = egl.eglCreateContext(
                        display,
                        configs[0],
                        EGL10.EGL_NO_CONTEXT,
                        new int[] {0x3098, 2, EGL10.EGL_NONE}
                    );

                    if (context != null && context != EGL10.EGL_NO_CONTEXT) {
                        int[] surfaceAttribs = {
                            EGL10.EGL_WIDTH, 1,
                            EGL10.EGL_HEIGHT, 1,
                            EGL10.EGL_NONE
                        };
                        EGLConfig config = configs[0];
                        Object surface = egl.eglCreatePbufferSurface(display, config, surfaceAttribs);

                        if (surface != null && surface != EGL10.EGL_NO_SURFACE) {
                            egl.eglMakeCurrent(display, (javax.microedition.khronos.egl.EGLSurface) surface,
                                (javax.microedition.khronos.egl.EGLSurface) surface, context);

                            renderer = GLES20.glGetString(GLES20.GL_RENDERER);
                            vendor = GLES20.glGetString(GLES20.GL_VENDOR);
                            version = GLES20.glGetString(GLES20.GL_VERSION);

                            egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                            egl.eglDestroySurface(display, (javax.microedition.khronos.egl.EGLSurface) surface);
                        }
                        egl.eglDestroyContext(display, context);
                    }
                }
                egl.eglTerminate(display);
            }
        } catch (Exception e) {
            // Fallback: GPU info not available
        }

        if (renderer == null) renderer = "";
        if (vendor == null) vendor = "";
        if (version == null) version = "";

        String rendererLower = renderer.toLowerCase(Locale.ROOT);
        boolean hasAdreno = Pattern.compile("(adreno|qcom|qualcomm)").matcher(rendererLower).find();
        boolean hasMali = Pattern.compile("mali").matcher(rendererLower).find();
        boolean hasPowerVR = Pattern.compile("powervr").matcher(rendererLower).find();
        boolean supportsOpenCL = isOpenCLSupported();

        String gpuType;
        if (hasAdreno) {
            gpuType = "Adreno (Qualcomm)";
        } else if (hasMali) {
            gpuType = "Mali (ARM)";
        } else if (hasPowerVR) {
            gpuType = "PowerVR (Imagination)";
        } else if (!renderer.isEmpty()) {
            gpuType = renderer;
        } else {
            gpuType = "Unknown";
        }

        return new GpuInfo(renderer, vendor, version, hasAdreno, hasMali, hasPowerVR, supportsOpenCL, gpuType);
    }

    public static CpuInfo getCpuInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        List<CpuInfo.Processor> processors = new ArrayList<>();
        Set<String> features = new HashSet<>();

        File cpuInfoFile = new File("/proc/cpuinfo");
        if (cpuInfoFile.exists()) {
            try {
                List<String> cpuInfoLines = new ArrayList<>();
                Scanner scanner = new Scanner(cpuInfoFile);
                while (scanner.hasNextLine()) {
                    cpuInfoLines.add(scanner.nextLine());
                }
                scanner.close();

                CpuInfo.Processor currentProcessor = null;
                boolean hasData = false;
                String processor = null, modelName = null, cpuMHz = null, vendorId = null;

                for (String line : cpuInfoLines) {
                    if (line.isEmpty() && hasData) {
                        if (processor != null || modelName != null || cpuMHz != null || vendorId != null) {
                            processors.add(new CpuInfo.Processor(processor, modelName, cpuMHz, vendorId));
                        }
                        processor = null;
                        modelName = null;
                        cpuMHz = null;
                        vendorId = null;
                        hasData = false;
                        continue;
                    }

                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        switch (key) {
                            case "processor":
                                processor = value;
                                hasData = true;
                                break;
                            case "model name":
                                modelName = value;
                                hasData = true;
                                break;
                            case "cpu MHz":
                                cpuMHz = value;
                                hasData = true;
                                break;
                            case "vendor_id":
                                vendorId = value;
                                hasData = true;
                                break;
                            case "flags":
                            case "Features":
                                String[] featureArray = value.split(" ");
                                for (String f : featureArray) {
                                    if (!f.isEmpty()) {
                                        features.add(f);
                                    }
                                }
                                break;
                        }
                    }
                }
                if (hasData) {
                    processors.add(new CpuInfo.Processor(processor, modelName, cpuMHz, vendorId));
                }
            } catch (Exception e) {
                // Ignore
            }
        }

        boolean hasFp16 = features.contains("fphp") || features.contains("fp16");
        boolean hasDotProd = features.contains("dotprod") || features.contains("asimddp");
        boolean hasSve = features.contains("sve");
        boolean hasI8mm = features.contains("i8mm");

        String socModel = "";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            socModel = Build.SOC_MODEL;
        }

        return new CpuInfo(cores, processors, features, hasFp16, hasDotProd, hasSve, hasI8mm, socModel);
    }

    public static long getAvailableMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        return memInfo.availMem;
    }

    /**
     * Returns total RAM of the device in bytes.
     */
    public static long getTotalMemory(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memInfo);
        return memInfo.totalMem;
    }

    /**
     * Returns total RAM in gigabytes (rounded to one decimal place).
     */
    public static float getTotalMemoryGb(Context context) {
        long totalBytes = getTotalMemory(context);
        return totalBytes / (1024.0f * 1024.0f * 1024.0f);
    }
}