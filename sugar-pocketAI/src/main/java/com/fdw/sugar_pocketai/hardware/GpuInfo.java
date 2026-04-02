package com.fdw.sugar_pocketai.hardware;

public class GpuInfo {
    private final String renderer;
    private final String vendor;
    private final String version;
    private final boolean hasAdreno;
    private final boolean hasMali;
    private final boolean hasPowerVR;
    private final boolean supportsOpenCL;
    private final String gpuType;

    public GpuInfo(String renderer, String vendor, String version,
                   boolean hasAdreno, boolean hasMali, boolean hasPowerVR,
                   boolean supportsOpenCL, String gpuType) {
        this.renderer = renderer;
        this.vendor = vendor;
        this.version = version;
        this.hasAdreno = hasAdreno;
        this.hasMali = hasMali;
        this.hasPowerVR = hasPowerVR;
        this.supportsOpenCL = supportsOpenCL;
        this.gpuType = gpuType;
    }

    public String getRenderer() {
        return renderer;
    }

    public String getVendor() {
        return vendor;
    }

    public String getVersion() {
        return version;
    }

    public boolean hasAdreno() {
        return hasAdreno;
    }

    public boolean hasMali() {
        return hasMali;
    }

    public boolean hasPowerVR() {
        return hasPowerVR;
    }

    public boolean supportsOpenCL() {
        return supportsOpenCL;
    }

    public String getGpuType() {
        return gpuType;
    }
}