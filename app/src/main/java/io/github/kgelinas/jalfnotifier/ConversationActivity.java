package io.github.kgelinas.jalfnotifier;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ConversationActivity extends AppCompatActivity {

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
            String conversationLink = getIntent().getStringExtra("conversationLink");
            String otherUserId = getIntent().getStringExtra("otherUserId");
            String otherName = getIntent().getStringExtra("otherName");
            String avatarUrl = getIntent().getStringExtra("avatarUrl");
            String sexIconUrl = getIntent().getStringExtra("sexIconUrl");
            boolean isOnline = getIntent().getBooleanExtra("isOnline", false);

            ConversationFragment fragment = ConversationFragment.newInstance(
                    conversationLink, otherUserId, otherName, avatarUrl, sexIconUrl, isOnline);

            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container_root, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
