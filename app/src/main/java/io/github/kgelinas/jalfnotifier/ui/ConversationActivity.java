package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ConversationActivity extends AppCompatActivity {

    /**
     * Attaches NEW_DOCUMENT flags and a unique conversation URI only when the host
     * Activity is running in multi-window / desktop freeform mode, keeping normal
     * phone navigation within the single main task stack.
     */
    public static void applyMultiWindowFlagsIfNeeded(Context context, Intent intent,
            String otherUserId, String conversationLink) {
        if (context == null || intent == null) return;
        boolean inMultiWindow = (context instanceof Activity) && ((Activity) context).isInMultiWindowMode();
        if (inMultiWindow) {
            String key = (otherUserId != null && !otherUserId.isEmpty())
                    ? otherUserId
                    : (conversationLink != null ? conversationLink : "");
            if (!key.isEmpty()) {
                intent.setData(Uri.parse("jalf://conversation/" + key));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fragment_container);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container_root), (v, insets) -> {
            Insets insetsValues = insets
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        if (savedInstanceState == null) {
            loadConversationFromIntent(getIntent());
        }
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        loadConversationFromIntent(intent);
    }

    private void loadConversationFromIntent(Intent intent) {
        if (intent == null) return;
        String conversationLink = intent.getStringExtra("conversationLink");
        String otherUserId = intent.getStringExtra("otherUserId");
        String otherName = intent.getStringExtra("otherName");
        String avatarUrl = intent.getStringExtra("avatarUrl");
        String sexIconUrl = intent.getStringExtra("sexIconUrl");
        boolean isOnline = intent.getBooleanExtra("isOnline", false);

        ConversationFragment fragment = ConversationFragment.newInstance(
                conversationLink, otherUserId, otherName, avatarUrl, sexIconUrl, isOnline);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container_root, fragment)
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
