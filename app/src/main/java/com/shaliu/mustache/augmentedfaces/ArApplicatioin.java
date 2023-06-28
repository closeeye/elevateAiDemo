package com.shaliu.mustache.augmentedfaces;

import android.app.Application;

import com.shaliu.mustache.repository.AppDatabase;
import com.shaliu.mustache.repository.VideoDao;

import androidx.room.Room;

/**
 * Sha: I temporarily put some global elements here.
 * Sha: In a real project, I should follow the project's specific rules, which may involve using ViewModel, Dagger, or other techniques.
 */
public class ArApplicatioin extends Application {
    /**
     * Sha: Life as long as AugmentedFacesActivity
     */
    public AugmentedFacesActivity.HistoryItemClickedListener historyItemClickedListener;

    /**
     * Sha: Life as long as Application
     */
    public static int index = 1;

    /**
     * Sha: Life as long as Application
     */
    public static int positionMustache;
    public VideoDao videoDao;

    @Override
    public void onCreate() {
        super.onCreate();
        initDB();
    }

    public void releaseResource(String name){
        if ("AugmentedFacesActivity".equalsIgnoreCase(name)) {
            historyItemClickedListener = null;
        }
    }

    private void initDB(){
        AppDatabase db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "my-database")
                .build();
        videoDao = db.videoDao();
    }
}
