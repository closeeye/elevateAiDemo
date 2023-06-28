package com.shaliu.mustache.beans;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "videos")
public class Video {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String filePath;
}