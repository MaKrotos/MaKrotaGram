package com.fdw.sugar_pocketai.model;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import androidx.room.Delete;

import java.util.List;

@Dao
public interface ModelDao {
    @Insert
    void insert(ModelEntity model);

    @Update
    void update(ModelEntity model);

    @Delete
    void delete(ModelEntity model);

    @Query("SELECT * FROM models")
    LiveData<List<ModelEntity>> getAll();

    @Query("SELECT * FROM models WHERE id = :id")
    ModelEntity getById(String id);

    @Query("SELECT * FROM models WHERE path = :path")
    ModelEntity getByPath(String path);

    @Query("DELETE FROM models WHERE id = :id")
    void deleteById(String id);
}