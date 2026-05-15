package com.example.qualia.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.qualia.data.model.Attachment;
import com.example.qualia.data.model.JournalEntry;

import java.util.List;

@Dao
public interface JournalDao {

    /**
     * Returns the auto-generated row id so the caller can link attachments.
     * Existing call sites that ignore the return value continue to work.
     */
    @Insert
    long insert(JournalEntry entry);

    /**
     * Used by the edit-window flow (entries written today are editable until
     * midnight). Updates the row identified by {@code entry.id}; we keep the
     * original {@code createdAt} so the entry stays on its day after editing.
     */
    @Update
    void update(JournalEntry entry);

    @Query("SELECT * FROM journal_entries ORDER BY createdAt DESC")
    List<JournalEntry> getAllEntries();

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    JournalEntry getById(int id);

    /** Alias used by JournalActivity. */
    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    JournalEntry getEntryById(int id);

    @Query("DELETE FROM journal_entries WHERE id = :id")
    void deleteById(int id);

    // ── Attachment support ────────────────────────────────────────────────────

    @Transaction
    default void updateEntryWithAttachments(JournalEntry entry, List<Attachment> attachments) {
        update(entry);
        deleteAttachmentsForEntry(entry.id);
        if (attachments != null && !attachments.isEmpty()) {
            insertAttachments(attachments);
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAttachments(List<Attachment> attachments);

    @Query("DELETE FROM attachments WHERE entryId = :entryId")
    void deleteAttachmentsForEntry(int entryId);

    @Query("SELECT * FROM attachments WHERE entryId = :entryId")
    List<Attachment> getAttachmentsForEntry(int entryId);
}
