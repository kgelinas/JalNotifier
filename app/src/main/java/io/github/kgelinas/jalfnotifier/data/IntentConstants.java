package io.github.kgelinas.jalfnotifier.data;

/**
 * BroadcastReceiver action strings and Intent extra keys.
 * Extracted from ApiConstants as part of the package refactoring.
 */
public final class IntentConstants {

    private IntentConstants() {}

    public static final String ACTION_REPLY =
            "io.github.kgelinas.jalfnotifier.ACTION_REPLY";
    public static final String ACTION_MARK_AS_READ =
            "io.github.kgelinas.jalfnotifier.ACTION_MARK_AS_READ";
    public static final String ACTION_OPEN_SETTINGS =
            "io.github.kgelinas.jalfnotifier.widget.ACTION_OPEN_SETTINGS";

    public static final String EXTRA_CONVERSATION_LINK = "conversationLink";
    public static final String EXTRA_OTHER_USER_ID = "otherUserId";
    public static final String EXTRA_OTHER_NAME = "otherName";
    public static final String EXTRA_AVATAR_URL = "avatarUrl";
    public static final String EXTRA_SEX_ICON_URL = "sexIconUrl";
    public static final String EXTRA_USER_LINK = "userLink";
    public static final String EXTRA_REMOTE_INPUT_KEY = "extra_reply";
}
