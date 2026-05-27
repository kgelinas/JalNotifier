package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_SEARCH_ITEM = 0;
    public static final int TYPE_LOAD_MORE = 1;

    private final List<SearchItem> items;
    private final Runnable onLoadMore;

    public static class SearchItem {
        public int type = TYPE_SEARCH_ITEM;

        public String userId;
        public String name;
        public String avatarUrl;
        public String age;
        public String location;
        public boolean isOnline;
        public boolean isVip;
        public String description;
        public String sexIconUrl;
        public List<String> privilegeIconUrls = new java.util.ArrayList<>();
        public String lastConnection;
        public boolean detailsFetched = false;
        public int nsfwRank = 0;
        public boolean isCertified = false;

        public SearchItem() {
            this.type = TYPE_LOAD_MORE;
        } // For load more

        public SearchItem(String userId, String name, String avatarUrl, String age, String location, boolean isOnline,
                boolean isVip, String description, String sexIconUrl, String lastConnection) {
            this.userId = userId;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.age = age;
            this.location = location;
            this.isOnline = isOnline;
            this.isVip = isVip;
            this.description = description;
            this.sexIconUrl = sexIconUrl;
            this.lastConnection = lastConnection;
            this.type = TYPE_SEARCH_ITEM;
        }

        public SearchItem(String userId, String name, String avatarUrl, String age, String location, boolean isOnline,
                boolean isVip, String description, String sexIconUrl, String lastConnection,
                List<String> privilegeIconUrls) {
            this(userId, name, avatarUrl, age, location, isOnline, isVip, description, sexIconUrl, lastConnection);
            if (privilegeIconUrls != null) {
                this.privilegeIconUrls.addAll(privilegeIconUrls);
            }
        }

        /**
         * Returns a formatted summary of the search item's details (age, location, last
         * connection).
         * Falls back to description if no other details are present.
         *
         * @return formatted summary string
         */
        public String getSummary() {
            StringBuilder sb = new StringBuilder();
            if (age != null && !age.isEmpty()) {
                sb.append(age);
            }
            if (location != null && !location.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" \u2022 ");
                sb.append(location);
            }
            if (lastConnection != null && !lastConnection.isEmpty()) {
                if (sb.length() > 0)
                    sb.append(" \u2022 ");
                sb.append(lastConnection);
            }

            if (sb.length() == 0 && description != null && !description.isEmpty()) {
                return description;
            }
            return sb.toString();
        }
    }

    public interface OnItemClickListener {
        void onItemClick(SearchItem item);
    }

    public interface ProfileDetailsFetcher {
        void fetch(SearchItem item);
    }

    private final OnItemClickListener listener;
    private final ProfileDetailsFetcher fetcher;

    public SearchAdapter(List<SearchItem> items, Runnable onLoadMore, OnItemClickListener listener,
            ProfileDetailsFetcher fetcher) {
        this.items = items;
        this.onLoadMore = onLoadMore;
        this.listener = listener;
        this.fetcher = fetcher;
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
        } else {
            // Reusing item_favorite_carousel layout since it has what we need
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_favorite_carousel, parent, false);
            return new SearchViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == TYPE_LOAD_MORE) {
            LoadMoreViewHolder vh = (LoadMoreViewHolder) holder;
            vh.btnLoadMore.setEnabled(true);
            vh.btnLoadMore.setText("Load More");
            if (onLoadMore != null) {
                vh.btnLoadMore.setOnClickListener(v -> {
                    vh.btnLoadMore.setEnabled(false);
                    vh.btnLoadMore.setText("Loading...");
                    onLoadMore.run();
                });
            }
            return;
        }

        SearchViewHolder vh = (SearchViewHolder) holder;
        SearchItem item = items.get(position);

        if (!item.detailsFetched && item.type == TYPE_SEARCH_ITEM && fetcher != null) {
            item.detailsFetched = true;
            fetcher.fetch(item);
        }

        vh.nameText.setText(item.name);
        String details = item.getSummary();

        if (!details.isEmpty()) {
            vh.detailsText.setVisibility(View.VISIBLE);
            vh.detailsText.setText(details);
        } else {
            vh.detailsText.setVisibility(View.GONE);
        }

        String avatarUrl = item.avatarUrl;
        if (avatarUrl != null && avatarUrl.startsWith("/")) {
            avatarUrl = ApiConstants.BASE_URL + avatarUrl;
        }

        // Fallback icon logic
        String loadUrl = (avatarUrl != null && !avatarUrl.isEmpty()) ? avatarUrl : null;
        if (loadUrl == null && item.sexIconUrl != null && !item.sexIconUrl.isEmpty()) {
            String sex = item.sexIconUrl;
            loadUrl = sex.startsWith("/") ? ApiConstants.BASE_URL + sex : sex;
        }

        SharedPreferences prefs = vh.itemView.getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

        com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                .circleCrop()
                .placeholder(R.drawable.ic_default_avatar)
                .error(R.drawable.ic_default_avatar);

        if (blurNsfw && item.nsfwRank > 0) {
            options = options.transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop(),
                    new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
        }

        Glide.with(vh.itemView.getContext())
                .load(loadUrl)
                .apply(options)
                .into(vh.avatarImage);

        if (item.sexIconUrl != null && !item.sexIconUrl.isEmpty()) {
            String sexIconUrl = item.sexIconUrl;
            if (sexIconUrl.startsWith("/")) {
                sexIconUrl = ApiConstants.BASE_URL + sexIconUrl;
            }
            Glide.with(vh.itemView.getContext())
                    .load(sexIconUrl)
                    .into(vh.sexIcon);
            vh.sexIcon.setVisibility(View.VISIBLE);
        } else {
            vh.sexIcon.setVisibility(View.GONE);
        }

        // Render privileges
        vh.privilegesContainer.removeAllViews();
        if (item.privilegeIconUrls != null && !item.privilegeIconUrls.isEmpty()) {
            vh.privilegesContainer.setVisibility(View.VISIBLE);
            for (String iconUrl : item.privilegeIconUrls) {
                ImageView iv = new ImageView(vh.itemView.getContext());
                int size = (int) (18 * vh.itemView.getContext().getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                lp.setMarginEnd((int) (4 * vh.itemView.getContext().getResources().getDisplayMetrics().density));
                iv.setLayoutParams(lp);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                vh.privilegesContainer.addView(iv);

                Glide.with(vh.itemView.getContext())
                        .load(iconUrl)
                        .into(iv);
            }
        } else {
            vh.privilegesContainer.setVisibility(View.GONE);
        }

        vh.onlineIndicator.setVisibility(item.isOnline ? View.VISIBLE : View.GONE);
        if (vh.badgeCertified != null) {
            vh.badgeCertified.setVisibility(item.isCertified ? View.VISIBLE : View.GONE);
        }

        // Provide click to view profile
        final String finalAvatarUrl = avatarUrl;
        vh.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImage;
        ImageView sexIcon;
        View onlineIndicator;
        TextView nameText;
        TextView detailsText;
        LinearLayout privilegesContainer;
        ImageView badgeCertified;

        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarImage = itemView.findViewById(R.id.fav_avatar);
            sexIcon = itemView.findViewById(R.id.fav_sex_icon);
            onlineIndicator = itemView.findViewById(R.id.fav_online_indicator);
            nameText = itemView.findViewById(R.id.fav_name);
            detailsText = itemView.findViewById(R.id.fav_details);
            privilegesContainer = itemView.findViewById(R.id.fav_privileges);
            badgeCertified = itemView.findViewById(R.id.fav_badge_certified);
        }
    }

    static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.button.MaterialButton btnLoadMore;

        public LoadMoreViewHolder(@NonNull View itemView) {
            super(itemView);
            btnLoadMore = itemView.findViewById(R.id.btn_load_more);
        }
    }
}
