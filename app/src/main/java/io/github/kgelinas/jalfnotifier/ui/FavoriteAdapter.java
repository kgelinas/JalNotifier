package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final List<FavoriteItem> items;

    public static class FavoriteItem {
        public FavoriteItem() {
            super();
        }

        public String name;
        public String age;
        public String details;
        public String city; // city scraped from HTML; appended to details when available
        public String distance; // distance scraped from HTML
        public String avatarUrl;
        public String sexIconUrl;
        public boolean isOnline;
        public boolean isFavorite;
        public boolean isNotified;
        public boolean isBookmarked;
        public boolean isCertified;
        public boolean isOnfire;
        public String userLink;
        public String otherUserId;
        public String sexLink;
        public int nsfwRank = 0;
        public String lastConnection;

        public FavoriteItem copy() {
            FavoriteItem c = new FavoriteItem();
            c.name = this.name;
            c.age = this.age;
            c.details = this.details;
            c.city = this.city;
            c.distance = this.distance;
            c.avatarUrl = this.avatarUrl;
            c.sexIconUrl = this.sexIconUrl;
            c.isOnline = this.isOnline;
            c.isFavorite = this.isFavorite;
            c.isNotified = this.isNotified;
            c.isBookmarked = this.isBookmarked;
            c.isCertified = this.isCertified;
            c.isOnfire = this.isOnfire;
            c.userLink = this.userLink;
            c.otherUserId = this.otherUserId;
            c.sexLink = this.sexLink;
            c.nsfwRank = this.nsfwRank;
            c.lastConnection = this.lastConnection;
            return c;
        }

        public String getUniqueKey() {
            if (otherUserId != null && !otherUserId.isEmpty()) {
                return otherUserId;
            }
            if (userLink != null && !userLink.isEmpty()) {
                return userLink;
            }
            return name != null ? name : "";
        }

        public boolean hasSameContent(FavoriteItem other) {
            if (other == null) return false;
            return isOnline == other.isOnline
                    && isFavorite == other.isFavorite
                    && isNotified == other.isNotified
                    && isBookmarked == other.isBookmarked
                    && isCertified == other.isCertified
                    && isOnfire == other.isOnfire
                    && nsfwRank == other.nsfwRank
                    && Objects.equals(name, other.name)
                    && Objects.equals(details, other.details)
                    && Objects.equals(avatarUrl, other.avatarUrl)
                    && Objects.equals(sexIconUrl, other.sexIconUrl)
                    && Objects.equals(lastConnection, other.lastConnection);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(FavoriteItem item);
    }

    private final OnItemClickListener listener;

    public FavoriteAdapter(List<FavoriteItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= items.size()) return RecyclerView.NO_ID;
        String key = items.get(position).getUniqueKey();
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            return key.hashCode();
        }
    }

    public void submitList(List<FavoriteItem> newItems) {
        final List<FavoriteItem> oldSnapshot = new ArrayList<>(this.items);
        final List<FavoriteItem> newSnapshot = new ArrayList<>(newItems.size());
        for (FavoriteItem item : newItems) {
            newSnapshot.add(item.copy());
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldSnapshot.size();
            }

            @Override
            public int getNewListSize() {
                return newSnapshot.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return Objects.equals(
                        oldSnapshot.get(oldItemPosition).getUniqueKey(),
                        newSnapshot.get(newItemPosition).getUniqueKey());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return oldSnapshot.get(oldItemPosition).hasSameContent(newSnapshot.get(newItemPosition));
            }

            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                // Return non-null payload so DefaultItemAnimator doesn't cross-fade/detach the ViewHolder
                return Boolean.TRUE;
            }
        });

        this.items.clear();
        this.items.addAll(newSnapshot);
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_carousel, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        FavoriteItem item = items.get(position);
        holder.nameText.setText(item.name);

        if (item.details != null && !item.details.isEmpty()) {
            holder.detailsText.setVisibility(View.VISIBLE);
            holder.detailsText.setText(item.details);
        } else {
            holder.detailsText.setVisibility(View.GONE);
        }

        String avatarUrl = item.avatarUrl;
        if (avatarUrl != null && avatarUrl.startsWith("/")) {
            avatarUrl = ApiConstants.BASE_URL + avatarUrl;
        }

        // Fall back to sex-specific icon when no profile photo
        String loadUrl = (avatarUrl != null && !avatarUrl.isEmpty()) ? avatarUrl : null;
        if (loadUrl == null && item.sexIconUrl != null && !item.sexIconUrl.isEmpty()) {
            String sex = item.sexIconUrl;
            loadUrl = sex.startsWith("/") ? ApiConstants.BASE_URL + sex : sex;
        }

        SharedPreferences prefs = holder.itemView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

        com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar);

        if (blurNsfw && item.nsfwRank > 0) {
            options = options.transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop(),
                    new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
        }

        if (loadUrl != null && !loadUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(loadUrl)
                    .apply(options)
                    .into(holder.avatarImage);
        } else {
            holder.avatarImage.setImageResource(R.drawable.ic_default_avatar);
        }

        holder.sexIconContainer.setVisibility(View.GONE);
        GenderColorUtils.applyGenderTint(
                holder.itemView.getContext(),
                (com.google.android.material.card.MaterialCardView) holder.itemView,
                holder.avatarSectionLayout,
                holder.textSectionLayout,
                item.sexIconUrl
        );

        holder.onlineIndicator.setVisibility(item.isOnline ? View.VISIBLE : View.GONE);

        if (item.isOnline) {
            holder.lastConnectionText.setText(holder.itemView.getContext().getString(R.string.last_active_format, holder.itemView.getContext().getString(R.string.online)));
            holder.lastConnectionText.setVisibility(View.VISIBLE);
        } else if (item.lastConnection != null && !item.lastConnection.isEmpty() && !"need_vip".equalsIgnoreCase(item.lastConnection)) {
            holder.lastConnectionText.setText(holder.itemView.getContext().getString(R.string.last_active_format, item.lastConnection));
            holder.lastConnectionText.setVisibility(View.VISIBLE);
        } else {
            holder.lastConnectionText.setVisibility(View.GONE);
        }

        // Badge icons
        holder.badgeStar.setVisibility(item.isFavorite ? View.VISIBLE : View.GONE);
        holder.badgeBell.setVisibility(item.isNotified ? View.VISIBLE : View.GONE);
        holder.badgeBookmark.setVisibility(item.isBookmarked ? View.VISIBLE : View.GONE);
        holder.badgeCertified.setVisibility(item.isCertified ? View.VISIBLE : View.GONE);
        holder.badgeFire.setVisibility(item.isOnfire ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        ImageView sexIcon;
        View sexIconContainer;
        View onlineIndicator;
        TextView nameText;
        TextView detailsText;
        TextView lastConnectionText;
        ImageView badgeStar;
        ImageView badgeBell;
        ImageView badgeBookmark;
        ImageView badgeCertified;
        ImageView badgeFire;
        View avatarSectionLayout;
        View textSectionLayout;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.fav_avatar);
            sexIcon = itemView.findViewById(R.id.fav_sex_icon);
            sexIconContainer = itemView.findViewById(R.id.fav_sex_icon_container);
            onlineIndicator = itemView.findViewById(R.id.fav_online_indicator);
            nameText = itemView.findViewById(R.id.fav_name);
            detailsText = itemView.findViewById(R.id.fav_details);
            lastConnectionText = itemView.findViewById(R.id.fav_last_connection);
            badgeStar = itemView.findViewById(R.id.fav_badge_star);
            badgeBell = itemView.findViewById(R.id.fav_badge_bell);
            badgeBookmark = itemView.findViewById(R.id.fav_badge_bookmark);
            badgeCertified = itemView.findViewById(R.id.fav_badge_certified);
            badgeFire = itemView.findViewById(R.id.fav_badge_fire);
            avatarSectionLayout = itemView.findViewById(R.id.fav_avatar_section_layout);
            textSectionLayout = itemView.findViewById(R.id.fav_text_section_layout);
        }
    }
}
