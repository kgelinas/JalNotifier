package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONObject;

public class EventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_EVENT = 0;
    public static final int TYPE_LOAD_MORE = 1;

    private final List<EventItem> items;

    public static class EventItem {
        public int type = TYPE_EVENT;
        public String title;
        public String body;
        public String timeIso;
        public boolean isUnread;
        public String avatarUrl;
        public String sexIconUrl;
        public String userLink;
        public String otherUserId;
        public String sexLink;
        public long sortTimestamp;
        public String secondaryImageUrl;
        public String why;
        public int nsfwRank = 0;

        public EventItem() {
            super();
        }

        public EventItem(int type) {
            super();
            this.type = type;
        }

        public int eventType;
        public boolean isOnline;
        public String picId;
        public boolean isLiked;
        public boolean isRealized;
        public String videoUrl;

        public JSONObject toJson() {
            try {
                JSONObject obj = new JSONObject();
                obj.put("title", title);
                obj.put("body", body);
                obj.put("timeIso", timeIso);
                obj.put("isUnread", isUnread);
                obj.put("avatarUrl", avatarUrl);
                obj.put("sexIconUrl", sexIconUrl);
                obj.put("userLink", userLink);
                obj.put("otherUserId", otherUserId);
                obj.put("sexLink", sexLink);
                obj.put("sortTimestamp", sortTimestamp);
                obj.put("secondaryImageUrl", secondaryImageUrl);
                obj.put("eventType", eventType);
                obj.put("isOnline", isOnline);
                obj.put("why", why);
                obj.put("nsfwRank", nsfwRank);
                obj.put("picId", picId);
                obj.put("isLiked", isLiked);
                obj.put("isRealized", isRealized);
                obj.put("videoUrl", videoUrl);
                return obj;
            } catch (Exception e) {
                return null;
            }
        }

        public static EventItem fromJson(JSONObject obj) {
            EventItem item = new EventItem();
            item.title = obj.optString("title", "");
            item.body = obj.optString("body", "");
            item.timeIso = obj.optString("timeIso", "");
            item.isUnread = obj.optBoolean("isUnread", false);
            item.avatarUrl = obj.optString("avatarUrl", "");
            item.sexIconUrl = obj.optString("sexIconUrl", "");
            item.userLink = obj.optString("userLink", "");
            item.otherUserId = obj.optString("otherUserId", "");
            item.sexLink = obj.optString("sexLink", "");
            item.sortTimestamp = obj.optLong("sortTimestamp", 0);
            item.secondaryImageUrl = obj.optString("secondaryImageUrl", "");
            item.eventType = obj.optInt("eventType", -1);
            item.isOnline = obj.optBoolean("isOnline", false);
            item.why = obj.optString("why", "");
            item.nsfwRank = obj.optInt("nsfwRank", 0);
            item.picId = obj.optString("picId", "");
            item.isLiked = obj.optBoolean("isLiked", false);
            item.isRealized = obj.optBoolean("isRealized", false);
            item.videoUrl = obj.optString("videoUrl", "");
            return item;
        }
    }

    public interface OnItemClickListener {
        void onItemClick(EventItem item);
    }

    public interface OnSecondaryImageClickListener {
        void onSecondaryImageClick(EventItem item);
    }

    private final OnItemClickListener listener;
    private OnSecondaryImageClickListener secondaryImageClickListener;
    private Runnable onLoadMoreListener;

    public EventAdapter(List<EventItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setOnSecondaryImageClickListener(OnSecondaryImageClickListener secondaryImageClickListener) {
        this.secondaryImageClickListener = secondaryImageClickListener;
    }

    public void setOnLoadMoreListener(Runnable onLoadMoreListener) {
        this.onLoadMoreListener = onLoadMoreListener;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOAD_MORE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_load_more, parent, false);
            return new LoadMoreViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder rawHolder, int position) {
        if (getItemViewType(position) == TYPE_LOAD_MORE) {
            LoadMoreViewHolder vh = (LoadMoreViewHolder) rawHolder;
            vh.btnLoadMore.setEnabled(true);
            vh.btnLoadMore.setText(R.string.show_more);
            if (onLoadMoreListener != null) {
                vh.btnLoadMore.setOnClickListener(v -> {
                    vh.btnLoadMore.setEnabled(false);
                    vh.btnLoadMore.setText(R.string.loading);
                    onLoadMoreListener.run();
                });
            }
            return;
        }

        EventViewHolder holder = (EventViewHolder) rawHolder;
        EventItem item = items.get(position);

        holder.titleText.setText(Html.fromHtml(item.title, Html.FROM_HTML_MODE_COMPACT));
        holder.timeText.setText(getRelativeTime(holder.itemView.getContext(), item.timeIso));

        if (item.body != null && !item.body.isEmpty()) {
            holder.bodyText.setVisibility(View.VISIBLE);
            holder.bodyText.setText(Html.fromHtml(item.body, Html.FROM_HTML_MODE_COMPACT));
        } else {
            holder.bodyText.setVisibility(View.GONE);
        }

        if (item.isUnread) {
            holder.titleText.setTypeface(null, Typeface.BOLD);
            holder.unreadDot.setVisibility(View.VISIBLE);
        } else {
            holder.titleText.setTypeface(null, Typeface.NORMAL);
            holder.unreadDot.setVisibility(View.GONE);
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

        String avatarUrl = item.avatarUrl;
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = ApiConstants.BASE_URL + avatarUrl;
            }
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl)
                    .apply(options)
                    .into(holder.avatarImage);
        } else if (item.sexIconUrl != null && !item.sexIconUrl.isEmpty()) {
            String sexUrl = item.sexIconUrl;
            if (sexUrl.startsWith("/")) {
                sexUrl = ApiConstants.BASE_URL + sexUrl;
            }
            Glide.with(holder.itemView.getContext())
                    .load(sexUrl)
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

        if (item.secondaryImageUrl != null && !item.secondaryImageUrl.isEmpty()) {
            String secondaryUrl = item.secondaryImageUrl;
            if (secondaryUrl.startsWith("/")) {
                secondaryUrl = ApiConstants.BASE_URL + secondaryUrl;
            }
            com.bumptech.glide.request.RequestOptions secondaryOptions = new com.bumptech.glide.request.RequestOptions()
                    .centerCrop()
                    .placeholder(R.drawable.ic_download)
                    .error(R.drawable.ic_lock_24);

            if (blurNsfw && item.nsfwRank > 0) {
                secondaryOptions = secondaryOptions.transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                        new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
            }

            Glide.with(holder.itemView.getContext())
                    .load(secondaryUrl)
                    .apply(secondaryOptions)
                    .into(holder.secondaryImage);
            holder.secondaryImage.setVisibility(View.VISIBLE);
            if (secondaryImageClickListener != null) {
                holder.secondaryImage.setClickable(true);
                holder.secondaryImage.setOnClickListener(v -> secondaryImageClickListener.onSecondaryImageClick(item));
            } else {
                holder.secondaryImage.setOnClickListener(null);
                holder.secondaryImage.setClickable(false);
            }
        } else {
            holder.secondaryImage.setVisibility(View.GONE);
            holder.secondaryImage.setOnClickListener(null);
            holder.secondaryImage.setClickable(false);
        }

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

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        ImageView sexIcon;
        View sexIconContainer;
        ImageView secondaryImage;
        View onlineIndicator;
        TextView titleText;
        TextView bodyText;
        TextView timeText;
        View unreadDot;
        View avatarSectionLayout;
        View textSectionLayout;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.event_avatar);
            sexIcon = itemView.findViewById(R.id.event_sex_icon);
            sexIconContainer = itemView.findViewById(R.id.event_sex_icon_container);
            secondaryImage = itemView.findViewById(R.id.event_secondary_image);
            onlineIndicator = itemView.findViewById(R.id.event_online_indicator);
            titleText = itemView.findViewById(R.id.event_title);
            bodyText = itemView.findViewById(R.id.event_body);
            timeText = itemView.findViewById(R.id.event_time);
            unreadDot = itemView.findViewById(R.id.event_unread_dot);
            avatarSectionLayout = itemView.findViewById(R.id.event_avatar_section_layout);
            textSectionLayout = itemView.findViewById(R.id.event_text_section_layout);
        }
    }

    static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.button.MaterialButton btnLoadMore;

        public LoadMoreViewHolder(@NonNull View itemView) {
            super(itemView);
            btnLoadMore = itemView.findViewById(R.id.btn_load_more);
        }
    }

    private String getRelativeTime(android.content.Context context, String isoTime) {
        return TimeUtils.getRelativeTime(context, isoTime);
    }
}
