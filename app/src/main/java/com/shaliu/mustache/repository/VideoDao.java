package com.shaliu.mustache.repository;

import com.shaliu.mustache.beans.Video;

import java.util.List;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

@Dao
public interface VideoDao {
    @Insert
    void insert(Video video);

    @Query("SELECT * FROM VIDEOS")
    List<Video> queryAll();
}
