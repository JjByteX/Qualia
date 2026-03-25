package com.example.qualia.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.qualia.data.model.JournalEntry;

@Database(entities = {JournalEntry.class}, version = 1, exportSchema = false)
public abstract class QualiaDatabase extends RoomDatabase {

    private static QualiaDatabase instance;

    public abstract JournalDao journalDao();

    public static synchronized QualiaDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    QualiaDatabase.class,
                    "qualia_database"
            ).fallbackToDestructiveMigration().build();
        }
        return instance;
    }
}