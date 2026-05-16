# Qualia v3.4 — polaroid retake/let-go + let-it-be drafts

Phase 4. Two features that were 80% done, finished. No DB migration. No new
resource ids needed beyond the ones already in `activity_journal.xml`.

## What's new

**Polaroid retake / let-go.** A quick tap on any polaroid (just a tap — no
drag, no hold) now reveals a small caption row above it: **retake · let go**.
Auto-dismisses after about 3.5 seconds if untouched.

- **retake** → camera opens. The new photo replaces the old one in-place,
  preserving position and tilt. The previous PNG (if it was a session-only
  capture) is deleted from disk; if the polaroid was loaded from an existing
  saved entry, the old file is left for the save-time diff to clean up so
  hitting "back" without saving doesn't damage the entry.
- **let go** → press-and-hold gesture, ~2.5s fade-to-zero just like the
  entry-level let-it-go. Hold all the way through → polaroid file deleted (if
  session-owned) and removed from the page. Release early → polaroid pops
  back to full opacity and stays.

**Let-it-be drafts.** New caption pair at the bottom-right of the journal
page: **let go · let it be**. They appear only once the page has anything
on it (text, a stroke, or a polaroid). Three doors out of the journal:

- **save** (top, unchanged) — commits the page as a real entry. Also clears
  any existing draft.
- **let it be** — keeps the page in scratch space. Re-opening the journal
  next time restores the text, the strokes, and any polaroids in their
  saved positions. Tapping save eventually consumes the draft. The
  Android **back button** also routes through "let it be" — closing the
  journal with anything on the page now silently keeps a draft.
- **let go** — hold-to-fade the page (same gesture as entry let-it-go).
  Hold through → discard the page. Polaroid PNGs captured this session
  (or restored from a previous draft) are deleted from disk so no orphans
  pile up. Polaroids loaded from an existing entry edit are NOT touched.

Edit mode (re-opening an existing entry) keeps only the **save** button.
You're already past the draft phase when editing.

## Files in this patch

```
app/src/main/java/com/example/qualia/util/PrefsManager.java     (+draft methods)
app/src/main/java/com/example/qualia/ui/DrawingView.java         (+stroke-finished listener)
app/src/main/java/com/example/qualia/ui/JournalActivity.java     (+three-doors + polaroid actions)
app/src/main/res/layout/activity_journal.xml                     (+let-be / let-go captions)
```

No new files. No removed files. No DB schema change.

## Install

1. Unzip into your project root, overwriting existing files.
2. Build → Clean Project → Rebuild.
3. Install on device.

## Verify (device)

- [ ] Open a fresh journal. Bottom-right is empty (no let-be / let-go).
- [ ] Type one character. **let go · let it be** appear.
- [ ] Take a polaroid. Quick tap on it → **retake · let go** chip appears
      above it. Wait ~3.5s → chip dismisses on its own.
- [ ] Tap the polaroid again → **retake** → camera opens → take a new
      photo → returns: same position, same tilt, new image.
- [ ] Tap the polaroid again → **let go** → press and hold ~2.5s. Polaroid
      fades. If you release early it returns to full opacity. If you hold
      through, the polaroid is gone and its file is deleted.
- [ ] With at least one line of text and a polaroid on the page, tap
      **let it be** at the bottom-right. Activity finishes.
- [ ] Re-open the journal: the text and the polaroid are back, polaroid in
      the same position. Add another stroke → tap **save** → next time
      the journal opens, it's blank.
- [ ] Repeat: type, draw, photo. Tap **let go** at bottom → press and
      hold ~2.5s. Page fades. After release, journal closes. Re-open →
      blank page. The polaroid file is gone from disk.
- [ ] Open an existing entry that's still inside the edit window
      ("still today"). Tap edit. The journal opens but **let go / let it
      be** are NOT visible. Only **save**.
- [ ] In edit mode, tap a polaroid → **retake · let go** still works on
      individual polaroids. (Edit-mode let-go on a polaroid removes it from
      the page; the polaroid file is cleaned up on **save**, not on the
      gesture itself, so hitting **back** without saving leaves the entry
      unchanged.)

## Tunables (all at the top of `JournalActivity.java`)

- `LET_GO_HOLD_MS` — ~2500. Length of the hold gesture for both page-level
  and per-polaroid let-go. Same constant on purpose.
- `POLAROID_ACTION_TIMEOUT_MS` — ~3500. How long the action chip stays
  visible before auto-dismissing.
- `longPressMs` (inside `attachDragHandler`) — 280. The "is this a tap or
  a drag?" gate. Quick tap = chip; longer hold = drag.

## Notes

- Drafts live in `SharedPreferences` under the key `journal_draft`. The
  payload is JSON: `{ "text": "...", "strokes": "...", "polaroids": [...] }`.
- Polaroid files referenced from a draft live in `getFilesDir()/polaroids/`,
  same as freshly captured ones. They're tracked in
  `JournalActivity.sessionPolaroids` so a subsequent **let go** can clean
  them up.
- `DrawingView` got a `setOnStrokeFinished(Runnable)` hook so the journal
  can refresh door visibility on stroke-up / undo / clear without polling.
