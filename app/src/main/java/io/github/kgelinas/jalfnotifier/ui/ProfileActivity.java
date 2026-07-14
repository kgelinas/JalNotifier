package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState == null) {
            String userId = getIntent().getStringExtra("userId");
            String avatarUrl = getIntent().getStringExtra("avatarUrl");
            boolean isFavorite = getIntent().getBooleanExtra("isFavorite", false);
            boolean isBookmarked = getIntent().getBooleanExtra("isBookmarked", false);
            boolean fromChat = getIntent().getBooleanExtra("fromChat", false);

            ProfileFragment fragment = ProfileFragment.newInstance(userId, avatarUrl, isFavorite, isBookmarked, fromChat);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container_root, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }
}
