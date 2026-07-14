package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import android.content.Context;
import android.content.SharedPreferences;

public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_MINE = 1;
    private static final int TYPE_THEIRS = 2;
    private static final int TYPE_AI_LOADING = 3;

    private final List<MessageItem> items;
    private final String myUserId;
    private OnMessageClickListener listener;

    public interface OnMessageClickListener {
        void onPendingMessageClick(MessageItem item);

        void onMessageLongClick(MessageItem item);
    }

    public MessageAdapter(List<MessageItem> items, String myUserId) {
        this.items = items;
        this.myUserId = myUserId;
    }

    public void setOnMessageClickListener(OnMessageClickListener listener) {
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        MessageItem item = items.get(position);
        if (item.isAiLoading) {
            return TYPE_AI_LOADING;
        }
        return (item.fromUserLink != null && item.fromUserLink.endsWith("/" + myUserId))
                ? TYPE_MINE
                : TYPE_THEIRS;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_MINE) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_mine, parent, false);
            return new MineViewHolder(view);
        } else if (viewType == TYPE_THEIRS) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_theirs, parent, false);
            return new TheirsViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_message_ai_loading, parent, false);
            return new AiLoadingViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        MessageItem item = items.get(position);
        if (holder instanceof MineViewHolder) {
            MineViewHolder vh = (MineViewHolder) holder;
            vh.msgText.setText(item.text);
            if (item.isPending) {
                vh.msgTime.setText(vh.itemView.getContext().getString(R.string.hint_pending));
                vh.msgReadIndicator.setVisibility(View.GONE);
                vh.msgEditIcon.setVisibility(View.VISIBLE);
                vh.itemView.setAlpha(0.6f);
                vh.itemView.setOnClickListener(v -> {
                    if (listener != null)
                        listener.onPendingMessageClick(item);
                });
            } else {
                vh.msgTime.setText(formatTime(vh.itemView.getContext(), item.postedIso8601));
                vh.msgReadIndicator.setVisibility(item.isRead ? View.VISIBLE : View.GONE);
                vh.msgEditIcon.setVisibility(View.GONE);
                vh.itemView.setAlpha(1.0f);
                vh.itemView.setOnClickListener(null);
            }
            vh.msgEphemeralIcon.setVisibility(item.isEphemeral ? View.VISIBLE : View.GONE);
            vh.msgText.setVisibility(item.text != null && !item.text.isEmpty() ? View.VISIBLE : View.GONE);
            if (item.photoUrl != null && !item.photoUrl.isEmpty()) {
                vh.msgPhoto.setVisibility(View.VISIBLE);
                String fullUrl = item.photoUrl.startsWith("http") ? item.photoUrl
                        : ApiConstants.BASE_URL + item.photoUrl;

                SharedPreferences prefs = vh.itemView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
                boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

                com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                        .placeholder(R.drawable.ic_download)
                        .error(R.drawable.ic_lock_24);

                if (blurNsfw && item.nsfwRank > 0) {
                    options = options.transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                            new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
                } else {
                    options = options.centerCrop();
                }

                Glide.with(vh.itemView.getContext())
                        .load(fullUrl)
                        .apply(options)
                        .into(vh.msgPhoto);

                vh.msgPhoto.setOnClickListener(v -> {
                    Intent intent = new Intent(vh.itemView.getContext(), FullscreenImageActivity.class);
                    intent.putExtra("imageUrl", fullUrl);
                    intent.putIntegerArrayListExtra("imageRanks", new ArrayList<>(Collections.singletonList(item.nsfwRank)));
                    vh.itemView.getContext().startActivity(intent);
                });
            } else {
                vh.msgPhoto.setVisibility(View.GONE);
            }

            vh.itemView.setOnLongClickListener(v -> {
                if (listener != null)
                    listener.onMessageLongClick(item);
                return true;
            });
        } else if (holder instanceof TheirsViewHolder) {
            TheirsViewHolder vh = (TheirsViewHolder) holder;
            vh.msgText.setText(item.text);
            vh.msgTime.setText(formatTime(vh.itemView.getContext(), item.postedIso8601));
            vh.msgEphemeralIcon.setVisibility(item.isEphemeral ? View.VISIBLE : View.GONE);
            vh.msgText.setVisibility(item.text != null && !item.text.isEmpty() ? View.VISIBLE : View.GONE);
            if (item.photoUrl != null && !item.photoUrl.isEmpty()) {
                vh.msgPhoto.setVisibility(View.VISIBLE);
                String fullUrl = item.photoUrl.startsWith("http") ? item.photoUrl
                        : ApiConstants.BASE_URL + item.photoUrl;

                SharedPreferences prefs = vh.itemView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
                boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

                com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                        .placeholder(R.drawable.ic_download)
                        .error(R.drawable.ic_lock_24);

                if (blurNsfw && item.nsfwRank > 0) {
                    options = options.transform(new com.bumptech.glide.load.resource.bitmap.CenterCrop(),
                            new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
                } else {
                    options = options.centerCrop();
                }

                Glide.with(vh.itemView.getContext())
                        .load(fullUrl)
                        .apply(options)
                        .into(vh.msgPhoto);

                vh.msgPhoto.setOnClickListener(v -> {
                    Intent intent = new Intent(vh.itemView.getContext(), FullscreenImageActivity.class);
                    intent.putExtra("imageUrl", fullUrl);
                    intent.putIntegerArrayListExtra("imageRanks", new ArrayList<>(Collections.singletonList(item.nsfwRank)));
                    vh.itemView.getContext().startActivity(intent);
                });
            } else {
                vh.msgPhoto.setVisibility(View.GONE);
            }

            vh.itemView.setOnLongClickListener(v -> {
                if (listener != null)
                    listener.onMessageLongClick(item);
                return true;
            });
        } else if (holder instanceof AiLoadingViewHolder) {
            AiLoadingViewHolder vh = (AiLoadingViewHolder) holder;
            vh.loadingText.setText(item.loadingStatus != null ? item.loadingStatus : "Preparing an answer...");
            
            // Premium breathing/pulsing animation
            android.view.animation.AlphaAnimation anim = new android.view.animation.AlphaAnimation(0.3f, 1.0f);
            anim.setDuration(1000);
            anim.setRepeatMode(android.view.animation.Animation.REVERSE);
            anim.setRepeatCount(android.view.animation.Animation.INFINITE);
            vh.loadingIcon.startAnimation(anim);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String formatTime(android.content.Context context, String isoString) {
        return TimeUtils.formatChatTimestamp(context, isoString);
    }

    public static class MessageItem {
        public String fromUserLink;
        public String messageLink;
        public String text;
        public String postedIso8601;
        public String photoUrl;
        public int nsfwRank;
        public boolean isRead;
        public boolean isPending;
        public boolean isEphemeral;
        public boolean isAiLoading;
        public String loadingStatus;
    }

    static class MineViewHolder extends RecyclerView.ViewHolder {
        TextView msgText, msgTime;
        ShapeableImageView msgPhoto;
        ImageView msgReadIndicator;
        ImageView msgEditIcon;
        ImageView msgEphemeralIcon;

        public MineViewHolder(@NonNull View itemView) {
            super(itemView);
            msgText = itemView.findViewById(R.id.msg_text);
            msgTime = itemView.findViewById(R.id.msg_time);
            msgPhoto = itemView.findViewById(R.id.msg_photo);
            msgReadIndicator = itemView.findViewById(R.id.msg_read_indicator);
            msgEditIcon = itemView.findViewById(R.id.msg_edit_icon);
            msgEphemeralIcon = itemView.findViewById(R.id.msg_ephemeral_icon);
        }
    }

    static class TheirsViewHolder extends RecyclerView.ViewHolder {
        TextView msgText, msgTime;
        ShapeableImageView msgPhoto;
        ImageView msgEphemeralIcon;

        public TheirsViewHolder(@NonNull View itemView) {
            super(itemView);
            msgText = itemView.findViewById(R.id.msg_text);
            msgTime = itemView.findViewById(R.id.msg_time);
            msgPhoto = itemView.findViewById(R.id.msg_photo);
            msgEphemeralIcon = itemView.findViewById(R.id.msg_ephemeral_icon);
        }
    }

    static class AiLoadingViewHolder extends RecyclerView.ViewHolder {
        ImageView loadingIcon;
        TextView loadingText;

        public AiLoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            loadingIcon = itemView.findViewById(R.id.ai_loading_icon);
            loadingText = itemView.findViewById(R.id.ai_loading_text);
        }
    }
}
