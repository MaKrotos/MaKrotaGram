package com.fdw.sugar_pocketai.model;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import android.content.Context;

@Database(entities = {ModelEntity.class}, version = 1, exportSchema = false)
@TypeConverters({})
public abstract class ModelDatabase extends RoomDatabase {
    private static volatile ModelDatabase INSTANCE;

    public abstract ModelDao modelDao();

    public static ModelDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ModelDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            ModelDatabase.class,
                            "model_database"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}