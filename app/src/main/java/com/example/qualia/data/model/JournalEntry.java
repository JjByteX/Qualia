package com.example.qualia.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "journal_entries")
public class JournalEntry {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String text;
    public long createdAt;

    public JournalEntry(String text, long createdAt) {
        this.text = text;
        this.createdAt = createdAt;
    }
}