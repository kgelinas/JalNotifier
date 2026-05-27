package io.github.kgelinas.jalfnotifier;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Handles uploading photos directly to the JALF API.
 * Supports multipart form-data uploads with proper file handling.
 * 
 * Endpoint: POST /rest/users/{userId}/photos/albums/messages
 * 
 * Response includes:
 * - thumbnail_uri: low-res preview URL
 * - image_uri: high-res photo URL
 * - original_file_sha256: integrity hash
 * - state: approval state (e.g., "approval", "approved")
 */
public class PhotoUploadTask {

    private static final String TAG = "PhotoUploadTask";
    private static final int UPLOAD_TIMEOUT_MS = 60000; // 60 seconds

    private OkHttpClient client;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private String fullCookie;
    private String suid;
    private String userId;

    public interface PhotoUploadCallback {
        void onUploadSuccess(JSONObject response);
        void onUploadProgress(long bytesUploaded, long totalBytes);
        void onUploadFailure(String errorMessage);
    }

    public PhotoUploadTask(String fullCookie, String suid, String userId) {
        // Derive from the shared pool; override timeouts for large file uploads.
        this.client = JalfNotifierApplication.httpClient().newBuilder()
                .connectTimeout(UPLOAD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(UPLOAD_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        this.fullCookie = fullCookie;
        this.suid = suid;
        this.userId = userId;
    }

    /**
     * Upload a photo file to a specific album link or name.
     * Sends multipart form-data with photo file and metadata.
     */
    public void uploadPhotoToAlbum(File photoFile, String albumLinkOrName, String comment, boolean anonymous, 
                           boolean showPublicAlbum, PhotoUploadCallback callback) {
        
        if (photoFile == null || !photoFile.exists()) {
            notifyFailure(callback, "Photo file does not exist");
            return;
        }

        if (!PhotoFileUtils.isFileSizeValid(photoFile, 50 * 1024 * 1024)) { // 50MB limit
            notifyFailure(callback, "Photo file too large (max 50MB)");
            return;
        }

        try {
            // Build multipart request body
            String mimeType = PhotoFileUtils.getMimeType(photoFile);
            RequestBody fileBody = RequestBody.create(photoFile, MediaType.parse(mimeType));

            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo_file", photoFile.getName(), fileBody)
                    .addFormDataPart("comment", comment != null ? comment : "")
                    .addFormDataPart("show_public_album", showPublicAlbum ? "1" : "0")
                    .addFormDataPart("anonymous", anonymous ? "1" : "0")
                    .addFormDataPart("photos_category_link", "")
                    .addFormDataPart("photo_category_link", "");

            RequestBody requestBody = bodyBuilder.build();

            // Build request URL dynamically
            String url;
            if (albumLinkOrName != null && albumLinkOrName.startsWith("/")) {
                url = ApiConstants.BASE_URL + albumLinkOrName;
            } else {
                String name = (albumLinkOrName != null) ? albumLinkOrName : "messages";
                url = ApiConstants.BASE_URL + "/rest/users/" + userId + "/photos/albums/" + name;
            }

            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("x-csrftoken", suid)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .build();

            Log.d(TAG, "Starting photo upload to: " + url);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Photo upload network failure", e);
                    notifyFailure(callback, "Upload failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            String bodyText = NetworkUtils.responseToString(r);
                            JSONObject responseJson = new JSONObject(bodyText);
                            Log.d(TAG, "Photo upload successful");
                            notifySuccess(callback, responseJson);
                        } else {
                            String errorBody = r.body() != null ? NetworkUtils.responseToString(r) : "Unknown error";
                            Log.e(TAG, "Photo upload failed: " + r.code() + " - " + errorBody);
                            notifyFailure(callback, "Upload failed (HTTP " + r.code() + ")");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing upload response", e);
                        notifyFailure(callback, "Response processing error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing photo upload", e);
            notifyFailure(callback, "Upload preparation failed: " + e.getMessage());
        }
    }

    /**
     * Upload a photo file directly to the messages album.
     * Sends multipart form-data with photo file and metadata.
     */
    public void uploadPhoto(File photoFile, String comment, boolean anonymous, 
                           boolean showPublicAlbum, PhotoUploadCallback callback) {
        
        if (photoFile == null || !photoFile.exists()) {
            notifyFailure(callback, "Photo file does not exist");
            return;
        }

        if (!PhotoFileUtils.isFileSizeValid(photoFile, 50 * 1024 * 1024)) { // 50MB limit
            notifyFailure(callback, "Photo file too large (max 50MB)");
            return;
        }

        try {
            // Build multipart request body
            String mimeType = PhotoFileUtils.getMimeType(photoFile);
            RequestBody fileBody = RequestBody.create(photoFile, MediaType.parse(mimeType));

            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("photo_file", photoFile.getName(), fileBody)
                    .addFormDataPart("comment", comment != null ? comment : "")
                    .addFormDataPart("show_public_album", showPublicAlbum ? "1" : "0")
                    .addFormDataPart("anonymous", anonymous ? "1" : "0")
                    .addFormDataPart("photos_category_link", "")
                    .addFormDataPart("photo_category_link", "");

            RequestBody requestBody = bodyBuilder.build();

            // Build request
            String url = ApiConstants.BASE_URL + "/rest/users/" + userId + "/photos/albums/messages";
            Request request = new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .addHeader("Cookie", fullCookie)
                    .addHeader("x-csrftoken", suid)
                    .addHeader("User-Agent", ApiConstants.USER_AGENT)
                    .addHeader("x-requested-with", "XMLHttpRequest")
                    .build();

            Log.d(TAG, "Starting photo upload to: " + url);

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Photo upload network failure", e);
                    notifyFailure(callback, "Upload failed: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try (Response r = response) {
                        if (r.isSuccessful() && r.body() != null) {
                            String bodyText = NetworkUtils.responseToString(r);
                            JSONObject responseJson = new JSONObject(bodyText);
                            Log.d(TAG, "Photo upload successful");
                            notifySuccess(callback, responseJson);
                        } else {
                            String errorBody = r.body() != null ? NetworkUtils.responseToString(r) : "Unknown error";
                            Log.e(TAG, "Photo upload failed: " + r.code() + " - " + errorBody);
                            notifyFailure(callback, "Upload failed (HTTP " + r.code() + ")");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing upload response", e);
                        notifyFailure(callback, "Response processing error: " + e.getMessage());
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing photo upload", e);
            notifyFailure(callback, "Upload preparation failed: " + e.getMessage());
        }
    }

    private void notifySuccess(PhotoUploadCallback callback, JSONObject response) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onUploadSuccess(response);
            }
        });
    }

    private void notifyFailure(PhotoUploadCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onUploadFailure(error);
            }
        });
    }

    private void notifyProgress(PhotoUploadCallback callback, long uploaded, long total) {
        mainHandler.post(() -> {
            if (callback != null) {
                callback.onUploadProgress(uploaded, total);
            }
        });
    }
}
