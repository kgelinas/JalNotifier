package io.github.kgelinas.jalfnotifier.ui;
import io.github.kgelinas.jalfnotifier.R;
import io.github.kgelinas.jalfnotifier.databinding.*;
import io.github.kgelinas.jalfnotifier.ui.*;
import io.github.kgelinas.jalfnotifier.data.*;
import io.github.kgelinas.jalfnotifier.data.repository.*;
import io.github.kgelinas.jalfnotifier.worker.*;
import io.github.kgelinas.jalfnotifier.util.*;


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

        setupSlotDropdown();
        setupLanguageDropdown();
        setupProviderDropdown();
        setupObservers();

        binding.editAiEndpoint.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.aiEndpoint.getValue())) {
                    viewModel.setAiEndpoint(s.toString());
                    viewModel.fetchAvailableModels();
                }
            }
        });

        binding.editFriendlyName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.friendlyName.getValue())) {
                    viewModel.setFriendlyName(s.toString());
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

        binding.editAiPromptTemplate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.aiPromptTemplate.getValue())) {
                    viewModel.setAiPromptTemplate(s.toString());
                }
            }
        });

        binding.editAiReplyPromptTemplate.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(viewModel.aiReplyPromptTemplate.getValue())) {
                    viewModel.setAiReplyPromptTemplate(s.toString());
                }
            }
        });

        String[] tags = new String[]{
            "{myProfile}", "{otherProfile}", "{history}", "{specificMessage}",
            "{name}", "{sex}", "{city}", "{age}", "{social_status}", "{goals}", "{sexual_orientation}", "{relationship}", "{fantasies}", "{profile_descriptions}",
            "{myName}", "{mySex}", "{myCity}", "{myAge}", "{mySocial_status}", "{myGoals}", "{mySexual_orientation}", "{myRelationship}", "{myFantasies}", "{myProfile_descriptions}"
        };
        ArrayAdapter<String> tagsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tags);
        binding.editAiPromptTemplate.setAdapter(tagsAdapter);
        binding.editAiPromptTemplate.setThreshold(1); // Trigger on the first character '{'
        android.widget.MultiAutoCompleteTextView.Tokenizer tokenizer = new android.widget.MultiAutoCompleteTextView.Tokenizer() {
            @Override
            public int findTokenStart(CharSequence text, int cursor) {
                int i = cursor;
                while (i > 0 && text.charAt(i - 1) != '{') {
                    i--;
                }
                if (i > 0 && text.charAt(i - 1) == '{') {
                    return i - 1;
                }
                return cursor;
            }

            @Override
            public int findTokenEnd(CharSequence text, int cursor) {
                int i = cursor;
                int len = text.length();
                while (i < len) {
                    if (text.charAt(i) == '}') {
                        return i + 1;
                    }
                    i++;
                }
                return len;
            }

            @Override
            public CharSequence terminateToken(CharSequence text) {
                return text;
            }
        };

        binding.editAiPromptTemplate.setTokenizer(tokenizer);
        binding.editAiReplyPromptTemplate.setAdapter(tagsAdapter);
        binding.editAiReplyPromptTemplate.setThreshold(1);
        binding.editAiReplyPromptTemplate.setTokenizer(tokenizer);

        binding.btnResetPromptTemplate.setOnClickListener(v -> {
            viewModel.setAiPromptTemplate(getString(R.string.default_ai_intro_prompt));
        });

        binding.btnResetReplyPromptTemplate.setOnClickListener(v -> {
            viewModel.setAiReplyPromptTemplate(getString(R.string.default_ai_reply_prompt));
        });

        binding.layoutAiPromptTemplate.setEndIconOnClickListener(v -> showPromptTemplateEditorDialog(true));
        binding.layoutAiReplyPromptTemplate.setEndIconOnClickListener(v -> showPromptTemplateEditorDialog(false));

        binding.switchIncludePicture.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setAiIncludePicture(isChecked);
        });

        binding.switchIncludeAllPictures.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setAiIncludeAllPictures(isChecked);
            if (isChecked) {
                android.widget.Toast.makeText(this, "Avertissement : Joindre toutes les photos peut ralentir la réponse.", android.widget.Toast.LENGTH_LONG).show();
            }
        });

        binding.switchEncodeBase64.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setAiEncodeBase64(isChecked);
        });

        viewModel.fetchAvailableModels();
    }

    private void setupSlotDropdown() {
        String[] slots = new String[] { "Primary LLM", "Fallback LLM 1", "Fallback LLM 2" };
        ArrayAdapter<String> slotAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, slots);
        binding.editLlmSlot.setAdapter(slotAdapter);
        binding.editLlmSlot.setText(slots[0], false);

        binding.editLlmSlot.setOnItemClickListener((parent, view, position, id) -> {
            viewModel.setCurrentLlmSlot(position);
        });
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
        viewModel.currentLlmSlot.observe(this, slot -> {
            if (slot != null) {
                String[] slots = new String[] { "Primary LLM", "Fallback LLM 1", "Fallback LLM 2" };
                if (slot >= 0 && slot < slots.length && !slots[slot].equals(binding.editLlmSlot.getText().toString())) {
                    binding.editLlmSlot.setText(slots[slot], false);
                }
            }
        });

        viewModel.geminiLanguage.observe(this, lang -> {
            if (lang != null && !lang.equals(binding.editGeminiLanguage.getText().toString())) {
                binding.editGeminiLanguage.setText(lang, false);
            }
        });

        viewModel.aiPromptTemplate.observe(this, pref -> {
            String toDisplay = (pref == null || pref.isEmpty()) ? getString(R.string.default_ai_intro_prompt) : pref;
            if (!toDisplay.equals(binding.editAiPromptTemplate.getText().toString())) {
                binding.editAiPromptTemplate.setText(toDisplay);
            }
            if (pref == null || pref.isEmpty()) {
                binding.layoutAiPromptTemplate.setHelperText("Currently using the default app template. Edit to override.");
            } else {
                binding.layoutAiPromptTemplate.setHelperText(null);
            }
        });

        viewModel.friendlyName.observe(this, name -> {
            if (name != null && !name.equals(binding.editFriendlyName.getText().toString())) {
                binding.editFriendlyName.setText(name);
            }
        });

        viewModel.aiReplyPromptTemplate.observe(this, pref -> {
            String toDisplay = (pref == null || pref.isEmpty()) ? getString(R.string.default_ai_reply_prompt) : pref;
            if (!toDisplay.equals(binding.editAiReplyPromptTemplate.getText().toString())) {
                binding.editAiReplyPromptTemplate.setText(toDisplay);
            }
            if (pref == null || pref.isEmpty()) {
                binding.layoutAiReplyPromptTemplate.setHelperText("Currently using the default app template. Edit to override.");
            } else {
                binding.layoutAiReplyPromptTemplate.setHelperText(null);
            }
        });

        viewModel.aiIncludePicture.observe(this, include -> {
            if (include != null && include != binding.switchIncludePicture.isChecked()) {
                binding.switchIncludePicture.setChecked(include);
            }
        });

        viewModel.aiIncludeAllPictures.observe(this, include -> {
            if (include != null && include != binding.switchIncludeAllPictures.isChecked()) {
                binding.switchIncludeAllPictures.setChecked(include);
            }
        });

        viewModel.aiEncodeBase64.observe(this, encode -> {
            if (encode != null && encode != binding.switchEncodeBase64.isChecked()) {
                binding.switchEncodeBase64.setChecked(encode);
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

    private void showPromptTemplateEditorDialog(boolean isIntro) {
        if (isIntro) binding.editAiPromptTemplate.dismissDropDown();
        else binding.editAiReplyPromptTemplate.dismissDropDown();

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_persona_editor, null);
        
        android.widget.MultiAutoCompleteTextView edit = view.findViewById(R.id.edit_persona_full);
        android.widget.Button btn = view.findViewById(R.id.btn_save_persona);

        // Setup the exact same tags dropdown logic for the fullscreen editor
        String[] tags = new String[]{
            "{myProfile}", "{otherProfile}", "{history}", "{specificMessage}",
            "{name}", "{sex}", "{city}", "{age}", "{social_status}", "{goals}", "{sexual_orientation}", "{relationship}", "{fantasies}", "{profile_descriptions}",
            "{myName}", "{mySex}", "{myCity}", "{myAge}", "{mySocial_status}", "{myGoals}", "{mySexual_orientation}", "{myRelationship}", "{myFantasies}", "{myProfile_descriptions}"
        };
        android.widget.ArrayAdapter<String> tagsAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tags);
        edit.setAdapter(tagsAdapter);
        edit.setThreshold(1);
        edit.setTokenizer(new android.widget.MultiAutoCompleteTextView.Tokenizer() {
            @Override
            public int findTokenStart(CharSequence text, int cursor) {
                int i = cursor;
                while (i > 0 && text.charAt(i - 1) != '{') {
                    i--;
                }
                if (i > 0 && text.charAt(i - 1) == '{') {
                    return i - 1;
                }
                return cursor;
            }

            @Override
            public int findTokenEnd(CharSequence text, int cursor) {
                int i = cursor;
                int len = text.length();
                while (i < len) {
                    if (text.charAt(i) == '}') {
                        return i + 1;
                    }
                    i++;
                }
                return len;
            }

            @Override
            public CharSequence terminateToken(CharSequence text) {
                return text;
            }
        });

        if (isIntro) {
            edit.setText(binding.editAiPromptTemplate.getText().toString());
            btn.setOnClickListener(v -> {
                binding.editAiPromptTemplate.setText(edit.getText().toString());
                dialog.dismiss();
            });
        } else {
            edit.setText(binding.editAiReplyPromptTemplate.getText().toString());
            btn.setOnClickListener(v -> {
                binding.editAiReplyPromptTemplate.setText(edit.getText().toString());
                dialog.dismiss();
            });
        }

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
