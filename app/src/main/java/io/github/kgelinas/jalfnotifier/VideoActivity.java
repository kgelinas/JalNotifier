package io.github.kgelinas.jalfnotifier;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class VideoActivity extends AppCompatActivity {

    private VideoView videoView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_activity_video);

        View mainView = findViewById(R.id.video_root_container);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return WindowInsetsCompat.CONSUMED;
            });
        }

        String videoUrl = getIntent().getStringExtra("videoUrl");
        if (videoUrl == null || videoUrl.isEmpty()) {
            finish();
            return;
        }

        videoView = findViewById(R.id.video_view);
        progressBar = findViewById(R.id.video_progress);
        View btnClose = findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> finish());

        MediaController mediaController = new MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        progressBar.setVisibility(View.VISIBLE);
        videoView.setVideoURI(Uri.parse(videoUrl));

        videoView.setOnPreparedListener(mp -> {
            progressBar.setVisibility(View.GONE);
            videoView.start();
        });

        videoView.setOnErrorListener((mp, what, extra) -> {
            progressBar.setVisibility(View.GONE);
            return false;
        });

        videoView.setOnCompletionListener(mp -> finish());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView.isPlaying()) {
            videoView.pause();
        }
    }
}
