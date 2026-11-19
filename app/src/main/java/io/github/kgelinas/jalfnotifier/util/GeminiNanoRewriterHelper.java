package io.github.kgelinas.jalfnotifier.util;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.GenAiException;
import com.google.mlkit.genai.rewriting.Rewriter;
import com.google.mlkit.genai.rewriting.RewriterOptions;
import com.google.mlkit.genai.rewriting.Rewriting;
import com.google.mlkit.genai.rewriting.RewritingRequest;
import com.google.mlkit.genai.rewriting.RewritingResult;
import com.google.mlkit.genai.rewriting.RewritingSuggestion;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.github.kgelinas.jalfnotifier.data.ApiConstants;
import io.github.kgelinas.jalfnotifier.data.AppPrefs;

/**
 * Helper around Google ML Kit GenAI Rewriting (on-device Gemini Nano via AICore).
 * Automatically falls back to Language.ENGLISH (Feature 607) when a localized
 * LoRA feature (such as Feature 611 for French) is not provisioned on the device.
 */
public class GeminiNanoRewriterHelper {
    private static final String TAG = "GeminiNanoRewriter";

    public static final String TONE_REPHRASE = "REPHRASE";
    public static final String TONE_FRIENDLY = "FRIENDLY";
    public static final String TONE_EMOJIFY = "EMOJIFY";
    public static final String TONE_SHORTEN = "SHORTEN";
    public static final String TONE_ELABORATE = "ELABORATE";
    public static final String TONE_PROFESSIONAL = "PROFESSIONAL";

    private static final Executor executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static volatile Boolean cachedSupportedState = null;
    private static volatile Integer cachedWorkingLanguage = null;

    public interface StatusCallback {
        void onStatus(int featureStatus, String statusMessage);
    }

    public interface SupportCallback {
        void onResult(boolean isSupported, int featureStatus);
    }

    public interface RewriteSingleCallback {
        void onSuccess(@NonNull String rewrittenText, @NonNull List<String> allVariants);
        void onError(@NonNull String errorMessage);
    }

    /**
     * Checks whether this device supports Gemini Nano Rewriting (trying preferred language first,
     * then falling back to Feature 607 / Language.ENGLISH).
     */
    public static void checkIfSupported(Context context, SupportCallback callback) {
        Boolean cached = cachedSupportedState;
        if (cached != null) {
            mainHandler.post(() -> callback.onResult(cached, cached ? FeatureStatus.AVAILABLE : FeatureStatus.UNAVAILABLE));
            return;
        }
        checkOrPrepareStatus(context, false, (status, msg) -> {
            boolean supported = (status == FeatureStatus.AVAILABLE
                    || status == FeatureStatus.DOWNLOADABLE
                    || status == FeatureStatus.DOWNLOADING);
            cachedSupportedState = supported;
            if (!supported) {
                AppPrefs.getInstance(context.getApplicationContext())
                        .putBoolean(ApiConstants.KEY_AI_NANO_REWRITE_ENABLED, false);
            }
            callback.onResult(supported, status);
        });
    }

    public static Boolean getCachedSupportedState() {
        return cachedSupportedState;
    }

    public static int resolveOutputType(String toneKey) {
        if (toneKey == null) return RewriterOptions.OutputType.REPHRASE;
        switch (toneKey.toUpperCase()) {
            case TONE_FRIENDLY:
                return RewriterOptions.OutputType.FRIENDLY;
            case TONE_EMOJIFY:
                return RewriterOptions.OutputType.EMOJIFY;
            case TONE_SHORTEN:
                return RewriterOptions.OutputType.SHORTEN;
            case TONE_ELABORATE:
                return RewriterOptions.OutputType.ELABORATE;
            case TONE_PROFESSIONAL:
                return RewriterOptions.OutputType.PROFESSIONAL;
            case TONE_REPHRASE:
            default:
                return RewriterOptions.OutputType.REPHRASE;
        }
    }

