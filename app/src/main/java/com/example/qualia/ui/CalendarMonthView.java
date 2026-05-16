package com.example.qualia.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Set;

/**
 * Month-grid calendar for the past-entries screen.
 *
 * <p>Header shows month + year with quiet ‹ › arrows. Below is a 7-column
 * grid (Sun…Sat) with up to six rows. Days that have at least one journal
 * entry are filled with a soft cream glow; days with attachments get a
 * darker dot in addition to the glow. Empty days stay plain — the gaps
 * are part of the rhythm of noticing.
 *
 * <p>The view is purely presentational: the activity owns the entries list
 * and computes which days have entries / attachments, then calls
 * {@link #setMonth(int, int, Set, Set, OnDayClickListener)} once per data
 * change.
 */
public class CalendarMonthView extends LinearLayout {

    public interface OnDayClickListener {
        /** Day numbers are 1-based; year is the calendar year (e.g. 2026). */
        void onDayClick(int year, int month, int day);
    }

    /** Listener for prev/next month nav. The activity is responsible for
     *  re-querying entries for the new month and calling setMonth again. */
    public interface OnNavigateListener {
        void onNavigate(int year, int month);
    }

    private static final String[] DAY_HEADERS = { "S", "M", "T", "W", "T", "F", "S" };

    private TextView   txtMonth;
    private TextView   btnPrev;
    private TextView   btnNext;
    private GridLayout grid;

    private int year;
    private int month;
    private OnNavigateListener navListener;

    public CalendarMonthView(Context context) { this(context, null); }
    public CalendarMonthView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        buildHeader();
        buildDayHeaderRow();
        buildGrid();
    }

    public void setOnNavigateListener(OnNavigateListener l) {
        this.navListener = l;
    }

    /** Renders the given year+month, highlighting days in {@code daysWithEntries}
     *  with a quiet glow and (a subset of those in) {@code daysWithAttachments}
     *  with an additional dot. {@code onDayClick} is fired when the user taps
     *  a day that has at least one entry — empty days are unclickable. */
    public void setMonth(int year, int month,
                         Set<Integer> daysWithEntries,
                         Set<Integer> daysWithAttachments,
                         OnDayClickListener onDayClick) {
        this.year  = year;
        this.month = month;
        SimpleDateFormat fmt = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        Calendar c = Calendar.getInstance();
        c.set(year, month, 1);
        txtMonth.setText(fmt.format(c.getTime()).toLowerCase(Locale.getDefault()));

        grid.removeAllViews();
        int firstDow = c.get(Calendar.DAY_OF_WEEK) - 1;             // Sun=0..Sat=6
        int daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Lead empty cells before the 1st.
        for (int i = 0; i < firstDow; i++) {
            grid.addView(makeEmptyCell());
        }
        // Days of month.
        for (int day = 1; day <= daysInMonth; day++) {
            boolean hasEntry      = daysWithEntries      != null && daysWithEntries.contains(day);
            boolean hasAttachment = daysWithAttachments  != null && daysWithAttachments.contains(day);
            grid.addView(makeDayCell(day, hasEntry, hasAttachment, onDayClick));
        }
        // Trailing empty cells to fill the last row (purely cosmetic — the
        // grid would otherwise leave a ragged bottom edge).
        int total = firstDow + daysInMonth;
        int trailing = (7 - (total % 7)) % 7;
        for (int i = 0; i < trailing; i++) {
            grid.addView(makeEmptyCell());
        }
    }

    // ── Layout builders ───────────────────────────────────────────────────────

    private void buildHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(8), 0, dp(8));

        btnPrev = new TextView(getContext());
        btnPrev.setText("‹");
        btnPrev.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnPrev.setTextColor(Color.parseColor("#6B6560"));
        btnPrev.setGravity(Gravity.CENTER);
        btnPrev.setMinWidth(dp(32));
        btnPrev.setOnClickListener(v -> navigate(-1));

        // Month label uses the journal-family body brown so it reads on
        // cream paper, matching the rest of the past-entries surface.
        txtMonth = new TextView(getContext());
        txtMonth.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        txtMonth.setTextColor(Color.parseColor("#3D332B"));
        txtMonth.setGravity(Gravity.CENTER);
        txtMonth.setLetterSpacing(0.08f);
        txtMonth.setTypeface(txtMonth.getTypeface(), Typeface.NORMAL);

        btnNext = new TextView(getContext());
        btnNext.setText("›");
        btnNext.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnNext.setTextColor(Color.parseColor("#6B6560"));
        btnNext.setGravity(Gravity.CENTER);
        btnNext.setMinWidth(dp(32));
        btnNext.setOnClickListener(v -> navigate(+1));

        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        header.addView(btnPrev,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        header.addView(txtMonth, labelLp);
        header.addView(btnNext,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(header, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void buildDayHeaderRow() {
        GridLayout row = new GridLayout(getContext());
        row.setColumnCount(7);
        row.setUseDefaultMargins(false);
        row.setPadding(0, dp(4), 0, dp(4));

        for (String label : DAY_HEADERS) {
            TextView header = new TextView(getContext());
            header.setText(label);
            header.setTextColor(Color.parseColor("#5A544F"));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            header.setGravity(Gravity.CENTER);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width  = 0;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
            row.addView(header, lp);
        }
        addView(row, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void buildGrid() {
        grid = new GridLayout(getContext());
        grid.setColumnCount(7);
        grid.setUseDefaultMargins(false);
        grid.setPadding(0, dp(4), 0, 0);
        addView(grid, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    // ── Cell builders ─────────────────────────────────────────────────────────

    private View makeEmptyCell() {
        TextView v = new TextView(getContext());
        v.setText("");
        v.setHeight(dp(40));
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width  = 0;
        lp.height = dp(40);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        v.setLayoutParams(lp);
        return v;
    }

    private View makeDayCell(final int day, final boolean hasEntry,
                             final boolean hasAttachment,
                             final OnDayClickListener listener) {
        TextView v = new TextView(getContext());
        v.setText(hasAttachment ? day + " ·" : String.valueOf(day));
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        v.setIncludeFontPadding(false);

        if (hasEntry) {
            // Quiet cream glow — readable on dark backgrounds, doesn't shout.
            GradientDrawable bg = new GradientDrawable();
            // On paper, the active-day chip is a soft warm-khaki tint with
            // the body-brown numeral so the chip echoes the page rather
            // than fighting it. (Was #22F5F0E8 / #F5F0E8 for dark backdrop.)
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.parseColor("#33A89880"));
            v.setBackground(bg);
            v.setTextColor(Color.parseColor("#3D332B"));
            v.setOnClickListener(view -> {
                if (listener != null) listener.onDayClick(year, month, day);
            });
        } else {
            // Empty-day numerals: very faint warm grey so the rhythm of
            // gaps is visible without competing with the entries. The
            // previous #3A3531 was nearly black-on-black on the old
            // backdrop; on paper we need a light, low-contrast warm grey.
            v.setTextColor(Color.parseColor("#C9B89E"));
            v.setClickable(false);
        }

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width  = 0;
        lp.height = dp(40);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        v.setLayoutParams(lp);
        return v;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void navigate(int delta) {
        Calendar c = Calendar.getInstance();
        c.set(year, month, 1);
        c.add(Calendar.MONTH, delta);
        if (navListener != null) {
            navListener.onNavigate(c.get(Calendar.YEAR), c.get(Calendar.MONTH));
        }
    }

    private int dp(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }
}
