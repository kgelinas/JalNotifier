package io.github.kgelinas.jalfnotifier;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.ScrollView;
import android.widget.Toast;
import androidx.appcompat.widget.TooltipCompat;
import androidx.lifecycle.ViewModelProvider;
import java.text.Normalizer;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuHost;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.ExistingPeriodicWorkPolicy;

import com.bumptech.glide.Glide;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.navigationrail.NavigationRailView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.json.JSONArray;
import org.json.JSONObject;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import android.widget.ProgressBar;

import java.util.stream.Collectors;
import java.util.function.Consumer;
import androidx.appcompat.app.AlertDialog;
import java.util.HashSet;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.slider.RangeSlider;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 123;
    private final OkHttpClient client = JalfNotifierApplication.httpClient();

    private BottomSheetDialog currentProfileSheet;

    private interface OnListLoadedListener {
        void onLoaded();
    }

    private com.google.android.material.progressindicator.CircularProgressIndicator loadingIndicator;
    private com.google.android.material.progressindicator.CircularProgressIndicator searchLoadingIndicator;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SwipeRefreshLayout swipeRefreshEvents;
    private SwipeRefreshLayout swipeRefreshFavorites;
    /**
     * Contact type filter: null=all, "favorite"=favorites only, "notified"=notified
     * only
     */
    private String contactTypeFilter = null;

    // Compact Filter States
    private final Set<String> filterSexesChats = new HashSet<>();
    private final Set<String> filterSexesFavorites = new HashSet<>();
    private final Set<String> filterSexesEvents = new HashSet<>();
    private boolean filterOnlineChats = false;

    private static final int FILTER_STATUS_ALL = 0;
    private static final int FILTER_STATUS_READ = 1;
    private static final int FILTER_STATUS_UNREAD = 2;
    private static final int FILTER_STATUS_RECEIVED = 3;
    private static final int FILTER_STATUS_DELIVERED = 4;
    private int filterStatusChats = FILTER_STATUS_ALL;
    private boolean filterOnlineFavorites = false;
    private boolean filterOnlineEvents = false;
    public static final int FILTER_EVENTS_DIRECTION_ALL = 0;
    public static final int FILTER_EVENTS_DIRECTION_INCOMING = 1;
    public static final int FILTER_EVENTS_DIRECTION_OUTGOING = 2;
    private int filterEventsDirection = FILTER_EVENTS_DIRECTION_ALL;

    private LinearLayout chatsContainer;
    private LinearLayout favoritesContainer;
    private LinearLayout eventsContainer;
    private RecyclerView recyclerFavorites;
    private RecyclerView recyclerChats;
    private RecyclerView recyclerEvents;
    private BottomNavigationView bottomNav;
    private NavigationRailView navRail;
    private View detailContainer;
    private boolean isTablet = false;

    private ChatAdapter chatAdapter;
    private FavoriteAdapter favoriteAdapter;
    private EventAdapter eventAdapter;
    private final List<ChatAdapter.ChatItem> chatItems = new CopyOnWriteArrayList<>();
    private final List<FavoriteAdapter.FavoriteItem> favoriteItems = new CopyOnWriteArrayList<>();
    private final List<EventAdapter.EventItem> eventItems = new CopyOnWriteArrayList<>();
    // Full unfiltered backing lists for sex filter
    private final CopyOnWriteArrayList<ChatAdapter.ChatItem> allNewItems = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChatAdapter.ChatItem> allArchivedItems = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ChatAdapter.ChatItem> allActiveItems = new CopyOnWriteArrayList<>();
    private final Set<String> pinnedConvoLinks = new HashSet<>();
    private boolean isChatSelectionMode = false;
    private View lockOverlay;
    private com.google.android.material.bottomsheet.BottomSheetDialog resultsBottomSheet;
    private View persistentSearchSheet;
    private com.google.android.material.bottomsheet.BottomSheetBehavior<View> persistentSearchBehavior;
    private final List<FavoriteAdapter.FavoriteItem> allFavoriteItems = new CopyOnWriteArrayList<>();
    private final List<EventAdapter.EventItem> allEventItems = new CopyOnWriteArrayList<>();
    private boolean isUnlockedTemporarily = false;

    private com.google.android.material.tabs.TabLayout tabLayoutChats;
    private static final int TAB_ACTIVE = 0;
    private static final int TAB_NEW = 1;
    private static final int TAB_ARCHIVED = 2;
    private int currentChatTab = TAB_ACTIVE;

    private String myUserId = "";
    private boolean isLoadingMore = false;
    private boolean isUserScrolling = false;

    private String myAvatarUrl = "";
    private int myNsfwRank = 0;
    private String myName = "";
    private String myDetails = "";
    private String myLocation = "";

    private int offsetActive = 0;
    private int offsetNew = 0;
    private int offsetArchived = 0;

    private int totalActiveCount = -1;
    private int totalNewCount = -1;
    private int totalArchivedCount = -1;

    private int totalUnreadActive = 0;
    private int totalUnreadNew = 0;
    private boolean isMyVip = false;
    private String favoritesSortOrder = "default"; // "default", "timedesc", "timeasc"
    private int totalUnreadArchived = 0;

    private ArrayList<String> savedChatsFilters = null;
    private ArrayList<String> savedFavoritesFilters = null;
    private ArrayList<String> savedEventsFilters = new ArrayList<>();

    private ImageView toolbarAvatar;
    private View toolbarOnlineIndicator;
    private boolean isMyOnline = false;
    private MaterialToolbar toolbar;

    private SearchAdapter searchAdapter;
    private final List<SearchAdapter.SearchItem> searchItems = new CopyOnWriteArrayList<>();
    private int currentSearchPageUnified = 1;
    private boolean isSearchingUnified = false;
    private long lastInteractionTime = System.currentTimeMillis();
    private final Runnable metadataListener = () -> {
        runOnUiThread(() -> {
            if (chatAdapter != null)
                chatAdapter.notifyDataSetChanged();
            if (favoriteAdapter != null)
                favoriteAdapter.notifyDataSetChanged();
            if (eventAdapter != null)
                eventAdapter.notifyDataSetChanged();
            if (searchAdapter != null)
                searchAdapter.notifyDataSetChanged();
        });
    };


    private final BroadcastReceiver sseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("io.github.kgelinas.jalfnotifier.SSE_EVENT".equals(action)) {
                String type = intent.getStringExtra("type");
                Log.d(TAG, "SSE event received: " + type);
                if (type == null)
                    return;

                if (type.startsWith("convo_") || type.equals("message")
                        || type.equals("convo_all_unread_messages_count_changed")) {
                    Log.d(TAG, "Refreshing all chat counts due to " + type);
                    fetchAllUnreadCounts();
                } else if (type.equals("user_stats_changed") || type.equals("looked") || type.equals("posted")) {
                    fetchEvents();
                    fetchFavorites();
                } else if (type.equals("login") || type.equals("joined")) {
                    fetchFavorites();
                }
            }
        }
    };

    // Unified Search Variables
    private com.google.android.material.materialswitch.MaterialSwitch switchAdvancedSearch;
    private View containerAdvancedFields, containerMultiLocation, containerSwitchAdvanced;
    private View layoutFantasiesUnified, layoutRelationalConfigUnified, layoutAvailabilityUnified;
    private android.widget.ImageView imgVipLock;

    private ChipGroup cgSeekUnified, cgWantedUnified, cgOrientUnified;
    private com.google.android.material.textfield.TextInputEditText etPseudoUnified;
    private RangeSlider sliderAgeUnified;
    private android.widget.TextView tvAgeRangeValueUnified;
    private com.google.android.material.checkbox.MaterialCheckBox chkOnlineUnified, chkExcludeChattedUnified, chkPhotoUnified, chkWebcamUnified, chkSpeedMeetingUnified, chkMyRegionUnified;
    private android.widget.RadioGroup rgOrderByUnified;

    private ChipGroup cgStatusUnified, cgRelCherUnified, cgSmokerUnified, cgZodiacUnified, cgAvailUnified, cgEthnicUnified;
    private RangeSlider sliderWeightUnified, sliderHeightUnified;
    private List<WeightOption> weightOptionsUnified = new ArrayList<>();
    private List<HeightOption> heightOptionsUnified = new ArrayList<>();

    private AutoCompleteTextView spinnerCountryUnified, spinnerProvinceUnified, spinnerRegionUnified;
    private TextInputLayout tilProvinceUnified, tilRegionUnified;
    private List<RegionDet> countriesUnified = new ArrayList<>();
    private List<RegionDet> provincesUnified = new ArrayList<>();
    private List<RegionDet> regionsUnified = new ArrayList<>();
    private RegionDet selectedCountryUnified, selectedProvinceUnified, selectedRegionUnified;

    private com.google.android.material.button.MaterialButton btnAddLocationUnified, btnAutoLocateUnified, btnPickFantasiesUnified, btnSaveSearchUnified, btnExecuteUnified, btnClearFantasiesUnified, btnResetSearchUnified;
    private ChipGroup cgSelectedLocationsUnified;
    private List<SelectedLocation> selectedLocationsUnified = new ArrayList<>();

    private List<Integer> selectedFantasyIdsUnified = new ArrayList<>();
    private TextView tvSelectedFantasiesUnified;
    private List<FantasyCategory> fantasyCategoriesUnified = new ArrayList<>();

    private ChipGroup cgSavedSearchesUnified;
    private View tvSavedSearchesTitleUnified;
    private View scrollSavedSearchesUnified;

    private boolean isLocatingUnified = false;
    
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (prefs, key) -> {
        if (key != null && key.startsWith("remote_geolocation")) {
            startLocationSyncWorker();
        }
    };


    private FusedLocationProviderClient fusedLocationClient;
    private boolean ethnicGroupsLoaded = false;

    public static class SelectedLocation {
        public RegionDet country;
        public RegionDet province;
        public RegionDet region;

        public SelectedLocation(RegionDet country, RegionDet province, RegionDet region) {
            this.country = country;
            this.province = province;
            this.region = region;
        }

        public String getLinkKey() {
            return (country != null ? country.link : "") + "|" +
                   (province != null ? province.link : "") + "|" +
                   (region != null ? region.link : "");
        }

        public String getFullId() {
            StringBuilder sb = new StringBuilder();
            sb.append(country != null && country.id != null ? country.id : "null");
            if (province != null) {
                sb.append(",").append(province.id != null ? province.id : "null");
            }
            if (region != null) {
                sb.append(",").append(region.id != null ? region.id : "null");
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (country != null)
                sb.append(country.name);
            if (province != null)
                sb.append(" > ").append(province.name);
            if (region != null)
                sb.append(" > ").append(region.name);
            return sb.toString();
        }
    }

    public static class WeightOption {
        public int id;
        public int kg;
        public int lbs;

        public WeightOption(int id, int kg, int lbs) {
            this.id = id;
            this.kg = kg;
            this.lbs = lbs;
        }
    }

    public static class HeightOption {
        public int id;
        public String metric;
        public String imperial;

        public HeightOption(int id, String metric, String imperial) {
            this.id = id;
            this.metric = metric;
            this.imperial = imperial;
        }
    }

    public static class RegionDet {
        public String name;
        public String link;
        public String id;

        public RegionDet(String name, String link) {
            this(name, link, null);
        }

        public RegionDet(String name, String link, String id) {
            this.name = name;
            this.link = link;
            if (id != null) {
                this.id = id;
            } else if (link != null) {
                String cleanLink = link.endsWith("/") ? link.substring(0, link.length() - 1) : link;
                String[] parts = cleanLink.split("/");
                this.id = parts[parts.length - 1];
            }
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Fantasy {
        public int id;
        public String name;
        public boolean selected;

        public Fantasy(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static class FantasyCategory {
        public String name;
        public List<Fantasy> fantasies = new ArrayList<>();

        public FantasyCategory(String name) {
            this.name = name;
        }
    }

    private boolean hasMoreSearchResults = true;

    private boolean weightsLoaded = false;
    private boolean heightsLoaded = false;
    private boolean detailedOptionsLoaded = false;
    private boolean countriesLoadedSimple = false;
    private boolean countriesLoadedUnified = false;

    private AppPrefs getAppPrefs() {
        return AppPrefs.getInstance(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Redirect to permissions onboarding if not yet done
        boolean onboardingDone = AppPrefs.getInstance(this).getBoolean(ApiConstants.KEY_PERMISSIONS_ONBOARDING_DONE, false);
        if (!onboardingDone) {
            startActivity(new Intent(this, PermissionsActivity.class));
            finish();
            return;
        }

        if (savedInstanceState != null) {
            isUnlockedTemporarily = savedInstanceState.getBoolean("isUnlockedTemporarily", false);
        }

        // Restore pinned links
        SharedPreferences pins = getSharedPreferences("PinnedConversations", MODE_PRIVATE);
        pinnedConvoLinks.addAll(pins.getStringSet("links", new HashSet<>()));

        SharedPreferences prefs = getAppPrefs().getRaw();
        myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        isMyVip = prefs.getBoolean("is_my_vip_" + myUserId, false);
        favoritesSortOrder = prefs.getString("favorites_sort_order_" + myUserId, "default");
        filterOnlineChats = prefs.getBoolean("filter_online_chats", false);
        filterOnlineFavorites = prefs.getBoolean("filter_online_favorites", false);
        filterOnlineEvents = prefs.getBoolean("filter_online_events", false);
        filterEventsDirection = prefs.getInt("filter_events_direction", FILTER_EVENTS_DIRECTION_ALL);
        filterStatusChats = prefs.getInt("filter_status_chats", FILTER_STATUS_ALL);
        contactTypeFilter = prefs.getString("filter_contact_type", null);

        Set<String> savedSexChats = prefs.getStringSet("filter_sexes_chats", null);
        if (savedSexChats != null) {
            filterSexesChats.clear();
            filterSexesChats.addAll(savedSexChats);
        }
        Set<String> savedSexFavs = prefs.getStringSet("filter_sexes_favorites", null);
        if (savedSexFavs != null) {
            filterSexesFavorites.clear();
            filterSexesFavorites.addAll(savedSexFavs);
        }
        Set<String> savedSexEvs = prefs.getStringSet("filter_sexes_events", null);
        if (savedSexEvs != null) {
            filterSexesEvents.clear();
            filterSexesEvents.addAll(savedSexEvs);
        }
        String suid = SecurePrefs.get(this).getString(ApiConstants.KEY_SUID, "");

        if (suid.isEmpty()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        AppLogger.log("Main: App started");

        MetadataManager.getInstance().init(this);
        syncUnitPreference();
        ProfileCacheManager.getInstance().init(this);
        MetadataManager.getInstance().addListener(metadataListener);
        androidx.lifecycle.ProcessLifecycleOwner.get().getLifecycle().addObserver(new AppLifecycleObserver(this));

        SettingsViewModel settingsVm = new androidx.lifecycle.ViewModelProvider(this).get(SettingsViewModel.class);
        settingsVm.blurNsfw.observe(this, active -> refreshAvatars());


        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        persistentSearchSheet = findViewById(R.id.persistent_search_sheet);
        if (persistentSearchSheet != null) {
            persistentSearchBehavior = com.google.android.material.bottomsheet.BottomSheetBehavior
                    .from(persistentSearchSheet);
            persistentSearchBehavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN);
        }

        toolbarAvatar = findViewById(R.id.toolbar_avatar);
        toolbarOnlineIndicator = findViewById(R.id.toolbar_online_indicator);
        toolbarAvatar.setOnClickListener(v -> showProfileSheet());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isChatSelectionMode) {
                    exitSelectionMode();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        swipeRefreshLayout = findViewById(R.id.swipe_refresh);
        swipeRefreshEvents = findViewById(R.id.swipe_refresh_events);
        swipeRefreshFavorites = findViewById(R.id.swipe_refresh_favorites);
        
        int colorPrimary = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE);
        int colorSurface = com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE);
        
        swipeRefreshLayout.setColorSchemeColors(colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(colorSurface);
        
        swipeRefreshEvents.setColorSchemeColors(colorPrimary);
        swipeRefreshEvents.setProgressBackgroundColorSchemeColor(colorSurface);
        
        swipeRefreshFavorites.setColorSchemeColors(colorPrimary);
        swipeRefreshFavorites.setProgressBackgroundColorSchemeColor(colorSurface);

        loadingIndicator = findViewById(R.id.loading_indicator);

        searchLoadingIndicator = findViewById(R.id.search_loading_indicator);

        chatsContainer = findViewById(R.id.chats_container);
        favoritesContainer = findViewById(R.id.favorites_container);
        eventsContainer = findViewById(R.id.events_container);
        View searchContainer = findViewById(R.id.search_container);
        recyclerFavorites = findViewById(R.id.recycler_favorites);
        recyclerChats = findViewById(R.id.recycler_chats);
        recyclerEvents = findViewById(R.id.recycler_events);
        bottomNav = findViewById(R.id.bottom_navigation);
        navRail = findViewById(R.id.navigation_rail);
        detailContainer = findViewById(R.id.detail_container);
        isTablet = (navRail != null);

        View mainView = findViewById(R.id.main);
        if (mainView == null)
            mainView = findViewById(android.R.id.content);

        ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
            Insets insetsValues = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        tabLayoutChats = findViewById(R.id.tab_layout_chats);

        // Simple search views removed

        lockOverlay = findViewById(R.id.lock_overlay);
        View btnUnlock = findViewById(R.id.btn_unlock);
        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> triggerUnlock());
        }

        checkLock();

        // Simple search views removed

        // Simple search listeners removed

        // Recycler for search results is now in a BottomSheet
        searchAdapter = new SearchAdapter(searchItems, () -> {
            if (!isSearchingUnified && hasMoreSearchResults) {
                currentSearchPageUnified++;
                performSearchUnified(currentSearchPageUnified);
            }
        }, item -> {
            openProfile(item.userId, item.name, item.avatarUrl);
        }, this::fetchProfileDetailsForSearch);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        if (isTablet) {
            navRail.setOnItemSelectedListener(item -> {
                bottomNav.setSelectedItemId(item.getItemId());
                return true;
            });
            // Setup header if needed
            View header = navRail.getHeaderView();
            if (header != null) {
                header.setOnClickListener(v -> showProfileSheet());
            }
        }

        tabLayoutChats.addTab(tabLayoutChats.newTab().setText(getString(R.string.tab_actives)));
        tabLayoutChats.addTab(tabLayoutChats.newTab().setText(getString(R.string.tab_new)));
        tabLayoutChats.addTab(tabLayoutChats.newTab().setText(getString(R.string.tab_archived)));


        tabLayoutChats.addOnTabSelectedListener(new com.google.android.material.tabs.TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(com.google.android.material.tabs.TabLayout.Tab tab) {
                currentChatTab = tab.getPosition();
                applyFilter("chats");

                boolean needsFetch = false;
                if (currentChatTab == TAB_ACTIVE && allActiveItems.isEmpty())
                    needsFetch = true;
                else if (currentChatTab == TAB_NEW && allNewItems.isEmpty())
                    needsFetch = true;
                else if (currentChatTab == TAB_ARCHIVED && allArchivedItems.isEmpty())
                    needsFetch = true;

                if (needsFetch) {
                    refreshData();
                }
            }

            @Override
            public void onTabUnselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(com.google.android.material.tabs.TabLayout.Tab tab) {
            }
        });

        // MetadataManager is initialized in onCreate
        recyclerFavorites.setLayoutManager(new LinearLayoutManager(this));
        favoriteAdapter = new FavoriteAdapter(favoriteItems, item -> {
            openProfile(item.otherUserId, item.name, item.avatarUrl);
        });
        recyclerFavorites.setAdapter(favoriteAdapter);

        recyclerChats.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(chatItems, () -> {
            if (!isLoadingMore) {
                isLoadingMore = true;
                if (currentChatTab == TAB_NEW)
                    fetchNewConversations(offsetNew, false);
                else if (currentChatTab == TAB_ARCHIVED)
                    fetchArchivedConversations(offsetArchived, false);
                else
                    fetchActiveConversations(offsetActive, false);
            }
        }, item -> {
            if (isChatSelectionMode) {
                // Toggle checked state handled by adapter, we just update UI/counter if needed
                updateSelectionCount();
            } else {
                openConversation(item);
            }
        }, item -> {
            openProfile(item.otherUserId, item.name, item.avatarUrl);
        }, item -> {
            enterSelectionMode(item);
            return true;
        });
        recyclerChats.setAdapter(chatAdapter);

        recyclerEvents.setLayoutManager(new LinearLayoutManager(this));
        eventAdapter = new EventAdapter(eventItems, item -> {
            openProfile(item.otherUserId, item.title.replaceAll("<[^>]*>", ""), item.avatarUrl);
        });
        recyclerEvents.setAdapter(eventAdapter);

        // Restore tab layout position and filter states if available
        if (savedInstanceState != null) {
            int savedTab = savedInstanceState.getInt("currentChatsTab", 0);
            if (savedTab == 1 && tabLayoutChats.getTabCount() > 1) {
                tabLayoutChats.getTabAt(1).select();
            }

            savedChatsFilters = savedInstanceState.getStringArrayList("chatsFilters");
            savedFavoritesFilters = savedInstanceState.getStringArrayList("favoritesFilters");
            savedEventsFilters = savedInstanceState.getStringArrayList("eventsFilters");
        }

        // Setup filters
        initFilterButtons();

        loadEventsFromCache();
        swipeRefreshEvents.setOnRefreshListener(this::fetchEvents);
        swipeRefreshFavorites.setOnRefreshListener(this::fetchFavorites);
        swipeRefreshLayout.setOnRefreshListener(this::refreshData);

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chats) {
                toolbar.setTitle(R.string.nav_chats);

                chatsContainer.setVisibility(View.VISIBLE);
                eventsContainer.setVisibility(View.GONE);
                favoritesContainer.setVisibility(View.GONE);
                searchContainer.setVisibility(View.GONE);
                fetchAllUnreadCounts();
                refreshData();
                invalidateOptionsMenu();
                return true;
            } else if (id == R.id.nav_events) {
                toolbar.setTitle(R.string.nav_events);

                chatsContainer.setVisibility(View.GONE);
                eventsContainer.setVisibility(View.VISIBLE);
                favoritesContainer.setVisibility(View.GONE);
                searchContainer.setVisibility(View.GONE);
                fetchEvents();
                invalidateOptionsMenu();
                return true;
            } else if (id == R.id.nav_search) {
                toolbar.setTitle(R.string.nav_search);

                chatsContainer.setVisibility(View.GONE);
                eventsContainer.setVisibility(View.GONE);
                favoritesContainer.setVisibility(View.GONE);
                searchContainer.setVisibility(View.VISIBLE);
                invalidateOptionsMenu();
                return true;
            } else if (id == R.id.nav_favorites) {
                toolbar.setTitle(R.string.nav_contacts);

                chatsContainer.setVisibility(View.GONE);
                eventsContainer.setVisibility(View.GONE);
                favoritesContainer.setVisibility(View.VISIBLE);
                searchContainer.setVisibility(View.GONE);
                fetchFavorites();
                invalidateOptionsMenu();
                return true;
            }
            return false;
        });

        if (isTablet) {
            // Initially sync navRail with bottomNav
            navRail.setSelectedItemId(bottomNav.getSelectedItemId());
        }

        bottomNav.setOnItemReselectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chats) {
                fetchAllUnreadCounts();
                refreshData();
            } else if (id == R.id.nav_events) {
                fetchEvents();
            } else if (id == R.id.nav_favorites) {
                fetchFavorites();
            } else if (id == R.id.nav_search) {
                currentSearchPageUnified = 1;
                searchItems.clear();
                searchAdapter.notifyDataSetChanged();
                performSearchUnified(1);
            }
        });

        // tabLayoutSearch listener removed

        setupUnifiedSearch();
        populateSavedSearchChipsUnified();

        // Restore BottomNav selected state
        if (savedInstanceState != null) {
            int selectedItemId = savedInstanceState.getInt("bottomNavItemId", R.id.nav_chats);
            if (bottomNav.getSelectedItemId() != selectedItemId) {
                bottomNav.setSelectedItemId(selectedItemId);
            }
        }



        checkAuthentication();

        startSseService();
        startPollingWorker();
        startLocationSyncWorker();
        fetchOwnProfile();
        UpdateManager.checkForUpdates(this, false);

        getAppPrefs().getRaw()
                .registerOnSharedPreferenceChangeListener(prefListener);

        // Initial fetches for all tabs on startup
        refreshData();
        fetchEvents();
        fetchFavorites();
        fetchAllUnreadCounts();

        handleIntent(getIntent());

        // Start the first-time onboarding tour (deferred so all views are laid out)
        final View mainRootView = mainView;
        mainRootView.post(() -> AppTourManager.getInstance().startIfNeeded(this));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastInteractionTime = System.currentTimeMillis();
        // Persist it if we want the service to read it across process restarts,
        // but for now, simple static is enough if the service stays alive.
        getAppPrefs().getRaw()
                .edit().putLong("lastInteractionTime", lastInteractionTime).apply();

        // Ensure SSE is running (restart if stopped by inactivity)
        startSseService();
    }

    private void handleIntent(Intent intent) {
        if (intent == null)
            return;

        if ("CLEANUP_CHATS".equals(intent.getAction())) {
            startConversationCleanup();
            return;
        }

        if (intent.hasExtra(ApiConstants.EXTRA_CONVERSATION_LINK)) {
            String link = intent.getStringExtra(ApiConstants.EXTRA_CONVERSATION_LINK);
            String userId = intent.getStringExtra(ApiConstants.EXTRA_OTHER_USER_ID);
            String name = intent.getStringExtra(ApiConstants.EXTRA_OTHER_NAME);
            String avatar = intent.getStringExtra(ApiConstants.EXTRA_AVATAR_URL);
            String sexIcon = intent.getStringExtra(ApiConstants.EXTRA_SEX_ICON_URL);

            ChatAdapter.ChatItem ci = new ChatAdapter.ChatItem(ChatAdapter.TYPE_CHAT);
            ci.conversationLink = link;
            ci.otherUserId = userId;
            ci.name = name;
            ci.avatarUrl = avatar;
            ci.sexIconUrl = sexIcon;
            openConversation(ci);
        } else if (intent.hasExtra(ApiConstants.EXTRA_USER_LINK)) {
            String userLink = intent.getStringExtra(ApiConstants.EXTRA_USER_LINK);
            String userId = extractUserIdFromLink(userLink);
            if (userId != null) {
                openProfile(userId, null, null);
            }
        }
    }

    private String extractUserIdFromLink(String link) {
        if (link == null)
            return null;
        if (link.contains("/rest/users/")) {
            String[] parts = link.split("/");
            if (parts.length >= 4)
                return parts[3];
        }
        return null;
    }

    private void openConversation(ChatAdapter.ChatItem item) {
        if (isTablet && detailContainer != null) {
            ConversationFragment fragment = ConversationFragment.newInstance(
                    item.conversationLink,
                    item.otherUserId,
                    item.name,
                    item.avatarUrl,
                    item.sexIconUrl,
                    item.isOnline);
            loadDetailFragment(fragment);
        } else {
            Intent intent = new Intent(this, ConversationActivity.class);
            intent.putExtra("conversationLink", item.conversationLink);
            intent.putExtra("otherName", item.name);
            intent.putExtra("avatarUrl", item.avatarUrl);
            intent.putExtra("sexIconUrl", item.sexIconUrl);
            intent.putExtra("otherUserId", item.otherUserId);
            intent.putExtra("isOnline", item.isOnline);
            startActivity(intent);
        }
    }

    void openProfile(String userId, String name, String avatarUrl) {
        Log.d(TAG, "openProfile called for ID: " + userId + ", name: " + name);
        if (userId == null || userId.isEmpty()) {
            Log.w(TAG, "openProfile aborted: userId is null or empty");
            return;
        }

        boolean isFav = false;
        boolean isBook = false;
        // Search in cached favorites for existing status
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (userId.equals(fi.otherUserId)) {
                isFav = fi.isFavorite;
                isBook = fi.isBookmarked;
                break;
            }
        }

        if (isTablet && detailContainer != null) {
            ProfileFragment fragment = ProfileFragment.newInstance(userId, avatarUrl, isFav, isBook);
            loadDetailFragment(fragment);
        } else {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("userId", userId);
            intent.putExtra("name", name);
            intent.putExtra("avatarUrl", avatarUrl);
            intent.putExtra("isFavorite", isFav);
            intent.putExtra("isBookmarked", isBook);
            startActivity(intent);
        }
    }

    private void loadDetailFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.detail_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    public boolean isUserFavorite(String userId) {
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (fi.otherUserId != null && fi.otherUserId.equals(userId)) {
                return fi.isFavorite;
            }
        }
        return false;
    }

    public boolean isUserBookmarked(String userId) {
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (fi.otherUserId != null && fi.otherUserId.equals(userId)) {
                return fi.isBookmarked;
            }
        }
        return false;
    }

    public void updateUserFavoriteStatus(String userId, boolean isFav) {
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (fi.otherUserId != null && fi.otherUserId.equals(userId)) {
                fi.isFavorite = isFav;
                break;
            }
        }
    }

    public void updateUserBookmarkStatus(String userId, boolean isBook) {
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (fi.otherUserId != null && fi.otherUserId.equals(userId)) {
                fi.isBookmarked = isBook;
                break;
            }
        }
    }

    public void updateUserNotifiedStatus(String userId, boolean isNotified) {
        for (FavoriteAdapter.FavoriteItem fi : allFavoriteItems) {
            if (fi.otherUserId != null && fi.otherUserId.equals(userId)) {
                fi.isNotified = isNotified;
                break;
            }
        }
    }


    @Override
    protected void onPause() {


        super.onPause();
        // Removed auto-save as per user request
        try {
            unregisterReceiver(sseReceiver);
        } catch (Exception e) {
            // Already unregistered or not registered
        }
        statusHandler.removeCallbacks(statusRunnable);
    }

    private void refreshData() {
        offsetActive = 0;
        offsetNew = 0;
        offsetArchived = 0;
        if (bottomNav.getSelectedItemId() == R.id.nav_chats) {
            fetchAllUnreadCounts();
        } else if (bottomNav.getSelectedItemId() == R.id.nav_favorites) {
            fetchFavorites();
        } else if (bottomNav.getSelectedItemId() == R.id.nav_events) {
            fetchEvents();
        }
    }

    private void extractPhotoFromProfile(JSONObject profile) {
        if (profile == null)
            return;

        myAvatarUrl = "";
        myNsfwRank = 0;
        JSONObject photoObj = profile.optJSONObject("photo");
        if (photoObj != null) {
            myAvatarUrl = photoObj.optString("image_144x189_link",
                    photoObj.optString("large_url",
                            photoObj.optString("thumbnail_uri",
                                    photoObj.optString("image_1024x768_link", ""))));
            
            String ratingLink = photoObj.optString("photo_rating_link", "");
            if (ratingLink.isEmpty()) {
                ratingLink = profile.optString("rating_link", "");
            }
            if (!ratingLink.isEmpty()) {
                if (ratingLink.endsWith("/2")) myNsfwRank = 1;
                else if (ratingLink.endsWith("/3")) myNsfwRank = 2;
                else if (ratingLink.endsWith("/4")) myNsfwRank = 3;
                else if (ratingLink.endsWith("/6")) myNsfwRank = 4;
                else if (ratingLink.endsWith("/8")) myNsfwRank = 5;
                else if (ratingLink.endsWith("/9")) myNsfwRank = 6;
                else if (ratingLink.endsWith("/12")) myNsfwRank = 7;
                else if (ratingLink.endsWith("/14")) myNsfwRank = 8;
                else if (ratingLink.endsWith("/15")) myNsfwRank = 9;
            }
        }

        if (myAvatarUrl.isEmpty()) {
            JSONArray photos = profile.optJSONArray("photos");
            if (photos != null && photos.length() > 0) {
                JSONObject p = photos.optJSONObject(0);
                if (p != null) {
                    myAvatarUrl = p.optString("image_uri",
                            p.optString("image_link",
                                    p.optString("large_url",
                                            p.optString("thumb_url",
                                                    p.optString("thumbnail_uri", "")))));
                    
                    String ratingLink = p.optString("photo_rating_link", "");
                    if (!ratingLink.isEmpty()) {
                        if (ratingLink.endsWith("/2")) myNsfwRank = 1;
                        else if (ratingLink.endsWith("/3")) myNsfwRank = 2;
                        else if (ratingLink.endsWith("/4")) myNsfwRank = 3;
                        else if (ratingLink.endsWith("/6")) myNsfwRank = 4;
                        else if (ratingLink.endsWith("/8")) myNsfwRank = 5;
                        else if (ratingLink.endsWith("/9")) myNsfwRank = 6;
                        else if (ratingLink.endsWith("/12")) myNsfwRank = 7;
                        else if (ratingLink.endsWith("/14")) myNsfwRank = 8;
                        else if (ratingLink.endsWith("/15")) myNsfwRank = 9;
                    }
                }
            }
        }

        if (!myAvatarUrl.isEmpty()) {
            if (!myAvatarUrl.startsWith("http"))
                myAvatarUrl = ApiConstants.BASE_URL + myAvatarUrl;
            
            refreshAvatars();
        }
    }

    private void refreshAvatars() {
        boolean blurNsfw = getAppPrefs().getRaw()
                .getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            com.bumptech.glide.request.RequestOptions options = new com.bumptech.glide.request.RequestOptions()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop();

            if (blurNsfw && myNsfwRank > 0) {
                options = options.transform(new com.bumptech.glide.load.resource.bitmap.CircleCrop(),
                        new jp.wasabeef.glide.transformations.BlurTransformation(25, 3));
            }

            if (toolbarAvatar != null) {
                if (myAvatarUrl != null && !myAvatarUrl.isEmpty()) {
                    Glide.with(MainActivity.this)
                            .load(myAvatarUrl)
                            .apply(options)
                            .into(toolbarAvatar);
                } else {
                    toolbarAvatar.setImageResource(R.drawable.ic_default_avatar);
                }
            }
            if (navRail != null) {
                View header = navRail.getHeaderView();
                if (header != null) {
                    ShapeableImageView navAvatar = header.findViewById(R.id.nav_rail_avatar);
                    if (navAvatar != null) {
                        if (myAvatarUrl != null && !myAvatarUrl.isEmpty()) {
                            Glide.with(MainActivity.this)
                                    .load(myAvatarUrl)
                                    .apply(options)
                                    .into(navAvatar);
                        } else {
                            navAvatar.setImageResource(R.drawable.ic_default_avatar);
                        }
                    }
                }
            }

            // Refresh all list adapters to apply/remove blur on thumbnails
            if (chatAdapter != null)
                chatAdapter.notifyDataSetChanged();
            if (eventAdapter != null)
                eventAdapter.notifyDataSetChanged();
            if (searchAdapter != null)
                searchAdapter.notifyDataSetChanged();
            if (favoriteAdapter != null)
                favoriteAdapter.notifyDataSetChanged();

            // Refresh detail pane fragments if they exist (for tablet mode)
            Fragment detail = getSupportFragmentManager().findFragmentById(R.id.detail_container);
            if (detail instanceof ConversationFragment) {
                ((ConversationFragment) detail).onResume();
            } else if (detail instanceof ProfileFragment) {
                ((ProfileFragment) detail).onResume();
            }
        });
    }

    private void fetchOwnProfile() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        SharedPreferences prefs = getAppPrefs().getRaw();
        String myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        if (myUserId.isEmpty())
            return;

        String url = ApiConstants.BASE_URL + "/rest/users/" + myUserId;
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
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject profile = new JSONObject(NetworkUtils.responseToString(r));
                            myName = profile.optString("name", profile.optString("pseudo", ""));

                            String age = profile.optString("age", "");
                            JSONObject loc = profile.optJSONObject("location");
                            myLocation = (loc != null) ? loc.optString("region", "") : profile.optString("city", "");

                            if (!age.isEmpty() && !myLocation.isEmpty())
                                myDetails = age + " ans, " + myLocation;
                            else if (!age.isEmpty())
                                myDetails = age + " ans";
                            else
                                myDetails = myLocation;

                            String eventsLink = profile.optString("user_events_link", "");
                            if (!eventsLink.isEmpty()) {
                                getAppPrefs().getRaw()
                                        .edit().putString(ApiConstants.KEY_SSE_URL, eventsLink).apply();
                            }

                            String onlineVal = profile.optString("online", "0");
                            isMyOnline = "1".equals(onlineVal) || profile.optBoolean("online", false)
                                    || profile.optBoolean("is_online", false);
                            runOnUiThread(() -> updateOnlineIndicator());

                            extractPhotoFromProfile(profile);

                            isMyVip = profile.optBoolean("is_vip", false) || profile.optBoolean("vip", false)
                                    || "1".equals(profile.optString("vip", "0")) || profile.optInt("vip", 0) > 0
                                    || profile.optInt("privilege", 0) > 0 || profile.optBoolean("pro", false)
                                    || "1".equals(profile.optString("pro", "0"));
                            prefs.edit().putBoolean("is_my_vip_" + myUserId, isMyVip).apply();

                            JSONObject options = profile.optJSONObject("options");
                            if (options != null) {
                                int remoteGeo = options.optInt("geolocation", 0);
                                int remoteShare = options.optInt("share_distance", 0);
                                prefs.edit()
                                    .putInt("remote_geolocation_" + myUserId, remoteGeo)
                                    .putInt("remote_share_distance_" + myUserId, remoteShare)
                                    .apply();
                            }
                        } catch (Exception e) {
                            AppLogger.log("MainActivity", "Error parsing profile detail", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchFavorites() {
        Log.d(TAG, "fetchFavorites requested");
        setLoading(true);

        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/favorites")
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    swipeRefreshFavorites.setRefreshing(false);
                    setLoading(false);
                });
            }


            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    Log.d(TAG, "fetchFavorites response received: " + r.code());
                    runOnUiThread(() -> {
                        swipeRefreshFavorites.setRefreshing(false);
                        setLoading(false);
                    });

                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONObject json = new JSONObject(body);
                            JSONArray arr = json.optJSONArray("items");
                            if (arr == null)
                                arr = new JSONArray(body);

                            // Collect favorite user links
                            Set<String> favoriteLinks = new java.util.LinkedHashSet<>();
                            final List<FavoriteAdapter.FavoriteItem> finalFavs = new ArrayList<>();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                FavoriteAdapter.FavoriteItem item = new FavoriteAdapter.FavoriteItem();

                                String userLink = obj.optString("user_link", "");
                                item.otherUserId = StringUtils.extractUserIdFromLink(userLink);
                                if (item.otherUserId.isEmpty()) {
                                    item.otherUserId = obj.optString("member_id", obj.optString("id", ""));
                                }

                                item.userLink = userLink;
                                item.name = obj.optString("description", "User");
                                item.avatarUrl = "";
                                item.details = "";
                                item.isOnline = obj.optBoolean("online", false);
                                item.isFavorite = true;
                                item.sexLink = "";

                                favoriteLinks.add(userLink);
                                finalFavs.add(item);

                                JSONObject cachedProfile = ProfileCacheManager.getInstance()
                                        .getProfile(item.otherUserId);
                                if (cachedProfile != null) {
                                    populateFavoriteFromCache(item, cachedProfile);
                                }

                                // Fetch fresh profile if avatar or lastConnection is missing, or cache is stale
                                boolean isStale = ProfileCacheManager.getInstance().isProfileStale(item.otherUserId);
                                if (item.avatarUrl == null || item.avatarUrl.isEmpty()
                                        || item.lastConnection == null || item.lastConnection.isEmpty()
                                        || isStale) {
                                    fetchUserProfileForFavorite(item, fullCookie, item.otherUserId);
                                }
                            }

                            // Merge notification-only users not already in favorites
                            SharedPreferences contactPrefs = getAppPrefs().getRaw();
                            Set<String> notifUsers;
                            try {
                                notifUsers = contactPrefs.getStringSet(ApiConstants.KEY_ONLINE_NOTIF_USERS,
                                        new java.util.HashSet<>());
                            } catch (ClassCastException e) {
                                notifUsers = new java.util.HashSet<>();
                                contactPrefs.edit().remove(ApiConstants.KEY_ONLINE_NOTIF_USERS).apply();
                            }
                            String namesJson = contactPrefs.getString(ApiConstants.KEY_ONLINE_NOTIF_NAMES, "{}");
                            JSONObject namesObj;
                            try {
                                namesObj = new JSONObject(namesJson);
                            } catch (Exception e) {
                                namesObj = new JSONObject();
                            }

                            for (String notifLink : notifUsers) {
                                boolean alreadyIn = false;
                                for (FavoriteAdapter.FavoriteItem fi : finalFavs) {
                                    if (notifLink.equals(fi.userLink)) {
                                        fi.isNotified = true;
                                        alreadyIn = true;
                                        break;
                                    }
                                }
                                if (!alreadyIn && !favoriteLinks.contains(notifLink)) {
                                    FavoriteAdapter.FavoriteItem item = new FavoriteAdapter.FavoriteItem();
                                    item.userLink = notifLink;
                                    item.name = namesObj.optString(notifLink, notifLink.replace("/rest/users/", ""));
                                    item.avatarUrl = "";
                                    item.details = "";
                                    item.isFavorite = false;
                                    item.isNotified = true;
                                    String[] parts = notifLink.split("/");
                                    if (parts.length >= 4)
                                        item.otherUserId = parts[parts.length - 1];
                                    finalFavs.add(item);
                                    fetchUserProfileForFavorite(item, fullCookie, item.otherUserId);
                                }
                            }

                            scrapeFavoritesHtml(finalFavs, () ->
                                fetchBookmarks(finalFavs, () -> {
                                    runOnUiThread(() -> {
                                        allFavoriteItems.clear();
                                        allFavoriteItems.addAll(finalFavs);
                                        applyContactsFilter();

                                        java.util.Set<String> favoritesSet = new java.util.HashSet<>();
                                        java.util.Set<String> bookmarksSet = new java.util.HashSet<>();
                                        for (FavoriteAdapter.FavoriteItem fi : finalFavs) {
                                            if (fi.userLink != null && !fi.userLink.isEmpty()) {
                                                if (fi.isFavorite) {
                                                    favoritesSet.add(fi.userLink);
                                                }
                                                if (fi.isBookmarked) {
                                                    bookmarksSet.add(fi.userLink);
                                                }
                                            }
                                        }
                                        getAppPrefs().getRaw().edit()
                                                .putStringSet("CACHED_FAVORITES_LINKS", favoritesSet)
                                                .putStringSet("CACHED_BOOKMARKS_LINKS", bookmarksSet)
                                                .apply();
                                    });
                                })
                            );
                        } catch (Exception e) {
                            AppLogger.log("MainActivity", "Error parsing favorites", e);
                        }
                    }
                }
            }
        });
    }

    /**
     * Fetches /ct/online/2 (Mes favoris) and merges HTML-exclusive data (distance,
     * city, online status) into the existing list. This is the only source for distance.
     */
    private void scrapeFavoritesHtml(final List<FavoriteAdapter.FavoriteItem> listToMerge,
            final Runnable onDone) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        Log.d(TAG, "scrapeFavoritesHtml started");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/online/2")
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                AppLogger.log(TAG, "scrapeFavoritesHtml failed", e);
                if (onDone != null) onDone.run();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);
                        List<FavoriteAdapter.FavoriteItem> scraped = ApiParser.parseFavoritesHtml(body);
                        Log.d(TAG, "scrapeFavoritesHtml found " + scraped.size() + " favorites");

                        for (FavoriteAdapter.FavoriteItem scrapedItem : scraped) {
                            FavoriteAdapter.FavoriteItem existing = null;
                            for (FavoriteAdapter.FavoriteItem fav : listToMerge) {
                                if (fav.otherUserId != null
                                        && fav.otherUserId.equals(scrapedItem.otherUserId)) {
                                    existing = fav;
                                    break;
                                }
                            }
                            if (existing == null) {
                                // User is a favorite but wasn't in REST response; add it
                                existing = scrapedItem;
                                listToMerge.add(existing);
                            } else {
                                // Merge HTML-exclusive data into existing REST item
                                existing.distance = scrapedItem.distance;
                                existing.city = scrapedItem.city;
                                existing.age = scrapedItem.age;
                                if (!scrapedItem.details.isEmpty()) {
                                    existing.details = scrapedItem.details;
                                }
                                // Respect per-card online icon for real-time status
                                existing.isOnline = scrapedItem.isOnline;
                                if (scrapedItem.isBookmarked) {
                                    existing.isBookmarked = true;
                                }
                                if (existing.avatarUrl == null || existing.avatarUrl.isEmpty()) {
                                    existing.avatarUrl = scrapedItem.avatarUrl;
                                }
                            }

                            // Load from cache or fetch REST profile
                            JSONObject cached = ProfileCacheManager.getInstance().getProfile(existing.otherUserId);
                            if (cached != null) {
                                populateFavoriteFromCache(existing, cached);
                            }
                            boolean isStale = ProfileCacheManager.getInstance().isProfileStale(existing.otherUserId);
                            if (cached == null || isStale || existing.lastConnection == null || existing.lastConnection.isEmpty()) {
                                fetchUserProfileForFavorite(existing, fullCookie, existing.otherUserId);
                            }
                        }
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error in scrapeFavoritesHtml", e);
                } finally {
                    if (onDone != null) onDone.run();
                }
            }
        });
    }

    private void fetchBookmarks(final List<FavoriteAdapter.FavoriteItem> listToMerge, final Runnable onDone) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        Log.d(TAG, "fetchBookmarks started");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/online/11")
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                AppLogger.log("JalfBookmarks", "fetchBookmarks failed", e);
                if (onDone != null)
                    onDone.run();
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);

                        Log.d(TAG, "HTML Body length: " + body.length());

                        List<FavoriteAdapter.FavoriteItem> scrapedItems = ApiParser.parseBookmarkHtml(body);
                        Log.d(TAG, "Scraper found " + scrapedItems.size() + " bookmarked users");

                        SecurePrefs secure = SecurePrefs.get(MainActivity.this);
                        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
                        boolean forceRefresh = swipeRefreshFavorites.isRefreshing();

                        for (FavoriteAdapter.FavoriteItem scrapedItem : scrapedItems) {
                            FavoriteAdapter.FavoriteItem existing = null;
                            for (FavoriteAdapter.FavoriteItem fav : listToMerge) {
                                if (fav.otherUserId != null && fav.otherUserId.equals(scrapedItem.otherUserId)) {
                                    existing = fav;
                                    break;
                                }
                            }

                            if (existing == null) {
                                existing = scrapedItem;
                                listToMerge.add(existing);
                            } else {
                                // Merge scraper data (city, distance, age) into existing item
                                existing.city = scrapedItem.city;
                                existing.age = scrapedItem.age;
                                existing.distance = scrapedItem.distance;
                                existing.details = scrapedItem.details;
                                existing.isBookmarked = true;
                                existing.isOnline = scrapedItem.isOnline;
                                if (existing.avatarUrl == null || existing.avatarUrl.isEmpty()) {
                                    existing.avatarUrl = scrapedItem.avatarUrl;
                                }
                            }

                            // Trigger REST enrichment
                            if (forceRefresh
                                    || ProfileCacheManager.getInstance().isStatusStale(scrapedItem.otherUserId)) {
                                fetchUserProfileForFavorite(existing, fullCookie, existing.otherUserId);
                            } else {
                                // Load from cache if fresh
                                JSONObject cached = ProfileCacheManager.getInstance().getProfile(existing.otherUserId);
                                if (cached != null) {
                                    existing.name = cached.optString("name", cached.optString("pseudo", existing.name));
                                    String onlineVal = cached.optString("online", "0");
                                    existing.isOnline = "1".equals(onlineVal) || cached.optBoolean("online", false)
                                            || cached.optBoolean("is_online", false);
                                } else {
                                    fetchUserProfileForFavorite(existing, fullCookie, existing.otherUserId);
                                }
                            }
                        }

                        runOnUiThread(() -> {
                            swipeRefreshFavorites.setRefreshing(false);
                        });
                    } else {
                        runOnUiThread(() -> swipeRefreshFavorites.setRefreshing(false));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in fetchBookmarks onResponse", e);
                    runOnUiThread(() -> swipeRefreshFavorites.setRefreshing(false));
                } finally {
                    if (onDone != null)
                        onDone.run();
                }
            }
        });
    }

    private void syncUnitPreference() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty())
            return;

        Request request = new Request.Builder()
                .url("https://m-app.jalf.com/ct/myOptions/display_options")
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("MainActivity", "Failed to sync unit preference", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String html = NetworkUtils.responseToString(r);
                        boolean isImperial = ApiParser.parseIsImperial(html);
                        getAppPrefs().getRaw()
                                .edit()
                                .putBoolean(ApiConstants.KEY_USE_IMPERIAL, isImperial)
                                .apply();
                        Log.d(TAG, "Unit preference synced: " + (isImperial ? "Imperial" : "Metric"));
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error parsing unit preference", e);
                }
            }
        });
    }

    private void fetchAllUnreadCounts() {
        fetchNewConversations(0, true);
        fetchActiveConversations(0, true);
        fetchArchivedConversations(0, true);
    }

    private void fetchConversations(final int offset, final boolean clearExisting) {
        if (isLoadingMore)
            return;
        isLoadingMore = true;

        if (currentChatTab == TAB_ARCHIVED) {
            fetchArchivedConversations(offset, clearExisting);
            return;
        } else if (currentChatTab == TAB_NEW) {
            fetchNewConversations(offset, clearExisting);
            return;
        }

        // TAB_ACTIVE
        fetchActiveConversations(offset, clearExisting);
    }

    private void fetchNewConversations(final int offset, final boolean clearExisting) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        int count = (offset == 0) ? 50 : 25;
        Request reqNew = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/conversations/new?offset=" + offset
                        + "&count=" + count)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        if (offset == 0) setLoading(true);


        client.newCall(reqNew).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isLoadingMore = false;
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    setLoading(false);
                });

            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    isLoadingMore = false;
                    runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        setLoading(false);
                    });

                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONArray arr = null;
                            try {
                                JSONObject json = new JSONObject(body);
                                totalNewCount = json.optInt("total_conversations_count", -1);
                                totalUnreadNew = json.optInt("total_unread_messages_count", 0);
                                arr = json.optJSONArray("items");
                                if (arr == null)
                                    arr = json.optJSONArray("conversations");
                            } catch (Exception e) {
                                arr = new JSONArray(body);
                            }

                            final JSONArray finalArr = arr;
                            final List<ChatAdapter.ChatItem> newItems = parseConversationJson(finalArr, false);

                            runOnUiThread(() -> {
                                if (clearExisting)
                                    allNewItems.clear();
                                // Remove existing Load More if present
                                if (!allNewItems.isEmpty()
                                        && allNewItems.get(allNewItems.size() - 1).type == ChatAdapter.TYPE_LOAD_MORE) {
                                    allNewItems.remove(allNewItems.size() - 1);
                                }

                                // Deduplicate
                                for (ChatAdapter.ChatItem item : newItems) {
                                    boolean exists = false;
                                    for (ChatAdapter.ChatItem existing : allNewItems) {
                                        if (Objects.equals(existing.conversationLink, item.conversationLink)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists)
                                        allNewItems.add(item);
                                }

                                if (totalNewCount > allNewItems.size()) {
                                    allNewItems.add(new ChatAdapter.ChatItem(ChatAdapter.TYPE_LOAD_MORE));
                                }

                                offsetNew = offset + (finalArr != null ? finalArr.length() : 0);
                                applyFilter("chats");
                                updateUnreadCounts();
                            });
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing new conversations", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchActiveConversations(final int offset, final boolean clearExisting) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        int count = (offset == 0) ? 50 : 25;
        Request reqActive = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/conversations/active?offset=" + offset
                        + "&count=" + count)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        if (offset == 0) setLoading(true);


        client.newCall(reqActive).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isLoadingMore = false;
                runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    isLoadingMore = false;
                    runOnUiThread(() -> swipeRefreshLayout.setRefreshing(false));
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONArray arr = null;
                            try {
                                JSONObject json = new JSONObject(body);
                                totalActiveCount = json.optInt("total_conversations_count", -1);
                                totalUnreadActive = json.optInt("total_unread_messages_count", 0);
                                arr = json.optJSONArray("items");
                                if (arr == null)
                                    arr = json.optJSONArray("conversations");
                            } catch (Exception e) {
                                arr = new JSONArray(body);
                            }

                            final JSONArray finalArr = arr;
                            final List<ChatAdapter.ChatItem> activeItems = parseConversationJson(finalArr, false);

                            runOnUiThread(() -> {
                                if (clearExisting)
                                    allActiveItems.clear();
                                // Remove existing Load More if present
                                if (!allActiveItems.isEmpty() && allActiveItems
                                        .get(allActiveItems.size() - 1).type == ChatAdapter.TYPE_LOAD_MORE) {
                                    allActiveItems.remove(allActiveItems.size() - 1);
                                }

                                // Deduplicate
                                for (ChatAdapter.ChatItem item : activeItems) {
                                    boolean exists = false;
                                    for (ChatAdapter.ChatItem existing : allActiveItems) {
                                        if (Objects.equals(existing.conversationLink, item.conversationLink)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists)
                                        allActiveItems.add(item);
                                }

                                if (totalActiveCount > allActiveItems.size()) {
                                    allActiveItems.add(new ChatAdapter.ChatItem(ChatAdapter.TYPE_LOAD_MORE));
                                }

                                offsetActive = offset + (finalArr != null ? finalArr.length() : 0);
                                applyFilter("chats");
                                updateUnreadCounts();
                            });
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing active conversations", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchArchivedConversations(final int offset, final boolean clearExisting) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        int count = (offset == 0) ? 50 : 25;
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/conversations/archived?offset=" + offset
                        + "&count=" + count)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        if (offset == 0) setLoading(true);
        client.newCall(req).enqueue(new Callback() {

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isLoadingMore = false;
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    setLoading(false);
                });
            }


            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    isLoadingMore = false;
                    runOnUiThread(() -> {
                        swipeRefreshLayout.setRefreshing(false);
                        setLoading(false);
                    });

                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONArray arr = null;
                            try {
                                JSONObject json = new JSONObject(body);
                                totalArchivedCount = json.optInt("total_conversations_count", -1);
                                totalUnreadArchived = json.optInt("total_unread_messages_count", 0);
                                arr = json.optJSONArray("items");
                                if (arr == null)
                                    arr = json.optJSONArray("conversations");
                            } catch (Exception e) {
                                arr = new JSONArray(body);
                            }

                            final JSONArray finalArr = arr;
                            final List<ChatAdapter.ChatItem> archivedItems = parseConversationJson(finalArr, false);

                            runOnUiThread(() -> {
                                if (clearExisting)
                                    allArchivedItems.clear();
                                // Remove existing Load More if present
                                if (!allArchivedItems.isEmpty() && allArchivedItems
                                        .get(allArchivedItems.size() - 1).type == ChatAdapter.TYPE_LOAD_MORE) {
                                    allArchivedItems.remove(allArchivedItems.size() - 1);
                                }

                                // Deduplicate
                                for (ChatAdapter.ChatItem item : archivedItems) {
                                    boolean exists = false;
                                    for (ChatAdapter.ChatItem existing : allArchivedItems) {
                                        if (Objects.equals(existing.conversationLink, item.conversationLink)) {
                                            exists = true;
                                            break;
                                        }
                                    }
                                    if (!exists)
                                        allArchivedItems.add(item);
                                }

                                if (totalArchivedCount > allArchivedItems.size()) {
                                    allArchivedItems.add(new ChatAdapter.ChatItem(ChatAdapter.TYPE_LOAD_MORE));
                                }

                                offsetArchived = offset + (finalArr != null ? finalArr.length() : 0);
                                applyFilter("chats");
                                updateUnreadCounts();
                            });
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing archived conversations", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchUserProfileForFavorite(FavoriteAdapter.FavoriteItem item, String fullCookie, String otherUserId) {
        SecurePrefs secure = SecurePrefs.get(this);
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + otherUserId)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject profile = new JSONObject(NetworkUtils.responseToString(r));

                            // Inject scraped data as fallback/enrichment for cache
                            if (item.city != null && !item.city.isEmpty() && !profile.has("city")) {
                                profile.put("city", item.city);
                            }
                            if (item.distance != null && !item.distance.isEmpty()) {
                                profile.put("distance", item.distance);
                            }
                            if (item.age != null && !item.age.isEmpty() && !profile.has("age")) {
                                profile.put("age", item.age);
                            }

                            ProfileCacheManager.getInstance().putProfile(MainActivity.this, otherUserId, profile);
                            item.name = profile.optString("name", profile.optString("pseudo", item.name));
                            item.isCertified = profile.optBoolean("certified", false);
                            item.lastConnection = profile.optString("last_connection", profile.optString("last_connected", ""));

                            String onlineVal = profile.optString("online", "0");
                            item.isOnline = "1".equals(onlineVal) || profile.optBoolean("online", false)
                                    || profile.optBoolean("is_online", false);

                            item.sexLink = profile.optString("sex_link", "");
                            if (!item.sexLink.isEmpty()) {
                                item.sexIconUrl = MetadataManager.getInstance().resolveIcon(item.sexLink);
                            }

                            String age = profile.optString("age", "");
                            if (!age.isEmpty() && !age.contains("ans"))
                                age += " ans";

                            String cachedCity = profile.optString("city", "");
                            if (cachedCity.isEmpty()) {
                                JSONObject region = profile.optJSONObject("region");
                                if (region != null) {
                                    cachedCity = region.optString("name", "");
                                }
                            }

                            if (!cachedCity.isEmpty()) {
                                item.city = cachedCity;
                            }

                            // Re-format details using stored distance if it exists
                            StringBuilder details = new StringBuilder();
                            if (!age.isEmpty())
                                details.append(age);
                            if (item.city != null && !item.city.isEmpty()) {
                                if (details.length() > 0)
                                    details.append(", ");
                                details.append(item.city);
                            }
                            if (item.distance != null && !item.distance.isEmpty()) {
                                if (details.length() > 0)
                                    details.append(" (").append(item.distance).append(")");
                                else
                                    details.append(item.distance);
                            }
                            item.details = details.toString();

                            runOnUiThread(() -> applyContactsFilter());

                            JSONObject photo = profile.optJSONObject("photo");
                            if (photo != null) {
                                item.avatarUrl = photo.optString("image_144x189_link",
                                        photo.optString("thumbnail_uri", ""));
                            }
                            if (item.avatarUrl == null || item.avatarUrl.isEmpty()) {
                                JSONArray photos = profile.optJSONArray("photos");
                                if (photos != null && photos.length() > 0) {
                                    JSONObject p = photos.getJSONObject(0);
                                    item.avatarUrl = p.optString("thumb_url", p.optString("large_url", ""));
                                }
                            }

                            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()
                                    && !item.avatarUrl.startsWith("http")) {
                                item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                            }

                            // Extract nsfwRank from photo metadata
                            item.nsfwRank = extractNsfwRankFromProfile(profile);
                            if (item.nsfwRank == 0) {
                                // Fallback: check photo album for authoritative rating link
                                fetchPhotoRankFromAlbum(otherUserId, albumRank -> {
                                    if (albumRank > 0) {
                                        item.nsfwRank = albumRank;
                                        runOnUiThread(() -> applyContactsFilter());
                                    }
                                });
                            }

                            runOnUiThread(() -> applyContactsFilter());



                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing favorite profile", e);
                        }
                    }
                }
            }
        });
    }

    private void populateFavoriteFromCache(FavoriteAdapter.FavoriteItem item, JSONObject profile) {
        if (item.name == null || item.name.equals("User") || item.name.equals("Someone")) {
            item.name = profile.optString("name", profile.optString("pseudo", item.name));
        }

        item.isCertified = profile.optBoolean("certified", false);
        item.sexLink = profile.optString("sex_link", "");
        if (!item.sexLink.isEmpty()) {
            item.sexIconUrl = MetadataManager.getInstance().resolveIcon(item.sexLink);
        }
        String age = profile.optString("age", "");
        String city = profile.optString("city", "");
        if (!city.isEmpty()) {
            item.city = city;
            if (!age.isEmpty())
                item.details = age + ", " + city;
            else
                item.details = city;
        } else if (!age.isEmpty()) {
            item.details = age + " ans";
        }

        if (!item.isOnline) {
            String onlineVal = profile.optString("online", "0");
            item.isOnline = "1".equals(onlineVal) || profile.optBoolean("online", false)
                    || profile.optBoolean("is_online", false);
        }

        JSONObject photo = profile.optJSONObject("photo");
        if (photo != null && (item.avatarUrl == null || item.avatarUrl.isEmpty())) {
            item.avatarUrl = photo.optString("image_144x189_link",
                    photo.optString("image_82x107_link", ""));
        }
        // last connection should never be loaded from cache to ensure fresh REST fetch
        runOnUiThread(() -> applyContactsFilter());
    }

    /**
     * Rebuilds item.details from item.city (and the age already baked into the
     * existing details string).
     */
    private void rebuildContactDetails(FavoriteAdapter.FavoriteItem item) {
        // Parse any existing age out of details (may be "28 ans" or "28 ans, OldCity")
        String age = "";
        if (item.details != null && !item.details.isEmpty()) {
            int commaIdx = item.details.indexOf(", ");
            String agePart = (commaIdx > 0) ? item.details.substring(0, commaIdx) : item.details;
            if (agePart.endsWith(" ans") || agePart.matches("\\d+.*"))
                age = agePart;
        }
        String c = (item.city != null) ? item.city : "";
        if (!age.isEmpty() && !c.isEmpty())
            item.details = age + ", " + c;
        else if (!age.isEmpty())
            item.details = age;
        else if (!c.isEmpty())
            item.details = c;
    }

    private void fetchUserProfileForEvent(EventAdapter.EventItem item, String fullCookie, String otherUserId) {
        SecurePrefs secure = SecurePrefs.get(this);
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + otherUserId)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            JSONObject profile = new JSONObject(NetworkUtils.responseToString(r));
                            ProfileCacheManager.getInstance().putProfile(MainActivity.this, otherUserId, profile);
                            item.isOnline = profile.optBoolean("is_online", false);
                            item.title = profile.optString("name", item.title);

                            String sexLink = profile.optString("sex_link", "");
                            if (sexLink != null && !sexLink.isEmpty()) {
                                item.sexIconUrl = MetadataManager.getInstance().resolveIcon(sexLink);
                            }

                            JSONObject pObj = profile.optJSONObject("photo");
                            if (pObj != null) {
                                item.avatarUrl = pObj.optString("image_144x189_link",
                                        pObj.optString("thumbnail_uri", ""));
                            }
                            if (item.avatarUrl == null || item.avatarUrl.isEmpty()) {
                                JSONArray photosArr = profile.optJSONArray("photos");
                                if (photosArr != null && photosArr.length() > 0) {
                                    JSONObject p = photosArr.getJSONObject(0);
                                    item.avatarUrl = p.optString("thumb_url", p.optString("large_url", ""));
                                }
                            }

                            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()
                                    && !item.avatarUrl.startsWith("http")) {
                                item.avatarUrl = ApiConstants.BASE_URL + item.avatarUrl;
                            }

                            // Extract nsfwRank from photo metadata
                            item.nsfwRank = extractNsfwRankFromProfile(profile);
                            if (item.nsfwRank == 0) {
                                fetchPhotoRankFromAlbum(otherUserId, albumRank -> {
                                    if (albumRank > 0) {
                                        item.nsfwRank = albumRank;
                                        runOnUiThread(() -> eventAdapter.notifyDataSetChanged());
                                    }
                                });
                            }

                            runOnUiThread(() -> eventAdapter.notifyDataSetChanged());
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing event profile", e);
                        }
                    }
                }
            }
        });
    }

    private void fetchEvents() {
        setLoading(true);

        if (filterEventsDirection == FILTER_EVENTS_DIRECTION_INCOMING || filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
            boolean isStale = (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL)
                    ? (ProfileCacheManager.getInstance().areEventsStale() || ProfileCacheManager.getInstance().areOutgoingEventsStale())
                    : ProfileCacheManager.getInstance().areEventsStale();

            if (!isStale && !eventItems.isEmpty()) {
                Log.d(TAG, "Events cache is fresh, skipping network fetch");
                runOnUiThread(() -> {
                    swipeRefreshEvents.setRefreshing(false);
                    setLoading(false);
                });
                return;
            }

            SecurePrefs secure = SecurePrefs.get(this);
            String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
            String suid = secure.getString(ApiConstants.KEY_SUID, "");
            Request req = new Request.Builder()
                    .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/notifications")
                    .addHeader("Cookie", fullCookie)
                    .addHeader("x-csrftoken", suid)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .build();
            client.newCall(req).enqueue(new Callback() {
                @Override
                public void onFailure(@NonNull Call call, @NonNull IOException e) {
                    runOnUiThread(() -> {
                        swipeRefreshEvents.setRefreshing(false);
                        setLoading(false);
                    });
                }


                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    try (Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            try {
                                String responseBody = NetworkUtils.responseToString(r);
                                JSONObject json = new JSONObject(responseBody);
                                JSONArray arr = json.optJSONArray("notifications");
                                if (arr == null)
                                    arr = new JSONArray();

                                // Update cache
                                ProfileCacheManager.getInstance().putNotifications(arr);
                                ProfileCacheManager.getInstance().save(MainActivity.this);

                                List<EventAdapter.EventItem> restEvents = ApiParser.parseNotificationJson(arr);
                                String fullCookie = SecurePrefs.get(MainActivity.this)
                                        .getString(ApiConstants.KEY_FULL_COOKIE, "");
                                // Trigger async profile enrichment for each parsed item
                                for (EventAdapter.EventItem item : restEvents) {
                                    if (item.otherUserId != null && !item.otherUserId.isEmpty()) {
                                        JSONObject cached = ProfileCacheManager.getInstance().getProfile(item.otherUserId);
                                        if (cached != null) {
                                            if (item.title == null || item.title.equals("Someone")) {
                                                item.title = cached.optString("name",
                                                        cached.optString("pseudo", item.title));
                                            }
                                            JSONObject photo = cached.optJSONObject("photo");
                                            if (photo != null && (item.avatarUrl == null || item.avatarUrl.isEmpty())) {
                                                item.avatarUrl = photo.optString("image_144x189_link",
                                                        photo.optString("image_82x107_link", ""));
                                            }
                                            
                                            // Update online status from cache
                                            String onlineVal = cached.optString("online", "0");
                                            item.isOnline = "1".equals(onlineVal) || cached.optBoolean("online", false)
                                                    || cached.optBoolean("is_online", false);
                                        }

                                        // Fetch if status is stale OR avatar is still missing
                                        if (ProfileCacheManager.getInstance().isStatusStale(item.otherUserId) 
                                                || item.avatarUrl == null || item.avatarUrl.isEmpty()) {
                                            fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                                        }
                                    }
                                }
                                fetchProfileViewers(restEvents, () -> fetchPokeEvents(restEvents));
                            } catch (Exception e) {
                                Log.e("MainActivity", "Error parsing notifications", e);
                                runOnUiThread(() -> {
                                    swipeRefreshEvents.setRefreshing(false);
                                    setLoading(false);
                                });

                            }
                        } else {
                            runOnUiThread(() -> swipeRefreshEvents.setRefreshing(false));
                        }
                    }
                }

            });
        } else {
            if (!ProfileCacheManager.getInstance().areOutgoingEventsStale() && !eventItems.isEmpty()) {
                Log.d(TAG, "Outgoing events cache is fresh, skipping network fetch");
                runOnUiThread(() -> {
                    swipeRefreshEvents.setRefreshing(false);
                    setLoading(false);
                });
                return;
            }
            fetchOutgoingPokeEvents();
        }
    }

    private String parseLookerDate(String rawDate) {
        return ApiParser.parseLookerDate(rawDate);
    }

    private int getMonthIndex(String month) {
        return ApiParser.getMonthIndex(month);
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

    private void fetchProfileViewers(List<EventAdapter.EventItem> restEvents, final Runnable onDone) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_LOOKERS)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                finalizeEvents(restEvents);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String html = NetworkUtils.responseToString(r);
                            List<EventAdapter.EventItem> viewerItems = ApiParser.parseProfileViewersHtml(html);
                            for (EventAdapter.EventItem item : viewerItems) {
                                Log.d(TAG,
                                        "Extracted profile viewer ID: " + item.otherUserId + " for: " + item.title);
                                restEvents.add(item);
                                fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing profile viewers", e);
                        }
                    }
                    if (onDone != null)
                        onDone.run();
                    else
                        finalizeEvents(restEvents);
                }
            }
        });

    }

    private void fetchPokeEvents(List<EventAdapter.EventItem> restEvents) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_POKES)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
                    fetchOutgoingPokeEventsChain(restEvents);
                } else {
                    finalizeEvents(restEvents);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String html = NetworkUtils.responseToString(r);
                            List<EventAdapter.EventItem> pokeItems = ApiParser.parsePokeHtml(html);
                            for (EventAdapter.EventItem item : pokeItems) {
                                restEvents.add(item);
                                Log.d(TAG, "Extracted poke ID: " + item.otherUserId + " for: " + item.title);
                                fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing pokes", e);
                        }
                    }
                    if (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
                        fetchOutgoingPokeEventsChain(restEvents);
                    } else {
                        finalizeEvents(restEvents);
                    }
                }
            }
        });
    }

    private void fetchOutgoingPokeEventsChain(List<EventAdapter.EventItem> restEvents) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_POKES_SENT)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                fetchOutgoingVisits(restEvents, null);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String html = NetworkUtils.responseToString(r);
                            List<EventAdapter.EventItem> pokeItems = ApiParser.parsePokeHtml(html);
                            for (EventAdapter.EventItem item : pokeItems) {
                                item.why = "outgoing_salutation";
                                item.eventType = -4;
                                Log.d(TAG, "Extracted outgoing poke ID Chain: " + item.otherUserId + " for: " + item.title);
                                fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                                restEvents.add(item);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing outgoing pokes in chain", e);
                        }
                    }
                    fetchOutgoingVisits(restEvents, null);
                }
            }
        });
    }

    private void fetchOutgoingPokeEvents() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_POKES_SENT)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                List<EventAdapter.EventItem> pokeItems = new ArrayList<>();
                fetchOutgoingVisits(pokeItems, () -> finalizeOutgoingEvents(pokeItems));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    List<EventAdapter.EventItem> pokeItems = new ArrayList<>();
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String html = NetworkUtils.responseToString(r);
                            pokeItems.addAll(ApiParser.parsePokeHtml(html));
                            for (EventAdapter.EventItem item : pokeItems) {
                                item.why = "outgoing_salutation";
                                item.eventType = -4;
                                Log.d(TAG, "Extracted outgoing poke ID: " + item.otherUserId + " for: " + item.title);
                                fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing outgoing pokes", e);
                        }
                    }
                    fetchOutgoingVisits(pokeItems, () -> finalizeOutgoingEvents(pokeItems));
                }
            }
        });
    }

    private void fetchOutgoingVisits(List<EventAdapter.EventItem> restEvents, final Runnable onDone) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + ApiConstants.PATH_VISITS_SENT)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (onDone != null) {
                    onDone.run();
                } else {
                    finalizeEvents(restEvents);
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String html = NetworkUtils.responseToString(r);
                            List<EventAdapter.EventItem> visitItems = ApiParser.parseProfileViewersHtml(html);
                            for (EventAdapter.EventItem item : visitItems) {
                                item.why = "visit_sent";
                                item.eventType = -6;
                                item.body = "visited their profile";
                                Log.d(TAG, "Extracted outgoing visit ID: " + item.otherUserId + " for: " + item.title);
                                restEvents.add(item);
                                fetchUserProfileForEvent(item, fullCookie, item.otherUserId);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing outgoing visits", e);
                        }
                    }
                    if (onDone != null) {
                        onDone.run();
                    } else {
                        finalizeEvents(restEvents);
                    }
                }
            }
        });
    }

    private void finalizeOutgoingEvents(List<EventAdapter.EventItem> events) {
        for (EventAdapter.EventItem item : events) {
            localizeEventItem(item);
        }
        List<EventAdapter.EventItem> finalData = ApiParser.deduplicateEvents(events);

        runOnUiThread(() -> {
            allEventItems.clear();
            allEventItems.addAll(finalData);
            applyFilter("events");
            swipeRefreshEvents.setRefreshing(false);
            setLoading(false);
            cacheConsolidatedOutgoingEvents(finalData);
        });
    }

    private void cacheConsolidatedOutgoingEvents(List<EventAdapter.EventItem> events) {
        JSONArray arr = new JSONArray();
        for (EventAdapter.EventItem item : events) {
            JSONObject obj = item.toJson();
            if (obj != null)
                arr.put(obj);
        }
        ProfileCacheManager.getInstance().putOutgoingEvents(arr);
        ProfileCacheManager.getInstance().save(this);
    }

    private void finalizeEvents(List<EventAdapter.EventItem> events) {
        for (EventAdapter.EventItem item : events) {
            localizeEventItem(item);
        }
        List<EventAdapter.EventItem> finalData = ApiParser.deduplicateEvents(events);

        runOnUiThread(() -> {
            allEventItems.clear();
            allEventItems.addAll(finalData);
            applyFilter("events");
            swipeRefreshEvents.setRefreshing(false);
            setLoading(false);

            if (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
                List<EventAdapter.EventItem> incoming = new ArrayList<>();
                List<EventAdapter.EventItem> outgoing = new ArrayList<>();
                for (EventAdapter.EventItem it : finalData) {
                    if (it.eventType == -4 || "outgoing_salutation".equals(it.why)
                            || it.eventType == -6 || "visit_sent".equals(it.why)) {
                        outgoing.add(it);
                    } else {
                        incoming.add(it);
                    }
                }
                cacheConsolidatedEvents(incoming);
                cacheConsolidatedOutgoingEvents(outgoing);
            } else {
                cacheConsolidatedEvents(finalData);
            }
        });
    }
    
    private void localizeEventItem(EventAdapter.EventItem item) {
        if ("Someone".equals(item.title) || "Quelqu'un".equals(item.title)) {
            item.title = getString(R.string.someone);
        }

        if (item.why == null || item.why.isEmpty())
            return;

        if ("favorite_of".equals(item.why) || item.eventType == 22) {
            item.body = getString(R.string.added_favorite);
        } else if ("your_favorite".equals(item.why) || item.eventType == 6) {
            item.body = getString(R.string.added_new_photo);
        } else if (item.eventType == 20 || "salutation".equals(item.why) || "posted".equals(item.why)) {
            item.body = getString(R.string.sent_poke);
        } else if ("outgoing_salutation".equals(item.why) || item.eventType == -4) {
            item.body = getString(R.string.sent_poke_outgoing);
        } else if ("visit_sent".equals(item.why) || item.eventType == -6) {
            item.body = getString(R.string.visited_profile_outgoing);
        } else if ("visit".equals(item.why) || item.eventType == -2) {
            item.body = getString(R.string.visited_profile);
        } else if ("notification".equals(item.why)) {
            item.body = getString(R.string.sent_notification);
        } else if ("author".equals(item.why)) {
            if (item.eventType == 3) {
                item.body = getString(R.string.would_recreate_photo);
            } else {
                item.body = getString(R.string.liked_photo);
            }
        }
    }


    private long parseIsoToMillis(String iso) {
        return ApiParser.parseIsoToMillis(iso);
    }

    private void cacheConsolidatedEvents(List<EventAdapter.EventItem> events) {
        JSONArray arr = new JSONArray();
        for (EventAdapter.EventItem item : events) {
            JSONObject obj = item.toJson();
            if (obj != null)
                arr.put(obj);
        }
        ProfileCacheManager.getInstance().putNotifications(arr);
        ProfileCacheManager.getInstance().save(this);
    }

    private void loadEventsFromCache() {
        List<EventAdapter.EventItem> cached = new ArrayList<>();
        JSONArray arr = null;
        JSONArray arrOutgoing = null;
        if (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
            arr = ProfileCacheManager.getInstance().getCachedNotifications();
            arrOutgoing = ProfileCacheManager.getInstance().getCachedOutgoingEvents();
        } else if (filterEventsDirection == FILTER_EVENTS_DIRECTION_INCOMING) {
            arr = ProfileCacheManager.getInstance().getCachedNotifications();
        } else {
            arr = ProfileCacheManager.getInstance().getCachedOutgoingEvents();
        }

        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                try {
                    cached.add(EventAdapter.EventItem.fromJson(arr.getJSONObject(i)));
                } catch (Exception e) {
                    AppLogger.log("MainActivity", "Error parsing cached incoming event item", e);
                }
            }
        }
        if (arrOutgoing != null) {
            for (int i = 0; i < arrOutgoing.length(); i++) {
                try {
                    cached.add(EventAdapter.EventItem.fromJson(arrOutgoing.getJSONObject(i)));
                } catch (Exception e) {
                    AppLogger.log("MainActivity", "Error parsing cached outgoing event item", e);
                }
            }
        }

        if (!cached.isEmpty()) {
            List<EventAdapter.EventItem> finalCached = ApiParser.deduplicateEvents(cached);
            Collections.sort(finalCached, (e1, e2) -> Long.compare(e2.sortTimestamp, e1.sortTimestamp));
            runOnUiThread(() -> {
                allEventItems.clear();
                allEventItems.addAll(finalCached);
                applyFilter("events");
            });
        }
    }

    private void updateUnreadCounts() {
        final int fActive = totalUnreadActive;
        final int fNew = totalUnreadNew;
        final int fArchived = totalUnreadArchived;

        runOnUiThread(() -> {
            if (tabLayoutChats.getTabAt(TAB_ACTIVE) != null) {
                tabLayoutChats.getTabAt(TAB_ACTIVE).setText(R.string.tab_actives);

                BadgeDrawable badge = tabLayoutChats.getTabAt(TAB_ACTIVE).getOrCreateBadge();
                badge.setHorizontalOffset(dpToPx(10));
                badge.setVerticalOffset(dpToPx(2));
                if (fActive > 0) {
                    badge.setVisible(true);
                    badge.setNumber(fActive);
                } else {
                    badge.setVisible(false);
                }
            }
            if (tabLayoutChats.getTabAt(TAB_NEW) != null) {
                tabLayoutChats.getTabAt(TAB_NEW).setText(R.string.tab_new);

                BadgeDrawable badge = tabLayoutChats.getTabAt(TAB_NEW).getOrCreateBadge();
                badge.setHorizontalOffset(dpToPx(10));
                badge.setVerticalOffset(dpToPx(2));
                if (fNew > 0) {
                    badge.setVisible(true);
                    badge.setNumber(fNew);
                } else {
                    badge.setVisible(false);
                }
            }
            if (tabLayoutChats.getTabAt(TAB_ARCHIVED) != null) {
                tabLayoutChats.getTabAt(TAB_ARCHIVED).setText(R.string.tab_archived);

                BadgeDrawable badge = tabLayoutChats.getTabAt(TAB_ARCHIVED).getOrCreateBadge();
                badge.setHorizontalOffset(dpToPx(10));
                badge.setVerticalOffset(dpToPx(2));
                if (fArchived > 0) {
                    badge.setVisible(true);
                    badge.setNumber(fArchived);
                } else {
                    badge.setVisible(false);
                }
            }

            int total = fActive + fNew + fArchived;
            BadgeDrawable badge = bottomNav.getOrCreateBadge(R.id.nav_chats);
            if (total > 0) {
                badge.setVisible(true);
                badge.setNumber(total);
            } else {
                badge.setVisible(false);
            }
        });
    }

    public String findConversationForUser(String targetUserId) {
        if (targetUserId == null || targetUserId.isEmpty())
            return null;
        for (ChatAdapter.ChatItem item : allActiveItems) {
            if (item.type == ChatAdapter.TYPE_CHAT && targetUserId.equals(item.otherUserId)) {
                return item.conversationLink;
            }
        }
        for (ChatAdapter.ChatItem item : allNewItems) {
            if (item.type == ChatAdapter.TYPE_CHAT && targetUserId.equals(item.otherUserId)) {
                return item.conversationLink;
            }
        }
        for (ChatAdapter.ChatItem item : allArchivedItems) {
            if (item.type == ChatAdapter.TYPE_CHAT && targetUserId.equals(item.otherUserId)) {
                return item.conversationLink;
            }
        }
        android.content.SharedPreferences convoPrefs = getSharedPreferences("ConversationLinks",
                android.content.Context.MODE_PRIVATE);
        return convoPrefs.getString(targetUserId, null);
    }

    public void backgroundSearchConversation(String targetUserId, int offset) {
        if (targetUserId == null || targetUserId.isEmpty())
            return;
        // Search in Active, New, Archived in parallel or sequence
        searchEndpointsForUser(targetUserId, "/rest/users/" + myUserId + "/conversations/active", offset);
        searchEndpointsForUser(targetUserId, "/rest/users/" + myUserId + "/conversations/new", offset);
        searchEndpointsForUser(targetUserId, "/rest/users/" + myUserId + "/conversations/archived", offset);
    }

    private void searchEndpointsForUser(String targetUserId, String endpointBase, int offset) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        int count = 50;
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + endpointBase + "?offset=" + offset + "&count=" + count)
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
                        JSONObject json = new JSONObject(NetworkUtils.responseToString(r));
                        int total = json.optInt("total_conversations_count", -1);
                        JSONArray arr = json.optJSONArray("conversations");
                        if (arr != null) {
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject obj = arr.getJSONObject(i);
                                JSONArray members = obj.optJSONArray("other_members");
                                if (members != null && members.length() > 0) {
                                    JSONObject m = members.getJSONObject(0);
                                    String uLink = m.optString("user_link", "");
                                    if (uLink.contains("/" + targetUserId)) {
                                        String convoLink = obj.optString("conversation_link", "");
                                        getSharedPreferences("ConversationLinks", MODE_PRIVATE)
                                                .edit().putString(targetUserId, convoLink).apply();
                                        return;
                                    }
                                }
                            }
                            // Recursive offset search if not found
                            if (total > offset + count) {
                                searchEndpointsForUser(targetUserId, endpointBase, offset + count);
                            }
                        }
                    }
                } catch (Exception e) {
                    AppLogger.log("MainActivity", "Error parsing search endpoints for user", e);
                }
            }
        });
    }

    private void updateEventsUi(List<EventAdapter.EventItem> events) {
        Collections.sort(events, (a, b) -> b.timeIso.compareTo(a.timeIso));
        runOnUiThread(() -> {
            allEventItems.clear();
            allEventItems.addAll(events);
            applyFilter("events");
        });
    }

    private String isoToTimestamp(String iso) {
        return iso;
    }

    private String timestampToIso(long ts) {
        return TimeUtils.timestampToIso(ts);
    }

    private long parseFrenchDateToTimestamp(String dateStr) {
        return ApiParser.parseFrenchDateToTimestamp(dateStr);
    }

    private void startPollingWorker() {
        OneTimeWorkRequest work = new OneTimeWorkRequest.Builder(JalfNotificationWorker.class).build();
        WorkManager.getInstance(this).enqueueUniqueWork("JalfPoll", ExistingWorkPolicy.KEEP, work);
    }

    private void startLocationSyncWorker() {
        SharedPreferences prefs = getAppPrefs().getRaw();
        // Use remote_geolocation instead of KEY_AUTO_LOCATION
        boolean isGeoEnabled = prefs.getInt("remote_geolocation_" + myUserId, 1) == 1;
        if (isGeoEnabled) {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();

            PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(LocationSyncWorker.class, 30, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork("LocationSync", ExistingPeriodicWorkPolicy.KEEP, work);
            Log.d(TAG, "LocationSyncWorker scheduled (Geolocation enabled)");
        } else {
            WorkManager.getInstance(this).cancelUniqueWork("LocationSync");
            Log.d(TAG, "LocationSyncWorker cancelled (Geolocation disabled)");
        }
    }

    private void startSseService() {
        SharedPreferences prefs = getAppPrefs().getRaw();
        String sseUrl = prefs.getString(ApiConstants.KEY_SSE_URL, "");
        if (sseUrl.isEmpty())
            return;

        Intent intent = new Intent(this, JalfSseService.class);
        intent.putExtra("url", sseUrl);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAvatars();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sseReceiver, new IntentFilter("io.github.kgelinas.jalfnotifier.SSE_EVENT"),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sseReceiver, new IntentFilter("io.github.kgelinas.jalfnotifier.SSE_EVENT"));
        }

        // Refresh all conversations if we are on the chats tab, to catch any
        // archive/delete
        // from ConversationActivity or other devices
        if (bottomNav != null && bottomNav.getSelectedItemId() == R.id.nav_chats) {
            fetchAllUnreadCounts();
        }
        statusHandler.post(statusRunnable);
    }

    @Override
    protected void onDestroy() {
        MetadataManager.getInstance().removeListener(metadataListener);
        getAppPrefs().getRaw()
                .unregisterOnSharedPreferenceChangeListener(prefListener);
        if (currentProfileSheet != null && currentProfileSheet.isShowing()) {
            currentProfileSheet.dismiss();
        }
        if (resultsBottomSheet != null && resultsBottomSheet.isShowing()) {
            resultsBottomSheet.dismiss();
        }
        super.onDestroy();
    }



    // ── Sex Filter Chips ──────────────────────────────────────────────────────

    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {
            fetchUserStatus();
            statusHandler.postDelayed(this, 5 * 60 * 1000); // 5 minutes
        }
    };

    public void fetchUserStatus() {
        if (myUserId == null || myUserId.isEmpty())
            return;

        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        String url = ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/status";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        Log.d("GhostMode", "[STATUS] Fetching /rest/users/" + myUserId + "/status");
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e("GhostMode", "[STATUS] Fetch failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String raw = NetworkUtils.responseToString(r);
                        Log.d("GhostMode", "[STATUS] Raw response: " + raw);
                        try {
                            JSONObject status = new JSONObject(raw);
                            boolean serverOnline = status.optInt("online", 0) == 1;

                            // /status always returns online=1 while connected.
                            // Visible=no (ghost mode) only hides us from others — it does NOT
                            // change this field. Use local pref as the source of truth for the indicator.
                            SharedPreferences prefs = getAppPrefs().getRaw();
                            boolean appearOffline = prefs.getBoolean(ApiConstants.KEY_APPEAR_OFFLINE, false);
                            isMyOnline = serverOnline && !appearOffline;
                            Log.w("GhostMode", "[STATUS] serverOnline=" + serverOnline
                                    + " appearOffline=" + appearOffline
                                    + " → indicator=" + isMyOnline);
                            runOnUiThread(() -> updateOnlineIndicator());
                        } catch (Exception e) {
                            Log.e("GhostMode", "[STATUS] JSON parse error: " + e.getMessage());
                        }
                    } else {
                        Log.e("GhostMode", "[STATUS] Unsuccessful response: " + r.code());
                    }
                }
            }
        });
    }

    void updateOnlineIndicator() {
        if (toolbarOnlineIndicator != null) {
            toolbarOnlineIndicator.setVisibility(isMyOnline ? View.VISIBLE : View.GONE);
        }
    }

    public void showProfileSheet() {
        ProfileSheetManager.showProfileSheet(this);
    }

    public BottomSheetDialog getCurrentProfileSheet() {
        return currentProfileSheet;
    }



    public void setCurrentProfileSheet(BottomSheetDialog sheet) {
        this.currentProfileSheet = sheet;
    }

    public String getMyUserId() { return myUserId; }
    public String getMyName() { return myName; }
    public String getMyAvatarUrl() { return myAvatarUrl; }
    public String getMyDetails() { return myDetails; }
    public String getMyLocation() { return myLocation; }
    public int getMyNsfwRank() { return myNsfwRank; }
    public boolean isMyOnline() { return isMyOnline; }
    public void setMyOnline(boolean online) { this.isMyOnline = online; }


    void performLogout() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/logout")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> finishLogout());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response r = response) {
                    runOnUiThread(() -> finishLogout());
                }
            }
        });
    }

    private void finishLogout() {
        // Clear in-memory and persistent profile/events caches
        ProfileCacheManager.getInstance().clear(this);

        SecurePrefs secure = SecurePrefs.get(this);
        SharedPreferences mainPrefs = getAppPrefs().getRaw();
        boolean keepCreds = mainPrefs.getBoolean(ApiConstants.KEY_BIOMETRIC_LOGIN, false);

        if (keepCreds) {
            // Keep USERNAME and PASSWORD for biometric auto-login
            secure.remove(ApiConstants.KEY_SUID);
            secure.remove(ApiConstants.KEY_FULL_COOKIE);
            secure.remove(ApiConstants.KEY_USER_PROFILE_JSON);
            secure.remove(ApiConstants.KEY_CACHED_EVENTS);
            secure.remove(ApiConstants.KEY_CACHED_PROFILES);
        } else {
            secure.clear();
        }

        // Always clear non-secure session ID
        mainPrefs.edit().remove(ApiConstants.KEY_USER_ID).apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        if (loadingIndicator != null) {
            if (loading) {
                // If any swipe refresh is already active (user pulled down), don't show the centered overlay
                boolean isRefreshing = (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) ||
                                     (swipeRefreshEvents != null && swipeRefreshEvents.isRefreshing()) ||
                                     (swipeRefreshFavorites != null && swipeRefreshFavorites.isRefreshing());
                
                if (isRefreshing) {
                    loadingIndicator.setVisibility(View.GONE);
                } else {
                    loadingIndicator.setVisibility(View.VISIBLE);
                }
            } else {
                loadingIndicator.setVisibility(View.GONE);
            }
        }
    }


    private void setSearchLoading(boolean loading) {
        if (searchLoadingIndicator != null) {
            searchLoadingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (resultsBottomSheet != null && resultsBottomSheet.isShowing()) {
            View progress = resultsBottomSheet.findViewById(R.id.search_loading_indicator);
            if (progress != null) {
                progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        }
    }



    private ArrayList<SearchOption> mapToSearchOptions(Map<String, String> category, String defaultLabel) {
        ArrayList<SearchOption> list = new ArrayList<>();
        list.add(new SearchOption("", defaultLabel));
        if (category != null) {
            for (Map.Entry<String, String> entry : category.entrySet()) {
                String link = entry.getKey();
                String id = link.substring(link.lastIndexOf("/") + 1);
                list.add(new SearchOption(id, entry.getValue()));
            }
        }
        return list;
    }


    private String normalizeForMatch(String s) {
        return StringUtils.normalizeForMatch(s);
    }

    private void setSpinnerByValue(AutoCompleteTextView spin, List<SearchOption> list, String value) {
        if (value == null || value.isEmpty())
            return;
        for (SearchOption opt : list) {
            if (value.equals(opt.value)) {
                spin.setText(opt.label, false);
                break;
            }
        }
    }

    private List<ChatAdapter.ChatItem> parseConversationJson(JSONArray arr, boolean forceUnread) {
        List<ChatAdapter.ChatItem> result = ApiParser.parseConversationJson(arr, forceUnread, myUserId);

        // Persist conversation links as a side-effect (kept here, not in the pure parser)
        android.content.SharedPreferences convoPrefs = getSharedPreferences("ConversationLinks",
                android.content.Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor convoEditor = convoPrefs.edit();
        for (ChatAdapter.ChatItem item : result) {
            if (item.otherUserId != null && !item.otherUserId.isEmpty()
                    && item.conversationLink != null && !item.conversationLink.isEmpty()) {
                convoEditor.putString(item.otherUserId, item.conversationLink);
            }
            if (item.sexLink != null && !item.sexLink.isEmpty()
                    && (item.sexIconUrl == null || item.sexIconUrl.isEmpty())) {
                item.sexIconUrl = MetadataManager.getInstance().resolveIcon(item.sexLink);
            }

            // Populate nsfwRank from profile cache if available, else trigger async fetch
            if (item.otherUserId != null && !item.otherUserId.isEmpty()) {
                JSONObject cached = ProfileCacheManager.getInstance().getProfile(item.otherUserId);
                if (cached != null) {
                    item.nsfwRank = extractNsfwRankFromProfile(cached);
                } else {
                    fetchUserProfileForChat(item);
                }
            }
        }
        convoEditor.apply();
        return result;
    }

    /** Extracts nsfwRank from a /rest/users/{id} JSON profile object. */
    private int extractNsfwRankFromProfile(JSONObject profile) {
        JSONObject photo = profile.optJSONObject("photo");
        if (photo != null) {
            String ratingLink = photo.optString("photo_rating_link", "");
            if (!ratingLink.isEmpty()) {
                return StringUtils.extractRankFromLink(ratingLink);
            }
            if (photo.optBoolean("is_sensitive", false)) {
                return 2; // SEXUEL minimum
            }
        }
        return 0;
    }

    private void fetchUserProfileForChat(ChatAdapter.ChatItem item) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        if (fullCookie.isEmpty() || item.otherUserId == null || item.otherUserId.isEmpty())
            return;

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + item.otherUserId)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject profile = new JSONObject(NetworkUtils.responseToString(r));
                        ProfileCacheManager.getInstance().putProfile(MainActivity.this, item.otherUserId, profile);
                        int rank = extractNsfwRankFromProfile(profile);
                        if (rank > 0 && rank != item.nsfwRank) {
                            item.nsfwRank = rank;
                            runOnUiThread(() -> {
                                if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                            });
                        } else if (rank == 0) {
                            fetchPhotoRankFromAlbum(item.otherUserId, albumRank -> {
                                if (albumRank > 0 && albumRank != item.nsfwRank) {
                                    item.nsfwRank = albumRank;
                                    runOnUiThread(() -> {
                                        if (chatAdapter != null) chatAdapter.notifyDataSetChanged();
                                    });
                                }
                            });
                        }
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Fetches the user's public photos album to determine the main photo's NSFW
     * rank. This is the most authoritative source because photo.is_sensitive on
     * /rest/users/{id} is not always set for non-explicit-but-rated photos (e.g.
     * PAS-RAP, PRIVÉ SEULEMENT).
     *
     * @param userId   the target user's ID
     * @param callback called on the background thread with the resolved rank (>0 =
     *                 NSFW, 0 = safe)
     */
    private void fetchPhotoRankFromAlbum(String userId, java.util.function.IntConsumer callback) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");
        if (fullCookie.isEmpty() || userId == null || userId.isEmpty())
            return;

        String url = ApiConstants.BASE_URL + "/rest/users/" + userId + "/photos/albums/public";
        Request req = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject json = new JSONObject(NetworkUtils.responseToString(r));
                        int rank = extractRankFromPhotoAlbumJson(json);
                        callback.accept(rank);
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    /**
     * Parses the /rest/users/{id}/photos/albums/public response and returns the
     * NSFW rank of the first (main) photo.
     * Checks photos_selection.photos[0].photo_rating_link first,
     * then items[0].rank as a fallback.
     */
    static int extractRankFromPhotoAlbumJson(JSONObject json) {
        try {
            // Primary: photos_selection.photos[].photo_rating_link
            JSONObject selection = json.optJSONObject("photos_selection");
            if (selection != null) {
                JSONArray photos = selection.optJSONArray("photos");
                if (photos != null && photos.length() > 0) {
                    JSONObject firstPhoto = photos.getJSONObject(0);
                    String ratingLink = firstPhoto.optString("photo_rating_link", "");
                    if (!ratingLink.isEmpty()) {
                        return StringUtils.extractRankFromLink(ratingLink);
                    }
                }
            }
            // Fallback: items[0].p_rank
            JSONArray items = json.optJSONArray("items");
            if (items != null && items.length() > 0) {
                JSONObject first = items.getJSONObject(0);
                // p_rank corresponds to the rating rank from the ratings table
                int pRank = first.optInt("p_rank", -1);
                if (pRank > 0) return pRank;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private ArrayList<SearchOption> parseSelectOptions(Document doc, String selector) {
        return new ArrayList<>(ApiParser.parseSelectOptions(doc, selector));
    }

    private void setupSpinner(AutoCompleteTextView spin, List<SearchOption> list) {
        ArrayAdapter<SearchOption> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                list);
        spin.setAdapter(adapter);
    }



    private void showSearchResultsSheet() {
        if (isTablet && persistentSearchBehavior != null) {
            // Populate recycler in the persistent sheet
            RecyclerView recyclerView = persistentSearchSheet.findViewById(R.id.recycler_results_sheet);
            if (recyclerView != null && recyclerView.getAdapter() != searchAdapter) {
                recyclerView.setLayoutManager(new LinearLayoutManager(this));
                recyclerView.setAdapter(searchAdapter);
            }
            persistentSearchBehavior
                    .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            return;
        }

        if (resultsBottomSheet != null && resultsBottomSheet.isShowing())
            return;

        resultsBottomSheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_search_results, null);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_results_sheet);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(searchAdapter);

        resultsBottomSheet.setContentView(view);
        resultsBottomSheet.show();
    }


    private String getSpinnerSelectedItem(AutoCompleteTextView spin, List<SearchOption> list) {
        String text = spin.getText().toString();
        for (SearchOption opt : list)
            if (opt.label.equals(text))
                return opt.value;
        return "";
    }

    private void initFilterButtons() {
        // Chats
        Chip btnSexChats = findViewById(R.id.btn_sex_filter_chats);
        Chip btnOnlineChats = findViewById(R.id.btn_online_filter_chats);
        Chip btnStatusChats = findViewById(R.id.btn_status_filter_chats);

        if (btnSexChats != null) {
            updateSexFilterButtonText("chats", btnSexChats);
            btnSexChats.setOnClickListener(v -> {
                btnSexChats.setChecked(!filterSexesChats.isEmpty());
                showSexFilterBottomSheet("chats");
            });
        }
        if (btnOnlineChats != null) {
            updateFilterButtonStyle(btnOnlineChats, filterOnlineChats);
            btnOnlineChats.setOnClickListener(v -> {
                filterOnlineChats = !filterOnlineChats;
                getAppPrefs().getRaw().edit()
                        .putBoolean("filter_online_chats", filterOnlineChats).apply();
                updateFilterButtonStyle(btnOnlineChats, filterOnlineChats);
                applyFilter("chats");
            });
        }
        if (btnStatusChats != null) {
            if (filterStatusChats == FILTER_STATUS_ALL) {
                btnStatusChats.setText(R.string.filter_chats_status_all);
                btnStatusChats.setChecked(false);
            } else if (filterStatusChats == FILTER_STATUS_READ) {
                btnStatusChats.setText(R.string.filter_chats_status_read);
                btnStatusChats.setChecked(true);
            } else if (filterStatusChats == FILTER_STATUS_UNREAD) {
                btnStatusChats.setText(R.string.filter_chats_status_unread);
                btnStatusChats.setChecked(true);
            } else if (filterStatusChats == FILTER_STATUS_RECEIVED) {
                btnStatusChats.setText(R.string.filter_chats_status_received);
                btnStatusChats.setChecked(true);
            } else {
                btnStatusChats.setText(R.string.filter_chats_status_delivered);
                btnStatusChats.setChecked(true);
            }
            btnStatusChats.setOnClickListener(v -> {
                btnStatusChats.setChecked(filterStatusChats != FILTER_STATUS_ALL);
                showStatusFilterBottomSheet();
            });
        }

        // Favorites
        Chip btnSexFav = findViewById(R.id.btn_sex_filter_favorites);
        Chip btnOnlineFav = findViewById(R.id.btn_online_filter_favorites);
        Chip btnContactFav = findViewById(R.id.btn_contact_filter);
        Chip btnSortFav = findViewById(R.id.btn_sort_favorites);

        if (btnSexFav != null) {
            updateSexFilterButtonText("favorites", btnSexFav);
            btnSexFav.setOnClickListener(v -> {
                btnSexFav.setChecked(!filterSexesFavorites.isEmpty());
                showSexFilterBottomSheet("favorites");
            });
        }
        if (btnOnlineFav != null) {
            updateFilterButtonStyle(btnOnlineFav, filterOnlineFavorites);
            btnOnlineFav.setOnClickListener(v -> {
                filterOnlineFavorites = !filterOnlineFavorites;
                getAppPrefs().getRaw().edit()
                        .putBoolean("filter_online_favorites", filterOnlineFavorites).apply();
                updateFilterButtonStyle(btnOnlineFav, filterOnlineFavorites);
                applyFilter("favorites");
            });
        }
        if (btnContactFav != null) {
            if (contactTypeFilter == null) {
                btnContactFav.setText(R.string.filter_contact_type_title);
                btnContactFav.setChecked(false);
            } else {
                String filterLabel = "";
                if ("favorite".equals(contactTypeFilter)) {
                    filterLabel = getString(R.string.filter_contact_type_contacts);
                } else if ("notified".equals(contactTypeFilter)) {
                    filterLabel = getString(R.string.filter_contact_type_notified);
                } else if ("bookmark".equals(contactTypeFilter)) {
                    filterLabel = getString(R.string.filter_contact_type_bookmarks);
                }
                btnContactFav.setText(getString(R.string.filter_contact, filterLabel));
                btnContactFav.setChecked(true);
            }
            btnContactFav.setOnClickListener(v -> {
                btnContactFav.setChecked(contactTypeFilter != null);
                showContactTypeFilterBottomSheet();
            });
        }
        if (btnSortFav != null) {
            btnSortFav.setChecked(!"default".equals(favoritesSortOrder));
            if ("timedesc".equals(favoritesSortOrder)) {
                btnSortFav.setText(R.string.sort_favorites_timedesc);
            } else if ("timeasc".equals(favoritesSortOrder)) {
                btnSortFav.setText(R.string.sort_favorites_timeasc);
            } else {
                btnSortFav.setText(R.string.sort_favorites_default);
            }
            btnSortFav.setOnClickListener(v -> {
                btnSortFav.setChecked(!"default".equals(favoritesSortOrder));
                showFavoritesSortBottomSheet();
            });
        }

        // Events
        Chip btnDirectionEv = findViewById(R.id.btn_direction_filter_events);
        Chip btnSexEv = findViewById(R.id.btn_sex_filter_events);
        Chip btnOnlineEv = findViewById(R.id.btn_online_filter_events);

        if (btnDirectionEv != null) {
            btnDirectionEv.setChecked(filterEventsDirection != FILTER_EVENTS_DIRECTION_ALL);
            if (filterEventsDirection == FILTER_EVENTS_DIRECTION_ALL) {
                btnDirectionEv.setText(R.string.filter_events_direction_all);
            } else if (filterEventsDirection == FILTER_EVENTS_DIRECTION_INCOMING) {
                btnDirectionEv.setText(R.string.filter_events_direction_incoming);
            } else {
                btnDirectionEv.setText(R.string.filter_events_direction_outgoing);
            }
            btnDirectionEv.setOnClickListener(v -> {
                btnDirectionEv.setChecked(filterEventsDirection != FILTER_EVENTS_DIRECTION_ALL);
                showEventsDirectionFilterBottomSheet();
            });
        }

        if (btnSexEv != null) {
            updateSexFilterButtonText("events", btnSexEv);
            btnSexEv.setOnClickListener(v -> {
                btnSexEv.setChecked(!filterSexesEvents.isEmpty());
                showSexFilterBottomSheet("events");
            });
        }
        if (btnOnlineEv != null) {
            updateFilterButtonStyle(btnOnlineEv, filterOnlineEvents);
            btnOnlineEv.setOnClickListener(v -> {
                filterOnlineEvents = !filterOnlineEvents;
                getAppPrefs().getRaw().edit()
                        .putBoolean("filter_online_events", filterOnlineEvents).apply();
                updateFilterButtonStyle(btnOnlineEv, filterOnlineEvents);
                applyFilter("events");
            });
        }
    }

    private void showStatusFilterBottomSheet() {
        String[] options = {
                getString(R.string.filter_chats_status_all),
                getString(R.string.filter_chats_status_read),
                getString(R.string.filter_chats_status_unread),
                getString(R.string.filter_chats_status_received),
                getString(R.string.filter_chats_status_delivered)
        };
        int currentSelection = filterStatusChats;
        showOptionsBottomSheet(getString(R.string.filter_chats_status_title), options, currentSelection, position -> {
            filterStatusChats = position;
            Chip btn = findViewById(R.id.btn_status_filter_chats);
            if (position == FILTER_STATUS_ALL) {
                btn.setText(R.string.filter_chats_status_all);
                btn.setChecked(false);
            } else if (position == FILTER_STATUS_READ) {
                btn.setText(R.string.filter_chats_status_read);
                btn.setChecked(true);
            } else if (position == FILTER_STATUS_UNREAD) {
                btn.setText(R.string.filter_chats_status_unread);
                btn.setChecked(true);
            } else if (position == FILTER_STATUS_RECEIVED) {
                btn.setText(R.string.filter_chats_status_received);
                btn.setChecked(true);
            } else {
                btn.setText(R.string.filter_chats_status_delivered);
                btn.setChecked(true);
            }
            applyFilter("chats");
        });
    }

    private void showContactTypeFilterBottomSheet() {
        String[] options = {
                getString(R.string.filter_contact_type_all),
                getString(R.string.filter_contact_type_contacts),
                getString(R.string.filter_contact_type_notified),
                getString(R.string.filter_contact_type_bookmarks)
        };

        String[] filters = { null, "favorite", "notified", "bookmark" };
        int currentSelection = 0;
        for (int i = 0; i < filters.length; i++) {
            if (Objects.equals(filters[i], contactTypeFilter)) {
                currentSelection = i;
                break;
            }
        }

        showOptionsBottomSheet(getString(R.string.filter_contact_type_title), options, currentSelection, position -> {

            contactTypeFilter = filters[position];
            Chip btn = findViewById(R.id.btn_contact_filter);
            btn.setText(getString(R.string.filter_contact, options[position]));

            btn.setChecked(position != 0);
            applyFilter("favorites");
        });
    }

    private void showEventsDirectionFilterBottomSheet() {
        String[] options = {
                getString(R.string.filter_events_direction_all),
                getString(R.string.filter_events_direction_incoming),
                getString(R.string.filter_events_direction_outgoing)
        };

        int currentSelection = filterEventsDirection;

        showOptionsBottomSheet(getString(R.string.filter_events_direction_title), options, currentSelection, position -> {
            filterEventsDirection = position;
            getAppPrefs().getRaw().edit()
                    .putInt("filter_events_direction", filterEventsDirection).apply();

            Chip btn = findViewById(R.id.btn_direction_filter_events);
            if (btn != null) {
                btn.setText(options[position]);
                btn.setChecked(position != FILTER_EVENTS_DIRECTION_ALL);
            }
            ProfileCacheManager.getInstance().markEventsStale();
            fetchEvents();
        });
    }

    private void showOptionsBottomSheet(String title, String[] options, int currentSelection,
            java.util.function.Consumer<Integer> onSelected) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                this);
        BottomSheetUtils.setupFullHeight(dialog);
        View view = getLayoutInflater().inflate(R.layout.layout_filter_options_bottom_sheet, null);
        dialog.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tv_options_title);
        tvTitle.setText(title);

        ChipGroup chipGroup = view.findViewById(R.id.options_chip_group);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            Chip chip = new Chip(this);
            chip.setText(options[i]);
            chip.setCheckable(true);
            chip.setChecked(i == currentSelection);
            chipGroup.addView(chip);
        }

        view.findViewById(R.id.btn_options_apply).setOnClickListener(v -> {
            int selectedIndex = -1;
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                if (((Chip) chipGroup.getChildAt(i)).isChecked()) {
                    selectedIndex = i;
                    break;
                }
            }
            if (selectedIndex != -1) {
                onSelected.accept(selectedIndex);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateFilterButtonStyle(Chip chip, boolean active) {
        chip.setChecked(active);
    }

    private void showSexFilterBottomSheet(String tab) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(
                this);
        BottomSheetUtils.setupFullHeight(dialog);
        View view = getLayoutInflater().inflate(R.layout.layout_filter_bottom_sheet, null);
        dialog.setContentView(view);

        ChipGroup chipGroup = view.findViewById(R.id.filter_sex_chip_group);
        Button btnReset = view.findViewById(R.id.btn_filter_reset);
        Button btnApply = view.findViewById(R.id.btn_filter_apply);

        String[] labels = {
                getString(R.string.sex_all),
                getString(R.string.sex_male),
                getString(R.string.sex_female),
                getString(R.string.sex_couple),
                getString(R.string.sex_travesti),
                getString(R.string.sex_trans),
                getString(R.string.sex_couple_f),
                getString(R.string.sex_couple_h)
        };
        final String labelAll = getString(R.string.sex_all);

        final Set<String> currentSelections = new HashSet<>();
        if ("chats".equals(tab))
            currentSelections.addAll(filterSexesChats);
        else if ("favorites".equals(tab))
            currentSelections.addAll(filterSexesFavorites);
        else if ("events".equals(tab))
            currentSelections.addAll(filterSexesEvents);

        for (String label : labels) {
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setChecked(currentSelections.contains(label) || (currentSelections.isEmpty() && labelAll.equals(label)));

            chipGroup.addView(chip);

            chip.setOnClickListener(v -> {
                if (labelAll.equals(label)) {

                    if (chip.isChecked()) {
                        for (int i = 0; i < chipGroup.getChildCount(); i++) {
                            Chip c = (Chip) chipGroup.getChildAt(i);
                            if (c != chip)
                                c.setChecked(false);
                        }
                    } else {
                        chip.setChecked(true);
                    }
                } else {
                    if (chip.isChecked()) {
                        for (int i = 0; i < chipGroup.getChildCount(); i++) {
                            Chip c = (Chip) chipGroup.getChildAt(i);
                            if (labelAll.equals(c.getText().toString()))

                                c.setChecked(false);
                        }
                    } else {
                        boolean noneChecked = true;
                        for (int i = 0; i < chipGroup.getChildCount(); i++) {
                            if (((Chip) chipGroup.getChildAt(i)).isChecked()) {
                                noneChecked = false;
                                break;
                            }
                        }
                        if (noneChecked) {
                            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                                Chip c = (Chip) chipGroup.getChildAt(i);
                                if (labelAll.equals(c.getText().toString()))

                                    c.setChecked(true);
                            }
                        }
                    }
                }
            });
        }

        btnReset.setOnClickListener(v -> {
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                Chip c = (Chip) chipGroup.getChildAt(i);
                c.setChecked(labelAll.equals(c.getText().toString()));

            }
        });

        btnApply.setOnClickListener(v -> {
            Set<String> selected = new HashSet<>();
            for (int i = 0; i < chipGroup.getChildCount(); i++) {
                Chip c = (Chip) chipGroup.getChildAt(i);
                if (c.isChecked() && !labelAll.equals(c.getText().toString())) {

                    selected.add(c.getText().toString());
                }
            }
            // If all 7 individual sex categories are selected, treat it as "All" (empty set)
            if (selected.size() >= 7) {
                selected.clear();
            }
            SharedPreferences.Editor editor = getAppPrefs().getRaw().edit();
            if ("chats".equals(tab)) {
                filterSexesChats.clear();
                filterSexesChats.addAll(selected);
                editor.putStringSet("filter_sexes_chats", selected);
                updateSexFilterButtonText("chats", findViewById(R.id.btn_sex_filter_chats));
            } else if ("favorites".equals(tab)) {
                filterSexesFavorites.clear();
                filterSexesFavorites.addAll(selected);
                editor.putStringSet("filter_sexes_favorites", selected);
                updateSexFilterButtonText("favorites", findViewById(R.id.btn_sex_filter_favorites));
            } else if ("events".equals(tab)) {
                filterSexesEvents.clear();
                filterSexesEvents.addAll(selected);
                editor.putStringSet("filter_sexes_events", selected);
                updateSexFilterButtonText("events", findViewById(R.id.btn_sex_filter_events));
            }
            editor.apply();
            applyFilter(tab);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSexFilterButtonText(String tab, Chip btn) {
        Set<String> sel;
        if ("chats".equals(tab))
            sel = filterSexesChats;
        else if ("favorites".equals(tab))
            sel = filterSexesFavorites;
        else
            sel = filterSexesEvents;

        if (sel.isEmpty() || sel.size() >= 7) {
            btn.setText(R.string.sex_filter_label);

            btn.setChecked(false);
        } else if (sel.size() == 1) {
            btn.setText(sel.iterator().next());
            btn.setChecked(true);
        } else {
            btn.setText(getString(R.string.sex_filter_label_count, sel.size()));

            btn.setChecked(true);
        }
    }

    private void applyFilter(String tab) {
        if ("favorites".equals(tab))
            applyContactsFilter();
        else if ("events".equals(tab))
            applyEventsFilter();
        else
            applyChatsFilter();
    }

    private void applyChatsFilter() {
        List<ChatAdapter.ChatItem> backingList;
        if (currentChatTab == TAB_ARCHIVED)
            backingList = allArchivedItems;
        else if (currentChatTab == TAB_NEW)
            backingList = allNewItems;
        else
            backingList = allActiveItems;

        List<ChatAdapter.ChatItem> filtered = new ArrayList<>();
        boolean containsLoadMore = false;

        for (ChatAdapter.ChatItem it : backingList) {
            if (it.type == ChatAdapter.TYPE_LOAD_MORE) {
                containsLoadMore = true;
            } else if (it.type == ChatAdapter.TYPE_CHAT) {
                if (filterOnlineChats && !it.isOnline)
                    continue;
                if (filterStatusChats == FILTER_STATUS_READ && it.isUnread)
                    continue;
                if (filterStatusChats == FILTER_STATUS_UNREAD && !it.isUnread)
                    continue;
                boolean isChecked = it.otherReadUntil != null && !it.otherReadUntil.isEmpty() && it.lastPosted != null
                        && !it.lastPosted.isEmpty() && !it.isUnread && it.otherReadUntil.compareTo(it.lastPosted) >= 0;
                if (filterStatusChats == FILTER_STATUS_DELIVERED && isChecked)
                    continue;
                if (filterStatusChats == FILTER_STATUS_RECEIVED && !isChecked)
                    continue;
                if (!filterSexesChats.isEmpty() && !matchesAnySex(it.sexIconUrl, filterSexesChats))
                    continue;
                it.isPinned = pinnedConvoLinks.contains(it.conversationLink);
                filtered.add(it);
            }
        }

        // Sort pinned items to top
        List<ChatAdapter.ChatItem> sorted = new ArrayList<>();
        List<ChatAdapter.ChatItem> pinned = filtered.stream().filter(i -> i.isPinned).collect(Collectors.toList());
        List<ChatAdapter.ChatItem> unpinned = filtered.stream().filter(i -> !i.isPinned).collect(Collectors.toList());
        sorted.addAll(pinned);
        sorted.addAll(unpinned);

        // Save scroll position using a stable anchor item (matched by conversationLink) to prevent jumping
        String anchorConvoLink = null;
        int topOffset = 0;
        RecyclerView.LayoutManager layoutManager = recyclerChats.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager) {
            int lastVisible = ((LinearLayoutManager) layoutManager).findLastVisibleItemPosition();
            for (int i = lastVisible; i >= 0; i--) {
                if (i < chatItems.size() && chatItems.get(i).type == ChatAdapter.TYPE_CHAT) {
                    ChatAdapter.ChatItem anchorItem = chatItems.get(i);
                    anchorConvoLink = anchorItem.conversationLink;
                    View anchorView = layoutManager.findViewByPosition(i);
                    if (anchorView != null) {
                        topOffset = anchorView.getTop() - recyclerChats.getPaddingTop();
                    }
                    break;
                }
            }
        }

        chatItems.clear();
        chatItems.addAll(sorted);
        if (containsLoadMore) {
            chatItems.add(new ChatAdapter.ChatItem(ChatAdapter.TYPE_LOAD_MORE));
        }
        chatAdapter.notifyDataSetChanged();

        // Find the new index of the stable anchor item in the updated list
        int newAnchorPosition = -1;
        if (anchorConvoLink != null) {
            for (int i = 0; i < chatItems.size(); i++) {
                if (Objects.equals(chatItems.get(i).conversationLink, anchorConvoLink)) {
                    newAnchorPosition = i;
                    break;
                }
            }
        }

        // Restore scroll position to the new index of the stable anchor item
        if (layoutManager instanceof LinearLayoutManager && newAnchorPosition != -1) {
            final int finalPos = newAnchorPosition;
            final int finalOffset = topOffset;
            final String finalLink = anchorConvoLink;
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(finalPos, finalOffset);
            
            // Post fallback to catch delayed/asynchronous layout passes
            recyclerChats.post(() -> {
                if (recyclerChats.getLayoutManager() instanceof LinearLayoutManager) {
                    int currentPos = -1;
                    for (int i = 0; i < chatItems.size(); i++) {
                        if (Objects.equals(chatItems.get(i).conversationLink, finalLink)) {
                            currentPos = i;
                            break;
                        }
                    }
                    if (currentPos != -1) {
                        ((LinearLayoutManager) recyclerChats.getLayoutManager()).scrollToPositionWithOffset(currentPos, finalOffset);
                    }
                }
            });
        }
    }

    private void applyEventsFilter() {
        List<EventAdapter.EventItem> filtered = new ArrayList<>();
        for (EventAdapter.EventItem it : allEventItems) {
            if (filterOnlineEvents && !it.isOnline)
                continue;
            if (!filterSexesEvents.isEmpty() && !matchesAnySex(it.sexIconUrl, filterSexesEvents))
                continue;
            filtered.add(it);
        }
        eventItems.clear();
        Collections.sort(filtered, (e1, e2) -> Long.compare(e2.sortTimestamp, e1.sortTimestamp));
        eventItems.addAll(filtered);
        eventAdapter.notifyDataSetChanged();
    }

    private void applyContactsFilter() {
        List<FavoriteAdapter.FavoriteItem> filtered = new ArrayList<>();
        for (FavoriteAdapter.FavoriteItem it : allFavoriteItems) {
            if ("favorite".equals(contactTypeFilter) && !it.isFavorite)
                continue;
            if ("notified".equals(contactTypeFilter) && !it.isNotified)
                continue;
            if ("bookmark".equals(contactTypeFilter) && !it.isBookmarked)
                continue;
            if (filterOnlineFavorites && !it.isOnline)
                continue;
            if (!filterSexesFavorites.isEmpty() && !matchesAnySex(it.sexIconUrl, filterSexesFavorites))
                continue;
            filtered.add(it);
        }

        // Apply sorting according to last connection if VIP/Pro is active
        if (isMyVip && !"default".equals(favoritesSortOrder)) {
            Collections.sort(filtered, (item1, item2) -> {
                long t1 = parseLastConnectionToTimestamp(item1.lastConnection, item1.isOnline);
                long t2 = parseLastConnectionToTimestamp(item2.lastConnection, item2.isOnline);
                if (t1 == 0 && t2 == 0) return 0;
                if (t1 == 0) return 1;  // Put 0 values at the bottom
                if (t2 == 0) return -1; // Put 0 values at the bottom
                
                if ("timedesc".equals(favoritesSortOrder)) {
                    return Long.compare(t2, t1); // newest first (descending)
                } else {
                    return Long.compare(t1, t2); // oldest first (ascending)
                }
            });
        }

        favoriteItems.clear();
        favoriteItems.addAll(filtered);
        favoriteAdapter.notifyDataSetChanged();
    }

    private void showFavoritesSortBottomSheet() {
        if (!isMyVip) {
            Toast.makeText(this, R.string.vip_required_sort, Toast.LENGTH_LONG).show();
            return;
        }

        String[] options = {
                getString(R.string.sort_favorites_default),
                getString(R.string.sort_favorites_timedesc),
                getString(R.string.sort_favorites_timeasc)
        };

        String[] sortOrders = { "default", "timedesc", "timeasc" };
        int currentSelection = 0;
        for (int i = 0; i < sortOrders.length; i++) {
            if (Objects.equals(sortOrders[i], favoritesSortOrder)) {
                currentSelection = i;
                break;
            }
        }

        showOptionsBottomSheet(getString(R.string.sort_by), options, currentSelection, position -> {
            favoritesSortOrder = sortOrders[position];
            getAppPrefs().getRaw()
                    .edit()
                    .putString("favorites_sort_order_" + myUserId, favoritesSortOrder)
                    .apply();
            Chip btn = findViewById(R.id.btn_sort_favorites);
            if (btn != null) {
                btn.setText(options[position]);
                btn.setChecked(position != 0);
            }
            applyContactsFilter();
        });
    }

    private long parseLastConnectionToTimestamp(String value, boolean isOnline) {
        if (isOnline) {
            return System.currentTimeMillis();
        }
        if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("need_vip")) {
            return 0; // indicates "no time shown"
        }

        String str = value.trim().toLowerCase();
        long now = System.currentTimeMillis();

        // Handle precise French connection states in order of modern/scraped presence
        if (str.contains("présentement en ligne") || str.equals("membre présentement en ligne")) {
            return now;
        }
        if (str.equals("ce soir")) {
            return now - 2 * 60 * 60 * 1000L;
        }
        if (str.equals("cet après-midi") || str.equals("cet apres-midi")) {
            return now - 6 * 60 * 60 * 1000L;
        }
        if (str.equals("ce matin")) {
            return now - 12 * 60 * 60 * 1000L;
        }
        if (str.equals("cette nuit")) {
            return now - 18 * 60 * 60 * 1000L;
        }
        if (str.equals("hier soir")) {
            return now - 24 * 60 * 60 * 1000L;
        }
        if (str.equals("la nuit passée") || str.equals("la nuit passee")) {
            return now - 26 * 60 * 60 * 1000L;
        }
        if (str.equals("hier après-midi") || str.equals("hier apres-midi")) {
            return now - 30 * 60 * 60 * 1000L;
        }
        if (str.equals("hier matin")) {
            return now - 36 * 60 * 60 * 1000L;
        }

        // Relative time like "Il y a une semaine", "Il y a un mois", etc.
        if (str.contains("il y a")) {
            if (str.contains("une semaine")) {
                return now - 7 * 24 * 60 * 60 * 1000L;
            }
            if (str.contains("plus d'un ans") || str.contains("plus d'un an")) {
                return now - 500 * 24 * 60 * 60 * 1000L;
            }
            if (str.contains("un an")) {
                return now - 365 * 24 * 60 * 60 * 1000L;
            }
            if (str.contains("plus d'un mois")) {
                return now - 45 * 24 * 60 * 60 * 1000L;
            }
            if (str.contains("un mois")) {
                return now - 30 * 24 * 60 * 60 * 1000L;
            }
            
            // Weekly checks (e.g. "Il y a 2 semaines")
            if (str.contains("semaine")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                if (m.find()) {
                    int val = Integer.parseInt(m.group());
                    return now - val * 7 * 24 * 60 * 60 * 1000L;
                }
            }
            
            // Monthly checks (e.g. "Il y a 2 mois")
            if (str.contains("mois")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                if (m.find()) {
                    int val = Integer.parseInt(m.group());
                    return now - val * 30 * 24 * 60 * 60 * 1000L;
                }
            }
            
            // Yearly checks (e.g. "Il y a 2 ans")
            if (str.contains("ans") || str.contains("an")) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
                if (m.find()) {
                    int val = Integer.parseInt(m.group());
                    return now - val * 365 * 24 * 60 * 60 * 1000L;
                }
            }
        }
        
        // Handle common online/now terms
        if (str.contains("en ligne") || str.contains("online") || str.contains("maintenant") || str.contains("now") || str.contains("en ce moment")) {
            return System.currentTimeMillis();
        }

        // Handle relative time like "Il y a 5 minutes" or "5 minutes ago"
        if (str.contains("il y a") || str.contains("ago")) {
            // extract digits
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(str);
            if (m.find()) {
                int val = Integer.parseInt(m.group());
                if (str.contains("minute")) {
                    return now - val * 60 * 1000L;
                } else if (str.contains("heure") || str.contains("hour")) {
                    return now - val * 60 * 60 * 1000L;
                } else if (str.contains("jour") || str.contains("day")) {
                    return now - val * 24 * 60 * 60 * 1000L;
                }
            }
        }

        return 0; // fallback to "no time shown"
    }

    private boolean matchesAnySex(String sexIconUrl, java.util.Collection<String> activeLabels) {
        return FilterUtils.matchesAnySex(sexIconUrl, activeLabels);
    }

    // ─────────────────────────── Search Tabs ────────────────────────────



    // ─────────────────────────── Simple Search ───────────────────────────

    private void setupUnifiedSearch() {
        View root = findViewById(R.id.include_unified_search);
        if (root == null) return;

        switchAdvancedSearch = findViewById(R.id.switch_advanced_search);
        containerAdvancedFields = root.findViewById(R.id.container_advanced_fields);
        containerMultiLocation = root.findViewById(R.id.container_multi_location);

        // Bind core fields
        cgSeekUnified = root.findViewById(R.id.cg_seek_unified);
        cgWantedUnified = root.findViewById(R.id.cg_wanted_unified);
        cgOrientUnified = root.findViewById(R.id.cg_orient_unified);
        etPseudoUnified = root.findViewById(R.id.et_pseudo_unified);
        sliderAgeUnified = root.findViewById(R.id.slider_age_unified);
        tvAgeRangeValueUnified = root.findViewById(R.id.tv_age_range_value_unified);
        if (sliderAgeUnified != null) {
            sliderAgeUnified.setValueFrom(18f);
            sliderAgeUnified.setValueTo(99f);
            sliderAgeUnified.setValues(18f, 99f);
            if (tvAgeRangeValueUnified != null) {
                tvAgeRangeValueUnified.setText("18 - 99");
            }
            sliderAgeUnified.addOnChangeListener((slider, value, fromUser) -> {
                if (tvAgeRangeValueUnified != null) {
                    List<Float> values = slider.getValues();
                    tvAgeRangeValueUnified.setText(Math.round(values.get(0)) + " - " + Math.round(values.get(1)));
                }
            });
        }
        chkOnlineUnified = root.findViewById(R.id.chk_online_unified);
        chkExcludeChattedUnified = root.findViewById(R.id.chk_exclude_chatted_unified);
        chkPhotoUnified = root.findViewById(R.id.chk_photo_unified);
        chkWebcamUnified = root.findViewById(R.id.chk_webcam_unified);
        chkSpeedMeetingUnified = root.findViewById(R.id.chk_speed_meeting_unified);
        chkMyRegionUnified = root.findViewById(R.id.chk_my_region_unified);
        rgOrderByUnified = root.findViewById(R.id.rg_orderby_unified);
        if (rgOrderByUnified != null) rgOrderByUnified.check(R.id.rb_visit_unified);

        // Bind advanced fields
        cgStatusUnified = root.findViewById(R.id.cg_status_unified);
        cgRelCherUnified = root.findViewById(R.id.cg_relcher_unified);
        cgSmokerUnified = root.findViewById(R.id.cg_smoker_unified);
        cgZodiacUnified = root.findViewById(R.id.cg_zodiac_unified);
        cgAvailUnified = root.findViewById(R.id.cg_avail_unified);
        sliderWeightUnified = root.findViewById(R.id.slider_weight_unified);
        sliderHeightUnified = root.findViewById(R.id.slider_height_unified);
        cgEthnicUnified = root.findViewById(R.id.cg_ethnic_unified);

        // Bind location fields
        spinnerCountryUnified = root.findViewById(R.id.spinner_country_unified);
        spinnerProvinceUnified = root.findViewById(R.id.spinner_province_unified);
        spinnerRegionUnified = root.findViewById(R.id.spinner_region_unified);
        tilProvinceUnified = root.findViewById(R.id.til_province_unified);
        tilRegionUnified = root.findViewById(R.id.til_region_unified);
        btnAddLocationUnified = root.findViewById(R.id.btn_add_location_unified);
        if (btnAddLocationUnified != null) btnAddLocationUnified.setOnClickListener(v -> addSelectedLocationUnified());
        cgSelectedLocationsUnified = root.findViewById(R.id.cg_selected_locations_unified);
        btnAutoLocateUnified = root.findViewById(R.id.btn_auto_locate_unified);

        layoutFantasiesUnified = root.findViewById(R.id.layout_fantasies_unified);
        layoutRelationalConfigUnified = root.findViewById(R.id.layout_relational_config_unified);
        layoutAvailabilityUnified = root.findViewById(R.id.layout_availability_unified);

        // Bind fantasies
        btnPickFantasiesUnified = root.findViewById(R.id.btn_pick_fantasies_unified);
        btnClearFantasiesUnified = root.findViewById(R.id.btn_clear_fantasies_unified);
        tvSelectedFantasiesUnified = root.findViewById(R.id.tv_selected_fantasies_unified);

        // Bind saved searches
        cgSavedSearchesUnified = root.findViewById(R.id.cg_saved_searches_unified);
        tvSavedSearchesTitleUnified = root.findViewById(R.id.tv_saved_searches_title_unified);
        scrollSavedSearchesUnified = root.findViewById(R.id.scroll_saved_searches_unified);
        btnResetSearchUnified = root.findViewById(R.id.btn_reset_search_unified);
        btnSaveSearchUnified = root.findViewById(R.id.btn_save_search_unified);
        btnExecuteUnified = root.findViewById(R.id.btn_execute_unified);

        // Toggle logic
        if (switchAdvancedSearch != null) {
            switchAdvancedSearch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (containerAdvancedFields != null) containerAdvancedFields.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (containerMultiLocation != null) containerMultiLocation.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (layoutFantasiesUnified != null) layoutFantasiesUnified.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (layoutRelationalConfigUnified != null) layoutRelationalConfigUnified.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (layoutAvailabilityUnified != null) layoutAvailabilityUnified.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                
                // Toggle multi-selection support
                if (cgSeekUnified != null) cgSeekUnified.setSingleSelection(!isChecked);
                if (cgWantedUnified != null) cgWantedUnified.setSingleSelection(!isChecked);
                if (cgOrientUnified != null) cgOrientUnified.setSingleSelection(!isChecked);
                
                SearchSettingsManager.saveAdvancedToggleState(this, isChecked);
            });
            boolean isAdvanced = SearchSettingsManager.loadAdvancedToggleState(this);
            switchAdvancedSearch.setChecked(isAdvanced);
            if (containerAdvancedFields != null) containerAdvancedFields.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            if (containerMultiLocation != null) containerMultiLocation.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            if (layoutFantasiesUnified != null) layoutFantasiesUnified.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            if (layoutRelationalConfigUnified != null) layoutRelationalConfigUnified.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            if (layoutAvailabilityUnified != null) layoutAvailabilityUnified.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            
            if (cgSeekUnified != null) cgSeekUnified.setSingleSelection(!isAdvanced);
            if (cgWantedUnified != null) cgWantedUnified.setSingleSelection(!isAdvanced);
            if (cgOrientUnified != null) cgOrientUnified.setSingleSelection(!isAdvanced);
        }

        // Initialize adapters and fetch data
        setupLocationSpinnersUnified();
        fetchUnifiedSearchDropdowns();
        
        fetchEthnicGroupsUnified();
        fetchDetailedSearchOptionsUnified();
        fetchWeightsUnified();
        fetchHeightsUnified();

        if (btnAutoLocateUnified != null)
            btnAutoLocateUnified.setOnClickListener(v -> startLocationDetectionUnified());
        
        if (btnSaveSearchUnified != null)
            btnSaveSearchUnified.setOnClickListener(v -> saveCurrentUnifiedSearch());

        if (btnResetSearchUnified != null)
            btnResetSearchUnified.setOnClickListener(v -> resetUnifiedSearchToDefault());
        
        if (btnExecuteUnified != null) {
            btnExecuteUnified.setOnClickListener(v -> {
                searchItems.clear();
                if (searchAdapter != null) searchAdapter.notifyDataSetChanged();
                currentSearchPageUnified = 1;
                showSearchResultsSheet();
                performSearchUnified(1);
            });
        }

        populateSavedSearchChipsUnified();
    }

    private void resetUnifiedSearchToDefault() {
        // Uncheck all ChipGroups
        restoreChips(cgSeekUnified, null);
        restoreChips(cgWantedUnified, null);
        restoreChips(cgOrientUnified, null);
        restoreChips(cgStatusUnified, null);
        restoreChips(cgRelCherUnified, null);
        restoreChips(cgAvailUnified, null);
        restoreChips(cgSmokerUnified, null);
        restoreChips(cgZodiacUnified, null);
        if (cgEthnicUnified != null) {
            cgEthnicUnified.setTag(null);
            restoreChips(cgEthnicUnified, null);
        }

        // Reset text field
        if (etPseudoUnified != null) etPseudoUnified.setText("");

        // Reset age slider
        if (sliderAgeUnified != null) {
            sliderAgeUnified.setValues(18f, 99f);
            if (tvAgeRangeValueUnified != null) {
                tvAgeRangeValueUnified.setText("18 - 99");
            }
        }

        // Reset checkboxes
        if (chkOnlineUnified != null) chkOnlineUnified.setChecked(false);
        if (chkExcludeChattedUnified != null) chkExcludeChattedUnified.setChecked(false);
        if (chkPhotoUnified != null) chkPhotoUnified.setChecked(false);
        if (chkWebcamUnified != null) chkWebcamUnified.setChecked(false);
        if (chkSpeedMeetingUnified != null) chkSpeedMeetingUnified.setChecked(false);
        if (chkMyRegionUnified != null) chkMyRegionUnified.setChecked(false);

        // Reset order by
        if (rgOrderByUnified != null) {
            rgOrderByUnified.check(R.id.rb_visit_unified);
        }

        // Reset location selection
        selectedCountryUnified = null;
        selectedProvinceUnified = null;
        selectedRegionUnified = null;
        if (spinnerCountryUnified != null) spinnerCountryUnified.setText("", false);
        if (spinnerProvinceUnified != null) spinnerProvinceUnified.setText("", false);
        if (spinnerRegionUnified != null) spinnerRegionUnified.setText("", false);
        if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.GONE);
        if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);

        // Reset multi locations
        selectedLocationsUnified.clear();
        if (cgSelectedLocationsUnified != null) {
            cgSelectedLocationsUnified.removeAllViews();
        }

        // Reset fantasies
        clearFantasiesUnified();

        // Reset advanced sliders
        if (sliderWeightUnified != null) {
            sliderWeightUnified.setValues(sliderWeightUnified.getValueFrom(), sliderWeightUnified.getValueTo());
        }
        if (sliderHeightUnified != null) {
            sliderHeightUnified.setValues(sliderHeightUnified.getValueFrom(), sliderHeightUnified.getValueTo());
        }
        
        Toast.makeText(this, R.string.search_reset_success, Toast.LENGTH_SHORT).show();
    }

    private void fetchUnifiedSearchDropdowns() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/search/0")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    String html = NetworkUtils.responseToString(r);
                    Document doc = Jsoup.parse(html);

                    runOnUiThread(() -> {
                        populateChipsFromSelectUnified(doc, "select[name=seek]", cgSeekUnified);
                        populateChipsFromSelectUnified(doc, "select[name=wanted]", cgWantedUnified);
                        populateChipsFromSelectUnified(doc, "select[name=orient]", cgOrientUnified);
                    });
                }
            }
        });
    }

    private void populateUnifiedDropdown(Document doc, String selector,
                                         AutoCompleteTextView spinner, @SuppressWarnings("SameParameterValue") String tagKey) {
        if (spinner == null) return;
        Element select = doc.selectFirst(selector);
        if (select == null) return;

        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (Element opt : select.select("option")) {
            labels.add(opt.text());
            values.add(opt.attr("value"));
        }
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labels);
        spinner.setAdapter(adapter);
        if (!labels.isEmpty()) {
            // Only set default if no tag is set yet (to avoid overwriting restoration)
            if (spinner.getTag() == null) {
                spinner.setText(labels.get(0), false);
                spinner.setTag(values.get(0));
            }
        }
        spinner.setOnItemClickListener((parent, view, position, id) -> {
            spinner.setTag(values.get(position));
        });
    }

    private void setupLocationSpinnersUnified() {
        if (spinnerCountryUnified == null || countriesLoadedUnified) return;
        fetchRegionsListUnified("/rest/regions", list -> {
            countriesLoadedUnified = true;
            countriesUnified.clear();
            countriesUnified.addAll(list);
            ArrayAdapter<RegionDet> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, countriesUnified);
            spinnerCountryUnified.setAdapter(adapter);
            spinnerCountryUnified.setOnItemClickListener((parent, view, position, id) -> {
                selectedCountryUnified = (RegionDet) parent.getAdapter().getItem(position);
                selectedProvinceUnified = null;
                selectedRegionUnified   = null;
                spinnerProvinceUnified.setText("", false);
                spinnerRegionUnified.setText("", false);
                if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.GONE);
                if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                fetchProvincesUnified(selectedCountryUnified.link);
            });
        });
    }

    private void fetchProvincesUnified(String link) {
        if (link == null || link.isEmpty()) return;
        fetchRegionsListUnified(link, list -> {
            provincesUnified.clear();
            provincesUnified.addAll(list);
            if (provincesUnified.isEmpty()) return;
            ArrayAdapter<RegionDet> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, provincesUnified);
            spinnerProvinceUnified.setAdapter(adapter);
            if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.VISIBLE);
            spinnerProvinceUnified.setOnItemClickListener((parent, view, position, id) -> {
                selectedProvinceUnified = (RegionDet) parent.getAdapter().getItem(position);
                selectedRegionUnified   = null;
                spinnerRegionUnified.setText("", false);
                if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                fetchRegionsUnified(selectedProvinceUnified.link);
            });
        });
    }

    private void fetchRegionsUnified(String link) {
        if (link == null || link.isEmpty()) return;
        fetchRegionsListUnified(link, list -> {
            regionsUnified.clear();
            regionsUnified.addAll(list);
            if (regionsUnified.isEmpty()) return;
            ArrayAdapter<RegionDet> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, regionsUnified);
            spinnerRegionUnified.setAdapter(adapter);
            if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.VISIBLE);
            spinnerRegionUnified.setOnItemClickListener((parent, view, position, id) ->
                    selectedRegionUnified = (RegionDet) parent.getAdapter().getItem(position));
        });
    }

    private void fetchRegionsListUnified(String link, Consumer<List<RegionDet>> callback) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + link)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "Failed to fetch regions: " + link, e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String jsonStr = r.body().string();
                        JSONObject json = new JSONObject(jsonStr);
                        JSONObject subRegions = json.optJSONObject("sub_regions");
                        if (subRegions != null) {
                            JSONArray listArray = subRegions.optJSONArray("list");
                            if (listArray != null) {
                                List<RegionDet> list = new ArrayList<>();
                                for (int i = 0; i < listArray.length(); i++) {
                                    JSONObject obj = listArray.getJSONObject(i);
                                    list.add(new RegionDet(obj.getString("name"), obj.getString("region_link")));
                                }
                                runOnUiThread(() -> callback.accept(list));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing regions", e);
                }
            }
        });
    }

    private void performSearchUnified(int page) {
        if (isSearchingUnified) return;
        isSearchingUnified = true;
        setSearchLoading(true);

        final boolean excludeChatted = chkExcludeChattedUnified != null && chkExcludeChattedUnified.isChecked();

        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid       = secure.getString(ApiConstants.KEY_SUID, "");

        boolean isAdvanced = switchAdvancedSearch != null && switchAdvancedSearch.isChecked();

        // Core fields (Common)
        List<String> seekTags = getSelectedChipTags(cgSeekUnified);
        List<String> wantedTags = getSelectedChipTags(cgWantedUnified);
        List<String> orientTags = getSelectedChipTags(cgOrientUnified);

        String seek   = !seekTags.isEmpty() ? seekTags.get(0) : "";
        String wanted = !wantedTags.isEmpty() ? wantedTags.get(0) : "";
        String orient = !orientTags.isEmpty() ? orientTags.get(0) : "";
        String pseudo = etPseudoUnified    != null ? etPseudoUnified.getText().toString().trim()    : "";
        String ageMin = "18";
        String ageMax = "99";
        if (sliderAgeUnified != null) {
            List<Float> values = sliderAgeUnified.getValues();
            ageMin = String.valueOf(Math.round(values.get(0)));
            ageMax = String.valueOf(Math.round(values.get(1)));
        }

        boolean online      = chkOnlineUnified       != null && chkOnlineUnified.isChecked();
        boolean hasPhoto    = chkPhotoUnified         != null && chkPhotoUnified.isChecked();
        boolean hasWebcam   = chkWebcamUnified        != null && chkWebcamUnified.isChecked();
        boolean speedMeeting= chkSpeedMeetingUnified != null && chkSpeedMeetingUnified.isChecked();
        boolean myRegion    = chkMyRegionUnified      != null && chkMyRegionUnified.isChecked();

        String orderBy = "1";
        if (rgOrderByUnified != null) {
            int checked = rgOrderByUnified.getCheckedRadioButtonId();
            if (checked == R.id.rb_reg_unified)   orderBy = "2";
            else if (checked == R.id.rb_match_unified) orderBy = "3";
        }

        Request request;
        if (isAdvanced) {
            // Advanced Search Logic
            String ethnic = "";
            if (cgEthnicUnified != null) {
                List<String> tags = getSelectedChipTags(cgEthnicUnified);
                if (!tags.isEmpty()) ethnic = tags.get(0);
            }

            // Convert slider index → API option ID (same logic as legacy Det search)
            String weightD = null, weightF = null, heightD = null, heightF = null;
            if (sliderWeightUnified != null && !weightOptionsUnified.isEmpty()) {
                int idxD = (int) sliderWeightUnified.getValues().get(0).floatValue();
                int idxF = (int) sliderWeightUnified.getValues().get(1).floatValue();
                idxD = Math.max(0, Math.min(idxD, weightOptionsUnified.size() - 1));
                idxF = Math.max(0, Math.min(idxF, weightOptionsUnified.size() - 1));
                if (idxD == 0 && idxF == weightOptionsUnified.size() - 1) {
                    weightD = null;
                    weightF = null;
                } else {
                    weightD = String.valueOf(weightOptionsUnified.get(idxD).id);
                    weightF = String.valueOf(weightOptionsUnified.get(idxF).id);
                }
            }
            if (sliderHeightUnified != null && !heightOptionsUnified.isEmpty()) {
                int idxD = (int) sliderHeightUnified.getValues().get(0).floatValue();
                int idxF = (int) sliderHeightUnified.getValues().get(1).floatValue();
                idxD = Math.max(0, Math.min(idxD, heightOptionsUnified.size() - 1));
                idxF = Math.max(0, Math.min(idxF, heightOptionsUnified.size() - 1));
                if (idxD == 0 && idxF == heightOptionsUnified.size() - 1) {
                    heightD = null;
                    heightF = null;
                } else {
                    heightD = String.valueOf(heightOptionsUnified.get(idxD).id);
                    heightF = String.valueOf(heightOptionsUnified.get(idxF).id);
                }
            }

            SearchSettingsManager.SavedSettings settings = getUnifiedSearchSettings();
            request = SearchApiUtils.buildDetailedSearchRequest(
                    settings, page, fullCookie, suid, orderBy, ethnic,
                    weightD, weightF, heightD, heightF);
        } else {
            // Simple Search Logic
            String location = "";
            if (selectedCountryUnified != null) {
                StringBuilder sb = new StringBuilder(selectedCountryUnified.id);
                if (selectedProvinceUnified != null) {
                    sb.append(",").append(selectedProvinceUnified.id);
                    if (selectedRegionUnified != null) sb.append(",").append(selectedRegionUnified.id);
                }
                location = sb.toString();
            }

            request = SearchApiUtils.buildSimpleSearchRequest(
                    seek, wanted, orient, pseudo, ageMin, ageMax,
                    online, hasPhoto, myRegion, speedMeeting,
                    location, orderBy, page, fullCookie, suid);
        }

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                isSearchingUnified = false;
                runOnUiThread(() -> {
                    setSearchLoading(false);
                    Toast.makeText(MainActivity.this, R.string.search_failed, Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    isSearchingUnified = false;
                    runOnUiThread(() -> setSearchLoading(false));
                    if (!r.isSuccessful() || r.body() == null) return;
                    String html = NetworkUtils.responseToString(r);
                    List<SearchAdapter.SearchItem> newItems = parseSearchResults(html);

                    if (excludeChatted) {
                        android.content.SharedPreferences convoPrefs = getSharedPreferences("ConversationLinks", MODE_PRIVATE);
                        java.util.Map<String, ?> chattedMap = convoPrefs.getAll();
                        if (!chattedMap.isEmpty()) {
                            java.util.Iterator<SearchAdapter.SearchItem> iterator = newItems.iterator();
                            while (iterator.hasNext()) {
                                SearchAdapter.SearchItem item = iterator.next();
                                if (item.userId != null && chattedMap.containsKey(item.userId)) {
                                    iterator.remove();
                                }
                            }
                        }
                    }

                    runOnUiThread(() -> {
                        if (newItems.isEmpty() && page == 1)
                            Toast.makeText(MainActivity.this, R.string.no_results_found, Toast.LENGTH_SHORT).show();

                        currentSearchPageUnified = page;
                        if (!searchItems.isEmpty()
                                && searchItems.get(searchItems.size() - 1).type == SearchAdapter.TYPE_LOAD_MORE) {
                            searchItems.remove(searchItems.size() - 1);
                        }
                        searchItems.addAll(newItems);
                        if (!newItems.isEmpty()) {
                            SearchAdapter.SearchItem lm = new SearchAdapter.SearchItem();
                            lm.type = SearchAdapter.TYPE_LOAD_MORE;
                            searchItems.add(lm);
                        }
                        searchAdapter.notifyDataSetChanged();

                        for (SearchAdapter.SearchItem item : newItems) {
                            if (item.userId != null && !item.userId.isEmpty()) {
                                fetchProfileDetailsForSearch(item);
                            }
                        }
                    });
                }
            }
        });
    }

    private void saveCurrentUnifiedSearch() {
        final SearchSettingsManager.SavedSettings settings = getUnifiedSearchSettings();

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint(R.string.search_name_hint);
        input.setPadding(48, 32, 48, 32);

        new AlertDialog.Builder(this)
                .setTitle(R.string.save_search_title)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        SearchSettingsManager.saveNamedSearch(MainActivity.this, name, settings, false);
                        populateSavedSearchChipsUnified();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private SearchSettingsManager.SavedSettings getUnifiedSearchSettings() {
        SearchSettingsManager.SavedSettings s = new SearchSettingsManager.SavedSettings();
        
        // Common fields
        s.isAdvancedSearch = switchAdvancedSearch != null && switchAdvancedSearch.isChecked();
        List<String> seekTags = getSelectedChipTags(cgSeekUnified);
        List<String> wantedTags = getSelectedChipTags(cgWantedUnified);
        List<String> orientTags = getSelectedChipTags(cgOrientUnified);

        s.seekSimple = !seekTags.isEmpty() ? seekTags.get(0) : "";
        s.wantedSimple = !wantedTags.isEmpty() ? wantedTags.get(0) : "";
        s.orientSimple = !orientTags.isEmpty() ? orientTags.get(0) : "";
        
        // Detailed lists will be overwritten if in advanced mode
        s.seekDet = new ArrayList<>(seekTags);
        s.wantedDet = new ArrayList<>(wantedTags);
        s.orientDet = new ArrayList<>(orientTags);
        s.pseudoSimple = (etPseudoUnified != null) ? etPseudoUnified.getText().toString().trim() : "";
        s.ageMinSimple = "18";
        s.ageMaxSimple = "99";
        if (sliderAgeUnified != null) {
            List<Float> values = sliderAgeUnified.getValues();
            s.ageMinSimple = String.valueOf(Math.round(values.get(0)));
            s.ageMaxSimple = String.valueOf(Math.round(values.get(1)));
        }
        s.ageMinDet = s.ageMinSimple;
        s.ageMaxDet = s.ageMaxSimple;
        s.onlineSimple = (chkOnlineUnified != null && chkOnlineUnified.isChecked());
        s.excludeChatted = (chkExcludeChattedUnified != null && chkExcludeChattedUnified.isChecked());
        s.photoSimple = (chkPhotoUnified != null && chkPhotoUnified.isChecked());
        s.webcamDet = (chkWebcamUnified != null && chkWebcamUnified.isChecked());
        s.speedMeetingDet = (chkSpeedMeetingUnified != null && chkSpeedMeetingUnified.isChecked());
        s.myRegionSimple = (chkMyRegionUnified != null && chkMyRegionUnified.isChecked());

        if (rgOrderByUnified != null) {
            int checked = rgOrderByUnified.getCheckedRadioButtonId();
            if (checked == R.id.rb_reg_unified) s.orderBySimple = "2";
            else if (checked == R.id.rb_match_unified) s.orderBySimple = "3";
            else s.orderBySimple = "1";
        } else {
            s.orderBySimple = "1";
        }

        // Single location
        if (selectedCountryUnified != null) {
            s.countryLabelSimple = selectedCountryUnified.name;
            s.countryLinkSimple = selectedCountryUnified.link;
            s.countryValueSimple = selectedCountryUnified.id;
        }
        if (selectedProvinceUnified != null) {
            s.provinceLabelSimple = selectedProvinceUnified.name;
            s.provinceLinkSimple = selectedProvinceUnified.link;
            s.provinceValueSimple = selectedProvinceUnified.id;
        }
        if (selectedRegionUnified != null) {
            s.regionLabelSimple = selectedRegionUnified.name;
            s.regionLinkSimple = selectedRegionUnified.link;
            s.regionValueSimple = selectedRegionUnified.id;
        }

        // Advanced fields
        if (s.isAdvancedSearch) {
            s.statusDet = getSelectedChipTags(cgStatusUnified);
            s.relCherDet = getSelectedChipTags(cgRelCherUnified);
            s.availDet = getSelectedChipTags(cgAvailUnified);
            s.smokerDet = getSelectedChipTags(cgSmokerUnified);
            s.zodiacDet = getSelectedChipTags(cgZodiacUnified);

            // Store slider index so it can be restored later (slider is index-based)
            if (sliderWeightUnified != null) {
                s.weightMinDet = sliderWeightUnified.getValues().get(0);
                s.weightMaxDet = sliderWeightUnified.getValues().get(1);
            }
            if (sliderHeightUnified != null) {
                s.heightMinDet = sliderHeightUnified.getValues().get(0);
                s.heightMaxDet = sliderHeightUnified.getValues().get(1);
            }

            String selectedEthnic = "";
            if (cgEthnicUnified != null) {
                List<String> tags = getSelectedChipTags(cgEthnicUnified);
                if (!tags.isEmpty()) selectedEthnic = tags.get(0);
            }
            s.ethnicDet = selectedEthnic;
            // Multiple locations
            s.selectedLocationsDet.clear();
            if (selectedLocationsUnified.isEmpty() && selectedCountryUnified != null) {
                SearchSettingsManager.SavedSettings.SavedLocation l = new SearchSettingsManager.SavedSettings.SavedLocation();
                l.countryLabel = selectedCountryUnified.name; l.countryValue = selectedCountryUnified.id; l.countryLink = selectedCountryUnified.link;
                if (selectedProvinceUnified != null) { l.provinceLabel = selectedProvinceUnified.name; l.provinceValue = selectedProvinceUnified.id; l.provinceLink = selectedProvinceUnified.link; }
                if (selectedRegionUnified != null) { l.regionLabel = selectedRegionUnified.name; l.regionValue = selectedRegionUnified.id; l.regionLink = selectedRegionUnified.link; }
                s.selectedLocationsDet.add(l);
            } else {
                for (SelectedLocation sl : selectedLocationsUnified) {
                    SearchSettingsManager.SavedSettings.SavedLocation l = new SearchSettingsManager.SavedSettings.SavedLocation();
                    l.countryLabel = sl.country.name; l.countryValue = sl.country.id; l.countryLink = sl.country.link;
                    if (sl.province != null) { l.provinceLabel = sl.province.name; l.provinceValue = sl.province.id; l.provinceLink = sl.province.link; }
                    if (sl.region != null) { l.regionLabel = sl.region.name; l.regionValue = sl.region.id; l.regionLink = sl.region.link; }
                    s.selectedLocationsDet.add(l);
                }
            }
            
            s.fantasyIdsDet = new ArrayList<>(selectedFantasyIdsUnified);
        }

        return s;
    }

    private void populateSavedSearchChipsUnified() {
        if (cgSavedSearchesUnified == null) return;
        List<SearchSettingsManager.NamedSearch> saved = SearchSettingsManager.loadNamedSearches(this, false);
        cgSavedSearchesUnified.removeAllViews();

        if (saved.isEmpty()) {
            if (tvSavedSearchesTitleUnified != null) tvSavedSearchesTitleUnified.setVisibility(View.GONE);
            if (scrollSavedSearchesUnified != null) scrollSavedSearchesUnified.setVisibility(View.GONE);
        } else {
            if (tvSavedSearchesTitleUnified != null) tvSavedSearchesTitleUnified.setVisibility(View.VISIBLE);
            if (scrollSavedSearchesUnified != null) scrollSavedSearchesUnified.setVisibility(View.VISIBLE);
            for (SearchSettingsManager.NamedSearch ns : saved) {
                addSavedSearchChip(cgSavedSearchesUnified, ns, false);
            }
        }
    }

    private void applySavedSettingsToUnified(SearchSettingsManager.SavedSettings s) {
        if (switchAdvancedSearch != null) {
            switchAdvancedSearch.setChecked(s.isAdvancedSearch);
        }

        selectChipsByTags(cgSeekUnified, s.seekDet);
        selectChipsByTags(cgWantedUnified, s.wantedDet);
        selectChipsByTags(cgOrientUnified, s.orientDet);
        if (etPseudoUnified != null) etPseudoUnified.setText(s.pseudoSimple);
        if (sliderAgeUnified != null) {
            try {
                float min = s.ageMinSimple == null || s.ageMinSimple.isEmpty() ? 18f : Float.parseFloat(s.ageMinSimple);
                float max = s.ageMaxSimple == null || s.ageMaxSimple.isEmpty() ? 99f : Float.parseFloat(s.ageMaxSimple);
                sliderAgeUnified.setValues(min, max);
                if (tvAgeRangeValueUnified != null) {
                    tvAgeRangeValueUnified.setText(Math.round(min) + " - " + Math.round(max));
                }
            } catch (Exception ignored) {}
        }
        if (chkOnlineUnified != null) chkOnlineUnified.setChecked(s.onlineSimple);
        if (chkExcludeChattedUnified != null) chkExcludeChattedUnified.setChecked(s.excludeChatted);
        if (chkPhotoUnified != null) chkPhotoUnified.setChecked(s.photoSimple);
        if (chkWebcamUnified != null) chkWebcamUnified.setChecked(s.webcamDet);
        if (chkSpeedMeetingUnified != null) chkSpeedMeetingUnified.setChecked(s.speedMeetingDet);
        if (chkMyRegionUnified != null) chkMyRegionUnified.setChecked(s.myRegionSimple);

        if (rgOrderByUnified != null) {
            if ("2".equals(s.orderBySimple)) rgOrderByUnified.check(R.id.rb_reg_unified);
            else if ("3".equals(s.orderBySimple)) rgOrderByUnified.check(R.id.rb_match_unified);
            else rgOrderByUnified.check(R.id.rb_visit_unified);
        }

        // Restore single location
        if (s.countryLabelSimple != null && !s.countryLabelSimple.isEmpty()) {
            selectedCountryUnified = new RegionDet(s.countryLabelSimple, s.countryLinkSimple, s.countryValueSimple);
            if (spinnerCountryUnified != null) spinnerCountryUnified.setText(s.countryLabelSimple, false);
            
            if (s.provinceLabelSimple != null && !s.provinceLabelSimple.isEmpty()) {
                selectedProvinceUnified = new RegionDet(s.provinceLabelSimple, s.provinceLinkSimple, s.provinceValueSimple);
                if (spinnerProvinceUnified != null) {
                    spinnerProvinceUnified.setText(s.provinceLabelSimple, false);
                    if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.VISIBLE);
                }
                fetchProvincesUnified(selectedCountryUnified.link); 

                if (s.regionLabelSimple != null && !s.regionLabelSimple.isEmpty()) {
                    selectedRegionUnified = new RegionDet(s.regionLabelSimple, s.regionLinkSimple, s.regionValueSimple);
                    if (spinnerRegionUnified != null) {
                        spinnerRegionUnified.setText(s.regionLabelSimple, false);
                        if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.VISIBLE);
                    }
                    fetchRegionsUnified(selectedProvinceUnified.link);
                } else {
                    selectedRegionUnified = null;
                    if (spinnerRegionUnified != null) spinnerRegionUnified.setText("", false);
                    if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                }
            } else {
                selectedProvinceUnified = null;
                selectedRegionUnified = null;
                if (spinnerProvinceUnified != null) spinnerProvinceUnified.setText("", false);
                if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.GONE);
                if (spinnerRegionUnified != null) spinnerRegionUnified.setText("", false);
                if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                fetchProvincesUnified(selectedCountryUnified.link);
            }
        }

        // Advanced fields
        if (s.isAdvancedSearch) {
            restoreChips(cgStatusUnified, s.statusDet);
            restoreChips(cgRelCherUnified, s.relCherDet);
            restoreChips(cgAvailUnified, s.availDet);
            restoreChips(cgSmokerUnified, s.smokerDet);
            restoreChips(cgZodiacUnified, s.zodiacDet);

            if (sliderWeightUnified != null && !weightOptionsUnified.isEmpty()) {
                float maxW = sliderWeightUnified.getValueTo();
                float lo = Math.max(0, Math.min(s.weightMinDet, maxW));
                float hi = Math.max(lo, Math.min(s.weightMaxDet, maxW));
                sliderWeightUnified.setValues(lo, hi);
            }
            if (sliderHeightUnified != null && !heightOptionsUnified.isEmpty()) {
                float maxH = sliderHeightUnified.getValueTo();
                float lo = Math.max(0, Math.min(s.heightMinDet, maxH));
                float hi = Math.max(lo, Math.min(s.heightMaxDet, maxH));
                sliderHeightUnified.setValues(lo, hi);
            }

            if (cgEthnicUnified != null) {
                cgEthnicUnified.setTag(s.ethnicDet);
                restoreChips(cgEthnicUnified, java.util.Collections.singletonList(s.ethnicDet));
            }

            // Restore multiple locations
            selectedLocationsUnified.clear();
            if (cgSelectedLocationsUnified != null) cgSelectedLocationsUnified.removeAllViews();
            for (SearchSettingsManager.SavedSettings.SavedLocation sl : s.selectedLocationsDet) {
                RegionDet c = new RegionDet(sl.countryLabel, sl.countryLink, sl.countryValue);
                RegionDet p = sl.provinceValue != null ? new RegionDet(sl.provinceLabel, sl.provinceLink, sl.provinceValue) : null;
                RegionDet r = sl.regionValue != null ? new RegionDet(sl.regionLabel, sl.regionLink, sl.regionValue) : null;
                SelectedLocation sloc = new SelectedLocation(c, p, r);
                selectedLocationsUnified.add(sloc);
                addLocationChipUnified(sloc);
            }

            selectedFantasyIdsUnified = new ArrayList<>(s.fantasyIdsDet);
            updateFantasySummaryUnified();
        }
    }



    private void addSelectedLocationUnified() {
        if (selectedCountryUnified == null) {
            Toast.makeText(this, R.string.select_country_first, Toast.LENGTH_SHORT).show();
            return;
        }
        SelectedLocation sl = new SelectedLocation(selectedCountryUnified, selectedProvinceUnified, selectedRegionUnified);
        String uniqueKey = sl.getLinkKey();
        String fullId = sl.getFullId();
        
        Log.d("JalfSearch", "Attempting to add location: " + sl.toString() + " [ID: " + fullId + "] [Key: " + uniqueKey + "]");
        
        for (SelectedLocation existing : selectedLocationsUnified) {
            String existingKey = existing.getLinkKey();
            String existingId = existing.getFullId();
            Log.d("JalfSearch", "Checking against existing: " + existing.toString() + " [ID: " + existingId + "] [Key: " + existingKey + "]");
            
            boolean keyMatch = !uniqueKey.replace("|", "").isEmpty() && existingKey.equals(uniqueKey);
            boolean idMatch = !fullId.replace(",", "").replace("null", "").isEmpty() && existingId.equals(fullId);
            
            if (keyMatch || idMatch) {
                Log.w("JalfSearch", "Duplicate detected! Collision on " + (idMatch ? "ID (" + fullId + ")" : "Key (" + uniqueKey + ")"));
                Toast.makeText(this, R.string.location_already_added, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        
        selectedLocationsUnified.add(sl);
        addLocationChipUnified(sl);
        Log.i("JalfSearch", "Location added successfully. Total count: " + selectedLocationsUnified.size());
    }

    private void addLocationChipUnified(SelectedLocation sl) {
        if (cgSelectedLocationsUnified == null) return;
        Chip chip = new Chip(this);
        StringBuilder sb = new StringBuilder(sl.country.name);
        if (sl.province != null) sb.append(" > ").append(sl.province.name);
        if (sl.region != null) sb.append(" > ").append(sl.region.name);
        chip.setText(sb.toString());
        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> {
            selectedLocationsUnified.remove(sl);
            cgSelectedLocationsUnified.removeView(chip);
        });
        cgSelectedLocationsUnified.addView(chip);
    }

    private void clearFantasiesUnified() {
        selectedFantasyIdsUnified.clear();
        updateFantasySummaryUnified();
    }



    private void fetchDetailedSearchOptionsUnified() {
        if (detailedOptionsLoaded) return;
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/search_det/1")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null)
                        return;
                    String html = NetworkUtils.responseToString(r);
                    Document doc = Jsoup.parse(html);

                    runOnUiThread(() -> {
                        populateChipsFromCheckboxesUnified(doc, "div#status_soc", cgStatusUnified);
                        populateChipsFromCheckboxesUnified(doc, "div#relCher", cgRelCherUnified);
                        populateChipsFromCheckboxesUnified(doc, "div#Disponible", cgAvailUnified);
                        populateChipsFromCheckboxesUnified(doc, "div#Fumeur", cgSmokerUnified);
                        populateChipsFromCheckboxesUnified(doc, "div#Signe", cgZodiacUnified);
                        parseFantasiesUnified(doc);
                        detailedOptionsLoaded = true;
                    });
                }
            }
        });
    }

    private void fetchWeightsUnified() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/weights")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    org.json.JSONObject json = new org.json.JSONObject(r.body().string());
                    org.json.JSONArray items = json.optJSONArray("weights");
                    List<WeightOption> opts = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            org.json.JSONObject obj = items.optJSONObject(i);
                            if (obj == null) continue;
                            try {
                                String metric = obj.optString("metric", "");
                                String imperial = obj.optString("imperial", "");
                                int kg = 0;
                                if (metric.contains("kg")) {
                                    kg = Integer.parseInt(metric.split(" ")[0]);
                                }
                                int lbs = 0;
                                if (imperial.contains("lbs")) {
                                    lbs = Integer.parseInt(imperial.split(" ")[0]);
                                }
                                String link = obj.optString("weight_link", "");
                                int id = Integer.parseInt(link.substring(link.lastIndexOf("/") + 1));
                                opts.add(new WeightOption(id, kg, lbs));
                            } catch (Exception ignored) {}
                        }
                    }
                    runOnUiThread(() -> {
                        weightOptionsUnified.clear();
                        weightOptionsUnified.addAll(opts);
                        updateSliderRange(sliderWeightUnified, weightOptionsUnified);
                        if (sliderWeightUnified != null && !weightOptionsUnified.isEmpty()) {
                            sliderWeightUnified.setLabelFormatter(value -> {
                                int idx = Math.max(0, Math.min((int) value, weightOptionsUnified.size() - 1));
                                WeightOption opt = weightOptionsUnified.get(idx);
                                return opt.kg + "kg / " + opt.lbs + "lbs";
                            });
                        }
                    });
                } catch (Exception e) { Log.e(TAG, "fetchWeightsUnified", e); }
            }
        });
    }

    private void fetchHeightsUnified() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/heights")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    org.json.JSONObject json = new org.json.JSONObject(r.body().string());
                    org.json.JSONArray items = json.optJSONArray("heights");
                    List<HeightOption> opts = new ArrayList<>();
                    if (items != null) {
                        for (int i = 0; i < items.length(); i++) {
                            org.json.JSONObject obj = items.optJSONObject(i);
                            if (obj == null) continue;
                            try {
                                String metric = obj.optString("metric", "");
                                String imperial = obj.optString("imperial", "");
                                String link = obj.optString("height_link", "");
                                int id = Integer.parseInt(link.substring(link.lastIndexOf("/") + 1));
                                opts.add(new HeightOption(id, metric, imperial));
                            } catch (Exception ignored) {}
                        }
                    }
                    runOnUiThread(() -> {
                        heightOptionsUnified.clear();
                        heightOptionsUnified.addAll(opts);
                        updateSliderRange(sliderHeightUnified, heightOptionsUnified);
                        if (sliderHeightUnified != null && !heightOptionsUnified.isEmpty()) {
                            sliderHeightUnified.setLabelFormatter(value -> {
                                int idx = Math.max(0, Math.min((int) value, heightOptionsUnified.size() - 1));
                                HeightOption opt = heightOptionsUnified.get(idx);
                                return opt.metric + " / " + opt.imperial;
                            });
                        }
                    });
                } catch (Exception e) { Log.e(TAG, "fetchHeightsUnified", e); }
            }
        });
    }

    private void fetchEthnicGroupsUnified() {
        if (ethnicGroupsLoaded) return;
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/ethnic-groups")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (!r.isSuccessful() || r.body() == null) return;
                    org.json.JSONObject json = new org.json.JSONObject(r.body().string());
                    org.json.JSONArray array = json.optJSONArray("ethnic_groups");
                    List<SearchOption> list = new ArrayList<>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            org.json.JSONObject obj = array.optJSONObject(i);
                            if (obj == null) continue;
                            String link = obj.optString("ethnic_group_link", "");
                            String desc = obj.optString("description", "");
                            if (!link.isEmpty() && !desc.isEmpty()) {
                                String id = link.substring(link.lastIndexOf("/") + 1);
                                list.add(new SearchOption(id, desc));
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        if (cgEthnicUnified == null) return;
                        cgEthnicUnified.removeAllViews();
                        for (SearchOption opt : list) {
                            if (opt.value.equals("0") || opt.value.isEmpty()) continue; // Skip default/any value
                            Chip chip = new Chip(MainActivity.this);
                            chip.setText(opt.label);
                            chip.setCheckable(true);
                            chip.setTag(opt.value);
                            cgEthnicUnified.addView(chip);
                        }
                        ethnicGroupsLoaded = true;
                        
                        Object tag = cgEthnicUnified.getTag();
                        if (tag != null && !tag.toString().isEmpty()) {
                            restoreChips(cgEthnicUnified, java.util.Collections.singletonList(tag.toString()));
                        }
                    });
                } catch (Exception e) { Log.e(TAG, "fetchEthnicGroupsUnified", e); }
            }
        });
    }

    private void restoreChips(ChipGroup group, List<String> values) {
        if (group == null)
            return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof Chip) {
                ((Chip) v).setChecked(false);
            }
        }
        if (values == null || values.isEmpty())
            return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View v = group.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                String tag = chip.getTag() != null ? chip.getTag().toString() : "";
                if (values.contains(tag)) {
                    chip.setChecked(true);
                }
            }
        }
    }

    private void parseFantasiesUnified(Document doc) {
        fantasyCategoriesUnified.clear();
        Elements accordions = doc.select("button.accordion");
        for (Element acc : accordions) {
            String catName = acc.text().trim();
            FantasyCategory category = new FantasyCategory(catName);
            Element panel = acc.nextElementSibling();
            if (panel != null && panel.hasClass("panel_body")) {
                Elements cards = panel.select(".fantasy-card");
                for (Element card : cards) {
                    String fName = card.select(".fantasy-card__label").text().trim();
                    String fIdStr = card.attr("data-fantasyid");
                    if (!fIdStr.isEmpty()) {
                        category.fantasies.add(new Fantasy(Integer.parseInt(fIdStr), fName));
                    }
                }
            }
            if (!category.fantasies.isEmpty()) {
                fantasyCategoriesUnified.add(category);
            }
        }

        if (btnPickFantasiesUnified != null) {
            btnPickFantasiesUnified.setOnClickListener(v -> {
                FantasyPickerBottomSheet sheet = new FantasyPickerBottomSheet(fantasyCategoriesUnified, selectedFantasyIdsUnified,
                        ids -> {
                            selectedFantasyIdsUnified = ids;
                            updateFantasySummaryUnified();
                        });
                sheet.show(getSupportFragmentManager(), "FantasyPicker");
            });
        }
        if (btnClearFantasiesUnified != null) {
            btnClearFantasiesUnified.setOnClickListener(v -> {
                selectedFantasyIdsUnified.clear();
                updateFantasySummaryUnified();
            });
        }
    }

    private void updateFantasySummaryUnified() {
        if (selectedFantasyIdsUnified.isEmpty()) {
            if (tvSelectedFantasiesUnified != null) tvSelectedFantasiesUnified.setText(R.string.no_fantasies_selected);
            if (btnClearFantasiesUnified != null) btnClearFantasiesUnified.setVisibility(View.GONE);
        } else {
            List<String> names = new ArrayList<>();
            for (FantasyCategory cat : fantasyCategoriesUnified) {
                for (Fantasy f : cat.fantasies) {
                    if (selectedFantasyIdsUnified.contains(f.id)) {
                        names.add(f.name);
                    }
                }
            }
            if (tvSelectedFantasiesUnified != null) {
                tvSelectedFantasiesUnified.setText(getString(R.string.fantasies_selected_count, names.size(), String.join(", ", names)));
            }
            if (btnClearFantasiesUnified != null) btnClearFantasiesUnified.setVisibility(View.VISIBLE);
        }
    }




    private List<SearchAdapter.SearchItem> parseSearchResults(String html) {
        return ApiParser.parseSearchResults(html);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isChatSelectionMode) {
            getMenuInflater().inflate(R.menu.menu_chat_selection, menu);
            MenuItem pinItem = menu.findItem(R.id.action_pin); // Dynamic pin title
            return true;
        }
        if (bottomNav.getSelectedItemId() == R.id.nav_search) {
            getMenuInflater().inflate(R.menu.menu_search, menu);
        } else {
            getMenuInflater().inflate(R.menu.main_menu, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_select_all) {
            toggleSelectAll();
            return true;
        } else if (id == R.id.action_pin) {
            pinSelected();
            return true;
        } else if (id == R.id.action_archive) {
            archiveSelected();
            return true;
        } else if (id == R.id.action_delete) {
            deleteSelected();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void enterSelectionMode(ChatAdapter.ChatItem anchorItem) {
        isChatSelectionMode = true;
        if (anchorItem != null) {
            anchorItem.isSelected = true;
        }
        chatAdapter.setSelectionMode(true);
        invalidateOptionsMenu();
        updateSelectionCount();
    }

    private void exitSelectionMode() {
        isChatSelectionMode = false;
        chatAdapter.setSelectionMode(false);
        invalidateOptionsMenu();
        updateSelectionCount();
    }

    private void updateSelectionCount() {
        if (isChatSelectionMode) {
            long count = chatItems.stream().filter(i -> i.isSelected).count();
            if (count == 0) {
                exitSelectionMode();
            } else {
                toolbar.setTitle(getString(R.string.items_selected, count));

            }
        } else {
            toolbar.setTitle(getString(R.string.tab_chats));
        }
    }

    private void savePins() {
        SharedPreferences pins = getSharedPreferences("PinnedConversations", MODE_PRIVATE);
        pins.edit().putStringSet("links", pinnedConvoLinks).apply();
    }

    private void archiveSelected() {
        List<ChatAdapter.ChatItem> selected = chatItems.stream().filter(i -> i.isSelected).collect(Collectors.toList());
        for (ChatAdapter.ChatItem item : selected) {
            performSingleArchive(item);
        }
        exitSelectionMode();
        fetchConversations(0, true);
    }

    private void deleteSelected() {
        long count = chatItems.stream().filter(i -> i.isSelected).count();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_conversations_title)

                .setMessage(getString(R.string.delete_conversations_message, (int)count))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    List<ChatAdapter.ChatItem> selected = chatItems.stream().filter(i -> i.isSelected)
                            .collect(java.util.stream.Collectors.toList());
                    for (ChatAdapter.ChatItem item : selected) {
                        performSingleDelete(item);
                    }
                    exitSelectionMode();
                    fetchConversations(0, true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void pinSelected() {
        List<ChatAdapter.ChatItem> selected = chatItems.stream().filter(i -> i.isSelected).collect(Collectors.toList());
        boolean anyUnpinned = selected.stream().anyMatch(i -> !pinnedConvoLinks.contains(i.conversationLink));

        for (ChatAdapter.ChatItem item : selected) {
            if (anyUnpinned) {
                pinnedConvoLinks.add(item.conversationLink);
            } else {
                pinnedConvoLinks.remove(item.conversationLink);
            }
        }
        savePins();
        applyFilter("chats");
        exitSelectionMode();
    }

    private void unpinSelected() {
        List<ChatAdapter.ChatItem> selected = chatItems.stream().filter(i -> i.isSelected).collect(Collectors.toList());
        for (ChatAdapter.ChatItem item : selected) {
            pinnedConvoLinks.remove(item.conversationLink);
        }
        savePins();
        applyFilter("chats");
        exitSelectionMode();
    }

    private void toggleSelectAll() {
        boolean allSelected = chatItems.stream().filter(i -> i.type == ChatAdapter.TYPE_CHAT)
                .allMatch(i -> i.isSelected);
        for (ChatAdapter.ChatItem item : chatItems) {
            if (item.type == ChatAdapter.TYPE_CHAT) {
                item.isSelected = !allSelected;
            }
        }
        chatAdapter.notifyDataSetChanged();
        updateSelectionCount();
    }

    private void performSingleArchive(ChatAdapter.ChatItem item) {
        if (item.conversationLink == null || item.conversationLink.isEmpty())
            return;
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        JSONObject payload = new JSONObject();
        try {
            payload.put("folder_link", "/rest/users/" + myUserId + "/conversations/archived");
        } catch (Exception e) {
        }
        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.conversationchange+json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(item.conversationLink))
                .post(body)
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
                response.close();
            }
        });
    }

    private void performSingleDelete(ChatAdapter.ChatItem item) {
        if (item.conversationLink == null || item.conversationLink.isEmpty())
            return;
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(item.conversationLink))
                .delete()
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
                response.close();
            }
        });
    }

    private void startConversationCleanup() {
        List<ChatAdapter.ChatItem> uniqueConversations = new ArrayList<>();
        Set<String> links = new HashSet<>();
        for (ChatAdapter.ChatItem item : allActiveItems) {
            if (item.conversationLink != null && links.add(item.conversationLink)) {
                uniqueConversations.add(item);
            }
        }
        for (ChatAdapter.ChatItem item : allNewItems) {
            if (item.conversationLink != null && links.add(item.conversationLink)) {
                uniqueConversations.add(item);
            }
        }
        for (ChatAdapter.ChatItem item : allArchivedItems) {
            if (item.conversationLink != null && links.add(item.conversationLink)) {
                uniqueConversations.add(item);
            }
        }

        if (uniqueConversations.isEmpty()) {
            Toast.makeText(this, R.string.cleanup_no_candidates, Toast.LENGTH_SHORT).show();
            return;
        }

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
        progressBar.setPadding(padding, padding, padding, padding);

        AlertDialog scanDialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.cleanup_dialog_title))
                .setMessage(getString(R.string.cleanup_scanning, 0, uniqueConversations.size()))
                .setView(progressBar)
                .setCancelable(false)
                .create();
        scanDialog.show();

        List<ChatAdapter.ChatItem> candidates = new ArrayList<>();
        scanNext(0, uniqueConversations, candidates, scanDialog);
    }

    private void scanNext(final int index, final List<ChatAdapter.ChatItem> list, final List<ChatAdapter.ChatItem> candidates, final AlertDialog dialog) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (index >= list.size()) {
            runOnUiThread(() -> {
                dialog.dismiss();
                showCleanupSelectionDialog(candidates);
            });
            return;
        }

        runOnUiThread(() -> dialog.setMessage(getString(R.string.cleanup_scanning, index + 1, list.size())));

        ChatAdapter.ChatItem item = list.get(index);
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(item.conversationLink))
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                scanNext(index + 1, list, candidates, dialog);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
                            String body = NetworkUtils.responseToString(r);
                            JSONObject json = new JSONObject(body);
                            String canPost = json.optString("can_post", "yes");
                            if (!"yes".equals(canPost)) {
                                candidates.add(item);
                            }
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error parsing conversation details during cleanup scan", e);
                        }
                    }
                }
                scanNext(index + 1, list, candidates, dialog);
            }
        });
    }

    private void showCleanupSelectionDialog(final List<ChatAdapter.ChatItem> candidates) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, R.string.cleanup_no_candidates, Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] names = new CharSequence[candidates.size()];
        final boolean[] checked = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            ChatAdapter.ChatItem item = candidates.get(i);
            String name = item.name;
            if (name == null || name.isEmpty()) {
                name = item.otherUserId != null && !item.otherUserId.isEmpty()
                        ? "#" + item.otherUserId
                        : getString(R.string.someone);
            }
            if (item.lastMessage != null && !item.lastMessage.isEmpty()) {
                names[i] = name + " — " + item.lastMessage;
            } else {
                names[i] = name;
            }
            checked[i] = true;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.cleanup_dialog_title)
                // NOTE: Do NOT add setMessage() here — it hides setMultiChoiceItems() in AlertDialog
                .setMultiChoiceItems(names, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    List<ChatAdapter.ChatItem> toDelete = new ArrayList<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) {
                            toDelete.add(candidates.get(i));
                        }
                    }
                    if (!toDelete.isEmpty()) {
                        executeCleanupPurge(toDelete);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void executeCleanupPurge(final List<ChatAdapter.ChatItem> toDelete) {
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        int padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, getResources().getDisplayMetrics()));
        progressBar.setPadding(padding, padding, padding, padding);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cleanup_purging)
                .setView(progressBar)
                .setCancelable(false)
                .create();
        progressDialog.show();

        deleteNext(0, toDelete, progressDialog);
    }

    private void deleteNext(final int index, final List<ChatAdapter.ChatItem> list, final AlertDialog progressDialog) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (index >= list.size()) {
            runOnUiThread(() -> {
                progressDialog.dismiss();
                String msg = getString(R.string.cleanup_success, list.size());
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                refreshData();
            });
            return;
        }

        ChatAdapter.ChatItem item = list.get(index);
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(item.conversationLink))
                .delete()
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                deleteNext(index + 1, list, progressDialog);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                response.close();
                deleteNext(index + 1, list, progressDialog);
            }
        });
    }

    private String getAbsoluteUrl(String link) {
        if (link == null)
            return "";
        if (link.startsWith("http"))
            return link;
        return ApiConstants.BASE_URL + link;
    }

    private void fetchProfileDetailsForSearch(SearchAdapter.SearchItem item) {
        fetchUserProfileRestForSearch(item);
    }

    private void fetchUserProfileRestForSearch(SearchAdapter.SearchItem item) {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        if (fullCookie.isEmpty())
            return;

        // Use REST API to fetch profile details to avoid adding to "consulted users" list and for better performance
        String url = ApiConstants.BASE_URL + "/rest/users/" + item.userId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                item.detailsFetched = false;
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String jsonStr = NetworkUtils.responseToString(r);
                        JSONObject profile = new JSONObject(jsonStr);
                        ProfileCacheManager.getInstance().putProfile(MainActivity.this, item.userId, profile);
                        
                        boolean updated = false;

                        // 0. Certified status
                        boolean isCertified = profile.optBoolean("certified", false);
                        if (isCertified != item.isCertified) {
                            item.isCertified = isCertified;
                            updated = true;
                        }

                        // 1. Basic Metadata
                        String age = profile.optString("age", "");
                        if (!age.isEmpty() && !age.equals(item.age)) {
                            item.age = age;
                            updated = true;
                        }

                        // 2. City
                        String city = profile.optString("city", "");
                        if (!city.isEmpty()) {
                            // If we already have location from search results (which is usually the region),
                            // we append the city if it's different.
                            if (item.location == null || item.location.isEmpty()) {
                                item.location = city;
                            } else if (!item.location.contains(city)) {
                                item.location = city + " • " + item.location;
                            }
                            updated = true;
                        }

                        // 3. Online Status
                        boolean isOnline = profile.optBoolean("is_online", false);
                        if (isOnline != item.isOnline) {
                            item.isOnline = isOnline;
                            updated = true;
                        }

                        // 4. Privileges
                        JSONArray privileges = profile.optJSONArray("privileges_links");
                        if (privileges != null) {
                            for (int i = 0; i < privileges.length(); i++) {
                                String iconUrl = privileges.optString(i, "");
                                if (!iconUrl.isEmpty()) {
                                    if (!iconUrl.startsWith("http"))
                                        iconUrl = ApiConstants.BASE_URL + iconUrl;
                                    if (!item.privilegeIconUrls.contains(iconUrl)) {
                                        item.privilegeIconUrls.add(iconUrl);
                                        updated = true;
                                    }
                                }
                            }
                        }

                        // 5. NSFW Rank
                        int rank = extractNsfwRankFromProfile(profile);
                        if (rank > 0 && rank != item.nsfwRank) {
                            item.nsfwRank = rank;
                            updated = true;
                        } else if (rank == 0) {
                            // is_sensitive may be false for certain ratings (e.g. PAS-RAP).
                            // Check the actual photo album for the rating link.
                            fetchPhotoRankFromAlbum(item.userId, albumRank -> {
                                if (albumRank > 0 && albumRank != item.nsfwRank) {
                                    item.nsfwRank = albumRank;
                                    runOnUiThread(() -> {
                                        if (searchAdapter != null) {
                                            int idx = searchItems.indexOf(item);
                                            if (idx >= 0) searchAdapter.notifyItemChanged(idx);
                                        }
                                    });
                                }
                            });
                        }

                        if (updated) {
                            runOnUiThread(() -> {
                                if (searchAdapter != null) {
                                    int idx = searchItems.indexOf(item);
                                    if (idx >= 0) {
                                        searchAdapter.notifyItemChanged(idx);
                                    }
                                }
                            });
                        }
                    } else {
                        item.detailsFetched = false;
                    }
                } catch (Exception e) {
                    item.detailsFetched = false;
                    Log.e("MainActivity", "Error parsing REST profile info for search", e);
                }
            }
        });
    }

    private void startLocationDetectionUnified() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION },
                    PERMISSION_REQUEST_CODE);
        } else {
            getCurrentLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();

            }
        }
    }

    private void getCurrentLocation() {
        if (isLocatingUnified)
            return;
        isLocatingUnified = true;
        if (btnAutoLocateUnified != null)
            btnAutoLocateUnified.setEnabled(false);

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    reverseGeocode(location);
                } else {
                    // Fallback to active request if last location is null
                    com.google.android.gms.location.LocationRequest request = new com.google.android.gms.location.LocationRequest.Builder(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 1000)
                            .setMaxUpdates(1)
                            .build();
                    fusedLocationClient.requestLocationUpdates(request, new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                            if (locationResult.getLastLocation() != null) {
                                reverseGeocode(locationResult.getLastLocation());
                            } else {
                                handleLocationFailure();
                            }
                        }
                    }, Looper.getMainLooper());
                }
            }).addOnFailureListener(this, e -> handleLocationFailure());
        } catch (SecurityException e) {
            handleLocationFailure();
        }
    }

    private void handleLocationFailure() {
        isLocatingUnified = false;
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, R.string.cannot_get_location, Toast.LENGTH_SHORT).show();
            if (btnAutoLocateUnified != null)
                btnAutoLocateUnified.setEnabled(true);
        });
    }

    private void reverseGeocode(Location loc) {
        JalfNotifierApplication.IO_EXECUTOR.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.FRENCH);
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1, new Geocoder.GeocodeListener() {
                        @Override
                        public void onGeocode(@NonNull List<Address> addresses) {
                            processGeocodeResults(addresses);
                        }

                        @Override
                        public void onError(@Nullable String errorMessage) {
                            runOnUiThread(() -> {
                                isLocatingUnified = false;
                                if (btnAutoLocateUnified != null)
                                    btnAutoLocateUnified.setEnabled(true);
                            });
                        }
                    });
                } else {
                    @SuppressWarnings("deprecation")
                    List<Address> addresses = geocoder.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
                    processGeocodeResults(addresses);
                }
            } catch (Exception e) {
                Log.e(TAG, "reverseGeocode failed", e);
                runOnUiThread(() -> {
                    isLocatingUnified = false;
                    if (btnAutoLocateUnified != null)
                        btnAutoLocateUnified.setEnabled(true);
                });
            }
        });
    }

    private void processGeocodeResults(List<Address> addresses) {
        if (addresses != null && !addresses.isEmpty()) {
            Address addr = addresses.get(0);
            String country = addr.getCountryName();
            String province = addr.getAdminArea();
            String region = addr.getSubAdminArea() != null ? addr.getSubAdminArea() : addr.getLocality();

            runOnUiThread(() -> {
                matchAndSelectLocation(country, province, region);
                if (btnAutoLocateUnified != null)
                    btnAutoLocateUnified.setEnabled(true);
                isLocatingUnified = false;
                isLocatingUnified = false;
            });
        } else {
            runOnUiThread(() -> {
                isLocatingUnified = false;
                Toast.makeText(MainActivity.this, R.string.geocoding_failed, Toast.LENGTH_SHORT).show();
                if (btnAutoLocateUnified != null)
                    btnAutoLocateUnified.setEnabled(true);
                isLocatingUnified = false;
            });
        }
    }

    private void matchAndSelectLocation(String country, String province, String region) {
        AppLogger.log("Locating: " + country + ", " + province + ", " + region);

        // Defensive reset of current selection to ensure a clean state
        runOnUiThread(() -> {
            if (spinnerCountryUnified != null) spinnerCountryUnified.setText("", false);
            if (spinnerProvinceUnified != null) spinnerProvinceUnified.setText("", false);
            if (spinnerRegionUnified != null) spinnerRegionUnified.setText("", false);
            if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.GONE);
            if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
        });
        selectedCountryUnified = null;
        selectedProvinceUnified = null;
        selectedRegionUnified = null;

        String normCountry = normalizeForMatch(country);
        String normProvince = normalizeForMatch(province);
        String normRegion = normalizeForMatch(region);

        if (!normCountry.isEmpty() && !countriesUnified.isEmpty()) {
            for (RegionDet rd : countriesUnified) {
                String normSmCountry = normalizeForMatch(rd.name);
                if (normSmCountry.equals(normCountry) || normCountry.contains(normSmCountry)) {
                    selectedCountryUnified = rd;
                    runOnUiThread(() -> {
                        spinnerCountryUnified.setText(rd.name, false);
                        provincesUnified.clear();
                        regionsUnified.clear();
                        if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.GONE);
                        if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                    });

                    fetchProvincesUnifiedSequential(rd.link, () -> {
                        if (!normProvince.isEmpty()) {
                            for (RegionDet pr : provincesUnified) {
                                String normSmPr = normalizeForMatch(pr.name);
                                if (normSmPr.equals(normProvince) || normProvince.contains(normSmPr)) {
                                    selectedProvinceUnified = pr;
                                    runOnUiThread(() -> {
                                        spinnerProvinceUnified.setText(pr.name, false);
                                        if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                                    });

                                    fetchRegionsUnifiedSequential(pr.link, () -> {
                                        if (!normRegion.isEmpty()) {
                                            for (RegionDet re : regionsUnified) {
                                                String normSmRe = normalizeForMatch(re.name);
                                                if (normSmRe.equals(normRegion) || normRegion.contains(normSmRe)) {
                                                    selectedRegionUnified = re;
                                                    runOnUiThread(() -> spinnerRegionUnified.setText(re.name, false));
                                                    break;
                                                }
                                            }
                                        }
                                    });
                                    break;
                                }
                            }
                        }
                    });
                    break;
                }
            }
        }
    }

    private void fetchProvincesUnifiedSequential(String url, OnListLoadedListener listener) {
        fetchRegionsListUnified(url, list -> {
            provincesUnified.clear();
            provincesUnified.addAll(list);
            runOnUiThread(() -> {
                if (!provincesUnified.isEmpty()) {
                    ArrayAdapter<RegionDet> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                            provincesUnified);
                    spinnerProvinceUnified.setAdapter(adapter);
                    if (tilProvinceUnified != null) tilProvinceUnified.setVisibility(View.VISIBLE);
                    spinnerProvinceUnified.setOnItemClickListener((parent, view, position, id) -> {
                        selectedProvinceUnified = (RegionDet) parent.getAdapter().getItem(position);
                        selectedRegionUnified   = null;
                        spinnerRegionUnified.setText("", false);
                        if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.GONE);
                        fetchRegionsUnified(selectedProvinceUnified.link);
                    });
                    if (listener != null) listener.onLoaded();
                }
            });
        });
    }

    private void fetchRegionsUnifiedSequential(String url, OnListLoadedListener listener) {
        fetchRegionsListUnified(url, list -> {
            regionsUnified.clear();
            regionsUnified.addAll(list);
            runOnUiThread(() -> {
                if (!regionsUnified.isEmpty()) {
                    ArrayAdapter<RegionDet> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                            regionsUnified);
                    spinnerRegionUnified.setAdapter(adapter);
                    if (tilRegionUnified != null) tilRegionUnified.setVisibility(View.VISIBLE);
                    spinnerRegionUnified.setOnItemClickListener((parent, view, position, id) ->
                            selectedRegionUnified = (RegionDet) parent.getAdapter().getItem(position));
                    if (listener != null) listener.onLoaded();
                }
            });
        });
    }



    private void updateSliderRange(RangeSlider slider, List<?> options) {
        if (slider == null || options == null || options.isEmpty())
            return;
        float max = (float) (options.size() - 1);

        // Use setValues safely to avoid IllegalStateException when changing bounds
        // Set dummy values within any possible range first
        slider.setValues(0f, 0f);
        slider.setValueFrom(0f);
        slider.setValueTo(max > 0 ? max : 1f);
        slider.setValues(0f, max > 0 ? max : 1f);
        slider.setStepSize(1f);
    }

    private void checkLock() {
        SharedPreferences prefs = getAppPrefs().getRaw();
        boolean isLocked = prefs.getBoolean(ApiConstants.KEY_BIOMETRIC_LOCK, false);
        if (isLocked && lockOverlay != null && !isUnlockedTemporarily) {
            lockOverlay.setVisibility(View.VISIBLE);
            lockOverlay.post(this::triggerUnlock);
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (bottomNav != null) {
            outState.putInt("selectedNavId", bottomNav.getSelectedItemId());
        }
        if (tabLayoutChats != null) {
            outState.putInt("selectedChatTab", tabLayoutChats.getSelectedTabPosition());
        }
        outState.putBoolean("isSelectionMode", isChatSelectionMode);
        outState.putBoolean("isUnlockedTemporarily", isUnlockedTemporarily);
    }

    @Override
    protected void onRestoreInstanceState(@androidx.annotation.NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isUnlockedTemporarily = savedInstanceState.getBoolean("isUnlockedTemporarily", false);
        int navId = savedInstanceState.getInt("selectedNavId", R.id.nav_chats);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(navId);
        }
        if (navRail != null) {
            navRail.setSelectedItemId(navId);
        }
        int chatTab = savedInstanceState.getInt("selectedChatTab", 0);
        if (tabLayoutChats != null && tabLayoutChats.getTabAt(chatTab) != null) {
            tabLayoutChats.getTabAt(chatTab).select();
        }
        if (savedInstanceState.getBoolean("isSelectionMode", false)) {
            enterSelectionMode(null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getAction() == android.view.KeyEvent.ACTION_DOWN
                && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ESCAPE) {
            if (persistentSearchBehavior != null && persistentSearchBehavior
                    .getState() != com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN) {
                persistentSearchBehavior
                        .setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN);
                return true;
            }
            if (isChatSelectionMode) {
                exitSelectionMode();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void triggerUnlock() {
        if (lockOverlay == null)
            return;
        BiometricHelper.showPrompt(this, getString(R.string.app_locked), getString(R.string.auth_required_to_unlock),
                new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(
                            @androidx.annotation.NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                        isUnlockedTemporarily = true;
                        runOnUiThread(() -> {
                            if (lockOverlay != null)
                                lockOverlay.setVisibility(View.GONE);
                        });
                    }

                    @Override
                    public void onAuthenticationError(int errorCode,
                            @androidx.annotation.NonNull CharSequence errString) {
                        // User can retry via button
                    }
                });
    }

    private List<String> getSelectedChipTags(ChipGroup cg) {
        List<String> selected = new ArrayList<>();
        if (cg == null) return selected;
        for (int i = 0; i < cg.getChildCount(); i++) {
            View v = cg.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                if (chip.isChecked() && chip.getTag() != null) {
                    selected.add(chip.getTag().toString());
                }
            }
        }
        return selected;
    }

    private void addSavedSearchChip(ChipGroup cg, SearchSettingsManager.NamedSearch ns, boolean selected) {
        if (cg == null || ns == null) return;
        Chip chip = new Chip(this);
        chip.setText(ns.name);
        chip.setCheckable(true);
        chip.setChecked(selected);
        chip.setTag(ns);
        chip.setOnCheckedChangeListener((v, isChecked) -> {
            if (isChecked) {
                loadSavedSearchUnified(ns);
            }
        });
        chip.setOnLongClickListener(v -> {
            showSavedSearchOptionsUnified(ns);
            return true;
        });
        cg.addView(chip);
    }

    private void loadSavedSearchUnified(SearchSettingsManager.NamedSearch ns) {
        if (ns == null || ns.settings == null) return;
        applySavedSettingsToUnified(ns.settings);
        Toast.makeText(this, getString(R.string.search_loaded, ns.name), Toast.LENGTH_SHORT).show();
    }

    private void showSavedSearchOptionsUnified(SearchSettingsManager.NamedSearch ns) {
        new AlertDialog.Builder(this)
                .setTitle(ns.name)
                .setItems(new String[]{getString(R.string.load), getString(R.string.rename), getString(R.string.delete)}, (dialog, which) -> {
                    if (which == 0) {
                        loadSavedSearchUnified(ns);
                    } else if (which == 1) {
                        renameSavedSearchUnified(ns);
                    } else if (which == 2) {
                        deleteSavedSearchUnified(ns);
                    }
                })
                .show();
    }

    private void deleteSavedSearchUnified(SearchSettingsManager.NamedSearch ns) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(getString(R.string.delete_search_message, ns.name))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    SearchSettingsManager.deleteNamedSearch(this, ns.name, false);
                    populateSavedSearchChipsUnified();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void renameSavedSearchUnified(SearchSettingsManager.NamedSearch ns) {
        final EditText input = new EditText(this);
        input.setText(ns.name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename)
                .setView(input)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        SearchSettingsManager.renameNamedSearch(this, ns.name, newName, false);
                        populateSavedSearchChipsUnified();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void populateChipsFromCheckboxesUnified(Document doc, String selector, ChipGroup cg) {
        if (doc == null || cg == null) return;
        Elements containers = doc.select(selector);
        if (containers.isEmpty()) return;

        cg.removeAllViews();
        Elements labels = containers.first().select("label");
        for (Element label : labels) {
            String text = label.text().trim();
            String forId = label.attr("for");
            Element input = containers.first().selectFirst("input#" + forId);
            if (input == null) {
                // Fallback just in case
                input = label.selectFirst("input");
            }
            if (input != null) {
                String value = input.val();
                Chip chip = new Chip(this);
                chip.setText(text);
                chip.setCheckable(true);
                chip.setTag(value);
                cg.addView(chip);
            }
        }
    }

    private void populateChipsFromSelectUnified(Document doc, String selector, ChipGroup cg) {
        if (doc == null || cg == null) return;
        Element select = doc.selectFirst(selector);
        if (select == null) return;

        cg.removeAllViews();
        Elements options = select.select("option");
        for (Element opt : options) {
            String value = opt.val();
            if (value.isEmpty()) continue; // Skip "Any" or empty placeholder

            String label = opt.text().trim();
            Chip chip = new Chip(this);
            chip.setText(label);
            chip.setCheckable(true);
            chip.setTag(value);
            cg.addView(chip);
        }
    }


    private void selectChipsByTags(ChipGroup cg, List<String> tags) {
        if (cg == null || tags == null) return;
        for (int i = 0; i < cg.getChildCount(); i++) {
            View v = cg.getChildAt(i);
            if (v instanceof Chip) {
                Chip chip = (Chip) v;
                if (chip.getTag() != null) {
                    chip.setChecked(tags.contains(chip.getTag().toString()));
                }
            }
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    private void checkAuthentication() {
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        Log.d(TAG, "checkAuthentication: Starting check. SUID present: " + !suid.isEmpty());

        if (suid.isEmpty()) {
            Log.d(TAG, "checkAuthentication: SUID is empty. Redirecting to login.");
            redirectToLogin();
            return;
        }

        Request req = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/accueil")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(req).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull IOException e) {
                Log.e(TAG, "checkAuthentication: Network failure: " + e.getMessage(), e);
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws IOException {
                try (okhttp3.Response r = response) {
                    String finalUrl = r.request().url().toString();
                    int code = r.code();
                    Log.d(TAG, "checkAuthentication: Received response. Code: " + code + ", Final URL: " + finalUrl);

                    for (String name : r.headers().names()) {
                        Log.d(TAG, "  Header: " + name + " = " + r.header(name));
                    }

                    boolean isLoggedOut = false;
                    if (code == 401) {
                        Log.d(TAG, "checkAuthentication: Detected 401 Unauthorized.");
                        isLoggedOut = true;
                    } else if (finalUrl.contains("/connect") || finalUrl.contains("/login")) {
                        Log.d(TAG, "checkAuthentication: Final URL suggests login redirect.");
                        isLoggedOut = true;
                    } else {
                        // Check if the response body contains login fields or is missing the user's home/rest path
                        String body = r.peekBody(1024 * 50).string(); // Peak up to 50KB
                        if (body.contains("name=\"Username\"") || body.contains("name=\"Password\"")) {
                            Log.d(TAG, "checkAuthentication: Body contains login credentials form inputs.");
                            isLoggedOut = true;
                        } else if (!body.contains("/rest/users/") && !body.contains("RestApi")) {
                            Log.d(TAG, "checkAuthentication: Body lacks user rest paths or api refs. Assuming not logged in.");
                            isLoggedOut = true;
                        }
                    }

                    if (isLoggedOut) {
                        Log.w(TAG, "checkAuthentication: Session is invalid/expired. Triggering relogin flow.");
                        runOnUiThread(() -> handleSessionExpired());
                    } else {
                        Log.d(TAG, "checkAuthentication: Session is active and valid.");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "checkAuthentication: Error during session check", e);
                }
            }
        });
    }

    private void handleSessionExpired() {
        Log.w(TAG, "handleSessionExpired: Clearing credentials and redirecting to LoginActivity.");
        Toast.makeText(this, R.string.session_expired_toast, Toast.LENGTH_LONG).show();
        SecurePrefs.get(this)
                .putString(ApiConstants.KEY_SUID, "")
                .putString(ApiConstants.KEY_FULL_COOKIE, "");
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
