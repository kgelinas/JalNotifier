package io.github.kgelinas.jalfnotifier;

import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import android.content.SharedPreferences;
import android.content.Context;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

public class FullscreenImageActivity extends AppCompatActivity {
    private List<String> imageList;
    private List<Integer> imageRanks;
    private boolean blurNsfw = true;
    private int initialPosition;
    private ViewPager2 viewPager;
    private ArrayList<String> picIds;
    private boolean[] likes;
    private boolean[] realizes;
    private FloatingActionButton fabLike;
    private FloatingActionButton fabRealize;
    private boolean isFabMenuOpen = false;

    private void toggleFabMenu(FloatingActionButton fabMain, FloatingActionButton fabSave) {
        isFabMenuOpen = !isFabMenuOpen;
        int position = viewPager.getCurrentItem();
        String picId = (picIds != null && picIds.size() > position) ? picIds.get(position) : null;
        boolean hasPicId = picId != null && !picId.isEmpty();

        if (isFabMenuOpen) {
            fabMain.setImageResource(R.drawable.ic_close_24);
            fabSave.show();
            if (hasPicId) {
                fabLike.show();
                fabRealize.show();
            }
        } else {
            fabMain.setImageResource(R.drawable.ic_menu);
            fabSave.hide();
            fabLike.hide();
            fabRealize.hide();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

        imageList = getIntent().getStringArrayListExtra("imageUrlList");
        if (imageList == null)
            imageList = getIntent().getStringArrayListExtra("images");

        imageRanks = getIntent().getIntegerArrayListExtra("imageRanks");
        picIds = getIntent().getStringArrayListExtra("picIds");
        likes = getIntent().getBooleanArrayExtra("likes");
        realizes = getIntent().getBooleanArrayExtra("realizes");

        SharedPreferences prefs = getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        blurNsfw = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);

        initialPosition = getIntent().getIntExtra("initialPosition", 0);
        if (!getIntent().hasExtra("initialPosition")) {
            initialPosition = getIntent().getIntExtra("position", 0);
        }

        if (imageList == null || imageList.isEmpty()) {
            String singleUrl = getIntent().getStringExtra("imageUrl");
            if (singleUrl != null && !singleUrl.isEmpty()) {
                imageList = new ArrayList<>();
                imageList.add(singleUrl);
            } else {
                Toast.makeText(this, R.string.no_image_to_display, Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        MaterialToolbar toolbar = findViewById(R.id.toolbar_fullscreen);
        toolbar.setNavigationOnClickListener(v -> finish());

        FloatingActionButton fabSave = findViewById(R.id.fab_save);
        fabSave.setOnClickListener(v -> saveImageToGallery());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            toolbar.setPadding(0, systemBars.top, 0, 0);

            ViewGroup.MarginLayoutParams fabParams = (ViewGroup.MarginLayoutParams) fabSave.getLayoutParams();
            fabParams.bottomMargin = systemBars.bottom + (int) (24 * getResources().getDisplayMetrics().density);
            fabSave.setLayoutParams(fabParams);

            return insets;
        });

        viewPager = findViewById(R.id.view_pager_fullscreen);
        FullscreenImageAdapter adapter = new FullscreenImageAdapter(imageList);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(initialPosition, false);

        FloatingActionButton fabMain = findViewById(R.id.fab_main);
        fabLike = findViewById(R.id.fab_like);
        fabRealize = findViewById(R.id.fab_realize);

        if (fabMain != null) {
            fabMain.setOnClickListener(v -> toggleFabMenu(fabMain, fabSave));
        }

        if (picIds != null) {
            viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    super.onPageSelected(position);
                    String picId = picIds.get(position);
                    boolean hasPicId = picId != null && !picId.isEmpty();
                    
                    if (hasPicId) {
                        fabLike.setImageResource(likes[position] ? R.drawable.ic_like_active : R.drawable.ic_like_inactive);
                        fabRealize.setImageResource(realizes[position] ? R.drawable.ic_realize_active : R.drawable.ic_realize_inactive);
                    }
                    
                    if (isFabMenuOpen) {
                        if (hasPicId) {
                            fabLike.show();
                            fabRealize.show();
                        } else {
                            fabLike.hide();
                            fabRealize.hide();
                        }
                    } else {
                        fabLike.hide();
                        fabRealize.hide();
                    }
                }
            });

            fabLike.setOnClickListener(v -> {
                int pos = viewPager.getCurrentItem();
                String picId = picIds.get(pos);
                if (picId == null || picId.isEmpty()) return;
                boolean newState = !likes[pos];
                likes[pos] = newState;
                fabLike.setImageResource(newState ? R.drawable.ic_like_active : R.drawable.ic_like_inactive);
                toggleStatus(picId, newState, "add", "del");
            });

