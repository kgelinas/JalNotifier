package io.github.kgelinas.jalfnotifier.util;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Utility class for photo file operations.
 * Handles MIME type detection, file size validation, URI to file conversion, and cleanup.
 */
public class PhotoFileUtils {

    private static final String TAG = "PhotoFileUtils";
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

    /**
     * Get MIME type for a file based on its extension.
     * Supports JPEG, PNG, GIF, WebP, and other common image formats.
     */
    public static String getMimeType(File file) {
        if (file == null || !file.exists()) {
            return "image/jpeg"; // Default to JPEG
        }

        String filename = file.getName().toLowerCase(Locale.US);
        
        // Check by extension first
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.endsWith(".png")) {
            return "image/png";
        } else if (filename.endsWith(".gif")) {
            return "image/gif";
        } else if (filename.endsWith(".webp")) {
            return "image/webp";
        } else if (filename.endsWith(".bmp")) {
            return "image/bmp";
        }

        // Try using MimeTypeMap for unknown extensions
        String ext = MimeTypeMap.getFileExtensionFromUrl(file.getAbsolutePath());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        
        return mimeType != null ? mimeType : "image/jpeg";
    }

    /**
     * Check if file size is valid (not exceeding maximum).
     */
    public static boolean isFileSizeValid(File file, long maxSize) {
        if (file == null || !file.exists()) {
            return false;
        }
        return file.length() <= maxSize;
    }

    /**
     * Get human-readable file size string (e.g., "2.5 MB").
     */
    public static String getFileSizeString(File file) {
        if (file == null || !file.exists()) {
            return "0 B";
        }

        long bytes = file.length();
        if (bytes <= 0) return "0 B";

        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        
        return String.format(Locale.US, "%.1f %s", 
            bytes / Math.pow(1024, digitGroups), 
            units[digitGroups]);
    }

    /**
     * Copy file from URI (e.g., from gallery) to a local file.
     * Useful for converting gallery URIs into usable File objects.
     */
    public static void copyUriToFile(Context context, Uri uri, File outputFile) throws IOException {
        InputStream inputStream = null;
        FileOutputStream outputStream = null;

        try {
            inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                throw new IOException("Could not open input stream for URI: " + uri);
            }

            outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Safely delete a file.
     */
    public static boolean deleteFile(File file) {
        if (file == null) {
            return true;
        }
        if (!file.exists()) {
            return true;
        }
        return file.delete();
    }

    /**
     * Check if a file is a valid image based on magic bytes (file signature).
     * This provides better detection than just checking extensions.
     */
    public static boolean isValidImage(File file) {
        if (file == null || !file.exists() || file.length() < 4) {
            return false;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            if (fis.read(header) < 4) {
                return false;
            }

            // Check for common image file signatures
            // JPEG: FF D8 FF
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return true;
            }
            // PNG: 89 50 4E 47
            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return true;
            }
            // GIF: 47 49 46
            if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46) {
                return true;
            }
            // WebP: RIFF ... WEBP
            if (header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46) {
                return true; // Could be WebP or other RIFF format
            }
            // BMP: 42 4D
            if (header[0] == 0x42 && header[1] == 0x4D) {
                return true;
            }

            return false;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Placeholder for future image compression functionality.
     * Could compress JPEG/PNG to reduce file size before upload.
     */
    public static File ensureCompressed(File originalFile, int maxWidthPx, int maxHeightPx) throws IOException {
        // TODO: Implement image compression using Android's Bitmap API
        // For now, just return the original file
        return originalFile;
    }
}
