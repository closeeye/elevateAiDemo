package com.shaliu.mustache.augmentedfaces;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.shaliu.mustache.R;
import com.shaliu.mustache.beans.Video;

import java.io.IOException;
import java.util.List;

public class GridActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerView.setLayoutManager(layoutManager);
        VideoAdapter adapter = new VideoAdapter();
        recyclerView.setAdapter(adapter);
    }

    public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
        private List<Video> videoList;

        VideoAdapter() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    videoList = ((ArApplicatioin) getApplication()).videoDao.queryAll();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            notifyDataSetChanged();
                        }
                    });
                }
            }).start();
        }

        @Override
        public VideoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.video_item_layout, parent, false);
            return new VideoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(VideoViewHolder holder, int position) {
            ViewGroup.LayoutParams layoutParams = holder.itemView.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT; // 设置宽度为200像素
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT; // 设置高度为自适应
            holder.itemView.setLayoutParams(layoutParams);

            String filePath = videoList.get(position).filePath;
            Bitmap thumbnail = loadThumbnailFromVideo(filePath);

            holder.thumbnailImageView.setImageBitmap(thumbnail);
            long videoDuration = getVideoDuration(filePath);
            holder.durationTextView.setText("duration" + videoDuration);
            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((ArApplicatioin)getApplication()).historyItemClickedListener.historyItemClicked(filePath, videoDuration);
                    finish();
                }
            });
        }

        @Override
        public int getItemCount() {
            if (videoList == null || videoList.size() == 0){
                return 0;
            }
            return videoList.size();
        }

        public class VideoViewHolder extends RecyclerView.ViewHolder {
            public ImageView thumbnailImageView;
            public TextView durationTextView;
            public TextView tagTextView;

            public VideoViewHolder(View itemView) {
                super(itemView);
                thumbnailImageView = itemView.findViewById(R.id.thumbnailImageView);
                durationTextView = itemView.findViewById(R.id.durationTextView);
                tagTextView = itemView.findViewById(R.id.tagTextView);
            }
        }
    }

    private Bitmap loadThumbnailFromVideo(String filePath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(filePath);

        String rotationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        int rotation = Integer.parseInt(rotationString);
        Bitmap thumbnail = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Video.Thumbnails.MINI_KIND);

        Matrix matrix = new Matrix();
        matrix.postRotate(-90);
        Bitmap rotatedThumbnail = Bitmap.createBitmap(thumbnail, 0, 0, thumbnail.getWidth(), thumbnail.getHeight(), matrix, true);

        return rotatedThumbnail;
    }

    public long getVideoDuration(String videoPath) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoPath);

        String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        long duration = Long.parseLong(durationString);

        try {
            retriever.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return duration;
    }
}