    public static int resolvePreferredLanguage(Context context) {
        String lang = AppPrefs.getInstance(context).getString(ApiConstants.KEY_GEMINI_LANGUAGE, "Français");
        if (lang == null) return RewriterOptions.Language.FRENCH;
        String lower = lang.toLowerCase();
        if (lower.contains("english") || lower.contains("anglais")) {
            return RewriterOptions.Language.ENGLISH;
        } else if (lower.contains("español") || lower.contains("spanish") || lower.contains("espagnol")) {
            return RewriterOptions.Language.SPANISH;
        } else if (lower.contains("deutsch") || lower.contains("german") || lower.contains("allemand")) {
            return RewriterOptions.Language.GERMAN;
        } else if (lower.contains("italiano") || lower.contains("italian") || lower.contains("italien")) {
            return RewriterOptions.Language.ITALIAN;
        }
        return RewriterOptions.Language.FRENCH;
    }

    /**
     * Checks whether Gemini Nano Rewriting is available on this device and optionally triggers download.
     * If the preferred language feature (e.g. 611 for French) is UNAVAILABLE or fails, automatically
     * retries with Language.ENGLISH (Feature 607).
     */
    public static void checkOrPrepareStatus(Context context, boolean downloadIfNeeded, StatusCallback callback) {
        Context appCtx = context.getApplicationContext();
        int preferredLang = cachedWorkingLanguage != null
                ? cachedWorkingLanguage
                : resolvePreferredLanguage(appCtx);
        checkStatusWithLanguage(appCtx, preferredLang, downloadIfNeeded, true, callback);
    }

    private static void checkStatusWithLanguage(
            Context appCtx,
            int language,
            boolean downloadIfNeeded,
            boolean allowFallbackTo607,
            StatusCallback callback) {
        RewriterOptions options = RewriterOptions.builder(appCtx)
                .setOutputType(RewriterOptions.OutputType.REPHRASE)
                .setLanguage(language)
                .build();
        Rewriter rewriter = Rewriting.getClient(options);

        ListenableFuture<Integer> statusFuture = rewriter.checkFeatureStatus();
        Futures.addCallback(statusFuture, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(Integer status) {
                int st = status != null ? status : FeatureStatus.UNAVAILABLE;
                if (st == FeatureStatus.UNAVAILABLE
                        && allowFallbackTo607
                        && language != RewriterOptions.Language.ENGLISH) {
                    rewriter.close();
                    AppLogger.log(TAG, "Language " + language + " unavailable in AICore, falling back to Feature 607 (ENGLISH)");
                    checkStatusWithLanguage(appCtx, RewriterOptions.Language.ENGLISH, downloadIfNeeded, false, callback);
                    return;
                }
                if (st == FeatureStatus.AVAILABLE || st == FeatureStatus.DOWNLOADABLE || st == FeatureStatus.DOWNLOADING) {
                    cachedWorkingLanguage = language;
                    cachedSupportedState = true;
                }
                if (st == FeatureStatus.DOWNLOADABLE && downloadIfNeeded) {
                    mainHandler.post(() -> callback.onStatus(FeatureStatus.DOWNLOADING, "Downloading Gemini Nano (Feature 607)..."));
                    rewriter.downloadFeature(new DownloadCallback() {
                        @Override
                        public void onDownloadStarted(long bytesToDownload) {
                        }

                        @Override
                        public void onDownloadProgress(long totalBytesDownloaded) {
                        }

                        @Override
                        public void onDownloadCompleted() {
                            rewriter.close();
                            cachedWorkingLanguage = language;
                            cachedSupportedState = true;
                            mainHandler.post(() -> callback.onStatus(FeatureStatus.AVAILABLE, "Available"));
                        }

                        @Override
                        public void onDownloadFailed(GenAiException e) {
                            rewriter.close();
                            if (allowFallbackTo607 && language != RewriterOptions.Language.ENGLISH) {
                                checkStatusWithLanguage(appCtx, RewriterOptions.Language.ENGLISH, true, false, callback);
                                return;
                            }
                            String msg = e != null && e.getMessage() != null ? e.getMessage() : "Download failed";
                            mainHandler.post(() -> callback.onStatus(FeatureStatus.UNAVAILABLE, msg));
                        }
                    });
                } else {
                    rewriter.close();
                    mainHandler.post(() -> callback.onStatus(st, null));
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                rewriter.close();
                if (allowFallbackTo607 && language != RewriterOptions.Language.ENGLISH) {
                    AppLogger.log(TAG, "Language " + language + " failed in AICore, falling back to Feature 607 (ENGLISH)", t);
                    checkStatusWithLanguage(appCtx, RewriterOptions.Language.ENGLISH, downloadIfNeeded, false, callback);
                    return;
                }
                mainHandler.post(() -> callback.onStatus(FeatureStatus.UNAVAILABLE, t.getMessage()));
            }
        }, executor);
    }

