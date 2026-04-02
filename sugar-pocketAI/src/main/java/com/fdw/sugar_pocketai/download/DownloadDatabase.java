package com.fdw.sugar_pocketai.download;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {DownloadEntity.class},
    version = 2,
    exportSchema = false
)
public abstract class DownloadDatabase extends RoomDatabase {
    public abstract DownloadDao downloadDao();

    private static final String DATABASE_NAME = "downloads.db";

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE downloads ADD COLUMN authToken TEXT");
        }
    };

    private static volatile DownloadDatabase INSTANCE;

    public static DownloadDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DownloadDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            DownloadDatabase.class,
                            DATABASE_NAME
                        )
                        .addMigrations(MIGRATION_1_2)
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}