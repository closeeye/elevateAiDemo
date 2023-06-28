package com.shaliu.mustache.augmentedfaces;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


import com.shaliu.mustache.R;

import java.util.ArrayList;
import java.util.List;

import androidx.recyclerview.widget.RecyclerView;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {
    private List<Integer> imageList;
    private AugmentedFacesActivity.ItemClickListener mItemClickListener;
    public ImageAdapter() {
        // Sha: this is template code. In fact, imageList should not be a fixed death list.
        this.imageList = new ArrayList<>();
        imageList.add(1);
        imageList.add(2);
        imageList.add(3);
//        imageList.add(4);
    }

    // 创建ViewHolder并绑定视图
    @Override
    public ImageViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    // 绑定数据到ViewHolder上
    @Override
    public void onBindViewHolder(ImageViewHolder holder, int position) {
        int imageIndex = imageList.get(position);

//        Glide.with(holder.itemView.getContext())
//                .load(imageUrl)
//                .into(holder.imageView);
        // Sha: this is also a template code. In fact, it shouldn't be here.
        ViewGroup.LayoutParams layoutParams = holder.imageView.getLayoutParams();
        layoutParams.width = 400; // 设置宽度为200像素
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT; // 设置高度为自适应
        holder.imageView.setLayoutParams(layoutParams);
        switch (imageIndex){
            case 1:
                holder.imageView.setImageResource(R.drawable.m1);
                break;
            case 2:
                holder.imageView.setImageResource(R.drawable.m2);
                break;
            case 3:
                holder.imageView.setImageResource(R.drawable.m3);
                break;
//            case 4:
//                holder.imageView.setImageResource(R.drawable.m4);
//                break;
            default:
        }
        holder.imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mItemClickListener != null) {
                    mItemClickListener.onClick(position);
                }
            }
        });
    }

    // 返回数据集的大小
    @Override
    public int getItemCount() {
        return imageList.size();
    }

    public void setItemClickListener (AugmentedFacesActivity.ItemClickListener listener) {
        mItemClickListener = listener;
    }

    // ViewHolder类
    static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;

        ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageView);
        }
    }
}
