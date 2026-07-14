package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Handles camera and gallery photo selection for upload.
 * Manages file URIs, temporary files, and provides callbacks for selected photos.
 */
public class CameraGalleryPicker {

    private static final String TAG = "CameraGalleryPicker";
    private static final String FILE_PROVIDER_AUTHORITY = "io.github.kgelinas.jalfnotifier.fileprovider";

    private Context context;
    private PhotoSelectionCallback callback;
    private File currentPhotoFile;

    public interface PhotoSelectionCallback {
        void onPhotoSelected(File photoFile);
        void onPickerCancelled();
        void onPickerError(String error);
    }

    public CameraGalleryPicker(Context context, PhotoSelectionCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    /**
     * Open system camera to take a new photo.
     */
    public void openCamera(ActivityResultLauncher<Intent> launcher) {
        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            currentPhotoFile = createImageFile();
            if (currentPhotoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, currentPhotoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                launcher.launch(takePictureIntent);
            } else {
                notifyError("Could not create temporary photo file");
            }
        } catch (android.content.ActivityNotFoundException e) {
            notifyError("No camera app available on this device");
        } catch (Exception e) {
            notifyError("Error opening camera: " + e.getMessage());
        }
    }

    /**
     * Open gallery to select an existing photo.
     */
    public void openGallery(ActivityResultLauncher<Intent> launcher) {
        try {
            Intent pickPhotoIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickPhotoIntent.setType("image/*");
            launcher.launch(pickPhotoIntent);
        } catch (android.content.ActivityNotFoundException e) {
            notifyError("No gallery app available on this device");
        } catch (Exception e) {
            notifyError("Error opening gallery: " + e.getMessage());
        }
    }

    /**
     * Handle the result from camera capture.
     * Call this from your onActivityResult or launcher callback.
     */
    public void handleCameraResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK && currentPhotoFile != null && currentPhotoFile.exists()) {
            notifyPhotoSelected(currentPhotoFile);
        } else if (resultCode == Activity.RESULT_CANCELED) {
            if (currentPhotoFile != null && currentPhotoFile.exists()) {
                currentPhotoFile.delete();
            }
            notifyCancelled();
        } else {
            notifyError("Camera capture failed or was interrupted");
            if (currentPhotoFile != null && currentPhotoFile.exists()) {
                currentPhotoFile.delete();
            }
        }
    }

    /**
     * Handle the result from gallery selection.
     * Call this from your onActivityResult or launcher callback.
     */
    public void handleGalleryResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                Uri selectedImageUri = data.getData();
                if (selectedImageUri != null) {
                    // Convert gallery URI to a usable file
                    File photoFile = new File(context.getCacheDir(), "selected_photo_" + System.currentTimeMillis() + ".jpg");
                    PhotoFileUtils.copyUriToFile(context, selectedImageUri, photoFile);
                    notifyPhotoSelected(photoFile);
                } else {
                    notifyError("Could not retrieve selected image URI");
                }
            } catch (Exception e) {
                notifyError("Error processing gallery selection: " + e.getMessage());
            }
        } else if (resultCode == Activity.RESULT_CANCELED) {
            notifyCancelled();
        } else {
            notifyError("Gallery selection failed or was interrupted");
        }
    }

    /**
     * Create a temporary image file for camera capture.
     */
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JALF_" + timeStamp + "_";
        File storageDir = context.getCacheDir();
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }

    private void notifyPhotoSelected(File photoFile) {
        if (callback != null) {
            callback.onPhotoSelected(photoFile);
        }
    }

    private void notifyCancelled() {
        if (callback != null) {
            callback.onPickerCancelled();
        }
    }

    private void notifyError(String error) {
        if (callback != null) {
            callback.onPickerError(error);
        }
    }

    /**
     * Clean up temporary files.
     */
    public void cleanup() {
        if (currentPhotoFile != null && currentPhotoFile.exists()) {
            currentPhotoFile.delete();
        }
    }
}
