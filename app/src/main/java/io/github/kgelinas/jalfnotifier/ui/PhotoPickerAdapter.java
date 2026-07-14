package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class PhotoPickerAdapter extends RecyclerView.Adapter<PhotoPickerAdapter.PhotoViewHolder> {

    public interface OnPhotoClickListener {
        void onPhotoClicked(String photoLink);
    }

    private final List<PhotoItem> photos;
    private final OnPhotoClickListener listener;

    public PhotoPickerAdapter(List<PhotoItem> photos, OnPhotoClickListener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_picker, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoItem item = photos.get(position);
        
        String safeUrl = item.thumbUrl;
        if (safeUrl != null && !safeUrl.isEmpty()) {
            if (safeUrl.startsWith("/")) {
                safeUrl = ApiConstants.BASE_URL + safeUrl;
            }
            
            Glide.with(holder.itemView.getContext())
                    .load(safeUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_download)
                    .error(R.drawable.ic_lock_24)
                    .into(holder.imageThumb);
        } else {
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_lock_24)
                    .into(holder.imageThumb);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPhotoClicked(item.photoLink);
            }
        });
    }

    @Override
    public int getItemCount() {
        return photos.size();
    }

    public static class PhotoItem {
        public String photoLink;
        public String thumbUrl;

        public PhotoItem(String photoLink, String thumbUrl) {
            this.photoLink = photoLink;
            this.thumbUrl = thumbUrl;
        }
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView imageThumb;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageThumb = itemView.findViewById(R.id.picker_image_thumb);
        }
    }
}
