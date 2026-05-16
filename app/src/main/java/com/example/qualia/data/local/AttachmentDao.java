package com.example.qualia.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.qualia.data.model.Attachment;

import java.util.List;

@Dao
public interface AttachmentDao {

    @Insert
    long insert(Attachment attachment);

    @Query("SELECT * FROM attachments WHERE entryId = :entryId ORDER BY createdAt ASC")
    List<Attachment> getForEntry(int entryId);

    /**
     * Lightweight check used by the journal list to decide whether to draw the
     * faint "has attachment" indicator. Returns 0 when the entry is text-only.
     */
    @Query("SELECT COUNT(*) FROM attachments WHERE entryId = :entryId")
    int countForEntry(int entryId);

    @Query("SELECT entryId FROM attachments GROUP BY entryId")
    List<Integer> getEntryIdsWithAttachments();

    @Query("DELETE FROM attachments WHERE entryId = :entryId")
    void deleteForEntry(int entryId);
}
