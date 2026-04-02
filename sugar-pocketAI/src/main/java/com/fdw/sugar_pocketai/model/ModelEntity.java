package com.fdw.sugar_pocketai.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "models")
public class ModelEntity {
    @PrimaryKey
    @NonNull
    private final String id;
    private final String name;
    private final String path;
    private final long size;
    private final String format;
    private final String parameters; // JSON string or something
    private final long createdAt;

    public ModelEntity(@NonNull String id, String name, String path, long size,
                       String format, String parameters, long createdAt) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.size = size;
        this.format = format;
        this.parameters = parameters;
        this.createdAt = createdAt;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getFormat() {
        return format;
    }

    public String getParameters() {
        return parameters;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}