package io.github.kgelinas.jalfnotifier.data;

/**
 * Network endpoint and API constants.
 * Extracted from ApiConstants as part of the package refactoring.
 */
public final class NetworkConstants {

    private NetworkConstants() {}

    public static final String BASE_URL = "https://m-app.jalf.com";
    public static final String PATH_LOOKERS = "/ct/online/3";
    public static final String PATH_POKES = "/ct/online/8";
    public static final String PATH_POKES_SENT = "/ct/online/10";
    public static final String PATH_VISITS_SENT = "/ct/online/6";
    public static final String PATH_PROFILE_EDIT = "/ct/profile";
    public static final String PATH_GEOLOCATION_OPTIONS = "/ct/myOptions/geolocation";

    public static final String GEMINI_API_KEY = "";

    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36";
}
