package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.text.Html;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.bumptech.glide.Glide;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    private String userId;
    private boolean isFavorite;
    private boolean isBookmarked;
    private boolean isNotified;

    private String fullCookie;
    private String suid;
    private final OkHttpClient client = JalfNotifierApplication.httpClient();

    private MaterialToolbar toolbar;
    private TextView toolbarTitleText;
    private ImageView toolbarCertifiedBadge;
    private ProfilePhotoAdapter photoAdapter;
    private final List<ProfilePhotoAdapter.PhotoItem> allPhotoItems = new ArrayList<>();
    private final List<VideoAdapter.VideoItem> allVideos = new ArrayList<>();
    private VideoAdapter videoAdapter;
    private final List<String> orientationFromHtml = new ArrayList<>();
    private String cityFromHtml = null;
    private String age = null;
    private String sexLink = null;
    private String lastConnectedFromHtml = null;
    private String lastStatusFromHtml = null;
    private String registrationDateFromHtml = null;
    private String commonMatchesFromHtml = null;
    private JSONObject currentProfileData = null;

    private ImageView headerSexIcon;
    private TextView txtDescription, headerDetails;
    private View descriptionContainer;
    private ProgressBar progressBar;
    private View contentLayout;

    private ChipGroup chipGroupInterests;
    private GridLayout gridDetails;
    private View cardVideos, cardLastStatus;
    private RecyclerView recyclerVideos;
    private TextView profileLastStatusText;
    private LinearLayout headerPrivileges;

    private MenuItem menuFav;
    private MenuItem menuBookmark;
    private MenuItem menuNotify;
    private MenuItem menuBlock;

    private boolean isBlocked = false;

    private FloatingActionButton fabMain;
    private ExtendedFloatingActionButton fabPoke, fabMessage;

    private View cardManagePhotos;
    private androidx.appcompat.widget.AppCompatSpinner spinnerProfileAlbums;
    private com.google.android.material.button.MaterialButton btnProfileUploadCamera;
    private com.google.android.material.button.MaterialButton btnProfileUploadGallery;
    private com.google.android.material.progressindicator.LinearProgressIndicator profileUploadProgress;

    private CameraGalleryPicker profileCameraGalleryPicker;
    private androidx.activity.result.ActivityResultLauncher<Intent> profileCameraLauncher;
    private androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> profileGalleryLauncher;

    private final List<String> profileAlbumNames = new ArrayList<>();
    private final List<String> profileAlbumLinks = new ArrayList<>();
    private PhotoUploadTask profilePhotoUploadTask;

    private final android.content.BroadcastReceiver sseReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String type = intent.getStringExtra("type");
            String data = intent.getStringExtra("data");
            if ("looked".equals(type) && data != null) {
                try {
                    JSONObject json = new JSONObject(data);
                    JSONObject source = json.optJSONObject("source");
                    String lookerLink = source != null ? source.optString("user_link", "") : "";
                    if (!lookerLink.isEmpty() && lookerLink.contains(userId)) {
                        Log.d(TAG, "Current profile visited! Refreshing status...");
                        fetchStatusOnly();
                    }
                } catch (Exception ignored) {
                }
            }
        }
    };
    private boolean isFabMenuOpen = false;

    private static final int CHIP_TYPE_GOAL = 0;
    private static final int CHIP_TYPE_FANTASY = 1;
    private static final int CHIP_TYPE_INTEREST = 2;

    public static ProfileFragment newInstance(String userId, String avatarUrl, boolean isFavorite,
            boolean isBookmarked) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString("userId", userId);
        args.putString("avatarUrl", avatarUrl);
        args.putBoolean("isFavorite", isFavorite);
        args.putBoolean("isBookmarked", isBookmarked);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            userId = getArguments().getString("userId");
            isFavorite = getArguments().getBoolean("isFavorite", false);
            isBookmarked = getArguments().getBoolean("isBookmarked", false);
        }

        if (getContext() != null) {
            profileCameraGalleryPicker = new CameraGalleryPicker(getContext(), new CameraGalleryPicker.PhotoSelectionCallback() {
                @Override
                public void onPhotoSelected(java.io.File photoFile) {
                    uploadProfileSelectedPhoto(photoFile);
                }

                @Override
                public void onPickerCancelled() {
                    Toast.makeText(getContext(), "Picker cancelled", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onPickerError(String error) {
                    Toast.makeText(getContext(), error, Toast.LENGTH_SHORT).show();
                }
            });

            profileCameraLauncher = registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (profileCameraGalleryPicker != null) {
                            profileCameraGalleryPicker.handleCameraResult(result.getResultCode());
                        }
                    });

            profileGalleryLauncher = registerForActivityResult(
                    new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    uri -> {
                        if (uri != null && getContext() != null) {
                            try {
                                java.io.File photoFile = new java.io.File(getContext().getCacheDir(), "selected_photo_" + System.currentTimeMillis() + ".jpg");
                                PhotoFileUtils.copyUriToFile(getContext(), uri, photoFile);
                                uploadProfileSelectedPhoto(photoFile);
                            } catch (Exception e) {
                                Toast.makeText(getContext(), "Error processing gallery selection", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(getContext(), "Picker cancelled", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);

        if (userId != null) {
            userId = userId.replaceAll("[^0-9]", "");
        }

        if (userId == null || userId.isEmpty()) {
            Toast.makeText(getContext(), R.string.no_user_id_provided, Toast.LENGTH_SHORT).show();
            return;
        }

        if (getArguments() != null) {
            String intentAvatarUrl = getArguments().getString("avatarUrl");
            if (intentAvatarUrl != null && !intentAvatarUrl.isEmpty() && allPhotoItems.isEmpty()) {
                addPhotoItem(intentAvatarUrl, null);
            }
        }

        Context context = getContext();
        if (context == null)
            return;
        SecurePrefs secure = SecurePrefs.get(context);
        fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        suid = secure.getString(ApiConstants.KEY_SUID, "");

        SharedPreferences prefs = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> notifUsers;
        try {
            notifUsers = prefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, new HashSet<>());
        } catch (ClassCastException e) {
            notifUsers = new HashSet<>();
            prefs.edit().remove(ApiConstants.KEY_ONLINE_NOTIF_USERS).apply();
        }
        isNotified = notifUsers.contains("/rest/users/" + userId);

        if (getActivity() instanceof MainActivity) {
            String link = ((MainActivity) getActivity()).findConversationForUser(userId);
            if (link == null || link.isEmpty()) {
                ((MainActivity) getActivity()).backgroundSearchConversation(userId, 0);
            }
        }

        fetchProfile();
        checkBlockStatus();

        String myId = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ApiConstants.KEY_USER_ID, "");
        if (userId != null && userId.equals(myId)) {
            if (cardManagePhotos != null) {
                cardManagePhotos.setVisibility(View.VISIBLE);
            }
            setupUploadUi();
            fetchOwnAlbums();
        } else {
            if (cardManagePhotos != null) {
                cardManagePhotos.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            getContext().registerReceiver(sseReceiver,
                    new android.content.IntentFilter("io.github.kgelinas.jalfnotifier.SSE_EVENT"),
                    Context.RECEIVER_EXPORTED);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (photoAdapter != null && getContext() != null) {
            SharedPreferences prefs = getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);
            photoAdapter.setBlurNsfw(blurNsfw);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getContext() != null) {
            getContext().unregisterReceiver(sseReceiver);
        }
    }

    private void updateGridUi() {
        if (gridDetails == null || currentProfileData == null)
            return;

        gridDetails.removeAllViews();
        addDetailRow(getString(R.string.orientation), currentProfileData.optString("sexual_orientation_link"),
                currentProfileData.optString("sexual_orientation", ""));
        addDetailRow(getString(R.string.status), currentProfileData.optString("social_status_link"),
                currentProfileData.optString("social_status", ""));
        addDetailRow(getString(R.string.registered_since), null,
                formatVipFallback(registrationDateFromHtml != null ? registrationDateFromHtml
                        : currentProfileData.optString("registered_since", "")));

        addDetailRow(getString(R.string.last_connection), null,
                formatVipFallback(lastConnectedFromHtml != null ? lastConnectedFromHtml
                        : currentProfileData.optString("last_connection", "")));

        addDetailRow(getString(R.string.zodiac_sign), currentProfileData.optString("zodiac_sign_link"),
                currentProfileData.optString("zodiac_sign", ""));

        // Privileges
        headerPrivileges.removeAllViews();
        JSONArray privs = currentProfileData.optJSONArray("privileges_links");
        if (privs != null && getContext() != null) {
            for (int i = 0; i < privs.length(); i++) {
                String link = privs.optString(i);
                String iconUrl = MetadataManager.getInstance().resolveIcon(link);
                if (iconUrl != null) {
                    ImageView iv = new ImageView(getContext());
                    int size = (int) (20 * getResources().getDisplayMetrics().density);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
                    lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
                    iv.setLayoutParams(lp);
                    iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    headerPrivileges.addView(iv);
                    Glide.with(this).load(iconUrl).into(iv);
                }
            }
        }
        addDetailRow(getString(R.string.availability), currentProfileData.optString("schedule_available_link"),
                currentProfileData.optString("schedule_available", ""));
        addDetailRow(getString(R.string.alcohol), currentProfileData.optString("alcohol_use_link"),
                currentProfileData.optString("alcohol_use", ""));
        addDetailRow(getString(R.string.smoker), currentProfileData.optString("smoking_link"),
                currentProfileData.optString("smoking", ""));
        addDetailRow(getString(R.string.drugs), currentProfileData.optString("drug_use_link"),
                currentProfileData.optString("drug_use", ""));

        Context context = getContext();
        if (context != null) {
            boolean useImperial = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(ApiConstants.KEY_USE_IMPERIAL, false);
            String weightLink = currentProfileData.optString("weight_link", "");
            String weightVal = useImperial ? MetadataManager.getInstance().resolveImperial(weightLink)
                    : MetadataManager.getInstance().resolve(weightLink);
            if (weightVal == null)
                weightVal = currentProfileData.optString("weight", "");
            addDetailRow(getString(R.string.weight), null, weightVal);

            String heightLink = currentProfileData.optString("height_link", "");
            String heightVal = useImperial ? MetadataManager.getInstance().resolveImperial(heightLink)
                    : MetadataManager.getInstance().resolve(heightLink);
            if (heightVal == null)
                heightVal = currentProfileData.optString("height", "");
            addDetailRow(getString(R.string.height), null, heightVal);

        }
        addDetailRow(getString(R.string.occupation), currentProfileData.optString("occupation_link"),
                currentProfileData.optString("occupation", ""));
        addDetailRow(getString(R.string.ethnicity), currentProfileData.optString("ethnic_group_link"),
                currentProfileData.optString("ethnic_group", ""));

        if (commonMatchesFromHtml != null && !commonMatchesFromHtml.isEmpty()) {
            addDetailRow(getString(R.string.common_points), null, commonMatchesFromHtml);

        }
    }

    private void updateDetailsUi() {
        if (getContext() == null)
            return;

        String age = this.age != null ? this.age : "";
        String gender = MetadataManager.getInstance().resolve(this.sexLink);
        if (gender == null)
            gender = "";

        String city = (cityFromHtml != null && !cityFromHtml.isEmpty()) ? cityFromHtml : "";

        String details;
        if (!age.isEmpty() && !gender.isEmpty() && !city.isEmpty()) {
            details = getString(R.string.profile_details_format, age, gender, city);
        } else if (!age.isEmpty() && !gender.isEmpty()) {
            details = getString(R.string.profile_details_format_no_city, age, gender);
        } else if (!age.isEmpty() && !city.isEmpty()) {
            details = getString(R.string.profile_details_format_no_gender, age, city);
        } else {
            details = age;
        }

        if (headerDetails != null) {
            headerDetails.setText(details);
        }

        if (cardLastStatus != null && profileLastStatusText != null) {
            if (lastStatusFromHtml != null && !lastStatusFromHtml.isEmpty()) {
                profileLastStatusText.setText(lastStatusFromHtml);
                cardLastStatus.setVisibility(View.VISIBLE);
            } else {
                cardLastStatus.setVisibility(View.GONE);
            }
        }

        populateStatus(currentProfileData);
    }

    private void populateStatus(JSONObject data) {
        if (getContext() == null || !isAdded() || headerDetails == null || data == null)
            return;

        boolean isOnline = data.optInt("online", 0) == 1 || data.optBoolean("is_online", false);
        String currentText = headerDetails.getText().toString();

        // Remove existing status if any (already contains '• En ligne' or '• Hors
        // ligne'?)
        String onlineStr = " • " + getString(R.string.online);
        String offlineStr = " • " + getString(R.string.offline);
        currentText = currentText.replace(onlineStr, "").replace(offlineStr, "");

        if (isOnline) {
            currentText += onlineStr;
        } else {
            // Optional: show offline status too?
            // The user said "online status need to be refreshed", so probably yes.
            currentText += offlineStr;
        }
        headerDetails.setText(currentText);
    }

    private void updateInterestsUi() {
        if (chipGroupInterests == null)
            return;

        // Re-adding chips might be tricky if we don't want duplicates.
        // For simplicity, we just clear and add from orientationFromHtml if it is the
        // primary source.
        // Actually, populateProfile adds goals and fantasies too.
        // Let's just ensure orientations are added if they were missing.
        synchronized (orientationFromHtml) {
            for (String ornt : orientationFromHtml) {
                addChip(ornt, 2);
            }
        }
        View cardInterests = getView() != null ? getView().findViewById(R.id.card_interests) : null;
        if (cardInterests != null && chipGroupInterests.getChildCount() > 0) {
            cardInterests.setVisibility(View.VISIBLE);
        }
    }

    private void initViews(View v) {
        toolbar = v.findViewById(R.id.toolbar_profile);
        toolbarTitleText = v.findViewById(R.id.toolbar_title_text);
        toolbarCertifiedBadge = v.findViewById(R.id.toolbar_certified_badge);
        toolbar.setNavigationOnClickListener(view -> {
            if (getActivity() != null) {
                getActivity().getOnBackPressedDispatcher().onBackPressed();
            }
        });
        toolbar.inflateMenu(R.menu.menu_profile);
        menuFav = toolbar.getMenu().findItem(R.id.action_favorite);
        menuBookmark = toolbar.getMenu().findItem(R.id.action_bookmark);
        menuNotify = toolbar.getMenu().findItem(R.id.action_notify);
        menuBlock = toolbar.getMenu().findItem(R.id.action_block);
        updateMenuIcons();

        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);

        ViewPager2 photoPager = v.findViewById(R.id.profile_photo_pager);
        photoAdapter = new ProfilePhotoAdapter(allPhotoItems, position -> {
            Intent intent = new Intent(getContext(), FullscreenImageActivity.class);
            ArrayList<String> urls = new ArrayList<>();
            ArrayList<Integer> ranks = new ArrayList<>();
            for (ProfilePhotoAdapter.PhotoItem item : allPhotoItems) {
                urls.add(item.url);
                ranks.add(item.rank);
            }
            intent.putStringArrayListExtra("imageUrlList", urls);
            intent.putIntegerArrayListExtra("imageRanks", ranks);
            intent.putExtra("initialPosition", position);
            startActivity(intent);
        });
        photoPager.setAdapter(photoAdapter);

        TextView photoIndicator = v.findViewById(R.id.profile_photo_indicator);
        photoPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (allPhotoItems.size() > 1) {
                    photoIndicator.setVisibility(View.VISIBLE);
                    photoIndicator
                            .setText(getString(R.string.photo_indicator_format, position + 1, allPhotoItems.size()));
                } else {
                    photoIndicator.setVisibility(View.GONE);
                }
            }
        });

        headerSexIcon = v.findViewById(R.id.header_sex_icon);
        headerDetails = v.findViewById(R.id.header_details);
        txtDescription = v.findViewById(R.id.profile_bio);
        descriptionContainer = v.findViewById(R.id.card_bio);
        progressBar = v.findViewById(R.id.profile_progress);
        contentLayout = v.findViewById(R.id.profile_content);
        headerPrivileges = v.findViewById(R.id.header_privileges);

        cardManagePhotos = v.findViewById(R.id.card_manage_photos);
        spinnerProfileAlbums = v.findViewById(R.id.spinner_profile_albums);
        btnProfileUploadCamera = v.findViewById(R.id.btn_profile_upload_camera);
        btnProfileUploadGallery = v.findViewById(R.id.btn_profile_upload_gallery);
        profileUploadProgress = v.findViewById(R.id.profile_upload_progress);

        chipGroupInterests = v.findViewById(R.id.chip_group_interests);
        gridDetails = v.findViewById(R.id.grid_details);
        cardVideos = v.findViewById(R.id.card_videos);
        cardLastStatus = v.findViewById(R.id.card_last_status);
        profileLastStatusText = v.findViewById(R.id.profile_last_status_text);
        recyclerVideos = v.findViewById(R.id.recycler_videos);
        if (recyclerVideos != null) {
            videoAdapter = new VideoAdapter(allVideos, this::onVideoClicked);
            recyclerVideos.setAdapter(videoAdapter);
        }

        View cardLatestActivity = v.findViewById(R.id.card_latest_activity);
        if (cardLatestActivity != null) {
            cardLatestActivity.setOnClickListener(v1 -> fetchLatestActivity());
        }

        fabMain = v.findViewById(R.id.fab_main);
        fabPoke = v.findViewById(R.id.fab_poke);
        fabMessage = v.findViewById(R.id.fab_message);

        if (fabMain != null)
            fabMain.setOnClickListener(view -> toggleFabMenu());
        if (fabMessage != null)
            fabMessage.setOnClickListener(view -> {
                toggleFabMenu();
                openChat();
            });
        if (fabPoke != null)
            fabPoke.setOnClickListener(view -> {
                toggleFabMenu();
                showPokeDialog();
            });
    }

    private void toggleFabMenu() {
        isFabMenuOpen = !isFabMenuOpen;
        if (isFabMenuOpen) {
            if (fabPoke != null)
                fabPoke.show();
            if (fabMessage != null)
                fabMessage.show();
            if (fabMain != null)
                fabMain.setImageResource(R.drawable.ic_close_24);
        } else {
            if (fabPoke != null)
                fabPoke.hide();
            if (fabMessage != null)
                fabMessage.hide();
            if (fabMain != null)
                fabMain.setImageResource(R.drawable.ic_menu);
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_favorite) {
            toggleFavorite();
            return true;
        } else if (itemId == R.id.action_bookmark) {
            toggleBookmark();
            return true;
        } else if (itemId == R.id.action_notify) {
            toggleNotification();
            return true;
        } else if (itemId == R.id.action_block) {
            toggleBlock();
            return true;
        }
        return false;
    }

    private void updateMenuIcons() {
        if (menuFav != null) {
            menuFav.setIcon(isFavorite ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
            // Prioritize: TRUE states are ALWAYS visible, FALSE states are IF_ROOM
            // (overflow if crowded)
            menuFav.setShowAsAction(isFavorite ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        if (menuBookmark != null) {
            menuBookmark.setIcon(isBookmarked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            menuBookmark
                    .setShowAsAction(isBookmarked ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        if (menuNotify != null) {
            menuNotify.setIcon(isNotified ? R.drawable.ic_notifications_active : R.drawable.ic_notifications_none);
            menuNotify.setShowAsAction(isNotified ? MenuItem.SHOW_AS_ACTION_ALWAYS : MenuItem.SHOW_AS_ACTION_IF_ROOM);
        }
        if (menuBlock != null) {
            menuBlock.setTitle(isBlocked ? R.string.action_unblock : R.string.action_block);

            // Block is always last in the overflow menu
            menuBlock.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
    }

    private void toggleBlock() {
        Context context = getContext();
        if (context == null)
            return;
        String myId = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ApiConstants.KEY_USER_ID, "");
        if (myId == null || myId.isEmpty())
            return;

        if (isBlocked) {
            unblockUser(myId);
        } else {
            blockUser(myId);
        }
    }

    private void blockUser(String myId) {
        String url = ApiConstants.BASE_URL + "/rest/users/" + myId + "/blocked";
        String payload = "{\"user_link\":\"/rest/users/" + userId + "\"}";

        okhttp3.RequestBody body = okhttp3.RequestBody.create(payload,
                okhttp3.MediaType.parse("application/vnd.jalf.blockuser+json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Accept", "*/*")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper())
                        .post(() -> Toast.makeText(getContext(), R.string.error_block_failed, Toast.LENGTH_SHORT)
                                .show());

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.isSuccessful()) {
                        isBlocked = true;
                        updateMenuIcons();
                        Toast.makeText(getContext(), R.string.user_blocked, Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(getContext(), R.string.error_block_server, Toast.LENGTH_SHORT).show();

                    }
                    response.close();
                });
            }
        });
    }

    private void unblockUser(String myId) {
        String url = ApiConstants.BASE_URL + "/rest/users/" + myId + "/blocked/" + userId;
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper())
                        .post(() -> Toast.makeText(getContext(), R.string.error_unblock_failed, Toast.LENGTH_SHORT)
                                .show());

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (response.isSuccessful()) {
                        isBlocked = false;
                        updateMenuIcons();
                        Toast.makeText(getContext(), R.string.user_unblocked, Toast.LENGTH_SHORT).show();

                    } else {
                        Toast.makeText(getContext(), R.string.error_unblock_server, Toast.LENGTH_SHORT).show();

                    }
                    response.close();
                });
            }
        });
    }

    private void checkBlockStatus() {
        Context context = getContext();
        if (context == null)
            return;
        String myId = context.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(ApiConstants.KEY_USER_ID, "");
        if (myId == null || myId.isEmpty())
            return;

        String url = ApiConstants.BASE_URL + "/rest/users/" + myId + "/blocked";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONObject json = new JSONObject(body);
                            JSONArray items = json.optJSONArray("items");
                            boolean found = false;
                            if (items != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject item = items.optJSONObject(i);
                                    String blockedLink = item.optString("user_link", "");
                                    if (blockedLink.endsWith("/" + userId)) {
                                        found = true;
                                        break;
                                    }
                                }
                            }
                            isBlocked = found;
                            new Handler(Looper.getMainLooper()).post(() -> updateMenuIcons());
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        });
    }

    private void addPhotoItem(String url, String ratingLink) {
        if (getContext() == null || !isAdded()) {
            return;
        }
        if (url == null || url.isEmpty())
            return;

        String fullUrl = url;
        if (!fullUrl.startsWith("http")) {
            fullUrl = ApiConstants.BASE_URL + fullUrl;
        }

        for (int i = 0; i < allPhotoItems.size(); i++) {
            ProfilePhotoAdapter.PhotoItem existing = allPhotoItems.get(i);
            if (existing.url.equals(fullUrl)) {
                // Update rank/rating if we now have it (from REST)
                String newLabel = getRatingLabel(ratingLink);
                int newRank = getRatingRank(ratingLink);
                if (existing.rank == 0 && newRank > 0) {
                    allPhotoItems.set(i, new ProfilePhotoAdapter.PhotoItem(fullUrl, newLabel, newRank));
                    if (photoAdapter != null) {
                        photoAdapter.notifyItemChanged(i);
                    }
                }
                return;
            }
        }

        String label = getRatingLabel(ratingLink);
        int rank = getRatingRank(ratingLink);

        // Special case: if we only have the initial placeholder (rank 0) and we're adding 
        // the first rated photo from REST, replace it even if URL differs (thumbnail vs full)
        if (allPhotoItems.size() == 1 && allPhotoItems.get(0).rank == 0 && rank > 0) {
            allPhotoItems.set(0, new ProfilePhotoAdapter.PhotoItem(fullUrl, label, rank));
            if (photoAdapter != null) {
                photoAdapter.notifyItemChanged(0);
            }
            return;
        }

        allPhotoItems.add(new ProfilePhotoAdapter.PhotoItem(fullUrl, label, rank));
        if (photoAdapter != null) {
            photoAdapter.notifyItemInserted(allPhotoItems.size() - 1);
        }
    }

    private String getRatingLabel(String ratingLink) {
        if (getContext() == null || !isAdded()) {
            return "";
        }
        if (ratingLink == null)
            return "";
        if (ratingLink.endsWith("/1"))
            return getString(R.string.rating_dressed);

        if (ratingLink.endsWith("/2"))
            return getString(R.string.rating_naked);

        if (ratingLink.endsWith("/3"))
            return getString(R.string.rating_sexual);

        if (ratingLink.endsWith("/4"))
            return getString(R.string.rating_bdsm_soft);

        if (ratingLink.endsWith("/6"))
            return getString(R.string.rating_trav_trans);

        if (ratingLink.endsWith("/8"))
            return getString(R.string.rating_private_only);

        if (ratingLink.endsWith("/9"))
            return getString(R.string.rating_irrelevant);

        if (ratingLink.endsWith("/12"))
            return getString(R.string.rating_face_only);

        if (ratingLink.endsWith("/14"))
            return getString(R.string.rating_msg_only);

        if (ratingLink.endsWith("/15"))
            return getString(R.string.rating_forum_only);

        return "";
    }

    private int getRatingRank(String ratingLink) {
        if (ratingLink == null)
            return 0;
        if (ratingLink.endsWith("/1"))
            return 0;
        if (ratingLink.endsWith("/2"))
            return 1;
        if (ratingLink.endsWith("/3"))
            return 2;
        if (ratingLink.endsWith("/4"))
            return 3;
        if (ratingLink.endsWith("/6"))
            return 4;
        if (ratingLink.endsWith("/8"))
            return 5;
        if (ratingLink.endsWith("/9"))
            return 6;
        if (ratingLink.endsWith("/12"))
            return 7;
        if (ratingLink.endsWith("/14"))
            return 8;
        if (ratingLink.endsWith("/15"))
            return 9;
        return 0;
    }

    private String extractBestPhotoUrl(JSONObject p) {
        if (p == null)
            return "";
        return p.optString("image_uri",
                p.optString("image_link",
                        p.optString("thumbnail_uri",
                                p.optString("thumbnail_link",
                                        p.optString("thumb_url",
                                                p.optString("large_url", ""))))));
    }

    private void fetchProfile() {
        JSONObject cached = ProfileCacheManager.getInstance().getProfile(userId);
        boolean blur = true;
        if (getContext() != null) {
            blur = getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getBoolean(ApiConstants.KEY_BLUR_NSFW, true);
        }

        fetchHtmlData();

        if (cached != null) {
            Log.d(TAG, "Loading profile from cache for: " + userId);
            if (photoAdapter != null)
                photoAdapter.setBlurNsfw(blur);
            populateProfile(cached);
            if (ProfileCacheManager.getInstance().isStatusStale(userId)) {
                fetchStatusOnly();
            }
            return;
        }

        if (progressBar != null)
            progressBar.setVisibility(View.VISIBLE);

        // fetchHtmlData already called above
        if (photoAdapter != null)
            photoAdapter.setBlurNsfw(blur);

        String url = ApiConstants.BASE_URL + "/rest/users/" + userId;
        Log.d(TAG, "Fetching profile from: " + url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network failure while fetching profile", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (getContext() == null || !isAdded())
                        return;
                    if (progressBar != null)
                        progressBar.setVisibility(View.GONE);
                    if (contentLayout != null)
                        contentLayout.setVisibility(View.VISIBLE);
                    Toast.makeText(getContext(), R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONObject data = new JSONObject(body);

                            // Save to cache
                            ProfileCacheManager.getInstance().putProfile(getContext(), userId, data);

                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (getContext() == null || !isAdded())
                                    return;
                                populateProfile(data);
                            });
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing profile JSON", e);
                            handleRequestFinished();
                        }
                    } else {
                        Log.e(TAG, "Server returned error: " + r.code());
                        handleRequestFinished();
                    }
                }
            }
        });
    }

    private void handleRequestFinished() {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (getContext() == null || !isAdded())
                return;
            if (progressBar != null)
                progressBar.setVisibility(View.GONE);
            if (contentLayout != null)
                contentLayout.setVisibility(View.VISIBLE);
        });
    }

    private void fetchStatusOnly() {
        String url = ApiConstants.BASE_URL + "/rest/users/" + userId + "/status";
        Log.d(TAG, "Fetching light status from: " + url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject status = new JSONObject(NetworkUtils.responseToString(r));
                        int online = status.optInt("online", 0);
                        String lastConnected = status.optString("last_connected", "");

                        ProfileCacheManager.getInstance().updateStatus(getContext(), userId, online, lastConnected);

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (getContext() == null || !isAdded())
                                return;
                            // Update UI if still on this profile
                            JSONObject updated = ProfileCacheManager.getInstance().getProfile(userId);
                            if (updated != null)
                                populateStatus(updated);
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void populateProfile(JSONObject data) {
        if (getContext() == null || !isAdded() || data == null)
            return;
        if (progressBar != null)
            progressBar.setVisibility(View.GONE);
        if (contentLayout != null)
            contentLayout.setVisibility(View.VISIBLE);

        String name = data.optString("pseudo", "");
        if (name.isEmpty())
            name = data.optString("user_name", "");
        if (name.isEmpty())
            name = data.optString("name", "Unknown");

        if (toolbarTitleText != null) {
            toolbarTitleText.setText(name);
            toolbar.setTitle("");
        } else {
            toolbar.setTitle(name);
        }

        boolean isCertified = data.optBoolean("certified", false);
        if (toolbarCertifiedBadge != null) {
            toolbarCertifiedBadge.setVisibility(isCertified ? View.VISIBLE : View.GONE);
        }

        String bioHtml = extractBioHtml(data);
        if (getContext() != null) {
            String myId = getContext().getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(ApiConstants.KEY_USER_ID, "");
            if (userId.equals(myId)) {
                Log.d(TAG, "My profile bioHtml: " + bioHtml);
            }
        }

        if (!bioHtml.isEmpty()) {
            if (descriptionContainer != null)
                descriptionContainer.setVisibility(View.VISIBLE);
            txtDescription.setText(Html.fromHtml(bioHtml, Html.FROM_HTML_MODE_LEGACY));
        } else {
            if (descriptionContainer != null)
                descriptionContainer.setVisibility(View.GONE);
        }

        // Photos
        JSONArray photos = data.optJSONArray("photos");
        if (photos != null) {
            for (int i = 0; i < photos.length(); i++) {
                JSONObject p = photos.optJSONObject(i);
                String url = extractBestPhotoUrl(p);
                if (!url.isEmpty())
                    addPhotoItem(url, p.optString("photo_rating_link", ""));
            }
        } else {
            // Fallback for primary photo if 'photos' array is missing
            JSONObject photo = data.optJSONObject("photo");
            if (photo != null) {
                String thumb = photo.optString("image_144x189_link",
                        photo.optString("thumbnail_uri",
                                photo.optString("image_1024x768_link", "")));
                if (!thumb.isEmpty())
                    addPhotoItem(thumb, photo.optString("photo_rating_link", ""));
            }
        }

        String albumsLink = data.optString("user_photos_albums_link", "");
        if (!albumsLink.isEmpty())
            fetchAlbums(albumsLink);

        String videoAlbumsLink = data.optString("user_videos_albums_link", "");
        if (videoAlbumsLink.isEmpty())
            videoAlbumsLink = "/rest/users/" + userId + "/videos/albums";
        fetchVideoAlbums(videoAlbumsLink);

        sexLink = data.optString("sex_link", "");
        if (!sexLink.isEmpty()) {
            String sexIconUrl = MetadataManager.getInstance().resolveIcon(sexLink);
            if (sexIconUrl != null && !sexIconUrl.isEmpty()) {
                if (headerSexIcon != null) {
                    Glide.with(this).load(sexIconUrl).into(headerSexIcon);
                    headerSexIcon.setVisibility(View.VISIBLE);
                }
            } else {
                if (headerSexIcon != null)
                    headerSexIcon.setVisibility(View.GONE);
            }
        }

        age = data.optString("age", "");
        String gender = MetadataManager.getInstance().resolve(data.optString("sex_link", ""));
        if (gender == null)
            gender = data.optString("sex", "");
        String city = data.optString("city", "");
        if (cityFromHtml != null && !cityFromHtml.isEmpty()) {
            city = cityFromHtml;
        }

        String details;
        if (!age.isEmpty() && !gender.isEmpty() && !city.isEmpty()) {
            details = getString(R.string.profile_details_format, age, gender, city);
        } else if (!age.isEmpty() && !gender.isEmpty()) {
            details = getString(R.string.profile_details_format_no_city, age, gender);
        } else if (!age.isEmpty() && !city.isEmpty()) {
            details = getString(R.string.profile_details_format_no_gender, age, city);
        } else {
            details = age;
        }
        if (headerDetails != null) {
            headerDetails.setText(details);
        }

        if (chipGroupInterests != null) {
            chipGroupInterests.removeAllViews();
            JSONArray goals = data.optJSONArray("goals");
            if (goals != null) {
                for (int i = 0; i < goals.length(); i++)
                    addChip(goals.optString(i), 0);
            }
            JSONArray fantasies = data.optJSONArray("fantasies");
            if (fantasies != null) {
                for (int i = 0; i < fantasies.length(); i++)
                    addChip(fantasies.optString(i), 1);
            }
            JSONArray interests = data.optJSONArray("sexes_interested");
            if (interests != null) {
                for (int i = 0; i < interests.length(); i++)
                    addChip(interests.optString(i), 2);
            } else {
                JSONArray links = data.optJSONArray("sexes_interested_links");
                if (links != null && links.length() > 0)
                    resolveSexInterests(links);
            }
            synchronized (orientationFromHtml) {
                for (String ornt : orientationFromHtml) {
                    addChip(ornt, 2);
                }
            }
            View cardInterests = getView() != null ? getView().findViewById(R.id.card_interests) : null;
            if (cardInterests != null) {
                cardInterests.setVisibility(chipGroupInterests.getChildCount() == 0 ? View.GONE : View.VISIBLE);
            }
        }

        currentProfileData = data;
        updateGridUi();

        isFavorite = data.optBoolean("is_favorite", isFavorite);
        updateMenuIcons();
    }

    private void fetchAlbums(String link) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + link)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                            JSONArray items = root.optJSONArray("items");
                            if (items == null)
                                items = root.optJSONArray("photos_albums");
                            if (items == null)
                                items = root.optJSONArray("user_photos_albums");

                            if (items != null) {
                                for (int i = 0; i < items.length(); i++) {
                                    JSONObject a = items.optJSONObject(i);
                                    if (a != null) {
                                        String photosLink = a.optString("user_photos_album_link",
                                                a.optString("user_photos_link", ""));
                                        if (!photosLink.isEmpty())
                                            fetchPhotosFromAlbum(photosLink);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing albums", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchPhotosFromAlbum(String link) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + link)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                            JSONArray photos = null;
                            JSONObject selection = root.optJSONObject("photos_selection");
                            if (selection != null) {
                                photos = selection.optJSONArray("photos");
                            }
                            if (photos == null)
                                photos = root.optJSONArray("items");
                            if (photos == null)
                                photos = root.optJSONArray("user_photos");

                            if (photos != null) {
                                final JSONArray finalPhotos = photos;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (getContext() == null || !isAdded())
                                        return;
                                    for (int i = 0; i < finalPhotos.length(); i++) {
                                        JSONObject p = finalPhotos.optJSONObject(i);
                                        String url = extractBestPhotoUrl(p);
                                        if (!url.isEmpty())
                                            addPhotoItem(url, p.optString("photo_rating_link", ""));
                                    }
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing album photos", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchVideoAlbums(String link) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + link)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                            JSONArray albums = root.optJSONArray("videos_albums");
                            if (albums == null)
                                albums = root.optJSONArray("user_videos_albums");
                            if (albums != null) {
                                for (int i = 0; i < albums.length(); i++) {
                                    JSONObject album = albums.optJSONObject(i);
                                    if (album != null) {
                                        String videosLink = album.optString("user_videos_album_link");
                                        if (videosLink.isEmpty())
                                            videosLink = album.optString("user_videos_link");
                                        if (!videosLink.isEmpty())
                                            fetchVideosFromAlbum(videosLink);
                                    }
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing video albums", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchVideosFromAlbum(String link) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + link)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                            JSONArray finalVideos = null;
                            JSONObject selection = root.optJSONObject("videos_selection");
                            if (selection != null)
                                finalVideos = selection.optJSONArray("videos");
                            if (finalVideos == null)
                                finalVideos = root.optJSONArray("videos");
                            if (finalVideos == null)
                                finalVideos = root.optJSONArray("user_videos");

                            if (finalVideos != null) {
                                final JSONArray videosArray = finalVideos;
                                new Handler(Looper.getMainLooper()).post(() -> {
                                    if (getContext() == null || !isAdded())
                                        return;
                                    for (int i = 0; i < videosArray.length(); i++) {
                                        JSONObject v = videosArray.optJSONObject(i);
                                        if (v != null) {
                                            String thumb = v.optString("thumbnail_uri",
                                                    v.optString("thumbnail_link", v.optString("thumb_url")));
                                            if (!thumb.isEmpty()) {
                                                String videoLink = v.optString("video_link", "");
                                                String vId = v.optString("id", "");
                                                if (vId.isEmpty() && !videoLink.isEmpty()) {
                                                    String[] parts = videoLink.split("/");
                                                    if (parts.length > 0)
                                                        vId = parts[parts.length - 1];
                                                }
                                                VideoAdapter.VideoItem vi = new VideoAdapter.VideoItem(
                                                        vId,
                                                        v.optString("title"),
                                                        thumb,
                                                        v.optInt("duration", 0),
                                                        videoLink);
                                                addVideoItem(vi);
                                            }
                                        }
                                    }
                                });
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "Error parsing album videos", e);
                        }
                    }
                }
            }
        });
    }

    private void openChat() {
        Intent intent = new Intent(getContext(), ConversationActivity.class);
        intent.putExtra("otherUserId", userId);
        String otherName = "";
        if (toolbarTitleText != null && toolbarTitleText.getText() != null) {
            otherName = toolbarTitleText.getText().toString();
        } else {
            CharSequence title = toolbar.getTitle();
            if (title != null) {
                otherName = title.toString();
            }
        }
        intent.putExtra("otherName", otherName);
        if (!allPhotoItems.isEmpty())
            intent.putExtra("avatarUrl", allPhotoItems.get(0).url);

        if (getActivity() instanceof MainActivity) {
            String link = ((MainActivity) getActivity()).findConversationForUser(userId);
            if (link != null && !link.isEmpty()) {
                intent.putExtra("conversationLink", link);
            }
        }

        startActivity(intent);
    }

    private void showPokeDialog() {
        PokeBottomSheet pokeBottomSheet = PokeBottomSheet.newInstance(userId, fullCookie, (success, message) -> {
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show());
            }
        });
        pokeBottomSheet.show(getChildFragmentManager(), "poke");
    }

    private void toggleFavorite() {
        String action = isFavorite ? "del_fav" : "add_fav";
        String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + userId + "/1";

        RequestBody formBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("action", action)
                .build();

        Request request = new Request.Builder()
                .url(profileUrl)
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", profileUrl)
                .addHeader("Origin", ApiConstants.BASE_URL)
                .addHeader("Accept", "*/*")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error toggling favorite", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded())
                        return;
                    Toast.makeText(getContext(), R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    Log.d(TAG, "Favorite toggle response: " + r.code() + " action=" + action);
                    if (r.isSuccessful()) {
                        isFavorite = !isFavorite;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded())
                                return;
                            updateMenuIcons();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).updateUserFavoriteStatus(userId, isFavorite);
                            }
                        });
                    } else {
                        Log.e(TAG, "Favorite toggle failed with code: " + r.code());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded())
                                return;
                            Toast.makeText(getContext(), R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void toggleBookmark() {
        String action = isBookmarked ? "del_like" : "add_like";
        String profileUrl = ApiConstants.BASE_URL + "/ct/memberProfile/" + userId + "/1";

        RequestBody formBody = new okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("action", action)
                .build();

        Request request = new Request.Builder()
                .url(profileUrl)
                .post(formBody)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Referer", profileUrl)
                .addHeader("Origin", ApiConstants.BASE_URL)
                .addHeader("Accept", "*/*")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error toggling bookmark", e);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded())
                        return;
                    Toast.makeText(getContext(), R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    Log.d(TAG, "Bookmark toggle response: " + r.code());
                    if (r.isSuccessful()) {
                        isBookmarked = !isBookmarked;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded())
                                return;
                            updateMenuIcons();
                            Toast.makeText(getContext(),
                                    isBookmarked ? R.string.added_to_bookmarks : R.string.removed_from_bookmarks,
                                    Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).updateUserBookmarkStatus(userId, isBookmarked);
                            }
                        });
                    } else {
                        Log.e(TAG, "Bookmark toggle failed with code: " + r.code());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded())
                                return;
                            Toast.makeText(getContext(), R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void toggleNotification() {
        Context ctx = getContext();
        if (ctx == null)
            return;
        SharedPreferences prefs = ctx.getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> notifUsers;
        try {
            notifUsers = new HashSet<>(prefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, new HashSet<>()));
        } catch (ClassCastException e) {
            notifUsers = new HashSet<>();
            prefs.edit().remove(ApiConstants.KEY_ONLINE_NOTIF_USERS).apply();
        }
        String namesJson = prefs.getString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, "{}");

        String userLink = "/rest/users/" + userId;
        if (isNotified) {
            notifUsers.remove(userLink);
        } else {
            notifUsers.add(userLink);
            try {
                JSONObject namesObj = new JSONObject(namesJson);
                String otherName = "";
                if (toolbarTitleText != null && toolbarTitleText.getText() != null) {
                    otherName = toolbarTitleText.getText().toString();
                } else {
                    CharSequence title = toolbar.getTitle();
                    if (title != null) {
                        otherName = title.toString();
                    }
                }
                namesObj.put(userLink, otherName);
                namesJson = namesObj.toString();
            } catch (Exception ignored) {
            }
        }

        prefs.edit()
                .putStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS, notifUsers)
                .putString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, namesJson)
                .apply();

        isNotified = !isNotified;
        updateMenuIcons();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateUserNotifiedStatus(userId, isNotified);
        }
    }

    private void addChip(String label, int type) {
        if (getContext() == null || chipGroupInterests == null)
            return;
        Chip chip = new Chip(getContext());
        chip.setText(label);
        // Use M3 color role tokens — these correctly invert in dark mode.
        // Goal (CHIP_TYPE_GOAL) → SecondaryContainer / OnSecondaryContainer
        // Fantasy (CHIP_TYPE_FANTASY) → PrimaryContainer / OnPrimaryContainer
        // Interest (CHIP_TYPE_INTEREST) → TertiaryContainer / OnTertiaryContainer
        int[] colors = resolveChipColors(type);
        chip.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(colors[0]));
        chip.setTextColor(colors[1]);
        chipGroupInterests.addView(chip);
    }

    private int[] resolveChipColors(int type) {
        android.util.TypedValue tv = new android.util.TypedValue();
        android.content.Context context = getContext();
        if (context == null) {
            return new int[]{0, 0};
        }
        android.content.res.Resources.Theme theme = context.getTheme();
        int bgAttr, fgAttr;
        if (type == CHIP_TYPE_FANTASY) {
            bgAttr = com.google.android.material.R.attr.colorPrimaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnPrimaryContainer;
        } else if (type == CHIP_TYPE_INTEREST) {
            bgAttr = com.google.android.material.R.attr.colorTertiaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnTertiaryContainer;
        } else {
            bgAttr = com.google.android.material.R.attr.colorSecondaryContainer;
            fgAttr = com.google.android.material.R.attr.colorOnSecondaryContainer;
        }
        theme.resolveAttribute(bgAttr, tv, true);
        int bg = tv.data;
        theme.resolveAttribute(fgAttr, tv, true);
        int fg = tv.data;
        return new int[] { bg, fg };
    }

    private void addDetailRow(String label, String link, String fallback) {
        if (gridDetails == null)
            return;
        String value = link != null ? MetadataManager.getInstance().resolve(link) : null;
        if (value == null)
            value = fallback;
        if (value == null || value.isEmpty() || "null".equals(value))
            return;

        LinearLayout container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 8, 24, 8);

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        container.setLayoutParams(params);

        TextView lbl = new TextView(getContext());
        lbl.setText(label);
        lbl.setTextSize(12);

        TextView val = new TextView(getContext());
        val.setText(value);
        val.setTextSize(16);
        val.setSingleLine(false);

        container.addView(lbl);
        container.addView(val);
        gridDetails.addView(container);
    }

    private void resolveSexInterests(JSONArray links) {
        for (int i = 0; i < links.length(); i++) {
            String label = MetadataManager.getInstance().resolve(links.optString(i, ""));
            if (label != null)
                addChip(label, 2);
        }
    }

    private void fetchHtmlData() {
        if (fullCookie == null || fullCookie.isEmpty())
            return;

        cityFromHtml = null;
        lastConnectedFromHtml = null;
        lastStatusFromHtml = null;
        synchronized (orientationFromHtml) {
            orientationFromHtml.clear();
        }

        String url = ApiConstants.BASE_URL + "/ct/memberProfile/" + userId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Network error fetching HTML profile data", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);
                        Document doc = Jsoup.parse(body);

                        // 0. Last Status
                        Element statusP = doc.selectFirst("p.last_status");
                        if (statusP != null) {
                            lastStatusFromHtml = statusP.text().trim();
                        } else {
                            // Fallback to id if class is not enough
                            Element statusDesc = doc.getElementById("lastStatusDesc");
                            if (statusDesc != null) {
                                lastStatusFromHtml = statusDesc.text().trim();
                            }
                        }

                        // 1. City and Distance
                        Element regionSpan = doc.selectFirst("span.region_text");
                        if (regionSpan != null) {
                            String rawText = regionSpan.text().trim();
                            // Example: "Canada, Québec, à 5 km km"
                            String[] parts = rawText.split(", ");
                            if (parts.length >= 2) {
                                String city = parts[1].trim();
                                if (parts.length >= 3) {
                                    String dist = parts[2].trim();
                                    // Clean up "km km"
                                    dist = dist.replace(" km km", " km");
                                    cityFromHtml = city + " • " + dist;
                                } else {
                                    cityFromHtml = city;
                                }
                            }
                        }

                        // 2. Bookmark Status (Saved Profile)
                        Element removeBtn = doc.getElementById("remove_from_saved_profile");
                        if (removeBtn != null) {
                            isBookmarked = !removeBtn.hasClass("hidden");
                        } else {
                            Element addBtn = doc.getElementById("add_to_saved_profile");
                            if (addBtn != null) {
                                isBookmarked = addBtn.hasClass("hidden");
                            }
                        }

                        // 3. Orientations
                        Elements orntList = doc.select("ul.profile_orientation li");
                        synchronized (orientationFromHtml) {
                            orientationFromHtml.clear();
                            for (Element li : orntList) {
                                orientationFromHtml.add(li.text().trim());
                            }
                        }

                        // 4. Derniere connexion
                        Elements profileDetailsGroups = doc.select("div.control_group_grid.profile_details");
                        for (Element detail : profileDetailsGroups) {
                            Element labelEl = detail.selectFirst("span.profile_label_details");
                            if (labelEl != null && labelEl.text().toLowerCase().contains("connexion")) {
                                Element valEl = detail.selectFirst("span.profile_details");
                                if (valEl != null) {
                                    lastConnectedFromHtml = valEl.text().trim();
                                }
                            }

                            if (labelEl != null && labelEl.text().toLowerCase().contains("enregistré")) {
                                Element valEl = detail.selectFirst("span.profile_details");
                                if (valEl != null) {
                                    registrationDateFromHtml = valEl.text().trim();
                                }
                            }
                        }

                        // 6. Common Matches
                        Element matchesEl = doc.selectFirst("div.profile_match_points");
                        if (matchesEl != null) {
                            commonMatchesFromHtml = matchesEl.text().trim();
                        }

                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (!isAdded())
                                return;
                            updateMenuIcons();
                            updateDetailsUi();
                            updateInterestsUi();
                            updateGridUi();
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing consolidated HTML data", e);
                }
            }
        });
    }

    private void addVideoItem(VideoAdapter.VideoItem item) {
        if (item == null)
            return;
        boolean exists = false;
        for (VideoAdapter.VideoItem v : allVideos) {
            if (v.videoId.equals(item.videoId)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            allVideos.add(item);
            if (videoAdapter != null)
                videoAdapter.notifyItemInserted(allVideos.size() - 1);
            if (cardVideos != null)
                cardVideos.setVisibility(View.VISIBLE);
        }
    }

    private void onVideoClicked(VideoAdapter.VideoItem item) {
        fetchVideoDirectUrlAndPlay(item);
    }

    private void fetchVideoDirectUrlAndPlay(VideoAdapter.VideoItem item) {
        String url = item.getWebsiteUrl();
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                new Handler(Looper.getMainLooper())
                        .post(() -> {
                            if (getContext() == null || !isAdded())
                                return;
                            Toast.makeText(getContext(), R.string.error_fetching_video, Toast.LENGTH_SHORT).show();
                        });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String mp4Url = extractMp4Url(NetworkUtils.responseToString(r));
                        if (mp4Url != null) {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (getContext() == null || !isAdded())
                                    return;
                                playVideo(mp4Url);
                            });
                        } else {
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (getContext() == null || !isAdded())
                                    return;
                                Toast.makeText(getContext(), "Could not extract video link", Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private String extractMp4Url(String html) {
        Document doc = Jsoup.parse(html);
        Element source = doc.selectFirst("video source[type=video/mp4]");
        if (source != null) {
            String src = source.attr("src");
            if (!src.isEmpty())
                return src;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("VideoPlayer\\(['\"]([^'\"]+\\.mp4)['\"]");
        java.util.regex.Matcher m = p.matcher(html);
        if (m.find())
            return m.group(1);
        return null;
    }

    private void playVideo(String url) {
        try {
            Intent intent = new Intent(getContext(), VideoActivity.class);
            intent.putExtra("videoUrl", url);
            startActivity(intent);
        } catch (Exception e) {
            if (getContext() == null)
                return;
            Toast.makeText(getContext(), R.string.error_playing_video, Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchLatestActivity() {
        if (userId != null && !userId.isEmpty()) {
            if (getContext() == null || getActivity() == null)
                return;

            BottomSheetDialog dialog = new BottomSheetDialog(getContext());
            View sheetView = LayoutInflater.from(getContext()).inflate(R.layout.layout_bs_recent_activity, null);
            dialog.setContentView(sheetView);
            BottomSheetUtils.setupFullHeight(dialog);

            androidx.recyclerview.widget.RecyclerView recycler = sheetView.findViewById(R.id.bs_activity_recycler);
            android.widget.ProgressBar progress = sheetView.findViewById(R.id.bs_activity_progress);
            android.widget.TextView emptyView = sheetView.findViewById(R.id.bs_activity_empty);

            recycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(getContext()));
            java.util.List<EventAdapter.EventItem> events = new java.util.ArrayList<>();
            EventAdapter adapter = new EventAdapter(events, item -> {
                // Ignore clicks on recent activity for now
            });
            recycler.setAdapter(adapter);
            dialog.show();

            String url = ApiConstants.BASE_URL + "/ct/jalf_book_wall/1?filter_unum=" + userId;
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            progress.setVisibility(View.GONE);
                            emptyView.setVisibility(View.VISIBLE);
                        });
                    }
                }

                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                    try (Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            String html = NetworkUtils.responseToString(r);
                            Document doc = Jsoup.parse(html);
                            Elements publications = doc.select(".publication");

                            for (Element pub : publications) {
                                EventAdapter.EventItem item = new EventAdapter.EventItem();
                                Element nameEl = pub.selectFirst(".name_user a.mbrprof");
                                if (nameEl != null)
                                    item.title = nameEl.text();

                                Element actionEl = pub.selectFirst(".action h1");
                                if (actionEl != null) {
                                    String h1Text = actionEl.text();
                                    if (item.title != null && h1Text.startsWith(item.title)) {
                                        h1Text = h1Text.substring(item.title.length()).trim();
                                    }
                                    item.body = h1Text;
                                }

                                Element mediaH1 = pub.selectFirst(".media h1");
                                if (mediaH1 != null) {
                                    String mediaText = mediaH1.text();
                                    if (item.title != null && mediaText.startsWith(item.title)) {
                                        mediaText = mediaText.substring(item.title.length()).trim();
                                    }
                                    if (!mediaText.isEmpty()
                                            && (item.body == null || mediaText.length() > item.body.length())) {
                                        item.body = mediaText;
                                    }
                                }

                                Element descStatus = pub.selectFirst("#user_desc_status");
                                if (descStatus != null) {
                                    item.body = descStatus.html();
                                }

                                Element imgEl = pub.selectFirst(".action .media img, .media img");
                                if (imgEl != null) {
                                    item.secondaryImageUrl = imgEl.hasAttr("data-src") ? imgEl.attr("data-src")
                                            : imgEl.attr("src");
                                }

                                Element videoEl = pub.selectFirst("video");
                                if (videoEl != null) {
                                    String poster = videoEl.attr("poster");
                                    if (poster != null && !poster.isEmpty()) {
                                        item.secondaryImageUrl = poster;
                                    }
                                }

                                Element avatarEl = pub.selectFirst(".pic img");
                                if (avatarEl != null) {
                                    item.avatarUrl = avatarEl.hasAttr("data-src") ? avatarEl.attr("data-src")
                                            : avatarEl.attr("src");
                                }

                                Element dateEl = pub.selectFirst(".name_user p");
                                if (dateEl != null)
                                    item.timeIso = dateEl.text();

                                events.add(item);
                            }

                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> {
                                    progress.setVisibility(View.GONE);
                                    if (events.isEmpty()) {
                                        emptyView.setVisibility(View.VISIBLE);
                                    } else {
                                        adapter.notifyDataSetChanged();
                                    }
                                });
                            }
                        }
                    }
                }
            });
        }
    }

    private String extractBioHtml(JSONObject data) {
        JSONObject bios = data.optJSONObject("profile_descriptions");
        StringBuilder bioBuilder = new StringBuilder();
        if (bios != null) {
            JSONArray keys = bios.names();
            if (keys != null) {
                // Try to put FR first if available
                List<String> sortedKeys = new ArrayList<>();
                boolean hasFr = false;
                for (int i = 0; i < keys.length(); i++) {
                    String k = keys.optString(i);
                    if ("fr".equalsIgnoreCase(k))
                        hasFr = true;
                    else
                        sortedKeys.add(k);
                }
                if (hasFr)
                    sortedKeys.add(0, "fr");

                for (String langKey : sortedKeys) {
                    JSONObject langObj = bios.optJSONObject(langKey);
                    if (langObj != null) {
                        String desc = langObj.optString("description", "");
                        if (!desc.isEmpty()) {
                            if (bioBuilder.length() > 0) {
                                bioBuilder.append("<br><hr><br>");
                            }
                            // Always show label if more than one language, or if not the default "fr"
                            if (bios.length() > 1) {
                                bioBuilder.append("<b>").append(langKey.toUpperCase()).append("</b><br>");
                            }
                            bioBuilder.append(desc);
                        }
                    }
                }
            }
        }

        String bioHtml = bioBuilder.toString();
        String legacyDesc = data.optString("description", "");
        if (!legacyDesc.isEmpty()) {
            boolean alreadyPresent = false;
            if (!bioHtml.isEmpty()) {
                // Better cleaning for comparison
                String plainBio = Html.fromHtml(bioHtml, Html.FROM_HTML_MODE_LEGACY).toString().replaceAll("\\s+", " ");
                String plainLegacy = Html.fromHtml(legacyDesc, Html.FROM_HTML_MODE_LEGACY).toString()
                        .replaceAll("\\s+", " ");
                if (plainBio.contains(plainLegacy) || plainLegacy.contains(plainBio)) {
                    alreadyPresent = true;
                }
            }

            if (!alreadyPresent) {
                if (bioHtml.isEmpty()) {
                    bioHtml = legacyDesc;
                } else {
                    bioHtml += "<br><hr><br>" + legacyDesc;
                }
            }
        }
        return bioHtml;
    }

    private void setupUploadUi() {
        if (btnProfileUploadCamera != null) {
            btnProfileUploadCamera.setOnClickListener(v -> {
                if (profileCameraGalleryPicker != null) {
                    profileCameraGalleryPicker.openCamera(profileCameraLauncher);
                }
            });
        }
        if (btnProfileUploadGallery != null) {
            btnProfileUploadGallery.setOnClickListener(v -> {
                if (profileGalleryLauncher != null) {
                    profileGalleryLauncher.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                            .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build());
                }
            });
        }
    }

    private void fetchOwnAlbums() {
        if (userId == null || userId.isEmpty() || fullCookie == null || fullCookie.isEmpty()) return;
        
        String url = ApiConstants.BASE_URL + "/rest/users/" + userId + "/photos/albums";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Error fetching albums", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);
                        JSONObject json = new JSONObject(body);
                        JSONArray items = json.optJSONArray("items");
                        
                        final List<String> names = new ArrayList<>();
                        final List<String> links = new ArrayList<>();
                        
                        if (items != null) {
                            for (int i = 0; i < items.length(); i++) {
                                JSONObject album = items.optJSONObject(i);
                                if (album != null) {
                                    String desc = album.optString("description", "Unknown");
                                    String link = album.optString("user_photos_album_link", "");
                                    names.add(desc);
                                    links.add(link);
                                }
                            }
                        }
                        
                        if (names.isEmpty()) {
                            names.add("messages");
                            links.add("/rest/users/" + userId + "/photos/albums/messages");
                        }
                        
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                profileAlbumNames.clear();
                                profileAlbumNames.addAll(names);
                                profileAlbumLinks.clear();
                                profileAlbumLinks.addAll(links);
                                
                                if (getContext() != null && spinnerProfileAlbums != null) {
                                    android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                                            getContext(),
                                            android.R.layout.simple_spinner_item,
                                            profileAlbumNames
                                    );
                                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                                    spinnerProfileAlbums.setAdapter(adapter);
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing albums", e);
                }
            }
        });
    }

    private void uploadProfileSelectedPhoto(java.io.File photoFile) {
        if (getContext() == null || photoFile == null) return;
        
        // Get selected album link
        int selectedPos = 0;
        if (spinnerProfileAlbums != null) {
            selectedPos = spinnerProfileAlbums.getSelectedItemPosition();
        }
        
        String albumLink = "messages";
        if (selectedPos >= 0 && selectedPos < profileAlbumLinks.size()) {
            albumLink = profileAlbumLinks.get(selectedPos);
        }
        
        if (profilePhotoUploadTask == null) {
            profilePhotoUploadTask = new PhotoUploadTask(fullCookie, suid, userId);
        }
        
        // Show loading progress
        if (profileUploadProgress != null) {
            profileUploadProgress.setVisibility(View.VISIBLE);
        }
        setUploadButtonsEnabled(false);
        
        Toast.makeText(getContext(), "Uploading photo...", Toast.LENGTH_SHORT).show();
        
        profilePhotoUploadTask.uploadPhotoToAlbum(photoFile, albumLink, null, false, false, new PhotoUploadTask.PhotoUploadCallback() {
            @Override
            public void onUploadSuccess(JSONObject response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() == null || !isAdded())
                            return;
                        if (profileUploadProgress != null) {
                            profileUploadProgress.setVisibility(View.GONE);
                        }
                        setUploadButtonsEnabled(true);
                        Toast.makeText(getContext(), "Photo uploaded successfully!", Toast.LENGTH_SHORT).show();
                        // Refresh profile/photos
                        fetchProfile();
                    });
                }
            }

            @Override
            public void onUploadProgress(long bytesUploaded, long totalBytes) {
            }

            @Override
            public void onUploadFailure(String errorMessage) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (getContext() == null || !isAdded())
                            return;
                        if (profileUploadProgress != null) {
                            profileUploadProgress.setVisibility(View.GONE);
                        }
                        setUploadButtonsEnabled(true);
                        Toast.makeText(getContext(), "Upload failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void setUploadButtonsEnabled(boolean enabled) {
        if (btnProfileUploadCamera != null) btnProfileUploadCamera.setEnabled(enabled);
        if (btnProfileUploadGallery != null) btnProfileUploadGallery.setEnabled(enabled);
        if (spinnerProfileAlbums != null) spinnerProfileAlbums.setEnabled(enabled);
    }

    private String formatVipFallback(String value) {
        if (value == null || value.isEmpty())
            return "";
        if (value.equalsIgnoreCase("need_vip")) {
            return "Privilège Requis";
        }
        return value;
    }
}