    /**
     * Asynchronously rewrites a single text on-demand (for card button) and posts result on main thread.
     */
    public static void rewriteSingleAsync(Context context, String inputText, String toneKey, RewriteSingleCallback callback) {
        if (inputText == null || inputText.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("Empty text"));
            return;
        }
        Context appCtx = context.getApplicationContext();
        int outputType = resolveOutputType(toneKey);
        int preferredLang = cachedWorkingLanguage != null
                ? cachedWorkingLanguage
                : resolvePreferredLanguage(appCtx);

        rewriteSingleWithLanguage(appCtx, inputText.trim(), outputType, preferredLang, true, callback);
    }

    private static void rewriteSingleWithLanguage(
            Context appCtx,
            String text,
            int outputType,
            int language,
            boolean allowFallbackTo607,
            RewriteSingleCallback callback) {
        RewriterOptions options = RewriterOptions.builder(appCtx)
                .setOutputType(outputType)
                .setLanguage(language)
                .build();
        Rewriter rewriter = Rewriting.getClient(options);

        Futures.addCallback(rewriter.checkFeatureStatus(), new FutureCallback<Integer>() {
            @Override
            public void onSuccess(Integer status) {
                int st = status != null ? status : FeatureStatus.UNAVAILABLE;
                if (st == FeatureStatus.AVAILABLE) {
                    cachedWorkingLanguage = language;
                    runSingleInference(rewriter, text, callback);
                } else if (st == FeatureStatus.DOWNLOADABLE) {
                    cachedWorkingLanguage = language;
                    rewriter.downloadFeature(new DownloadCallback() {
                        @Override
                        public void onDownloadStarted(long bytesToDownload) {
                        }

                        @Override
                        public void onDownloadProgress(long totalBytesDownloaded) {
                        }

                        @Override
                        public void onDownloadCompleted() {
                            runSingleInference(rewriter, text, callback);
                        }

                        @Override
                        public void onDownloadFailed(GenAiException e) {
                            rewriter.close();
                            if (allowFallbackTo607 && language != RewriterOptions.Language.ENGLISH) {
                                rewriteSingleWithLanguage(appCtx, text, outputType, RewriterOptions.Language.ENGLISH, false, callback);
                                return;
                            }
                            String err = e != null && e.getMessage() != null ? e.getMessage() : "Gemini Nano download failed";
                            mainHandler.post(() -> callback.onError(err));
                        }
                    });
                } else if (allowFallbackTo607 && language != RewriterOptions.Language.ENGLISH) {
                    rewriter.close();
                    rewriteSingleWithLanguage(appCtx, text, outputType, RewriterOptions.Language.ENGLISH, false, callback);
                } else {
                    rewriter.close();
                    mainHandler.post(() -> callback.onError("Gemini Nano unavailable (status " + st + ")"));
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                rewriter.close();
                if (allowFallbackTo607 && language != RewriterOptions.Language.ENGLISH) {
                    rewriteSingleWithLanguage(appCtx, text, outputType, RewriterOptions.Language.ENGLISH, false, callback);
                    return;
                }
                mainHandler.post(() -> callback.onError(t.getMessage() != null ? t.getMessage() : "Gemini Nano error"));
            }
        }, executor);
    }

    private static void runSingleInference(Rewriter rewriter, String text, RewriteSingleCallback callback) {
        RewritingRequest request = RewritingRequest.builder(text).build();
        Futures.addCallback(rewriter.runInference(request), new FutureCallback<RewritingResult>() {
            @Override
            public void onSuccess(RewritingResult result) {
                rewriter.close();
                List<String> variants = new ArrayList<>();
                if (result != null && result.getResults() != null) {
                    for (RewritingSuggestion suggestion : result.getResults()) {
                        if (suggestion != null && suggestion.getText() != null) {
                            String s = suggestion.getText().trim();
                            if (!s.isEmpty() && !variants.contains(s)) {
                                variants.add(s);
                            }
                        }
                    }
                }
                if (!variants.isEmpty()) {
                    String best = variants.get(0);
                    mainHandler.post(() -> callback.onSuccess(best, variants));
                } else {
                    mainHandler.post(() -> callback.onError("No rewrite suggestions returned"));
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                rewriter.close();
                AppLogger.log(TAG, "Gemini Nano single rewrite failed", t);
                mainHandler.post(() -> callback.onError(t.getMessage() != null ? t.getMessage() : "Rewrite blocked or failed"));
            }
        }, executor);
    }

    /**
     * Synchronously rewrites an array of LLM options on a background thread (with timeout and per-item fallback).
     * Automatically falls back to Feature 607 (Language.ENGLISH) if the preferred language feature is unavailable.
     */
    public static String[] rewriteOptionsBlocking(Context context, String[] originalOptions, String toneKey) {
        if (originalOptions == null || originalOptions.length == 0 || context == null) {
            return originalOptions;
        }
        Context appCtx = context.getApplicationContext();
        int outputType = resolveOutputType(toneKey);
        int preferredLang = cachedWorkingLanguage != null
                ? cachedWorkingLanguage
                : resolvePreferredLanguage(appCtx);

        Rewriter rewriter = null;
        try {
            rewriter = prepareBlockingRewriter(appCtx, outputType, preferredLang);
            if (rewriter == null && preferredLang != RewriterOptions.Language.ENGLISH) {
                rewriter = prepareBlockingRewriter(appCtx, outputType, RewriterOptions.Language.ENGLISH);
            }
            if (rewriter == null) {
                return originalOptions;
            }

            String[] refined = new String[originalOptions.length];
            for (int i = 0; i < originalOptions.length; i++) {
                String orig = originalOptions[i] != null ? originalOptions[i].trim() : "";
                if (orig.isEmpty()) {
                    refined[i] = orig;
                    continue;
                }
                try {
                    RewritingRequest req = RewritingRequest.builder(orig).build();
                    RewritingResult res = rewriter.runInference(req).get(20, TimeUnit.SECONDS);
                    if (res != null && res.getResults() != null && !res.getResults().isEmpty()) {
                        String candidate = res.getResults().get(0).getText();
                        if (candidate != null && !candidate.trim().isEmpty()) {
                            refined[i] = candidate.trim();
                            continue;
                        }
                    }
                } catch (Exception itemEx) {
                    AppLogger.log(TAG, "Gemini Nano skipped/blocked option " + i + ", keeping original LLM text", itemEx);
                }
                refined[i] = orig;
            }
            return refined;
        } catch (Exception e) {
            AppLogger.log(TAG, "Gemini Nano batch rewrite fallback to original options", e);
            return originalOptions;
        } finally {
            if (rewriter != null) {
                try {
                    rewriter.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private static Rewriter prepareBlockingRewriter(Context appCtx, int outputType, int language) {
        RewriterOptions options = RewriterOptions.builder(appCtx)
                .setOutputType(outputType)
                .setLanguage(language)
                .build();
        Rewriter rewriter = Rewriting.getClient(options);
        try {
            Integer status = rewriter.checkFeatureStatus().get(10, TimeUnit.SECONDS);
            if (status != null && status == FeatureStatus.AVAILABLE) {
                cachedWorkingLanguage = language;
                cachedSupportedState = true;
                return rewriter;
            }
            if (status != null && status == FeatureStatus.DOWNLOADABLE) {
                CountDownLatch dlLatch = new CountDownLatch(1);
                final boolean[] dlSuccess = {false};
                rewriter.downloadFeature(new DownloadCallback() {
                    @Override
                    public void onDownloadStarted(long l) {}

                    @Override
                    public void onDownloadProgress(long l) {}

                    @Override
                    public void onDownloadCompleted() {
                        dlSuccess[0] = true;
                        dlLatch.countDown();
                    }

                    @Override
                    public void onDownloadFailed(GenAiException e) {
                        dlLatch.countDown();
                    }
                });
                dlLatch.await(45, TimeUnit.SECONDS);
                if (dlSuccess[0]) {
                    cachedWorkingLanguage = language;
                    cachedSupportedState = true;
                    return rewriter;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            rewriter.close();
        } catch (Exception ignored) {}
        return null;
    }
}
