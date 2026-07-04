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

        String[] tags = new String[]{"{myProfile}", "{otherProfile}", "{history}"};
        ArrayAdapter<String> tagsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tags);
        binding.editAiPromptTemplate.setAdapter(tagsAdapter);
        binding.editAiPromptTemplate.setThreshold(1); // Trigger on the first character '{'
        binding.editAiPromptTemplate.setTokenizer(new android.widget.MultiAutoCompleteTextView.Tokenizer() {
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

        binding.btnResetPromptTemplate.setOnClickListener(v -> {
            viewModel.setAiPromptTemplate("");
        });

        binding.editAiProfileFields.setOnClickListener(v -> showProfileFieldsDialog());

        binding.layoutAiPromptTemplate.setEndIconOnClickListener(v -> showPromptTemplateEditorDialog());
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
            if (pref != null && !pref.equals(binding.editAiPromptTemplate.getText().toString())) {
                binding.editAiPromptTemplate.setText(pref);
            }
            if (pref == null || pref.isEmpty()) {
                binding.layoutAiPromptTemplate.setHelperText("Leave empty to use the default app template");
            } else {
                binding.layoutAiPromptTemplate.setHelperText(null);
            }
        });

        viewModel.friendlyName.observe(this, name -> {
            if (name != null && !name.equals(binding.editFriendlyName.getText().toString())) {
                binding.editFriendlyName.setText(name);
            }
        });

        viewModel.aiProfileFields.observe(this, fields -> {
            if (fields != null && !fields.equals(binding.editAiProfileFields.getText().toString())) {
                binding.editAiProfileFields.setText(fields);
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

    private void showProfileFieldsDialog() {
        String[] allFields = {"name", "age", "city", "social_status", "goals", "sex", "sexes_interested", "sexual_orientation", "relationship", "fantasies", "profile_descriptions"};
        String currentPrefs = viewModel.aiProfileFields.getValue();
        if (currentPrefs == null) currentPrefs = "";
        
        java.util.List<String> selectedList = java.util.Arrays.asList(currentPrefs.split(","));
        boolean[] checkedItems = new boolean[allFields.length];
        for (int i = 0; i < allFields.length; i++) {
            checkedItems[i] = selectedList.contains(allFields[i]);
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_intelligence_profile_fields)
                .setMultiChoiceItems(allFields, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < allFields.length; i++) {
                        if (checkedItems[i]) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append(allFields[i]);
                        }
                    }
                    viewModel.setAiProfileFields(sb.toString());
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPromptTemplateEditorDialog() {
        binding.editAiPromptTemplate.dismissDropDown(); // Hide the main popup if it was open

        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_persona_editor, null);
        
        android.widget.MultiAutoCompleteTextView edit = view.findViewById(R.id.edit_persona_full);
        android.widget.Button btn = view.findViewById(R.id.btn_save_persona);

        // Setup the exact same tags dropdown logic for the fullscreen editor
        String[] tags = new String[]{"{myProfile}", "{otherProfile}", "{history}"};
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

        edit.setText(binding.editAiPromptTemplate.getText().toString());
        btn.setOnClickListener(v -> {
            binding.editAiPromptTemplate.setText(edit.getText().toString());
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
