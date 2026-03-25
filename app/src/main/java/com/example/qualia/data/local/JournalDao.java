package com.example.qualia.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.qualia.data.model.JournalEntry;

import java.util.List;

@Dao
public interface JournalDao {

    @Insert
    void insert(JournalEntry entry);

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    List<JournalEntry> getAllEntries();

    @Query("DELETE FROM journal_entries WHERE id = :id")
    void deleteById(int id);
}