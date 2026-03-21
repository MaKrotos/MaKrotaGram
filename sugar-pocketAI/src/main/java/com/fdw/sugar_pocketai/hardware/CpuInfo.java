package com.fdw.sugar_pocketai.hardware;

import java.util.List;
import java.util.Set;

public class CpuInfo {
    private final int cores;
    private final List<Processor> processors;
    private final Set<String> features;
    private final boolean hasFp16;
    private final boolean hasDotProd;
    private final boolean hasSve;
    private final boolean hasI8mm;
    private final String socModel;

    public CpuInfo(int cores, List<Processor> processors, Set<String> features,
                   boolean hasFp16, boolean hasDotProd, boolean hasSve, boolean hasI8mm, String socModel) {
        this.cores = cores;
        this.processors = processors;
        this.features = features;
        this.hasFp16 = hasFp16;
        this.hasDotProd = hasDotProd;
        this.hasSve = hasSve;
        this.hasI8mm = hasI8mm;
        this.socModel = socModel;
    }

    public int getCores() {
        return cores;
    }

    public List<Processor> getProcessors() {
        return processors;
    }

    public Set<String> getFeatures() {
        return features;
    }

    public boolean hasFp16() {
        return hasFp16;
    }

    public boolean hasDotProd() {
        return hasDotProd;
    }

    public boolean hasSve() {
        return hasSve;
    }

    public boolean hasI8mm() {
        return hasI8mm;
    }

    public String getSocModel() {
        return socModel;
    }

    public static class Processor {
        private final String processor;
        private final String modelName;
        private final String cpuMHz;
        private final String vendorId;

        public Processor(String processor, String modelName, String cpuMHz, String vendorId) {
            this.processor = processor;
            this.modelName = modelName;
            this.cpuMHz = cpuMHz;
            this.vendorId = vendorId;
        }

        public String getProcessor() {
            return processor;
        }

        public String getModelName() {
            return modelName;
        }

        public String getCpuMHz() {
            return cpuMHz;
        }

        public String getVendorId() {
            return vendorId;
        }
    }
}