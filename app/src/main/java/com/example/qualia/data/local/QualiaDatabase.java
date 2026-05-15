package com.example.qualia.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.qualia.data.model.Attachment;
import com.example.qualia.data.model.JournalEntry;

@Database(
        entities = {JournalEntry.class, Attachment.class},
        version = 2,
        exportSchema = false
)
public abstract class QualiaDatabase extends RoomDatabase {

    private static QualiaDatabase instance;

    public abstract JournalDao    journalDao();
    public abstract AttachmentDao attachmentDao();

    public static synchronized QualiaDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    QualiaDatabase.class,
                    "qualia_database"
            )
                    // v1 → v2 added the attachments table. There are no real users
                    // yet, so a destructive migration is acceptable. Existing journal
                    // entries on dev devices will be wiped on first launch after the
                    // schema bump — same trade-off the project already accepted at v1.
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
}
