package io.github.kgelinas.jalfnotifier.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.request.target.Target;
import com.caverock.androidsvg.PreserveAspectRatio;
import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Decodes SVG {@link InputStream}s into {@link Bitmap} resources so Glide transformations
 * (such as {@code centerCrop()}) and caching work seamlessly for SVG URLs.
 */
public class SvgBitmapDecoder implements ResourceDecoder<InputStream, Bitmap> {
    private static final int HEADER_PEEK_BYTES = 512;
    private static final int DEFAULT_SIZE_PX = 512;

    private final BitmapPool bitmapPool;

    public SvgBitmapDecoder(@NonNull BitmapPool bitmapPool) {
        this.bitmapPool = bitmapPool;
    }

    @Override
    public boolean handles(@NonNull InputStream source, @NonNull Options options) throws IOException {
        if (!source.markSupported()) {
            return false;
        }
        source.mark(HEADER_PEEK_BYTES);
        byte[] buffer = new byte[HEADER_PEEK_BYTES];
        int totalRead = 0;
        while (totalRead < HEADER_PEEK_BYTES) {
            int read = source.read(buffer, totalRead, HEADER_PEEK_BYTES - totalRead);
            if (read == -1) {
                break;
            }
            totalRead += read;
        }
        source.reset();
        if (totalRead <= 0) {
            return false;
        }
        String snippet = new String(buffer, 0, totalRead, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return snippet.contains("<svg");
    }

    @Nullable
    @Override
    public Resource<Bitmap> decode(@NonNull InputStream source, int width, int height, @NonNull Options options)
            throws IOException {
        try {
            SVG svg = SVG.getFromInputStream(source);
            RectF viewBox = svg.getDocumentViewBox();
            float docWidth = svg.getDocumentWidth();
            float docHeight = svg.getDocumentHeight();

            float intrinsicW = (viewBox != null && viewBox.width() > 0) ? viewBox.width()
                    : (docWidth > 0 ? docWidth : DEFAULT_SIZE_PX);
            float intrinsicH = (viewBox != null && viewBox.height() > 0) ? viewBox.height()
                    : (docHeight > 0 ? docHeight : DEFAULT_SIZE_PX);

            if (viewBox == null && intrinsicW > 0 && intrinsicH > 0) {
                svg.setDocumentViewBox(0f, 0f, intrinsicW, intrinsicH);
            }

            int reqW = (width == Target.SIZE_ORIGINAL || width <= 0) ? Math.round(intrinsicW) : width;
            int reqH = (height == Target.SIZE_ORIGINAL || height <= 0) ? Math.round(intrinsicH) : height;

            // Scale to cover requested dimensions while preserving original SVG aspect ratio,
            // so downstream Glide transformations (e.g. centerCrop) crop cleanly without distortion.
            float scale = Math.max((float) reqW / intrinsicW, (float) reqH / intrinsicH);
            int renderW = Math.max(1, Math.round(intrinsicW * scale));
            int renderH = Math.max(1, Math.round(intrinsicH * scale));

            svg.setDocumentWidth(renderW);
            svg.setDocumentHeight(renderH);
            svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.STRETCH);

            Bitmap bitmap = bitmapPool.get(renderW, renderH, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            svg.renderToCanvas(canvas);
            return BitmapResource.obtain(bitmap, bitmapPool);
        } catch (SVGParseException e) {
            throw new IOException("Failed to decode SVG stream", e);
        }
    }
}
