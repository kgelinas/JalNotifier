package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PokeBottomSheet extends BottomSheetDialogFragment {

    public interface OnPokeSentListener {
        void onPokeSent(boolean success, String message);
    }

    private String targetUserId;
    private String fullCookie;
    private OnPokeSentListener listener;

    private final OkHttpClient client = JalfNotifierApplication.httpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ProgressBar loadingBar;
    private RecyclerView recyclerView;
    private final List<Poke> pokes = new ArrayList<>();

    public static PokeBottomSheet newInstance(String targetUserId, String fullCookie, OnPokeSentListener listener) {
        PokeBottomSheet fragment = new PokeBottomSheet();
        fragment.targetUserId = targetUserId;
        fragment.fullCookie = fullCookie;
        fragment.listener = listener;
        return fragment;
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        BottomSheetUtils.setupFullHeight(dialog);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_pokes, container, false);

        loadingBar = view.findViewById(R.id.poke_loading);
        recyclerView = view.findViewById(R.id.recycler_pokes);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        fetchPokes();

        return view;
    }

    private void fetchPokes() {
        String url = ApiConstants.BASE_URL + "/ct/salutations/" + targetUserId + "/5";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (getContext() == null || !isAdded())
                        return;
                    Toast.makeText(getContext(), getString(R.string.failed_to_load_profile), Toast.LENGTH_SHORT).show();
                    dismiss();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String html = NetworkUtils.responseToString(r);
                        parsePokes(html, url);
                    } else {
                        mainHandler.post(() -> {
                            if (getContext() == null || !isAdded())
                                return;
                            Toast.makeText(getContext(), getString(R.string.failed_to_load_profile), Toast.LENGTH_SHORT)
                                    .show();
                            dismiss();
                        });
                    }
                }
            }
        });
    }

    private void parsePokes(String html, String url) {
        Document doc = Jsoup.parse(html, url);
        List<Poke> parsedPokes = new ArrayList<>();

        // 1. Try modern table-based selector from provided HTML
        Elements cells = doc.select("table#poke_list_table tr td.usr_info_p");
        for (Element cell : cells) {
            String pid = cell.id();
            if (pid.isEmpty())
                continue;

            String label = "";
            Element span = cell.selectFirst("span[id^=poke_desc_]");
            if (span != null) {
                label = span.text().trim();
                // Strip sender name prefix (e.g., "Jack86x22 te salue" -> "te salue")
                int firstSpace = label.indexOf(" ");
                if (firstSpace != -1) {
                    label = label.substring(firstSpace + 1).trim();
                }
            }

            String imageUrl = null;
            Element imgEl = cell.selectFirst("img");
            if (imgEl != null) {
                imageUrl = fixPokeImageUrl(imgEl.attr("src"));
                if (imageUrl != null && imageUrl.startsWith("/")) {
                    imageUrl = ApiConstants.BASE_URL + imageUrl;
                }
            }
            parsedPokes.add(new Poke(pid, label, imageUrl));
        }

        // 2. Fallback to legacy list-based selector
        if (parsedPokes.isEmpty()) {
            Elements items = doc.select("li[onclick^=send_poke_embed]");
            for (Element item : items) {
                String onclick = item.attr("onclick");
                String[] parts = onclick.split("'");
                if (parts.length < 4)
                    continue;
                String pid = parts[3];

                String label = item.text().trim();
                String imageUrl = null;
                Element imgEl = item.selectFirst("img");
                if (imgEl != null) {
                    imageUrl = fixPokeImageUrl(imgEl.attr("src"));
                    if (imageUrl != null && imageUrl.startsWith("/")) {
                        imageUrl = ApiConstants.BASE_URL + imageUrl;
                    }
                }
                parsedPokes.add(new Poke(pid, label, imageUrl));
            }
        }

        mainHandler.post(() -> {
            if (!isAdded())
                return;
            pokes.clear();
            pokes.addAll(parsedPokes);
            loadingBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.setAdapter(new PokeAdapter());
        });
    }

    private void sendPoke(String pid) {
        String url = ApiConstants.BASE_URL + "/ct/salutations/" + targetUserId + "/5";

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("action", "send_poke");
        params.put("pid", pid);
        params.put("replyto", "");

        RequestBody body = NetworkUtils.createIsoFormBody(params);

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", url)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isAdded())
                        return;
                    if (listener != null)
                        listener.onPokeSent(false, getString(R.string.error_network));
                    dismiss();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    mainHandler.post(() -> {
                        if (!isAdded())
                            return;
                        if (r.isSuccessful()) {
                            if (listener != null)
                                listener.onPokeSent(true, getString(R.string.poke_sent_success));
                        } else {
                            if (listener != null)
                                listener.onPokeSent(false, getString(R.string.error_server));
                        }
                        dismiss();
                    });
                }
            }
        });
    }

    private static class Poke {
        String id;
        String label;
        String imageUrl;

        Poke(String id, String label, String imageUrl) {
            this.id = id;
            this.label = label;
            this.imageUrl = imageUrl;
        }
    }

    private class PokeAdapter extends RecyclerView.Adapter<PokeAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_poke, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Poke poke = pokes.get(position);
            holder.label.setText(poke.label);
            if (poke.imageUrl != null) {
                Glide.with(holder.itemView.getContext())
                        .load(poke.imageUrl)
                        .into(holder.icon);
            }
            holder.itemView.setOnClickListener(v -> sendPoke(poke.id));
        }

        @Override
        public int getItemCount() {
            return pokes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView icon;
            TextView label;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.img_poke_thumb);
                label = itemView.findViewById(R.id.txt_poke_label);
            }
        }
    }

    private String fixPokeImageUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isEmpty())
            return null;
        // Transform /jalf/images/pokes/ID/thumb_poke_ID_X_X.png into
        // /jalf/images/pokes/thumb_poke_ID.png
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("pokes/(\\d+)/").matcher(rawUrl);
        if (m.find()) {
            return "/jalf/images/pokes/thumb_poke_" + m.group(1) + ".png";
        }
        return rawUrl;
    }
}
