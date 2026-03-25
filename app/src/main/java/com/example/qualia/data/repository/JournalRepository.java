package com.example.qualia.data.repository;

import android.content.Context;

import com.example.qualia.data.local.QualiaDatabase;
import com.example.qualia.data.model.JournalEntry;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JournalRepository {

    private final QualiaDatabase db;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JournalRepository(Context context) {
        db = QualiaDatabase.getInstance(context);
    }

    public void insert(JournalEntry entry) {
        executor.execute(() -> db.journalDao().insert(entry));
    }

    public void getAllEntries(Callback<List<JournalEntry>> callback) {
        executor.execute(() -> {
            List<JournalEntry> entries = db.journalDao().getAllEntries();
            callback.onResult(entries);
        });
    }

    public void deleteById(int id) {
        executor.execute(() -> db.journalDao().deleteById(id));
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}