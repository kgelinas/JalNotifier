package io.github.kgelinas.jalfnotifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Utility class for handling character encoding issues with legacy JALF server.
 * JALF uses ISO-8859-1 for almost everything.
 */
public class NetworkUtils {

    private NetworkUtils() {}

    /**
     * Simple parameter pair for form bodies.
     */
    public static class Param {
        public final String name;
        public final String value;

        public Param(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    /**
     * Creates a RequestBody for a form-urlencoded POST request using ISO-8859-1.
     * Supports multiple values for the same key.
     */
    public static RequestBody createIsoFormBody(java.util.List<Param> params) {
        StringBuilder sb = new StringBuilder();
        for (Param param : params) {
            if (sb.length() > 0) sb.append("&");
            try {
                sb.append(URLEncoder.encode(param.name, "ISO-8859-1"));
                sb.append("=");
                
                String val = param.value != null ? param.value : "";
                StringBuilder encodedVal = new StringBuilder();
                for (int i = 0; i < val.length(); i++) {
                    int codePoint = val.codePointAt(i);
                    if (codePoint > 255) {
                        encodedVal.append("&#").append(codePoint).append(";");
                        if (Character.isSupplementaryCodePoint(codePoint)) i++;
                    } else {
                        encodedVal.append((char) codePoint);
                    }
                }
                
                sb.append(URLEncoder.encode(encodedVal.toString(), "ISO-8859-1"));
            } catch (UnsupportedEncodingException e) {
                sb.append(param.name).append("=").append(param.value);
            }
        }
        return RequestBody.create(sb.toString(), MediaType.parse("application/x-www-form-urlencoded; charset=ISO-8859-1"));
    }

    /**
     * Helper for single-value params.
     */
    public static RequestBody createIsoFormBody(Map<String, String> params) {
        java.util.List<Param> list = new java.util.ArrayList<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            list.add(new Param(entry.getKey(), entry.getValue()));
        }
        return createIsoFormBody(list);
    }

    /**
     * Decodes a response body to a String, defaulting to ISO-8859-1 unless UTF-8 is specified.
     */
    @NonNull
    public static String responseToString(@Nullable Response response) throws IOException {
        if (response == null || response.body() == null) return "";
        try (ResponseBody body = response.body()) {
            byte[] bytes = body.bytes();
            String contentType = response.header("Content-Type");
            if (contentType != null && contentType.toLowerCase().contains("charset=utf-8")) {
                return new String(bytes, StandardCharsets.UTF_8);
            }
            // Default to ISO-8859-1 for JALF
            return new String(bytes, "ISO-8859-1");
        }
    }
}
