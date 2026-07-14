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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {

    public interface OnVideoClickListener {
        void onVideoClick(VideoItem item);
    }

    public static class VideoItem {
        public String videoId;
        public String title;
        public String thumbnailUri;
        public int duration;
        public String videoLink; // REST link: /rest/videos/VIDEOID

        public VideoItem(String videoId, String title, String thumbnailUri, int duration, String videoLink) {
            this.videoId = videoId;
            this.title = title;
            this.thumbnailUri = thumbnailUri;
            this.duration = duration;
            this.videoLink = videoLink;
        }

        public String getWebsiteUrl() {
            // Converts /rest/videos/VIDEOID to https://app.jalf.com/ct/videos/view/VIDEOID
            String id = videoId;
            if (id == null && videoLink != null) {
                String[] parts = videoLink.split("/");
                id = parts[parts.length - 1];
            }
            return ApiConstants.BASE_URL + "/ct/videos/view/" + id;
        }
    }

    private final List<VideoItem> items;
    private final OnVideoClickListener listener;

    public VideoAdapter(List<VideoItem> items, OnVideoClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_video_item, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        VideoItem item = items.get(position);

        String thumbUrl = item.thumbnailUri;
        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            if (!thumbUrl.startsWith("http")) {
                if (!thumbUrl.startsWith("/")) {
                    thumbUrl = "/" + thumbUrl;
                }
                thumbUrl = ApiConstants.BASE_URL + thumbUrl;
            }

            Glide.with(holder.itemView.getContext())
                    .load(thumbUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_play_arrow_24)
                    .error(R.drawable.ic_play_arrow_24)
                    .into(holder.thumbnail);
        } else {
            Glide.with(holder.itemView.getContext())
                    .load(R.drawable.ic_play_arrow_24)
                    .into(holder.thumbnail);
        }

        if (item.duration > 0) {
            holder.txtDuration.setVisibility(View.VISIBLE);
            holder.txtDuration.setText(formatDuration(item.duration));
        } else {
            holder.txtDuration.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null)
                listener.onVideoClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatDuration(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    static class VideoViewHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail;
        TextView txtDuration;

        VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.video_thumbnail);
            txtDuration = itemView.findViewById(R.id.video_duration);
        }
    }
}
