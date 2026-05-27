package io.github.kgelinas.jalfnotifier.sync;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.credentials.CredentialManager;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Handles Google OAuth and initializes the Google Drive V3 Service using modern
 * Credential Manager.
 */
public class GoogleDriveManager {
    private static final String TAG = "GoogleDriveManager";

    private static GoogleDriveManager instance;
    private Drive driveService;
    private final CredentialManager credentialManager;
    private final Executor executor = Executors.newSingleThreadExecutor();

    private GoogleDriveManager(Context context) {
        this.credentialManager = CredentialManager.create(context);
    }

    public static synchronized GoogleDriveManager getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleDriveManager(context);
        }
        return instance;
    }

    public interface SignInCallback {
        void onSuccess(String email);

        void onFailure(String message);
    }

    public void signIn(Activity activity, SignInCallback callback) {
        String webClientId = activity.getString(io.github.kgelinas.jalfnotifier.R.string.default_web_client_id);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        credentialManager.getCredentialAsync(
                activity,
                request,
                null,
                executor,
                new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        try {
                            GoogleIdTokenCredential credential = GoogleIdTokenCredential
                                    .createFrom(result.getCredential().getData());
                            activity.runOnUiThread(() -> callback.onSuccess(credential.getId()));
                        } catch (Exception e) {
                            activity.runOnUiThread(
                                    () -> callback.onFailure("Failed to parse credential: " + e.getMessage()));
                        }
                    }

                    @Override
                    public void onError(GetCredentialException e) {
                        activity.runOnUiThread(() -> callback.onFailure("Sign-in error: " + e.getMessage()));
                    }
                });
    }

    public void signOut() {
        // Credential Manager clearCredentialState handles sign-out
        driveService = null;
    }

    public Drive getDriveService(Context context, String accountEmail) {
        if (driveService != null)
            return driveService;

        if (accountEmail != null && !accountEmail.isEmpty()) {
            GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                    context, Collections.singleton(DriveScopes.DRIVE_APPDATA));
            credential.setSelectedAccountName(accountEmail);

            try {
                driveService = new Drive.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(),
                        new GsonFactory(),
                        credential)
                        .setApplicationName("JALF Notifier")
                        .build();
                return driveService;
            } catch (Exception e) {
                Log.e(TAG, "Failed to build Drive service", e);
            }
        }
        return null;
    }

    public boolean isSignedIn(Context context) {
        // Logic for tracking signed-in state with DataStore/Prefs instead of
        // GoogleSignIn.getLast...
        return false; // Placeholder, state should be managed by ViewModel
    }
}
