package io.github.kgelinas.jalfnotifier;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ConversationFragment extends Fragment {

    private static final String TAG = "ConversationFragment";

    private static final String ARG_CONVO_LINK = "conversationLink";
    private static final String ARG_OTHER_ID = "otherUserId";
    private static final String ARG_OTHER_NAME = "otherName";
    private static final String ARG_AVATAR_URL = "avatarUrl";
    private static final String ARG_SEX_ICON_URL = "sexIconUrl";
    private static final String ARG_IS_ONLINE = "isOnline";

    private final OkHttpClient client = JalfNotifierApplication.httpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String conversationLink;
    private String otherName;
    private String otherUserId;
    private String myUserId;
    private String fullCookie;
    private String suid;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerMessages;
    private TextInputEditText editMessage;
    private MaterialButton btnSend;
    private MaterialButton btnQuickResponse;
    private MaterialButton btnGeminiGenerate;
    private MaterialButton btnQueueMessage;
    private TextView txtTypingIndicator;
    private View imgOnlineIndicator;

    private MessageAdapter messageAdapter;
    private final List<MessageAdapter.MessageItem> messageList = new ArrayList<>();
    private MessageAdapter.MessageItem aiLoadingItem;

    private boolean isLoading = false;
    private int totalMessagesCount = 0;
    private int visibleMessagesCount = 0;
    private String oldestMessageTimestamp = null;
    private boolean isEphemeral = false;

    public static ConversationFragment newInstance(String convoLink, String otherId, String name, String avatar,
            String sexIcon, boolean isOnline) {
        ConversationFragment fragment = new ConversationFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CONVO_LINK, convoLink);
        args.putString(ARG_OTHER_ID, otherId);
        args.putString(ARG_OTHER_NAME, name);
        args.putString(ARG_AVATAR_URL, avatar);
        args.putString(ARG_SEX_ICON_URL, sexIcon);
        args.putBoolean(ARG_IS_ONLINE, isOnline);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            conversationLink = getArguments().getString(ARG_CONVO_LINK);
            otherUserId = getArguments().getString(ARG_OTHER_ID);
            otherName = getArguments().getString(ARG_OTHER_NAME);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.layout_fragment_conversation, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String avatarUrl = getArguments() != null ? getArguments().getString(ARG_AVATAR_URL) : null;
        String sexIconUrl = getArguments() != null ? getArguments().getString(ARG_SEX_ICON_URL) : null;

        Context context = requireContext();
        AppPrefs prefs = AppPrefs.getInstance(context);
        myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        SecurePrefs secure = SecurePrefs.get(context);
        fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        suid = secure.getString(ApiConstants.KEY_SUID, "");

        setupToolbar(view, otherName, avatarUrl, sexIconUrl);

        swipeRefresh = view.findViewById(R.id.swipe_refresh_conversation);
        recyclerMessages = view.findViewById(R.id.recycler_messages);
        editMessage = view.findViewById(R.id.edit_message);
        btnSend = view.findViewById(R.id.btn_send);
        btnQuickResponse = view.findViewById(R.id.btn_quick_response);
        btnGeminiGenerate = view.findViewById(R.id.btn_gemini_generate);
        btnQueueMessage = view.findViewById(R.id.btn_queue_message);
        txtTypingIndicator = view.findViewById(R.id.txt_typing_indicator);

        btnQuickResponse.setOnClickListener(v -> showQuickResponseMenu());
        btnGeminiGenerate.setOnClickListener(v -> showGeminiMenu(v));
        btnQueueMessage.setOnClickListener(v -> queueAutoMessage());

        editMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateButtonStates();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        updateButtonStates();
        updateAiButtonVisibility();

        LinearLayoutManager layoutManager = new LinearLayoutManager(context);
        layoutManager.setStackFromEnd(true);
        recyclerMessages.setLayoutManager(layoutManager);

        messageAdapter = new MessageAdapter(messageList, myUserId);
        messageAdapter.setOnMessageClickListener(new MessageAdapter.OnMessageClickListener() {
            @Override
            public void onPendingMessageClick(MessageAdapter.MessageItem item) {
                if (item.isPending) {
                    restorePendingMessage(item);
                }
            }

            @Override
            public void onMessageLongClick(MessageAdapter.MessageItem item) {
                // No longer used for AI, but keeping interface for stability
            }
        });
        recyclerMessages.setAdapter(messageAdapter);

        // Swipe-to-AI-Reply
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new SwipeToReplyCallback(context(), position -> {
            if (position != RecyclerView.NO_POSITION && position < messageList.size()) {
                MessageAdapter.MessageItem item = messageList.get(position);
                onGeminiGenerateSpecific(item);
                // Reset the swiped view state
                mainHandler.postDelayed(() -> messageAdapter.notifyItemChanged(position), 100);
            }
        }));
        itemTouchHelper.attachToRecyclerView(recyclerMessages);

        int colorPrimary = com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorPrimary, android.graphics.Color.BLUE);
        int colorSurface = com.google.android.material.color.MaterialColors.getColor(requireContext(), com.google.android.material.R.attr.colorSurface, android.graphics.Color.WHITE);
        swipeRefresh.setColorSchemeColors(colorPrimary);
        swipeRefresh.setProgressBackgroundColorSchemeColor(colorSurface);

        swipeRefresh.setOnRefreshListener(() -> {
            if (!isLoading && visibleMessagesCount < totalMessagesCount && oldestMessageTimestamp != null) {
                fetchMessages(oldestMessageTimestamp);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });

        recyclerMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (dy < 0) {
                    int firstVisibleItem = layoutManager.findFirstVisibleItemPosition();
                    if (firstVisibleItem <= 10 && !isLoading && visibleMessagesCount < totalMessagesCount
                            && oldestMessageTimestamp != null) {
                        fetchMessages(oldestMessageTimestamp);
                    }
                }
            }
        });

        btnSend.setOnClickListener(v -> sendMessage());
        editMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            if (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER
                    && event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                if (!event.isShiftPressed()) {
                    sendMessage();
                    return true;
                }
            }
            return false;
        });

        MaterialButton btnEphemeralToggle = view.findViewById(R.id.btn_ephemeral_toggle);
        LinearLayout bottomBar = view.findViewById(R.id.bottom_bar_container);
        btnEphemeralToggle.setOnClickListener(v -> {
            isEphemeral = !isEphemeral;
            updateEphemeralUI(btnEphemeralToggle, bottomBar);
        });
        updateEphemeralUI(btnEphemeralToggle, bottomBar);

        MaterialButton btnAttachPhoto = view.findViewById(R.id.btn_attach_photo);
        btnAttachPhoto.setOnClickListener(v -> showPhotoPicker());

        if (conversationLink == null || conversationLink.isEmpty()) {
            if (getActivity() instanceof MainActivity) {
                conversationLink = ((MainActivity) getActivity()).findConversationForUser(otherUserId);
            }
        }

        if (conversationLink != null && !conversationLink.isEmpty()) {
            fetchMessages(null);
        } else if (otherUserId != null && !otherUserId.isEmpty()) {
            searchConversationLinkAndFetch(otherUserId, 0);
        }

        requireActivity().addMenuProvider(new androidx.core.view.MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                if (getActivity() instanceof ConversationActivity) {
                    menuInflater.inflate(R.menu.menu_conversation, menu);
                } else {
                    menu.clear();
                }
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                return handleMenuItemSelected(menuItem);
            }
        }, getViewLifecycleOwner(), androidx.lifecycle.Lifecycle.State.RESUMED);
    }

    // Migrated to MenuProvider
    // Migrated logic to MenuProvider
    public boolean handleMenuItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_archive) {
            archiveConversation();
            return true;
        } else if (id == R.id.action_delete) {
            deleteConversation();
            return true;
        }
        return false;
    }

    private final Runnable clearTypingRunnable = () -> {
        View v = getView();
        if (v == null)
            return;
        TextView title = v.findViewById(R.id.toolbar_conv_title);
        if (title != null && otherName != null) {
            title.setText(otherName);
        }
        if (txtTypingIndicator != null) {
            txtTypingIndicator.setVisibility(View.GONE);
        }
    };

    private void showTypingIndicator() {
        View v = getView();
        if (v == null)
            return;
        TextView title = v.findViewById(R.id.toolbar_conv_title);
        if (title != null && otherName != null) {
            title.setText(otherName + getString(R.string.typing_indicator_suffix));

        }
        if (txtTypingIndicator != null && otherName != null) {
            txtTypingIndicator.setText(getString(R.string.typing_indicator_full, otherName));

            txtTypingIndicator.setVisibility(View.VISIBLE);
        }
        mainHandler.removeCallbacks(clearTypingRunnable);
        mainHandler.postDelayed(clearTypingRunnable, 3000);
    }

    private void updateReadReceipts(String readUntil) {
        boolean changed = false;
        for (MessageAdapter.MessageItem msg : messageList) {
            if (msg.fromUserLink != null && msg.fromUserLink.contains(myUserId) && !msg.isRead) {
                if (msg.postedIso8601 != null && msg.postedIso8601.compareTo(readUntil) <= 0) {
                    msg.isRead = true;
                    changed = true;
                }
            }
        }
        if (changed) {
            messageAdapter.notifyDataSetChanged();
        }
    }

    private final BroadcastReceiver sseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("io.github.kgelinas.jalfnotifier.SSE_EVENT".equals(intent.getAction())) {
                String type = intent.getStringExtra("type");
                String data = intent.getStringExtra("data");
                if (data == null || type == null)
                    return;

                try {
                    JSONObject d = new JSONObject(data);
                    String link = d.optString("conversation_link", "");
                    if (link.isEmpty() && d.has("message")) {
                        JSONObject msg = d.optJSONObject("message");
                        if (msg != null) {
                            link = msg.optString("conversation_link", "");
                        }
                    }
                    if (link.isEmpty() && d.has("source")) {
                        JSONObject source = d.optJSONObject("source");
                        if (source != null && source.has("user_link") && otherUserId != null) {
                            String uLink = source.getString("user_link");
                            if (uLink.contains(otherUserId))
                                link = conversationLink;
                        }
                    }
                    AppLogger.log(TAG, "SSE Received event: " + type + ", data contains link: " + link);
                    if (link != null && isSameConversation(link, conversationLink)) {
                        AppLogger.log(TAG, "SSE event matched current conversation: " + type);
                        if ("convo_typing".equals(type)) {
                            showTypingIndicator();
                        } else if ("convo_read_until".equals(type)) {
                            String readUntil = d.optString("read_until", "");
                            if (!readUntil.isEmpty()) {
                                updateReadReceipts(readUntil);
                            }
                        } else if ("convo_all_unread_messages_count_changed".equals(type)
                                || "convo_new_message".equals(type) || "message".equals(type)) {
                            fetchMessages(null);
                            sendReadReceipt();
                        }
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error in sseReceiver onReceive processing", e);
                }
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        if (messageAdapter != null && getContext() != null) {
            AppPrefs prefs = AppPrefs.getInstance(getContext());
            boolean blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);
            // messageAdapter doesn't have setBlurNsfw yet, I should add it or just notifyDataSetChanged since it reads prefs in onBind
            messageAdapter.notifyDataSetChanged();
        }
        updateAiButtonVisibility();
        IntentFilter filter = new IntentFilter("io.github.kgelinas.jalfnotifier.SSE_EVENT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(sseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(sseReceiver, filter);
        }
        sendReadReceipt();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(sseReceiver);
        mainHandler.removeCallbacks(clearTypingRunnable);
    }

    private void setupToolbar(View view, String name, String avatar, String sexIcon) {
        MaterialToolbar toolbar = view.findViewById(R.id.toolbar_conversation);
        if (getActivity() instanceof ConversationActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            activity.setSupportActionBar(toolbar);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        } else {
            // In tablet mode/MainActivity, we use the toolbar directly to avoid leaking
            // into the activity bar
            toolbar.inflateMenu(R.menu.menu_conversation);
            toolbar.setOnMenuItemClickListener(item -> handleMenuItemSelected(item));
        }

        // Navigation listener: if in an activity that isn't MainActivity, finish.
        // If in MainActivity, maybe we don't need it or it clears the detail pane.
        toolbar.setNavigationOnClickListener(v -> {
            if (getActivity() instanceof ConversationActivity) {
                getActivity().finish();
            } else {
                // In dual pane, maybe hide detail or just do nothing
            }
        });

        TextView title = view.findViewById(R.id.toolbar_conv_title);
        ShapeableImageView imgAvatar = view.findViewById(R.id.toolbar_conv_avatar);
        ImageView imgSex = view.findViewById(R.id.toolbar_conv_sex_icon);
        View sexContainer = view.findViewById(R.id.toolbar_conv_sex_icon_container);

        if (name != null)
            title.setText(name);

        imgOnlineIndicator = view.findViewById(R.id.toolbar_conv_online_indicator);
        boolean initialOnline = getArguments() != null && getArguments().getBoolean(ARG_IS_ONLINE, false);
        if (!initialOnline && otherUserId != null && !otherUserId.isEmpty()) {
            JSONObject otherCached = ProfileCacheManager.getInstance().getProfile(otherUserId);
            if (otherCached != null) {
                String onlineVal = otherCached.optString("online", "0");
                initialOnline = "1".equals(onlineVal) || otherCached.optBoolean("online", false)
                        || otherCached.optBoolean("is_online", false);
            }
        }
        if (imgOnlineIndicator != null) {
            imgOnlineIndicator.setVisibility(initialOnline ? View.VISIBLE : View.GONE);
        }

        if (avatar != null && !avatar.isEmpty()) {
            Glide.with(this).load(avatar).circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar).into(imgAvatar);
        }
        if (sexIcon != null && !sexIcon.isEmpty()) {
            sexContainer.setVisibility(View.VISIBLE);
            Glide.with(this).load(sexIcon).into(imgSex);
        } else {
            sexContainer.setVisibility(View.GONE);
        }

        View infoContainer = title.getParent() instanceof View ? (View) title.getParent() : title;
        infoContainer.setOnClickListener(v -> {
            if (otherUserId != null && !otherUserId.isEmpty()) {
                Intent intent = new Intent(context(), ProfileActivity.class);
                intent.putExtra("userId", otherUserId);
                intent.putExtra("name", name);
                intent.putExtra("avatarUrl", avatar);
                startActivity(intent);
            }
        });
    }

    private boolean isFragmentSafe() {
        return isAdded() && getContext() != null;
    }

    private Context context() {
        Context c = getContext();
        return c != null ? c : getActivity();
    }

    private void fetchMessages(String beforeTimestamp) {
        if (conversationLink == null || conversationLink.isEmpty())
            return;
        isLoading = true;
        if (beforeTimestamp == null)
            swipeRefresh.setRefreshing(true);

        String url = getAbsoluteUrl(conversationLink);
        if (url.contains("?")) {
            url += "&count=80";
        } else {
            url += "?count=80";
        }
        if (beforeTimestamp != null && !beforeTimestamp.isEmpty()) {
            try {
                url += "&before=" + java.net.URLEncoder.encode(beforeTimestamp, "UTF-8");
            } catch (Exception e) {
                AppLogger.log(TAG, "Error encoding timestamp in fetchMessages", e);
            }
        }

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Accept", "application/vnd.jalf.convo.conversation+json; charset=utf-8")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                    Toast.makeText(context(), R.string.error_network, Toast.LENGTH_SHORT).show();

                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    isLoading = false;
                    swipeRefresh.setRefreshing(false);
                });
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String body = NetworkUtils.responseToString(r);
                        JSONObject root = new JSONObject(body);

                        if (root.has("messages_count") && beforeTimestamp == null) {
                            totalMessagesCount = root.getInt("messages_count");
                        }

                        if (root.has("can_post") && !"yes".equals(root.getString("can_post"))) {
                            String canPost = root.getString("can_post");
                            mainHandler.post(() -> {
                                if (!isFragmentSafe() || getView() == null)
                                    return;
                                editMessage.setEnabled(false);
                                btnSend.setEnabled(false);
                                if ("blocked_by_user".equals(canPost)) {
                                    editMessage.setHint(R.string.error_blocked_by_user);

                                } else if ("other_temporarily_suspended".equals(canPost)) {
                                    editMessage.setHint(getString(R.string.convo_other_suspended));
                                } else {
                                    editMessage.setHint(R.string.error_cannot_reply);

                                }
                            });
                        }

                        JSONArray msgs = root.optJSONArray("messages");
                        if (msgs == null)
                            msgs = new JSONArray();

                        String otherReadUntil = "";
                        boolean parsedOnline = false;
                        JSONArray otherMembers = root.optJSONArray("other_members");
                        if (otherMembers != null && otherMembers.length() > 0) {
                            JSONObject om = otherMembers.getJSONObject(0);
                            if (!om.isNull("read_until")) {
                                otherReadUntil = om.optString("read_until", "");
                            }
                            String onlineVal = om.optString("online", "0");
                            parsedOnline = "1".equals(onlineVal) || om.optBoolean("online", false)
                                    || om.optBoolean("is_online", false);
                        }

                        final boolean isOnlineResult = parsedOnline;
                        mainHandler.post(() -> {
                            if (isFragmentSafe() && imgOnlineIndicator != null) {
                                imgOnlineIndicator.setVisibility(isOnlineResult ? View.VISIBLE : View.GONE);
                            }
                        });

                        List<MessageAdapter.MessageItem> newItems = new ArrayList<>();
                        for (int i = 0; i < msgs.length(); i++) {
                            JSONObject m = msgs.getJSONObject(i);
                            MessageAdapter.MessageItem item = new MessageAdapter.MessageItem();
                            item.fromUserLink = m.optString("from_user_link");
                            item.messageLink = m.optString("message_link");
                            item.postedIso8601 = m.optString("posted");
                            JSONObject content = m.optJSONObject("content");
                            if (content != null) {
                                item.text = content.optString("text");
                                item.photoUrl = content.optString("fullsize_uri",
                                        content.optString("thumbnail_uri", content.optString("image_uri", "")));
                                item.nsfwRank = StringUtils.extractRankFromLink(content.optString("photo_rating_link", ""));
                            }
                            item.isEphemeral = m.optJSONObject("ephemeral") != null;

                            if (item.fromUserLink != null && item.fromUserLink.endsWith("/" + myUserId)) {
                                if (!otherReadUntil.isEmpty() && item.postedIso8601 != null) {
                                    item.isRead = item.postedIso8601.compareTo(otherReadUntil) <= 0;
                                }
                            }

                            if ((item.text != null && !item.text.isEmpty())
                                    || (item.photoUrl != null && !item.photoUrl.isEmpty())) {
                                newItems.add(item);
                            }
                        }

                        Collections.sort(newItems, (m1, m2) -> {
                            if (m1.postedIso8601 == null && m2.postedIso8601 == null)
                                return 0;
                            if (m1.postedIso8601 == null)
                                return -1;
                            if (m2.postedIso8601 == null)
                                return 1;
                            return m1.postedIso8601.compareTo(m2.postedIso8601);
                        });

                        mainHandler.post(() -> {
                            if (beforeTimestamp == null) {
                                messageList.clear();
                                messageList.addAll(newItems);
                                injectPendingMessageIfAny();
                                if (!newItems.isEmpty()) {
                                    oldestMessageTimestamp = newItems.get(0).postedIso8601;
                                }
                                messageAdapter.notifyDataSetChanged();
                                if (!messageList.isEmpty()) {
                                    recyclerMessages.scrollToPosition(messageList.size() - 1);
                                }
                                sendReadReceipt();
                            } else if (!newItems.isEmpty()) {
                                messageList.addAll(0, newItems);
                                oldestMessageTimestamp = newItems.get(0).postedIso8601;
                                messageAdapter.notifyItemRangeInserted(0, newItems.size());
                            }
                            visibleMessagesCount = messageList.size();
                        });
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error in fetchMessages response processing", e);
                }
            }
        });
    }

    private void sendMessage() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (text.isEmpty())
            return;

        editMessage.setEnabled(false);
        btnSend.setEnabled(false);

        if (conversationLink == null || conversationLink.isEmpty()) {
            if (otherUserId != null && !otherUserId.isEmpty()) {
                startNewConversation(otherUserId, () -> performSendMessage(text));
            } else {
                Toast.makeText(context(), R.string.error_convo_not_initialized, Toast.LENGTH_SHORT).show();

                editMessage.setEnabled(true);
                btnSend.setEnabled(true);
            }
            return;
        }
        performSendMessage(text);
    }

    private void queueAutoMessage() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(context(), R.string.hint_enter_message_queue, Toast.LENGTH_SHORT).show();

            return;
        }

        if (otherUserId == null || otherUserId.isEmpty()) {
            Toast.makeText(context(), R.string.no_user_id_provided, Toast.LENGTH_SHORT).show();

            return;
        }

        try {
            AppPrefs prefs = AppPrefs.getInstance(requireContext());
            String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
            JSONObject pendingMap = new JSONObject(pendingJson);

            JSONObject autoMsg = new JSONObject();
            autoMsg.put("text", text);
            autoMsg.put("convoLink", conversationLink != null ? conversationLink : "");

            pendingMap.put(otherUserId, autoMsg);
            prefs.edit().putString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, pendingMap.toString()).apply();

            Toast.makeText(context(), R.string.message_queued, Toast.LENGTH_SHORT).show();

            editMessage.setText("");
            fetchMessages(null); // Refresh to show pending message
        } catch (Exception e) {
            Toast.makeText(context(), R.string.failed_to_queue, Toast.LENGTH_SHORT).show();

        }
    }

    private void performSendMessage(String text) {
        JSONObject payload = new JSONObject();
        try {
            payload.put("text", text);
            payload.put("ephemeral", isEphemeral);
        } catch (Exception ignored) {
        }

        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.newmessage.text+json"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    editMessage.setEnabled(true);
                    btnSend.setEnabled(true);
                    Toast.makeText(context(), R.string.failed_to_send, Toast.LENGTH_SHORT).show();

                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    mainHandler.post(() -> {
                        if (!isFragmentSafe()) return;
                        editMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        if (r.isSuccessful()) {
                            editMessage.setText("");
                            fetchMessages(null);
                        } else {
                            Toast.makeText(context(), R.string.error_sending, Toast.LENGTH_SHORT).show();

                        }
                    });
                }
            }
        });
    }

    private void updateButtonStates() {
        String text = editMessage.getText() != null ? editMessage.getText().toString().trim() : "";
        boolean hasText = !text.isEmpty();
        btnSend.setEnabled(hasText);
        btnQueueMessage.setEnabled(hasText);
        btnGeminiGenerate.setEnabled(hasText || true); // Keep Gemini enabled for brainstorming? Let's say yes but
                                                       // usually it needs context.
        // User only specified send and queue buttons.
    }

    private void updateAiButtonVisibility() {
        if (getContext() == null || btnGeminiGenerate == null) return;
        AppPrefs prefs = AppPrefs.getInstance(getContext());
        String provider = prefs.getString(ApiConstants.KEY_AI_PROVIDER, "Google Gemini").trim();
        String savedToken = prefs.getString(ApiConstants.KEY_AI_TOKEN, "").trim();
        String model = prefs.getString(ApiConstants.KEY_GEMINI_MODEL, "").trim();

        boolean needsToken = "Google Gemini".equals(provider) || "OpenRouter".equals(provider);
        boolean isConfigured;
        if (!needsToken) {
            isConfigured = !model.isEmpty();
        } else {
            isConfigured = !savedToken.isEmpty() || !ApiConstants.GEMINI_API_KEY.isEmpty();
        }

        btnGeminiGenerate.setVisibility(isConfigured ? View.VISIBLE : View.GONE);
    }

    private void setAiGeneratingState(boolean generating, String status) {
        mainHandler.post(() -> {
            if (btnGeminiGenerate != null) {
                btnGeminiGenerate.setEnabled(!generating);
            }
            if (generating) {
                if (aiLoadingItem == null) {
                    aiLoadingItem = new MessageAdapter.MessageItem();
                    aiLoadingItem.isAiLoading = true;
                    aiLoadingItem.loadingStatus = status;
                    messageList.add(aiLoadingItem);
                    messageAdapter.notifyItemInserted(messageList.size() - 1);
                    recyclerMessages.scrollToPosition(messageList.size() - 1);
                } else {
                    aiLoadingItem.loadingStatus = status;
                    int pos = messageList.indexOf(aiLoadingItem);
                    if (pos != -1) {
                        messageAdapter.notifyItemChanged(pos);
                    }
                }
            } else {
                if (aiLoadingItem != null) {
                    int pos = messageList.indexOf(aiLoadingItem);
                    if (pos != -1) {
                        messageList.remove(pos);
                        messageAdapter.notifyItemRemoved(pos);
                    }
                    aiLoadingItem = null;
                }
            }
        });
    }

    private void injectPendingMessageIfAny() {
        if (otherUserId == null || otherUserId.isEmpty())
            return;
        try {
            AppPrefs prefs = AppPrefs.getInstance(requireContext());
            String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
            JSONObject pendingMap = new JSONObject(pendingJson);
            if (pendingMap.has(otherUserId)) {
                JSONObject autoMsg = pendingMap.getJSONObject(otherUserId);
                MessageAdapter.MessageItem pendingItem = new MessageAdapter.MessageItem();
                pendingItem.text = autoMsg.optString("text");
                pendingItem.fromUserLink = "/rest/profiles/" + myUserId; // Local dummy
                pendingItem.isPending = true;
                messageList.add(pendingItem);
            }
        } catch (Exception ignored) {
        }
    }

    private void restorePendingMessage(MessageAdapter.MessageItem item) {
        if (otherUserId == null || otherUserId.isEmpty())
            return;
        try {
            AppPrefs prefs = AppPrefs.getInstance(requireContext());
            String pendingJson = prefs.getString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, "{}");
            JSONObject pendingMap = new JSONObject(pendingJson);
            if (pendingMap.has(otherUserId)) {
                pendingMap.remove(otherUserId);
                prefs.edit().putString(ApiConstants.KEY_PENDING_AUTO_MESSAGES, pendingMap.toString()).apply();

                editMessage.setText(item.text);
                fetchMessages(null); // Refresh UI
            }
        } catch (Exception ignored) {
        }
    }

    private void showPhotoPicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(context());
        BottomSheetUtils.setupFullHeight(dialog);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_photo_picker, null);
        dialog.setContentView(view);

        AutoCompleteTextView spinnerAlbums = view.findViewById(R.id.spinner_albums);
        View progress = view.findViewById(R.id.progress_photos);
        RecyclerView recyclerPickerPhotos = view.findViewById(R.id.recycler_picker_photos);

        recyclerPickerPhotos.setLayoutManager(new GridLayoutManager(context(), 3));
        List<PhotoPickerAdapter.PhotoItem> photoItems = new ArrayList<>();
        PhotoPickerAdapter adapter = new PhotoPickerAdapter(photoItems, photoLink -> {
            dialog.dismiss();
            sendPhoto(photoLink);
        });
        recyclerPickerPhotos.setAdapter(adapter);

        dialog.show();
        fetchAlbums(spinnerAlbums, progress, photoItems, adapter);
    }

    private void fetchAlbums(AutoCompleteTextView spinner, View progress, List<PhotoPickerAdapter.PhotoItem> photoItems,
            PhotoPickerAdapter adapter) {
        progress.setVisibility(View.VISIBLE);
        String url = ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/photos/albums";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    progress.setVisibility(View.GONE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                        JSONArray items = root.optJSONArray("items");
                        if (items == null)
                            items = root.optJSONArray("user_photos_albums");
                        if (items == null)
                            items = root.optJSONArray("photos_albums");
                        if (items == null)
                            items = new JSONArray();

                        List<String> albumNames = new ArrayList<>();
                        List<String> albumLinks = new ArrayList<>();

                        for (int i = 0; i < items.length(); i++) {
                            JSONObject a = items.getJSONObject(i);
                            albumNames.add(a.optString("name", a.optString("description", "Album")));
                            String link = a.optString("user_photos_album_link");
                            if (link.isEmpty())
                                link = a.optString("user_photos_link");
                            albumLinks.add(link);
                        }

                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            progress.setVisibility(View.GONE);
                            if (!albumNames.isEmpty()) {
                                ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(context(),
                                        android.R.layout.simple_dropdown_item_1line, albumNames);
                                spinner.setAdapter(spinAdapter);
                                spinner.setOnItemClickListener((parent, v, position, id) -> {
                                    fetchPhotosDialog(albumLinks.get(position), progress, photoItems, adapter);
                                });
                                // Automatically load first album
                                spinner.setText(albumNames.get(0), false);
                                fetchPhotosDialog(albumLinks.get(0), progress, photoItems, adapter);
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            progress.setVisibility(View.GONE);
                        });
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error in fetchAlbums onResponse", e);
                    mainHandler.post(() -> {
                        if (!isFragmentSafe()) return;
                        progress.setVisibility(View.GONE);
                    });
                }
            }
        });
    }

    private void fetchPhotosDialog(String albumLink, View progress, List<PhotoPickerAdapter.PhotoItem> photoItems,
            PhotoPickerAdapter adapter) {
        if (albumLink == null || albumLink.isEmpty())
            return;
        progress.setVisibility(View.VISIBLE);
        photoItems.clear();
        adapter.notifyDataSetChanged();

        String url = getAbsoluteUrl(albumLink);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    progress.setVisibility(View.GONE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject root = new JSONObject(NetworkUtils.responseToString(r));
                        JSONObject photosSelection = root.optJSONObject("photos_selection");
                        JSONArray items = null;

                        if (photosSelection != null) {
                            items = photosSelection.optJSONArray("photos");
                        }

                        if (items == null && root.has("items")) {
                            JSONArray rootItems = root.optJSONArray("items");
                            if (rootItems != null && rootItems.length() > 0) {
                                JSONObject firstItem = rootItems.optJSONObject(0);
                                if (firstItem != null && firstItem.has("photos_selection")) {
                                    JSONObject ps = firstItem.optJSONObject("photos_selection");
                                    if (ps != null) {
                                        items = ps.optJSONArray("photos");
                                    }
                                } else {
                                    items = rootItems;
                                }
                            }
                        }

                        if (items == null)
                            items = new JSONArray();

                        List<PhotoPickerAdapter.PhotoItem> newPhotos = new ArrayList<>();
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject p = items.getJSONObject(i);
                            String photoLink = p.optString("photo_link");
                            String thumb = p.optString("image_uri",
                                    p.optString("image_link",
                                            p.optString("thumbnail_uri",
                                                    p.optString("thumbnail_link",
                                                            p.optString("thumb_url",
                                                                    p.optString("large_url", ""))))));

                            if (!photoLink.isEmpty()) {
                                newPhotos.add(new PhotoPickerAdapter.PhotoItem(photoLink, thumb));
                            } else if (!thumb.isEmpty()) {
                                // Some API versions might use the thumb URL itself if photoLink is missing
                                newPhotos.add(new PhotoPickerAdapter.PhotoItem(thumb, thumb));
                            }
                        }

                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            progress.setVisibility(View.GONE);
                            photoItems.addAll(newPhotos);
                            adapter.notifyDataSetChanged();
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            progress.setVisibility(View.GONE);
                        });
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error in fetchPhotosDialog onResponse", e);
                    mainHandler.post(() -> {
                        if (!isFragmentSafe()) return;
                        progress.setVisibility(View.GONE);
                    });
                }
            }
        });
    }

    private void sendPhoto(String photoLink) {
        if (conversationLink == null || conversationLink.isEmpty())
            return;

        JSONObject payload = new JSONObject();
        try {
            payload.put("photo_link", photoLink);
            payload.put("ephemeral", isEphemeral);
        } catch (Exception e) {
            AppLogger.log(TAG, "Error building payload in sendPhoto", e);
        }

        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.newmessage.photo+json"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    Toast.makeText(context(), R.string.error_failed_to_send_photo, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            fetchMessages(null);
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), getString(R.string.error_sending_photo_code), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void showGeminiMenu(View v) {
        AppPrefs prefs = AppPrefs.getInstance(context());
        String savedToken = prefs.getString(ApiConstants.KEY_AI_TOKEN, "").trim();
        if (savedToken.isEmpty() && ApiConstants.GEMINI_API_KEY.isEmpty()) {
            android.widget.Toast.makeText(context(), R.string.gemini_error_no_token,
                    android.widget.Toast.LENGTH_LONG).show();
            startActivity(new android.content.Intent(context(), SettingsIntelligenceActivity.class));
            return;
        }

        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(context(), v);
        popup.getMenu().add(0, 1, 0, R.string.gemini_menu_introduction);
        popup.getMenu().add(0, 2, 1, R.string.gemini_menu_reply);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                onGeminiGenerate(false);
                return true;
            } else if (item.getItemId() == 2) {
                onGeminiGenerate(true);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void onGeminiGenerate(boolean isReply) {
        if (otherUserId == null || otherUserId.isEmpty())
            return;

        setAiGeneratingState(true, isReply ? getString(R.string.gemini_status_reply) : getString(R.string.gemini_status_introduction));

        String history = "";
        if (isReply) {
            StringBuilder sb = new StringBuilder();
            int start = Math.max(0, messageList.size() - 10);
            for (int i = start; i < messageList.size(); i++) {
                MessageAdapter.MessageItem item = messageList.get(i);
                if (item.text == null || item.text.isEmpty())
                    continue;

                String senderLabel = (item.fromUserLink != null && item.fromUserLink.contains(myUserId)) ? "[Moi]"
                        : ("[" + (otherName != null && !otherName.isEmpty() ? otherName : "L'autre") + "]");
                sb.append(senderLabel).append(": ").append(item.text).append("\n");
            }
            history = sb.toString();
        }
        final String finalHistory = history;

        // 1. Fetch current user's profile first
        AppPrefs prefs = AppPrefs.getInstance(context());
        String myUserId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        if (myUserId.isEmpty()) {
            fetchOtherProfileOnly(finalHistory);
            return;
        }

        // 1. Check cache first
        JSONObject myCached = myUserId.isEmpty() ? null : ProfileCacheManager.getInstance().getProfile(myUserId);
        JSONObject otherCached = ProfileCacheManager.getInstance().getProfile(otherUserId);

        if (otherCached != null && (myUserId.isEmpty() || myCached != null)) {
            AppLogger.log(TAG, "Using cached profiles for Gemini");
            callGeminiApi(myCached, otherCached, finalHistory, null);
            return;
        }

        if (myCached != null) {
            fetchOtherProfile(myCached, finalHistory);
            return;
        }

        String myUrl = ApiConstants.BASE_URL + "/rest/users/" + myUserId;
        Request myRequest = new Request.Builder()
                .url(myUrl)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(myRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // If my profile fails, try to continue with other profile only
                fetchOtherProfileOnly(finalHistory);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    JSONObject myProfile = null;
                    if (r.isSuccessful() && r.body() != null) {
                        myProfile = new JSONObject(NetworkUtils.responseToString(r));
                        ProfileCacheManager.getInstance().putProfile(context(), myUserId, myProfile);
                    }
                    final JSONObject finalMyProfile = myProfile;
                    fetchOtherProfile(finalMyProfile, finalHistory);
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error fetching my profile details", e);
                    fetchOtherProfileOnly(finalHistory);
                }
            }
        });
    }

    private void fetchOtherProfileOnly(String history) {
        fetchOtherProfile(null, history);
    }

    private void fetchOtherProfile(JSONObject myProfile, String history) {
        String url = ApiConstants.BASE_URL + "/rest/users/" + otherUserId;
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    setAiGeneratingState(false, null);
                    Toast.makeText(context(), R.string.error_fetching_profile_ai, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        JSONObject otherProfile = new JSONObject(NetworkUtils.responseToString(r));
                        ProfileCacheManager.getInstance().putProfile(context(), otherUserId, otherProfile);

                        String onlineVal = otherProfile.optString("online", "0");
                        boolean isOnline = "1".equals(onlineVal) || otherProfile.optBoolean("online", false)
                                || otherProfile.optBoolean("is_online", false);
                        mainHandler.post(() -> {
                            if (isFragmentSafe() && imgOnlineIndicator != null) {
                                imgOnlineIndicator.setVisibility(isOnline ? View.VISIBLE : View.GONE);
                            }
                        });

                        callGeminiApi(myProfile, otherProfile, history, null);
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            setAiGeneratingState(false, null);
                            Toast.makeText(context(), R.string.error_fetching_profile, Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Error fetching other profile details", e);
                    setAiGeneratingState(false, null);
                }
            }
        });
    }

    private void onGeminiGenerateSpecific(MessageAdapter.MessageItem item) {
        String specificMessage = item.text;
        setAiGeneratingState(true, getString(R.string.gemini_status_targeted));

        AppPrefs prefs = AppPrefs.getInstance(context());
        JSONObject myCached = myUserId.isEmpty() ? null : ProfileCacheManager.getInstance().getProfile(myUserId);
        JSONObject otherCached = ProfileCacheManager.getInstance().getProfile(otherUserId);

        boolean forceNetwork = (myCached == null && !myUserId.isEmpty()) || otherCached == null;
        StringBuilder sb = new StringBuilder();
        int count = Math.min(messageList.size(), 10);
        for (int i = Math.max(0, messageList.size() - count); i < messageList.size(); i++) {
            MessageAdapter.MessageItem mi = messageList.get(i);
            if (mi.isPending)
                continue;
            boolean isMine = mi.fromUserLink != null && mi.fromUserLink.endsWith("/" + myUserId);
            sb.append(isMine ? "Moi: " : "L'autre: ").append(mi.text).append("\n");
        }
        String finalHistory = sb.toString();

        if (!forceNetwork) {
            try {
                Log.d(TAG, "Using cached profiles for Gemini (Contextual)");
                callGeminiApi(myCached, otherCached, finalHistory, specificMessage);
            } catch (Exception e) {
                AppLogger.log(TAG, "Error using cached profiles for context AI", e);
                forceNetwork = true;
            }
        }

        if (forceNetwork) {
            if (otherUserId == null || otherUserId.isEmpty()) {
                setAiGeneratingState(false, null);
                return;
            }
            // Inline fetch to avoid disrupting original fetchOtherProfile signature
            String url = ApiConstants.BASE_URL + "/rest/users/" + otherUserId;
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("x-csrftoken", suid)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                    mainHandler.post(() -> {
                        if (!isFragmentSafe()) return;
                        setAiGeneratingState(false, null);
                        Toast.makeText(context(), R.string.error_fetching_profile_ai, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response)
                        throws java.io.IOException {
                    try (okhttp3.Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            try {
                                JSONObject fetchedOther = new JSONObject(NetworkUtils.responseToString(r));
                                ProfileCacheManager.getInstance().putProfile(context(), otherUserId, fetchedOther);

                                String onlineVal = fetchedOther.optString("online", "0");
                                boolean isOnline = "1".equals(onlineVal) || fetchedOther.optBoolean("online", false)
                                        || fetchedOther.optBoolean("is_online", false);
                                mainHandler.post(() -> {
                                    if (isFragmentSafe() && imgOnlineIndicator != null) {
                                        imgOnlineIndicator.setVisibility(isOnline ? View.VISIBLE : View.GONE);
                                    }
                                });

                                callGeminiApi(myCached, fetchedOther, finalHistory, specificMessage);
                            } catch (Exception e) {
                                mainHandler.post(() -> {
                                    if (!isFragmentSafe()) return;
                                    setAiGeneratingState(false, null);
                                    Toast.makeText(context(), R.string.error_parsing_profile, Toast.LENGTH_SHORT).show();
                                });
                            }
                        } else {
                            mainHandler.post(() -> {
                                if (!isFragmentSafe()) return;
                                setAiGeneratingState(false, null);
                                Toast.makeText(context(), R.string.error_fetching_profile, Toast.LENGTH_SHORT).show();
                            });
                        }
                    } catch (Exception e) {
                        AppLogger.log(TAG, "Error executing context AI on response callback", e);
                        setAiGeneratingState(false, null);
                    }
                }
            });
        }
    }

    private String formatProfileForPrompt(JSONObject profile) {
        if (profile == null) return "";
        StringBuilder sb = new StringBuilder();
        try {
            if (profile.has("pseudo") || profile.has("name")) {
                sb.append("Pseudo: ").append(profile.optString("pseudo", profile.optString("name", ""))).append("\n");
            }
            if (profile.has("age")) {
                sb.append("Âge: ").append(profile.optString("age", "")).append("\n");
            }
            if (profile.has("city")) {
                sb.append("Ville: ").append(profile.optString("city", "")).append("\n");
            }
            if (profile.has("description")) {
                String desc = profile.optString("description", "");
                if (desc.length() > 300) {
                    desc = desc.substring(0, 300) + "...";
                }
                sb.append("Description: ").append(desc).append("\n");
            }
            if (profile.has("looking_for")) {
                sb.append("Recherche: ").append(profile.optString("looking_for", "")).append("\n");
            }
            if (profile.has("relational_status")) {
                sb.append("Statut: ").append(profile.optString("relational_status", "")).append("\n");
            }
        } catch (Exception e) {
            AppLogger.log(TAG, "Error formatting profile for prompt", e);
        }
        return sb.toString();
    }

    private void callGeminiApi(JSONObject myProfile, JSONObject otherProfile, String history,
            String specificContextMessage) {
        AppPrefs prefs = AppPrefs.getInstance(context());
        String lang = prefs.getString(ApiConstants.KEY_GEMINI_LANGUAGE, "Français");
        String userPref = prefs.getString(ApiConstants.KEY_GEMINI_PREFERENCE, "");
        String model = prefs.getString(ApiConstants.KEY_GEMINI_MODEL, "models/gemini-1.5-flash");

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Tu es un assistant qui aide à écrire un message sur le réseau social JALF.\n");

        if (myProfile != null) {
            promptBuilder.append("Mon profil :\n").append(formatProfileForPrompt(myProfile)).append("\n");
        }

        if (otherProfile != null) {
            promptBuilder.append("Profil de l'autre personne :\n").append(formatProfileForPrompt(otherProfile)).append("\n");
        }

        if (userPref != null && !userPref.isEmpty()) {
            promptBuilder.append("Mes préférences : ").append(userPref).append("\n");
        }

        if (history != null && !history.isEmpty()) {
            promptBuilder.append("Historique de la conversation :\n").append(history).append("\n");
        }

        if (specificContextMessage != null && !specificContextMessage.isEmpty()) {
            promptBuilder.append(
                    "\nJe veux que tu répondes spécifiquement, directement, et personnellement au message exact suivant : \"")
                    .append(specificContextMessage).append("\"\n");
            promptBuilder.append("Ton rôle est de générer UNE proposition de réponse, en ").append(lang)
                    .append(", qui s'adresse au sujet de ce message précis.\n");
        } else if (history != null && !history.isEmpty()) {
            promptBuilder.append("C'est à mon tour de répondre. Écris un court message de réponse en ").append(lang)
                    .append(" qui soit naturel et engageant.\n");
        } else {
            promptBuilder.append("Écris un court message d'accroche ou de réponse en ").append(lang)
                    .append(" qui soit naturel et engageant.\n");
        }
        promptBuilder.append("Ne sors QUE le texte du message, sans guillemets.");

        String prompt = promptBuilder.toString();
        String savedToken = prefs.getString(ApiConstants.KEY_AI_TOKEN, "").trim();
        String tokenToUse = savedToken.isEmpty() ? ApiConstants.GEMINI_API_KEY.trim() : savedToken;
        String endpoint = prefs.getString(ApiConstants.KEY_AI_ENDPOINT, "https://generativelanguage.googleapis.com/v1beta/openai").trim();

        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }

        String apiUrl = endpoint;
        if (!apiUrl.endsWith("/chat/completions")) {
            if (apiUrl.endsWith("/openai")) {
                apiUrl = apiUrl + "/chat/completions";
            } else if (apiUrl.contains("/v1")) {
                apiUrl = apiUrl + "/chat/completions";
            } else {
                apiUrl = apiUrl + "/v1/chat/completions";
            }
        }

        JSONObject payload = new JSONObject();
        try {
            payload.put("model", model);

            JSONArray messages = new JSONArray();
            JSONObject message = new JSONObject();
            message.put("role", "user");
            message.put("content", prompt);
            messages.put(message);
            payload.put("messages", messages);
        } catch (Exception ignored) {}

        RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json"));
        Request.Builder requestBuilder = new Request.Builder()
                .url(apiUrl)
                .post(body);

        if (!tokenToUse.isEmpty()) {
            requestBuilder.addHeader("Authorization", "Bearer " + tokenToUse);
        }

        Request request = requestBuilder.build();

        okhttp3.OkHttpClient aiClient = client.newBuilder()
                .connectTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        aiClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                AppLogger.log(TAG, "AI API request failed", e);
                mainHandler.post(() -> {
                    if (!isAdded() || getContext() == null) return;
                    setAiGeneratingState(false, null);
                    String errMsg = getString(R.string.gemini_error_api_failure) + " (" + e.getMessage() + ")";
                    Toast.makeText(context(), errMsg, Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        String bodyString = r.body().string();
                        try {
                            JSONObject root = new JSONObject(bodyString);
                            String aiText = "";
                            if (root.has("choices")) {
                                aiText = root.getJSONArray("choices")
                                        .getJSONObject(0)
                                        .getJSONObject("message")
                                        .getString("content");
                            } else if (root.has("candidates")) {
                                aiText = root.getJSONArray("candidates")
                                        .getJSONObject(0)
                                        .getJSONObject("content")
                                        .getJSONArray("parts")
                                        .getJSONObject(0)
                                        .getString("text");
                            }

                            final String finalAiText = aiText;
                            mainHandler.post(() -> {
                                if (!isAdded() || getContext() == null) return;
                                setAiGeneratingState(false, null);
                                editMessage.setText(finalAiText.trim());
                                editMessage.setSelection(editMessage.getText().length());
                            });
                        } catch (Exception e) {
                            AppLogger.log(TAG, "Error parsing Gemini response: " + bodyString, e);
                            mainHandler.post(() -> {
                                if (!isAdded() || getContext() == null) return;
                                setAiGeneratingState(false, null);
                                Toast.makeText(context(), R.string.gemini_error_blocked,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    } else {
                        mainHandler.post(() -> {
                            if (!isAdded() || getContext() == null) return;
                            setAiGeneratingState(false, null);
                            Toast.makeText(context(), getString(R.string.gemini_error_api_error), Toast.LENGTH_SHORT).show();
                        });
                    }
                } catch (Exception e) {
                    AppLogger.log(TAG, "Exception during Gemini request", e);
                    mainHandler.post(() -> {
                        if (!isAdded() || getContext() == null) return;
                        setAiGeneratingState(false, null);
                        Toast.makeText(context(), R.string.gemini_error_exception, Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }

    private void showQuickResponseMenu() {
        Context ctx = context();
        AppPrefs prefs = AppPrefs.getInstance(ctx);
        String myId = prefs.getString(ApiConstants.KEY_USER_ID, "");
        String key = ApiConstants.KEY_QUICK_RESPONSES;
        if (!myId.isEmpty()) {
            key = ApiConstants.KEY_QUICK_RESPONSES + "_" + myId;
        }
        String json = prefs.getString(key, "[]");

        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) {
                Toast.makeText(ctx, R.string.settings_quick_responses_none, Toast.LENGTH_LONG).show();
                return;
            }

            BottomSheetDialog dialog = new BottomSheetDialog(ctx);
            BottomSheetUtils.setupFullHeight(dialog);
            LinearLayout layout = new LinearLayout(ctx);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));

            TextView title = new TextView(ctx);
            title.setText(R.string.quick_responses_title);
            TextViewCompat.setTextAppearance(title,
                    com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
            title.setPadding(0, 0, 0, dpToPx(16));
            layout.addView(title);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                String label = obj.optString("label", "");
                String message = obj.optString("message", "");

                if (label.isEmpty() || message.isEmpty())
                    continue;

                MaterialButton btn = new MaterialButton(ctx, null,
                        com.google.android.material.R.attr.materialButtonStyle);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, dpToPx(8));
                btn.setLayoutParams(lp);
                btn.setText(label);
                btn.setAllCaps(false);
                btn.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

                btn.setOnClickListener(v -> {
                    String finalMsg = message;
                    if (otherName != null) {
                        finalMsg = finalMsg.replace("{name}", otherName);
                    }
                    editMessage.setText(finalMsg);
                    if (editMessage.getText() != null) {
                        editMessage.setSelection(editMessage.getText().length());
                    }
                    dialog.dismiss();
                });

                layout.addView(btn);
            }

            ScrollView scrollView = new ScrollView(ctx);
            scrollView.addView(layout);
            dialog.setContentView(scrollView);
            dialog.show();

        } catch (Exception e) {
            AppLogger.log("ConversationFragment", "Error showing quick responses", e);
        }
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()));
    }

    private void archiveConversation() {
        if (conversationLink == null || conversationLink.isEmpty())
            return;

        JSONObject payload = new JSONObject();
        try {
            payload.put("folder_link", "/rest/users/" + myUserId + "/conversations/archived");
        } catch (Exception e) {
            AppLogger.log(TAG, "Error putting folder_link in archiveConversation", e);
        }

        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.conversationchange+json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    Toast.makeText(context(), R.string.error_failed_to_archive, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), R.string.status_archived, Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof ConversationActivity) {
                                getActivity().finish();
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), getString(R.string.error_archive_code), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void deleteConversation() {
        if (conversationLink == null || conversationLink.isEmpty())
            return;

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .delete()
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    Toast.makeText(context(), R.string.error_failed_to_delete, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), R.string.status_deleted, Toast.LENGTH_SHORT).show();
                            if (getActivity() instanceof ConversationActivity) {
                                getActivity().finish();
                            }
                        });
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), getString(R.string.error_delete_code), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            }
        });
    }

    private void sendReadReceipt() {
        if (conversationLink == null || conversationLink.isEmpty())
            return;

        AppPrefs prefs = AppPrefs.getInstance(requireContext());
        boolean shouldSend = prefs.getBoolean(ApiConstants.KEY_SEND_READ_RECEIPTS, true);
        if (!shouldSend)
            return;

        JSONObject payload = new JSONObject();
        try {
            payload.put("read_until", "now");
        } catch (Exception e) {
            AppLogger.log("ConversationFragment", "Error putting read_until in sendReadReceipt", e);
        }

        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.conversationchange+json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(getAbsoluteUrl(conversationLink))
                .patch(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("Accept", "*/*")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                AppLogger.log("ConversationFragment", "Failed to send read receipt", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    AppLogger.log("ConversationFragment", "Read receipt status: " + r.code());
                }
            }
        });
    }

    private String getAbsoluteUrl(String path) {
        if (path == null)
            return "";
        return path.startsWith("http") ? path : ApiConstants.BASE_URL + path;
    }

    private boolean isSameConversation(String link1, String link2) {
        return StringUtils.isSameConversation(link1, link2);
    }

    private void searchConversationLinkAndFetch(String targetUserId, int offset) {
        String[] endpoints = {
                "/rest/users/" + myUserId + "/conversations/active",
                "/rest/users/" + myUserId + "/conversations/new",
                "/rest/users/" + myUserId + "/conversations/archived"
        };
        searchConversationEndpoints(targetUserId, endpoints, 0, offset);
    }

    private void searchConversationEndpoints(String targetUserId, String[] endpoints, int endpointIndex, int offset) {
        if (endpointIndex >= endpoints.length) {
            mainHandler.post(() -> {
                if (!isFragmentSafe()) return;
                startNewConversation(targetUserId, null);
            });
            return;
        }

        int count = 50;
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + endpoints[endpointIndex] + "?offset=" + offset + "&count=" + count)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                searchConversationEndpoints(targetUserId, endpoints, endpointIndex + 1, 0);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful() && r.body() != null) {
                        try {
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
                                            conversationLink = obj.optString("conversation_link", "");
                                            // Update MainActivity cache if we found it here
                                            if (getActivity() instanceof MainActivity) {
                                                getActivity()
                                                        .getSharedPreferences("ConversationLinks", Context.MODE_PRIVATE)
                                                        .edit().putString(targetUserId, conversationLink).apply();
                                            }
                                            mainHandler.post(() -> {
                                                if (!isFragmentSafe()) return;
                                                fetchMessages(null);
                                            });
                                            return;
                                        }
                                    }
                                }
                                // Try next page on same endpoint if not found
                                if (total > offset + count) {
                                    searchConversationEndpoints(targetUserId, endpoints, endpointIndex, offset + count);
                                    return;
                                }
                            }
                        } catch (Exception e) {
                            AppLogger.log(TAG, "Error parsing search endpoints JSON response", e);
                        }
                    }
                    // Not found in this endpoint (even after pages), move to next one
                    searchConversationEndpoints(targetUserId, endpoints, endpointIndex + 1, 0);
                }
            }
        });
    }

    private void startNewConversation(String userId, Runnable onSuccess) {
        if (userId == null || userId.isEmpty())
            return;

        JSONObject payload = new JSONObject();
        try {
            JSONArray members = new JSONArray();
            members.put("/rest/users/" + userId);
            payload.put("other_members", members);
        } catch (Exception e) {
            AppLogger.log(TAG, "Error building new conversation payload", e);
        }

        RequestBody body = RequestBody.create(payload.toString(),
                MediaType.parse("application/vnd.jalf.convo.newconversation+json"));

        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/rest/users/" + myUserId + "/conversations/active")
                .post(body)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> {
                    if (!isFragmentSafe()) return;
                    Toast.makeText(context(), R.string.error_failed_to_start_convo, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String location = r.header("Location");
                        String bodyStr = NetworkUtils.responseToString(r);

                        if (location != null && !location.isEmpty()) {
                            conversationLink = location;
                        } else if (!bodyStr.isEmpty()) {
                            try {
                                if (bodyStr.startsWith("{")) {
                                    JSONObject json = new JSONObject(bodyStr);
                                    conversationLink = json.optString("conversation_link",
                                            json.optString("link", ""));
                                } else if (bodyStr.startsWith("/")) {
                                    conversationLink = bodyStr;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        if (conversationLink != null && !conversationLink.isEmpty()) {
                            mainHandler.post(() -> {
                                if (!isFragmentSafe()) return;
                                fetchMessages(null);
                                if (onSuccess != null) {
                                    onSuccess.run();
                                }
                            });
                        } else {
                            mainHandler.post(() -> {
                                if (!isFragmentSafe()) return;
                                Toast.makeText(context(), getString(R.string.error_starting_convo_code), Toast.LENGTH_SHORT)
                                    .show();
                                if (onSuccess != null) {
                                    editMessage.setEnabled(true);
                                    btnSend.setEnabled(true);
                                }
                            });
                        }
                    } else {
                        mainHandler.post(() -> {
                            if (!isFragmentSafe()) return;
                            Toast.makeText(context(), getString(R.string.error_starting_convo_code), Toast.LENGTH_SHORT)
                                    .show();
                        });
                    }
                }
            }
        });
    }


    private void updateEphemeralUI(MaterialButton btn, LinearLayout container) {
        if (container == null || btn == null) return;
        
        int errorColor = com.google.android.material.color.MaterialColors.getColor(container, com.google.android.material.R.attr.colorError);
        int errorContainer = com.google.android.material.color.MaterialColors.getColor(container, com.google.android.material.R.attr.colorErrorContainer);
        int normalTint = com.google.android.material.color.MaterialColors.getColor(container, com.google.android.material.R.attr.colorOnSurfaceVariant);
        int normalBg = com.google.android.material.color.MaterialColors.getColor(container, com.google.android.material.R.attr.colorSurfaceVariant);

        if (isEphemeral) {
            btn.setIconResource(R.drawable.ic_lock_24);
            btn.setIconTint(android.content.res.ColorStateList.valueOf(errorColor));
            container.setBackgroundColor(errorContainer);
        } else {
            btn.setIconResource(R.drawable.ic_lock_open_24);
            btn.setIconTint(android.content.res.ColorStateList.valueOf(normalTint));
            container.setBackgroundColor(normalBg);
        }
    }
}
