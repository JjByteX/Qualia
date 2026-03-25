package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.qualia.R;
import com.example.qualia.data.model.JournalEntry;
import com.example.qualia.data.repository.JournalRepository;

public class JournalActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_journal);

        EditText editText = findViewById(R.id.editJournal);
        TextView btnSave = findViewById(R.id.btnSave);
        TextView btnBack = findViewById(R.id.btnBack);
        TextView btnPastEntries = findViewById(R.id.btnPastEntries);

        btnSave.setOnClickListener(v -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                JournalEntry entry = new JournalEntry(text, System.currentTimeMillis());
                new JournalRepository(this).insert(entry);
                Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        btnBack.setOnClickListener(v -> finish());

        btnPastEntries.setOnClickListener(v -> {
            startActivity(new Intent(this, PastEntriesActivity.class));
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        });
    }
}