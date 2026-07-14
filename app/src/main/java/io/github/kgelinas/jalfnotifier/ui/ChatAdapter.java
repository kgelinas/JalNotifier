package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
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

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_CHAT = 1;
    public static final int TYPE_LOAD_MORE = 2;

    private final List<ChatItem> items;
    private final OnLoadMoreListener loadMoreListener;

    public interface OnLoadMoreListener {
        void onLoadMore();
    }

    public interface OnChatClickListener {
        void onChatClicked(ChatItem item);
    }

    public interface OnProfileClickListener {
        void onProfileClicked(ChatItem item);
    }

    public interface OnChatLongClickListener {
        boolean onChatLongClicked(ChatItem item);
    }

    public static class ChatItem {
        public int type;
        // For Headers
        public String title;
        // For Chats
        public String name;
        public String lastMessage;
        public String timeIso;
        public String avatarUrl;
        public boolean isUnread;
        public int unreadCount;
        public boolean isOnline;
        public String conversationLink;
        public String otherUserId;
        public String sexIconUrl;
        public String otherReadUntil;
        public String lastPosted;
        public String lastMessagePosted;
        public String sexLink;
        public boolean isLastMessageMine = false;
        public boolean isSelected = false;
        public boolean isPinned = false;
        public boolean isEphemeral = false;
        public int nsfwRank = 0;

        public ChatItem(int type) {
            this.type = type;
        }
    }

    private final OnChatClickListener chatClickListener;
    private final OnProfileClickListener profileClickListener;
    private final OnChatLongClickListener chatLongClickListener;

    private boolean selectionMode = false;

    public ChatAdapter(List<ChatItem> items, OnLoadMoreListener loadMoreListener,
            OnChatClickListener chatClickListener, OnProfileClickListener profileClickListener,
            OnChatLongClickListener chatLongClickListener) {
        this.items = items;
        this.loadMoreListener = loadMoreListener;
        this.chatClickListener = chatClickListener;
        this.profileClickListener = profileClickListener;
        this.chatLongClickListener = chatLongClickListener;
    }

    public void setSelectionMode(boolean mode) {
        this.selectionMode = mode;
        if (!mode) {
            for (ChatItem item : items)
                item.isSelected = false;
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).type;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_chat_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_LOAD_MORE) {
            View view = inflater.inflate(R.layout.item_load_more, parent, false);
            return new LoadMoreViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat, parent, false);
            return new ChatViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = items.get(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).titleText.setText(item.title);
        } else if (holder instanceof LoadMoreViewHolder) {
            ((LoadMoreViewHolder) holder).btnLoadMore.setOnClickListener(v -> {
                if (loadMoreListener != null)
                    loadMoreListener.onLoadMore();
            });
        } else if (holder instanceof ChatViewHolder) {
            ChatViewHolder chatHolder = (ChatViewHolder) holder;
            chatHolder.nameText.setText(item.name);
            chatHolder.messageText.setText(item.lastMessage);
            chatHolder.timeText.setText(getRelativeTime(chatHolder.itemView.getContext(), item.timeIso));

            if (item.isUnread) {
                chatHolder.nameText.setTypeface(null, Typeface.BOLD);
                chatHolder.messageText.setTypeface(null, Typeface.BOLD);
                chatHolder.unreadCountText.setVisibility(View.VISIBLE);
                if (item.unreadCount > 0) {
                    chatHolder.unreadCountText.setText(String.valueOf(item.unreadCount));
                } else {
                    chatHolder.unreadCountText.setText("");
                }
            } else {
                chatHolder.nameText.setTypeface(null, Typeface.NORMAL);
                chatHolder.messageText.setTypeface(null, Typeface.NORMAL);
                chatHolder.unreadCountText.setVisibility(View.GONE);
            }

            chatHolder.onlineIndicator.setVisibility(item.isOnline ? View.VISIBLE : View.GONE);

            String compareTime = (item.isLastMessageMine && item.lastMessagePosted != null
                    && !item.lastMessagePosted.isEmpty())
                            ? item.lastMessagePosted
                            : item.lastPosted;

            if (item.otherReadUntil != null && !item.otherReadUntil.isEmpty() && compareTime != null
                    && !compareTime.isEmpty() && !item.isUnread && item.isLastMessageMine) {
                // If the other person's read_until is same or later than the last message time,
                // show read double-check
                if (item.otherReadUntil.compareTo(compareTime) >= 0) {
                    chatHolder.readIndicator.setVisibility(View.VISIBLE);
                } else {
                    chatHolder.readIndicator.setVisibility(View.GONE);
                }
            } else {
                chatHolder.readIndicator.setVisibility(View.GONE);
            }

            chatHolder.pinIcon.setVisibility(item.isPinned ? View.VISIBLE : View.GONE);
            chatHolder.ephemeralIcon.setVisibility(item.isEphemeral ? View.VISIBLE : View.GONE);

            if (selectionMode) {
                chatHolder.checkBox.setVisibility(View.VISIBLE);
                chatHolder.checkBox.setChecked(item.isSelected);
            } else {
                chatHolder.checkBox.setVisibility(View.GONE);
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

            SharedPreferences prefs = chatHolder.itemView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

            com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar);

            if (blurNsfw && item.nsfwRank > 0) {
                options = options.transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop(),
                        new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
            }

            Glide.with(chatHolder.itemView.getContext())
                    .load(loadUrl)
                    .apply(options)
                    .into(chatHolder.avatarImage);

            chatHolder.sexIconContainer.setVisibility(View.GONE);
            GenderColorUtils.applyGenderTint(
                    chatHolder.itemView.getContext(),
                    (com.google.android.material.card.MaterialCardView) chatHolder.itemView,
                    chatHolder.avatarSectionLayout,
                    chatHolder.textSectionLayout,
                    item.sexIconUrl
            );

            // Click listener for chatting
            chatHolder.itemView.setOnClickListener(v -> {
                if (selectionMode) {
                    item.isSelected = !item.isSelected;
                    chatHolder.checkBox.setChecked(item.isSelected);
                    if (chatClickListener != null)
                        chatClickListener.onChatClicked(item); // Re-use callback to update counter in Activity
                } else {
                    if (chatClickListener != null) {
                        chatClickListener.onChatClicked(item);
                    }
                }
            });

            chatHolder.itemView.setOnLongClickListener(v -> {
                if (chatLongClickListener != null) {
                    return chatLongClickListener.onChatLongClicked(item);
                }
                return false;
            });

            chatHolder.checkBox.setOnClickListener(v -> {
                item.isSelected = chatHolder.checkBox.isChecked();
                if (chatClickListener != null)
                    chatClickListener.onChatClicked(item);
            });

            // Click listener for profile (avatar)
            chatHolder.avatarImage.setOnClickListener(v -> {
                if (profileClickListener != null) {
                    profileClickListener.onProfileClicked(item);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView titleText;

        public HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.header_title);
        }
    }

    static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        View btnLoadMore;

        public LoadMoreViewHolder(@NonNull View itemView) {
            super(itemView);
            btnLoadMore = itemView.findViewById(R.id.btn_load_more);
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        ImageView sexIcon;
        View sexIconContainer;
        View onlineIndicator;
        TextView unreadCountText;
        TextView nameText;
        TextView timeText;
        TextView messageText;
        ImageView readIndicator;
        android.widget.CheckBox checkBox;
        ImageView pinIcon;
        ImageView ephemeralIcon;
        View avatarSectionLayout;
        View textSectionLayout;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.chat_avatar);
            sexIcon = itemView.findViewById(R.id.chat_sex_icon);
            sexIconContainer = itemView.findViewById(R.id.chat_sex_icon_container);
            onlineIndicator = itemView.findViewById(R.id.chat_online_indicator);
            unreadCountText = itemView.findViewById(R.id.chat_unread_count);
            nameText = itemView.findViewById(R.id.chat_name);
            timeText = itemView.findViewById(R.id.chat_time);
            messageText = itemView.findViewById(R.id.chat_message);
            readIndicator = itemView.findViewById(R.id.chat_read_indicator);
            checkBox = itemView.findViewById(R.id.chat_checkbox);
            pinIcon = itemView.findViewById(R.id.chat_pin_icon);
            ephemeralIcon = itemView.findViewById(R.id.chat_ephemeral_icon);
            avatarSectionLayout = itemView.findViewById(R.id.chat_avatar_section_layout);
            textSectionLayout = itemView.findViewById(R.id.chat_text_section_layout);
        }
    }

    private String getRelativeTime(android.content.Context context, String isoTime) {
        return TimeUtils.getRelativeTime(context, isoTime);
    }
}
