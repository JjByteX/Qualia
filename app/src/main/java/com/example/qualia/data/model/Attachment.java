package com.example.qualia.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * An attachment to a journal entry — a chalk drawing or, later, a polaroid photo.
 *
 * Both mediums are stored on disk under {@code getFilesDir()/attachments/<entry_id>/...}.
 * The DB row is just a pointer + metadata. If the file is missing on read, the entry
 * still works — the attachment is silently skipped. A journal entry with a missing
 * attachment is more in keeping with the app's philosophy than a crash.
 */
@Entity(
        tableName = "attachments",
        foreignKeys = @ForeignKey(
                entity = JournalEntry.class,
                parentColumns = "id",
                childColumns = "entryId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("entryId")}
)
public class Attachment {

    /** Drawing attachment — colored-pencil / crayon strokes saved as a
     *  transparent PNG by JournalActivity. The legacy string value "chalk" is
     *  kept for backwards compatibility with the v1 schema. */
    public static final String TYPE_CHALK    = "chalk";

    /** Polaroid photo (PNG produced by CameraActivity — phase 2). */
    public static final String TYPE_POLAROID = "polaroid";

    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "entryId")
    public int entryId;

    /** {@link #TYPE_CHALK} or {@link #TYPE_POLAROID}. */
    public String type;

    /** Path relative to {@code Context.getFilesDir()}. */
    public String filePath;

    public long createdAt;

    public Attachment() { }

    public Attachment(int entryId, String type, String filePath, long createdAt) {
        this.entryId   = entryId;
        this.type      = type;
        this.filePath  = filePath;
        this.createdAt = createdAt;
    }
}
