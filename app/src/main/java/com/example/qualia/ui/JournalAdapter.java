package com.example.qualia.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qualia.R;
import com.example.qualia.data.model.JournalEntry;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JournalAdapter extends RecyclerView.Adapter<JournalAdapter.EntryViewHolder> {

    // Fading journal: entries reach minimum opacity at 60 days.
    // Never fully invisible — the shape of the writing remains.
    private static final float ALPHA_MAX = 1.0f;
    private static final float ALPHA_MIN = 0.12f;
    private static final int   FADE_DAYS = 60;

    private final List<JournalEntry>  entries;
    private final OnEntryClickListener listener;
    private final boolean             fadingEnabled;

    /** IDs of entries that have at least one attachment (chalk / polaroid). */
    private final Set<Integer>        entriesWithAttachments;

    public interface OnEntryClickListener {
        void onClick(JournalEntry entry);
    }

    public JournalAdapter(List<JournalEntry> entries,
                          OnEntryClickListener listener,
                          boolean fadingEnabled) {
        this(entries, listener, fadingEnabled, Collections.emptySet());
    }

    public JournalAdapter(List<JournalEntry> entries,
                          OnEntryClickListener listener,
                          boolean fadingEnabled,
                          Set<Integer> entriesWithAttachments) {
        this.entries                = entries;
        this.listener               = listener;
        this.fadingEnabled          = fadingEnabled;
        this.entriesWithAttachments = entriesWithAttachments;
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_journal_entry, parent, false);
        return new EntryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        JournalEntry entry = entries.get(position);

        SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        holder.txtDate.setText(sdf.format(new Date(entry.createdAt)));
        holder.txtPreview.setText(entry.text);

        // Faint medium indicator — visible only when this entry has an attachment.
        boolean hasAttachment = entriesWithAttachments.contains(entry.id);
        holder.txtMediumIndicator.setVisibility(hasAttachment ? View.VISIBLE : View.GONE);

        if (fadingEnabled) {
            long ageMs   = System.currentTimeMillis() - entry.createdAt;
            long ageDays = TimeUnit.MILLISECONDS.toDays(ageMs);
            float progress = Math.min(1f, (float) ageDays / FADE_DAYS);
            float alpha    = ALPHA_MAX - progress * (ALPHA_MAX - ALPHA_MIN);
            holder.itemView.setAlpha(alpha);
        } else {
            holder.itemView.setAlpha(ALPHA_MAX);
        }

        holder.itemView.setOnClickListener(v -> listener.onClick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView txtDate;
        TextView txtPreview;
        TextView txtMediumIndicator;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDate            = itemView.findViewById(R.id.txtDate);
            txtPreview         = itemView.findViewById(R.id.txtPreview);
            txtMediumIndicator = itemView.findViewById(R.id.txtMediumIndicator);
        }
    }
}
