package com.example.qualia.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.qualia.R;
import com.example.qualia.data.model.JournalEntry;
import com.example.qualia.data.repository.JournalRepository;
import com.example.qualia.util.PrefsManager;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Past-entries screen. Two views over the same data:
 *
 * <ul>
 *   <li><b>List</b>: the existing chronological list (fading text + medium
 *       indicator on entries with attachments).
 *   <li><b>Calendar</b>: a month grid where days with entries glow softly.
 *       Days with attachments get an additional dot. Empty days stay empty —
 *       the rhythm of noticing is allowed to have gaps.
 * </ul>
 *
 * <p>Tapping a day in the calendar opens the most recent entry from that day.
 * (Multiple entries on the same day surface in list mode.) The toggle in the
 * top-right swaps the views.
 */
public class PastEntriesActivity extends BaseActivity {

    private List<JournalEntry> allEntries;
    private Set<Integer>       attachmentEntryIds = new HashSet<>();
    private boolean            calendarMode;

    /** Map of (year * 100 + month) → set of days-with-entries for that month.
     *  Computed once from the loaded entries; reused as the user navigates
     *  prev/next to avoid re-querying the database. */
    private final Map<Integer, Set<Integer>> daysByMonth        = new HashMap<>();
    private final Map<Integer, Set<Integer>> daysByMonthAttach  = new HashMap<>();

    private RecyclerView      recycler;
    private CalendarMonthView calendarView;
    private TextView          btnViewToggle;
    private TextView          txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_past_entries);

        TextView btnBack = findViewById(R.id.btnBack);
        recycler         = findViewById(R.id.recyclerEntries);
        txtEmpty         = findViewById(R.id.txtEmpty);
        calendarView     = findViewById(R.id.calendarView);
        btnViewToggle    = findViewById(R.id.btnViewToggle);

        boolean fadingEnabled = new PrefsManager(this).isFadingJournal();

        btnBack.setOnClickListener(v -> finish());
        recycler.setLayoutManager(new LinearLayoutManager(this));

        btnViewToggle.setOnClickListener(v -> toggleMode());
        calendarView.setOnNavigateListener(this::renderCalendarMonth);

        JournalRepository repo = new JournalRepository(this);
        repo.getEntryIdsWithAttachments(idsWithAttachments -> {
            attachmentEntryIds = idsWithAttachments == null
                    ? new HashSet<>()
                    : new HashSet<>(idsWithAttachments);

            repo.getAllEntries(entries -> runOnUiThread(() -> {
                allEntries = entries;
                indexEntriesByMonth();
                if (entries == null || entries.isEmpty()) {
                    txtEmpty.setVisibility(View.VISIBLE);
                    recycler.setVisibility(View.GONE);
                    calendarView.setVisibility(View.GONE);
                    btnViewToggle.setVisibility(View.GONE);
                } else {
                    txtEmpty.setVisibility(View.GONE);
                    btnViewToggle.setVisibility(View.VISIBLE);
                    recycler.setAdapter(new JournalAdapter(
                            entries, this::openEntry, fadingEnabled, attachmentEntryIds));
                    applyMode();
                }
            }));
        });
    }

    /** Refresh on resume so changes from the edit window or "let it go"
     *  flow are reflected when the user returns to this screen. */
    @Override
    protected void onResume() {
        super.onResume();
        // First-render is handled by onCreate; only re-query on subsequent
        // resumes (when allEntries already exists).
        if (allEntries != null) refreshFromDb();
    }

    private void refreshFromDb() {
        boolean fadingEnabled = new PrefsManager(this).isFadingJournal();
        JournalRepository repo = new JournalRepository(this);
        repo.getEntryIdsWithAttachments(idsWithAttachments -> {
            attachmentEntryIds = idsWithAttachments == null
                    ? new HashSet<>()
                    : new HashSet<>(idsWithAttachments);
            repo.getAllEntries(entries -> runOnUiThread(() -> {
                allEntries = entries;
                indexEntriesByMonth();
                if (entries == null || entries.isEmpty()) {
                    txtEmpty.setVisibility(View.VISIBLE);
                    recycler.setVisibility(View.GONE);
                    calendarView.setVisibility(View.GONE);
                    btnViewToggle.setVisibility(View.GONE);
                    return;
                }
                txtEmpty.setVisibility(View.GONE);
                btnViewToggle.setVisibility(View.VISIBLE);
                recycler.setAdapter(new JournalAdapter(
                        entries, this::openEntry, fadingEnabled, attachmentEntryIds));
                applyMode();
            }));
        });
    }

    // ── Mode toggle ───────────────────────────────────────────────────────────

    private void toggleMode() {
        calendarMode = !calendarMode;
        applyMode();
    }

    private void applyMode() {
        if (calendarMode) {
            recycler.setVisibility(View.GONE);
            calendarView.setVisibility(View.VISIBLE);
            btnViewToggle.setText("list");
            // Default to the most recent entry's month (or current month if
            // somehow allEntries is empty when this runs).
            Calendar c = Calendar.getInstance();
            if (allEntries != null && !allEntries.isEmpty()) {
                c.setTimeInMillis(allEntries.get(0).createdAt);
            }
            renderCalendarMonth(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
        } else {
            recycler.setVisibility(View.VISIBLE);
            calendarView.setVisibility(View.GONE);
            btnViewToggle.setText("calendar");
        }
    }

    // ── Calendar rendering ────────────────────────────────────────────────────

    private void renderCalendarMonth(int year, int month) {
        int key = year * 100 + month;
        Set<Integer> days       = daysByMonth.get(key);
        Set<Integer> daysAttach = daysByMonthAttach.get(key);
        calendarView.setMonth(year, month, days, daysAttach, this::openMostRecentForDay);
    }

    /** Pre-computes which days of which months had entries (and which of those
     *  also had attachments) so navigating the calendar doesn't hit the DB. */
    private void indexEntriesByMonth() {
        daysByMonth.clear();
        daysByMonthAttach.clear();
        if (allEntries == null) return;
        Calendar c = Calendar.getInstance();
        for (JournalEntry e : allEntries) {
            c.setTimeInMillis(e.createdAt);
            int key = c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);
            Set<Integer> set = daysByMonth.get(key);
            if (set == null) {
                set = new HashSet<>();
                daysByMonth.put(key, set);
            }
            set.add(day);
            if (attachmentEntryIds.contains(e.id)) {
                Set<Integer> attachSet = daysByMonthAttach.get(key);
                if (attachSet == null) {
                    attachSet = new HashSet<>();
                    daysByMonthAttach.put(key, attachSet);
                }
                attachSet.add(day);
            }
        }
    }

    private void openMostRecentForDay(int year, int month, int day) {
        if (allEntries == null) return;
        Calendar c = Calendar.getInstance();
        // allEntries is sorted DESC by createdAt; the first match is the most
        // recent entry for the day.
        for (JournalEntry e : allEntries) {
            c.setTimeInMillis(e.createdAt);
            if (c.get(Calendar.YEAR)        == year
                    && c.get(Calendar.MONTH)        == month
                    && c.get(Calendar.DAY_OF_MONTH) == day) {
                openEntry(e);
                return;
            }
        }
    }

    // ── Shared open-entry handler ─────────────────────────────────────────────

    private void openEntry(JournalEntry entry) {
        Intent intent = new Intent(this, EntryDetailActivity.class);
        intent.putExtra("entry_id",   entry.id);
        intent.putExtra("entry_text", entry.text);
        intent.putExtra("entry_date", entry.createdAt);
        startActivity(intent);
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
