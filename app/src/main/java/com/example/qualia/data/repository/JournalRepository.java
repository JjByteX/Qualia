package com.example.qualia.data.repository;

import android.content.Context;

import com.example.qualia.data.local.QualiaDatabase;
import com.example.qualia.data.model.Attachment;
import com.example.qualia.data.model.JournalEntry;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class JournalRepository {

    private final QualiaDatabase db;
    private final File           filesDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public JournalRepository(Context context) {
        db       = QualiaDatabase.getInstance(context);
        // Cache the files dir up front so the background executor never touches
        // the (possibly disposed) Activity context for path-only work.
        filesDir = context.getApplicationContext().getFilesDir();
    }

    // ── Entries ───────────────────────────────────────────────────────────────

    /** Fire-and-forget insert. Use {@link #insertWithAttachments} when you need the new id. */
    public void insert(JournalEntry entry) {
        executor.execute(() -> db.journalDao().insert(entry));
    }

    /**
     * Insert an entry plus zero or more pending attachments in a single background
     * transaction. The supplied {@code pending} attachments have {@code entryId == 0};
     * this method assigns them the freshly-generated entry id before persisting.
     */
    public void insertWithAttachments(JournalEntry entry,
                                      List<Attachment> pending,
                                      Callback<Integer> done) {
        executor.execute(() -> {
            long entryId = db.journalDao().insert(entry);
            if (pending != null) {
                for (Attachment a : pending) {
                    a.entryId = (int) entryId;
                    db.attachmentDao().insert(a);
                }
            }
            if (done != null) done.onResult((int) entryId);
        });
    }

    /**
     * Edit-window save: replace an existing entry's text + attachments while
     * keeping its {@code createdAt} (so the entry stays on its day).
     *
     * <p>The caller passes the full set of attachments for the post-edit state.
     * Files referenced by the OLD attachment list but NOT in the new list are
     * deleted from disk along with their sidecars. Files that appear in both
     * lists are preserved untouched (their meta sidecars may be rewritten by
     * the activity before this call).
     */
    public void updateEntryWithAttachments(JournalEntry entry,
                                           List<Attachment> newAttachments,
                                           Callback<Integer> done) {
        executor.execute(() -> {
            int entryId = entry.id;

            // Snapshot old attachments and figure out which file paths the
            // user kept so we don't delete files we still reference.
            List<Attachment> oldAttachments = db.attachmentDao().getForEntry(entryId);
            Set<String> kept = new HashSet<>();
            if (newAttachments != null) {
                for (Attachment a : newAttachments) kept.add(a.filePath);
            }
            for (Attachment a : oldAttachments) {
                if (!kept.contains(a.filePath)) {
                    deleteAttachmentFiles(a);
                }
            }

            // Replace DB rows for this entry's attachments wholesale.
            db.attachmentDao().deleteForEntry(entryId);
            if (newAttachments != null) {
                for (Attachment a : newAttachments) {
                    a.entryId = entryId;
                    db.attachmentDao().insert(a);
                }
            }

            // Update the entry text. We never touch createdAt here — the entry
            // stays on the day it was first written.
            db.journalDao().update(entry);
            if (done != null) done.onResult(entryId);
        });
    }

    public void getAllEntries(Callback<List<JournalEntry>> callback) {
        executor.execute(() -> {
            List<JournalEntry> entries = db.journalDao().getAllEntries();
            callback.onResult(entries);
        });
    }

    public void getEntryById(int id, Callback<JournalEntry> callback) {
        executor.execute(() -> callback.onResult(db.journalDao().getById(id)));
    }

    public void deleteById(int id) {
        executor.execute(() -> {
            db.attachmentDao().deleteForEntry(id);
            db.journalDao().deleteById(id);
        });
    }

    /**
     * "Let it go" — releases an entry and all of its attachments (DB rows AND
     * the files on disk, including stroke-JSON sidecars and polaroid meta
     * sidecars). No trash, no undo. The act is the release.
     */
    public void letItGo(int entryId, Callback<Void> done) {
        executor.execute(() -> {
            List<Attachment> old = db.attachmentDao().getForEntry(entryId);
            for (Attachment a : old) {
                deleteAttachmentFiles(a);
            }
            db.attachmentDao().deleteForEntry(entryId);
            db.journalDao().deleteById(entryId);
            if (done != null) done.onResult(null);
        });
    }

    // ── Attachments ───────────────────────────────────────────────────────────

    public void getAttachmentsForEntry(int entryId, Callback<List<Attachment>> callback) {
        executor.execute(() -> callback.onResult(db.attachmentDao().getForEntry(entryId)));
    }

    public void getEntryIdsWithAttachments(Callback<List<Integer>> callback) {
        executor.execute(() -> callback.onResult(db.attachmentDao().getEntryIdsWithAttachments()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Removes the file pointed to by {@code a.filePath} along with both
     *  possible sidecars (the stroke {@code .json} for chalk drawings and the
     *  position {@code .meta.json} for polaroids). Missing files are silently
     *  ignored — the file having already vanished is fine. */
    private void deleteAttachmentFiles(Attachment a) {
        if (a == null || a.filePath == null) return;
        File file = new File(filesDir, a.filePath);
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
        int dot = a.filePath.lastIndexOf('.');
        String base = (dot > 0) ? a.filePath.substring(0, dot) : a.filePath;
        File sidecarJson = new File(filesDir, base + ".json");
        if (sidecarJson.exists()) {
            //noinspection ResultOfMethodCallIgnored
            sidecarJson.delete();
        }
        File metaJson = new File(filesDir, base + ".meta.json");
        if (metaJson.exists()) {
            //noinspection ResultOfMethodCallIgnored
            metaJson.delete();
        }
    }

    public interface Callback<T> {
        void onResult(T result);
    }
}
