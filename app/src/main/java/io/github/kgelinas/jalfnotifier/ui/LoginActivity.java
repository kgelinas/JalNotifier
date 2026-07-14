package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText usernameRoot;
    private TextInputEditText passwordRoot;
    private MaterialButton loginButton;
    private CircularProgressIndicator loginProgress;
    private TextView loginStatus;
    private MaterialButton btnBiometricLogin;

    private static final int PERMISSION_REQUEST_CODE = 124;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        
        // Redirect to permissions onboarding if not yet done
        boolean onboardingDone = AppPrefs.getInstance(this).getBoolean(ApiConstants.KEY_PERMISSIONS_ONBOARDING_DONE, false);
        if (!onboardingDone) {
            startActivity(new Intent(this, PermissionsActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        View rootContainer = findViewById(R.id.login_root_container);
        int initialPaddingLeft = rootContainer.getPaddingLeft();
        int initialPaddingTop = rootContainer.getPaddingTop();
        int initialPaddingRight = rootContainer.getPaddingRight();
        int initialPaddingBottom = rootContainer.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer, (v, insets) -> {
            Insets insetsValues = insets
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insetsValues.left + initialPaddingLeft, insetsValues.top + initialPaddingTop,
                    insetsValues.right + initialPaddingRight, insetsValues.bottom + initialPaddingBottom);
            return WindowInsetsCompat.CONSUMED;
        });

        usernameRoot = findViewById(R.id.username);
        passwordRoot = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        loginProgress = findViewById(R.id.login_progress);
        loginStatus = findViewById(R.id.login_status);
        btnBiometricLogin = findViewById(R.id.btn_biometric_login);

        loginButton.setOnClickListener(v -> attemptLogin());
        btnBiometricLogin.setOnClickListener(v -> checkBiometricAutoLogin());

        checkBiometricAutoLogin();
    }

    private void checkBiometricAutoLogin() {
        boolean biometricEnabled = AppPrefs.getInstance(this).getBoolean(ApiConstants.KEY_BIOMETRIC_LOGIN, false);
        SecurePrefs secure = SecurePrefs.get(this);
        String savedUser = secure.getString(ApiConstants.KEY_USERNAME, "");
        String savedPass = secure.getString(ApiConstants.KEY_PASSWORD, "");

        if (biometricEnabled && !savedUser.isEmpty() && !savedPass.isEmpty()) {
            btnBiometricLogin.setVisibility(View.VISIBLE);
            BiometricHelper.showPrompt(this, getString(R.string.biometric_login_title), getString(R.string.biometric_login_desc, savedUser),

                    new androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(
                                @androidx.annotation.NonNull androidx.biometric.BiometricPrompt.AuthenticationResult result) {
                            usernameRoot.setText(savedUser);
                            passwordRoot.setText(savedPass);
                            attemptLogin();
                        }
                    });
        } else {
            btnBiometricLogin.setVisibility(View.GONE);
        }
    }

    private void attemptLogin() {
        String username = usernameRoot.getText().toString().trim();
        String password = passwordRoot.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.login_input_required, Toast.LENGTH_SHORT).show();

            return;
        }

        loginButton.setEnabled(false);
        loginProgress.setVisibility(View.VISIBLE);
        loginStatus.setVisibility(View.VISIBLE);
        loginStatus.setText(R.string.login_status_authenticating);


        // Derive from the shared pool; add an in-memory CookieJar for the login handshake.
        OkHttpClient client = JalfNotifierApplication.httpClient().newBuilder()
                .cookieJar(new CookieJar() {
                    private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : new ArrayList<>();
                    }
                })
                .build();

        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("Username", username);
        params.put("Password", password);
        params.put("what", "start_page");

        RequestBody formBody = NetworkUtils.createIsoFormBody(params);

        // Map language code according to Jalf API specs: eng, keb, itl, esp, alm
        String osLang = java.util.Locale.getDefault().getLanguage();
        String jalfLang;
        if (osLang.startsWith("fr")) {
            jalfLang = "keb";
        } else if (osLang.startsWith("it")) {
            jalfLang = "itl";
        } else if (osLang.startsWith("es")) {
            jalfLang = "esp";
        } else if (osLang.startsWith("de")) {
            jalfLang = "alm";
        } else {
            jalfLang = "eng";
        }
        String loginUrl = ApiConstants.BASE_URL + "/ct/connect?Lang=" + jalfLang;

        Request request = new Request.Builder()
                .url(loginUrl)
                .post(formBody)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this,
                            getString(R.string.error_network_prefix), Toast.LENGTH_SHORT).show();

                    resetLoginUI();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        runOnUiThread(() -> loginStatus.setText(R.string.login_status_capturing));


                        HttpUrl parsedUrl = HttpUrl.parse(ApiConstants.BASE_URL + "/");
                        List<Cookie> cookies = client.cookieJar().loadForRequest(parsedUrl);
                        String suid = "";
                        StringBuilder fullCookieBuilder = new StringBuilder();
                        for (Cookie cookie : cookies) {
                            fullCookieBuilder.append(cookie.name()).append("=")
                                    .append(cookie.value()).append("; ");
                            if (cookie.name().equals("SUID"))
                                suid = cookie.value();
                        }
                        String fullCookie = fullCookieBuilder.toString();

                        if (suid.isEmpty()) {
                            runOnUiThread(() -> {
                                Toast.makeText(LoginActivity.this,
                                        R.string.login_failed_suid, Toast.LENGTH_SHORT).show();

                                resetLoginUI();
                            });
                            return;
                        }

                        // Store sensitive credentials in EncryptedSharedPreferences
                        SecurePrefs.get(LoginActivity.this)
                                .putString(ApiConstants.KEY_USERNAME, username)
                                .putString(ApiConstants.KEY_PASSWORD, password)
                                .putString(ApiConstants.KEY_SUID, suid)
                                .putString(ApiConstants.KEY_FULL_COOKIE, fullCookie);

                        runOnUiThread(() -> loginStatus.setText(R.string.login_status_discovering));

                        fetchUserIdAndStart(client, fullCookie);
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this,
                                    getString(R.string.login_failed_prefix), Toast.LENGTH_SHORT).show();

                            resetLoginUI();
                        });
                    }
                }
            }
        });
    }

    private void fetchUserIdAndStart(OkHttpClient client, String fullCookie) {
        Request request = new Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/accueil")
                .addHeader("Cookie", fullCookie)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    Toast.makeText(LoginActivity.this,
                            R.string.failed_to_load_profile, Toast.LENGTH_SHORT).show();

                    resetLoginUI();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (Response r = response) {
                    if (r.isSuccessful()) {
                        String html = NetworkUtils.responseToString(r);
                        String userId = extractUserId(html);
                        if (!userId.isEmpty()) {
                            // Store the non-sensitive USER_ID in plain prefs
                            AppPrefs.getInstance(LoginActivity.this)
                                    .putString(ApiConstants.KEY_USER_ID, userId);
                            runOnUiThread(() -> {
                                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                finish();
                            });
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(LoginActivity.this,
                                        R.string.login_failed_user_id, Toast.LENGTH_SHORT).show();

                                resetLoginUI();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(LoginActivity.this,
                                    R.string.profile_fetch_error, Toast.LENGTH_SHORT).show();

                            resetLoginUI();
                        });
                    }
                }
            }
        });
    }

    private String extractUserId(String html) {
        int index = html.indexOf("/rest/users/");
        if (index != -1) {
            int start = index + "/rest/users/".length();
            int end = start;
            while (end < html.length() && Character.isDigit(html.charAt(end)))
                end++;
            return html.substring(start, end);
        }
        return "";
    }

    private void resetLoginUI() {
        loginButton.setEnabled(true);
        loginProgress.setVisibility(View.GONE);
        loginStatus.setVisibility(View.GONE);
    }
}