            fabRealize.setOnClickListener(v -> {
                int pos = viewPager.getCurrentItem();
                String picId = picIds.get(pos);
                if (picId == null || picId.isEmpty()) return;
                boolean newState = !realizes[pos];
                realizes[pos] = newState;
                fabRealize.setImageResource(newState ? R.drawable.ic_realize_active : R.drawable.ic_realize_inactive);
                toggleStatus(picId, newState, "add-fntsm", "del-fntsm");
            });
            
            // Initial state trigger
            viewPager.post(() -> {
                int pos = viewPager.getCurrentItem();
                if (picIds.size() > pos && picIds.get(pos) != null && !picIds.get(pos).isEmpty()) {
                    fabLike.setImageResource(likes[pos] ? R.drawable.ic_like_active : R.drawable.ic_like_inactive);
                    fabRealize.setImageResource(realizes[pos] ? R.drawable.ic_realize_active : R.drawable.ic_realize_inactive);
                }
            });
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(ApiConstants.PREFS_NAME, Context.MODE_PRIVATE);
        boolean newBlur = prefs.getBoolean(ApiConstants.KEY_BLUR_NSFW, true);
        if (newBlur != blurNsfw) {
            blurNsfw = newBlur;
            if (viewPager != null && viewPager.getAdapter() != null) {
                viewPager.getAdapter().notifyDataSetChanged();
            }
        }
    }

    private void saveImageToGallery() {
        String currentUrl = imageList.get(viewPager.getCurrentItem());
        Glide.with(this)
                .asBitmap()
                .load(currentUrl)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource,
                            @Nullable Transition<? super Bitmap> transition) {
                        saveBitmap(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable errorDrawable) {
                        Toast.makeText(FullscreenImageActivity.this, R.string.failed_to_download_image, Toast.LENGTH_SHORT)
                                .show();
                    }
                });
    }

    private void saveBitmap(Bitmap bitmap) {
        String filename = "JALF_" + System.currentTimeMillis() + ".jpg";
        OutputStream fos;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Jalf");

                Uri imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                fos = getContentResolver().openOutputStream(imageUri);
            } else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        .toString();
                java.io.File image = new java.io.File(imagesDir, filename);
                fos = new java.io.FileOutputStream(image);
            }

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            if (fos != null) {
                fos.flush();
                fos.close();
            }
            Toast.makeText(this, R.string.image_saved_to_gallery, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
        }
    }

    private void toggleStatus(String picId, boolean newState, String addAction, String delAction) {
        String action = newState ? addAction : delAction;
        SecurePrefs secure = SecurePrefs.get(this);
        String fullCookie = secure.getString(ApiConstants.KEY_FULL_COOKIE, "");
        String suid = secure.getString(ApiConstants.KEY_SUID, "");

        okhttp3.RequestBody formBody = new okhttp3.FormBody.Builder()
                .add("action", action)
                .add("Pid", picId)
                .build();

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(ApiConstants.BASE_URL + "/ct/mediaLovers/pictures/" + picId)
                .addHeader("Cookie", fullCookie)
                .addHeader("x-csrftoken", suid)
                .addHeader("User-Agent", ApiConstants.USER_AGENT)
                .addHeader("x-requested-with", "XMLHttpRequest")
                .post(formBody)
                .build();

        JalfNotifierApplication.httpClient().newCall(request).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull okhttp3.Call call, @NonNull java.io.IOException e) {
                android.util.Log.e("FullscreenImage", "Failed to update picture status", e);
            }

            @Override
            public void onResponse(@NonNull okhttp3.Call call, @NonNull okhttp3.Response response) throws java.io.IOException {
                response.close();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (picIds != null && likes != null && realizes != null) {
            android.content.Intent resultIntent = new android.content.Intent();
            resultIntent.putExtra("likes", likes);
            resultIntent.putExtra("realizes", realizes);
            setResult(RESULT_OK, resultIntent);
        }
        super.onBackPressed();
    }

    private class FullscreenImageAdapter extends RecyclerView.Adapter<FullscreenImageAdapter.ViewHolder> {
        private final List<String> images;

        public FullscreenImageAdapter(List<String> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fullscreen_image, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int rank = (imageRanks != null && imageRanks.size() > position) ? imageRanks.get(position) : 0;
            if (blurNsfw && rank > 0) {
                Glide.with(FullscreenImageActivity.this)
                        .load(images.get(position))
                        .apply(RequestOptions
                                .bitmapTransform(new jp.wasabeef.glide.transformations.BlurTransformation(25, 3)))
                        .into(holder.imageView);
            } else {
                Glide.with(FullscreenImageActivity.this).load(images.get(position)).into(holder.imageView);
            }
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.img_fullscreen_item);
            }
        }
    }
}
