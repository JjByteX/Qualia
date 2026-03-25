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
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class JournalAdapter extends RecyclerView.Adapter<JournalAdapter.EntryViewHolder> {

    private final List<JournalEntry> entries;
    private final OnEntryClickListener listener;

    public interface OnEntryClickListener {
        void onClick(JournalEntry entry);
    }

    public JournalAdapter(List<JournalEntry> entries, OnEntryClickListener listener) {
        this.entries = entries;
        this.listener = listener;
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

        holder.itemView.setOnClickListener(v -> listener.onClick(entry));
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class EntryViewHolder extends RecyclerView.ViewHolder {
        TextView txtDate;
        TextView txtPreview;

        EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            txtDate = itemView.findViewById(R.id.txtDate);
            txtPreview = itemView.findViewById(R.id.txtPreview);
        }
    }
}