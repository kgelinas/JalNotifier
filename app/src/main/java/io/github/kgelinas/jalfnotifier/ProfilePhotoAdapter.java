package io.github.kgelinas.jalfnotifier;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class ProfilePhotoAdapter extends RecyclerView.Adapter<ProfilePhotoAdapter.PhotoViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public static class PhotoItem {
        public final String url;
        public final String rating;
        public final int rank;

        public PhotoItem(String url, String rating, int rank) {
            this.url = url;
            this.rating = rating;
            this.rank = rank;
        }
    }

    private final List<PhotoItem> photoItems;
    private final OnItemClickListener listener;
    private boolean blurNsfw = true;

    public ProfilePhotoAdapter(List<PhotoItem> photoItems, OnItemClickListener listener) {
        this.photoItems = photoItems;
        this.listener = listener;
    }

    public void setBlurNsfw(boolean blurNsfw) {
        if (this.blurNsfw != blurNsfw) {
            this.blurNsfw = blurNsfw;
            notifyDataSetChanged();
        }
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_profile_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        PhotoItem item = photoItems.get(position);

        RequestOptions options = new RequestOptions()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar);

        if (blurNsfw && item.rank > 0) {
            options = options.transform(new BlurTransformation(25, 3));
        }

        Glide.with(holder.itemView.getContext())
                .load(item.url)
                .apply(options)
                .into(holder.imageView);

        if (item.rank > 0 && item.rating != null && !item.rating.isEmpty()) {
            holder.ratingText.setText(item.rating);
            holder.ratingText.setVisibility(View.VISIBLE);
        } else {
            holder.ratingText.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    listener.onItemClick(pos);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return photoItems.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView ratingText;

        public PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.photo_item_image);
            ratingText = itemView.findViewById(R.id.photo_item_rating);
        }
    }
}
