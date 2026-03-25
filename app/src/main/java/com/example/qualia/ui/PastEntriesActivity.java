package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qualia.R;
import com.example.qualia.data.model.JournalEntry;
import com.example.qualia.data.repository.JournalRepository;

import java.util.List;

public class PastEntriesActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_past_entries);

        TextView btnBack = findViewById(R.id.btnBack);
        RecyclerView recycler = findViewById(R.id.recyclerEntries);
        TextView txtEmpty = findViewById(R.id.txtEmpty);

        btnBack.setOnClickListener(v -> finish());

        recycler.setLayoutManager(new LinearLayoutManager(this));

        new JournalRepository(this).getAllEntries(entries -> runOnUiThread(() -> {
            if (entries == null || entries.isEmpty()) {
                txtEmpty.setVisibility(View.VISIBLE);
                recycler.setVisibility(View.GONE);
            } else {
                txtEmpty.setVisibility(View.GONE);
                recycler.setVisibility(View.VISIBLE);
                recycler.setAdapter(new JournalAdapter(entries, entry -> {
                    Intent intent = new Intent(this, EntryDetailActivity.class);
                    intent.putExtra("entry_text", entry.text);
                    intent.putExtra("entry_date", entry.createdAt);
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
                }));
            }
        }));
    }
}