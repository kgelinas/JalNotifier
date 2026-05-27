package io.github.kgelinas.jalfnotifier;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import io.github.kgelinas.jalfnotifier.databinding.ActivitySettingsIntelligenceBinding;

public class SettingsIntelligenceActivity extends AppCompatActivity {

    private ActivitySettingsIntelligenceBinding binding;
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsIntelligenceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets insetsValues = insets
                    .getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insetsValues.left, insetsValues.top, insetsValues.right, insetsValues.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        setSupportActionBar(binding.settingsToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupLanguageDropdown();
        setupProviderDropdown();
        setupObservers();

        binding.editAiEndpoint.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.aiEndpoint.getValue())) {
                    viewModel.setAiEndpoint(s.toString());
                    viewModel.fetchAvailableModels();
                }
            }
        });

        binding.editAiToken.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.aiToken.getValue())) {
                    viewModel.setAiToken(s.toString());
                    // Refresh model list whenever the API key changes
                    viewModel.fetchAvailableModels();
                }
            }
        });

        binding.editGeminiPreference.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.geminiPreference.getValue())) {
                    viewModel.setGeminiPreference(s.toString());
                }
            }
        });

        binding.layoutGeminiPreference.setEndIconOnClickListener(v -> showPersonaEditorDialog());
        viewModel.fetchAvailableModels();
    }

    private void setupLanguageDropdown() {
        String[] languages = new String[] { "Français", "English", "Español", "Deutsch", "Italiano" };
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                languages);
        binding.editGeminiLanguage.setAdapter(langAdapter);

        binding.editGeminiLanguage.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            viewModel.setGeminiLanguage(selected);
        });
    }

    private void setupProviderDropdown() {
        String[] providers = new String[] {
            "Google Gemini",
            "OpenRouter",
            "Custom (OpenAI Compatible)"
        };
        ArrayAdapter<String> providerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                providers);
        binding.editAiProvider.setAdapter(providerAdapter);

        binding.editAiProvider.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            viewModel.setAiProvider(selected);

            String endpoint = "";
            if ("Google Gemini".equals(selected)) {
                endpoint = "https://generativelanguage.googleapis.com/v1beta/openai";
            } else if ("OpenRouter".equals(selected)) {
                endpoint = "https://openrouter.ai/api";
            }

            if (!endpoint.isEmpty()) {
                binding.editAiEndpoint.setText(endpoint);
                viewModel.setAiEndpoint(endpoint);
                viewModel.fetchAvailableModels();
            }
        });
    }

    private void setupObservers() {
        viewModel.geminiLanguage.observe(this, lang -> {
            if (lang != null && !lang.equals(binding.editGeminiLanguage.getText().toString())) {
                binding.editGeminiLanguage.setText(lang, false);
            }
        });

        viewModel.geminiPreference.observe(this, pref -> {
            if (pref != null && !pref.equals(binding.editGeminiPreference.getText().toString())) {
                binding.editGeminiPreference.setText(pref);
            }
        });

        viewModel.aiProvider.observe(this, provider -> {
            if (provider != null && !provider.equals(binding.editAiProvider.getText().toString())) {
                binding.editAiProvider.setText(provider, false);
            }
        });

        viewModel.aiToken.observe(this, token -> {
            if (token != null && !token.equals(binding.editAiToken.getText().toString())) {
                binding.editAiToken.setText(token);
            }
        });

        viewModel.aiEndpoint.observe(this, endpoint -> {
            if (endpoint != null && !endpoint.equals(binding.editAiEndpoint.getText().toString())) {
                binding.editAiEndpoint.setText(endpoint);
            }
        });

        viewModel.availableModels.observe(this, models -> {
            if (models != null) {
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line,
                        models);
                binding.editGeminiModel.setAdapter(adapter);
            }
        });

        binding.editGeminiModel.setOnItemClickListener((parent, view, position, id) -> {
            String selected = (String) parent.getItemAtPosition(position);
            viewModel.setGeminiModel(selected);
        });

        viewModel.geminiModel.observe(this, model -> {
            if (model != null && !model.equals(binding.editGeminiModel.getText().toString())) {
                binding.editGeminiModel.setText(model, false);
            }
        });
    }

    private void showPersonaEditorDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_persona_editor, null);
        EditText edit = view.findViewById(R.id.edit_persona_full);
        Button btn = view.findViewById(R.id.btn_save_persona);

        edit.setText(binding.editGeminiPreference.getText().toString());
        btn.setOnClickListener(v -> {
            binding.editGeminiPreference.setText(edit.getText().toString());
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
