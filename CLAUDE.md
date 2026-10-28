# Journal App — Project Guide

A Day One-style journaling app for Android that reads and writes plain markdown
directly in an existing Obsidian vault. The app and Obsidian (desktop and mobile)
must be fully interchangeable: anything written in one must look right in the other.

## Core principles

- **Markdown files are the source of truth.** The app never owns the data. The Room
  database is only a rebuildable cache; if it's wrong, delete it and rescan.
- **Match the existing vault.** The journal already exists as one note per day
  (see layout below). The app adapts to it; it must never reorganise or rename files.
- **Never clobber external edits.** Sync tools and Obsidian can change files
  at any time. Before saving, compare the file's on-disk `lastModified` with the value
  at open time; if it changed, re-read and merge (adding a new entry) or warn the user
  (editing an existing one) instead of overwriting.
- **Atomic writes.** Write to a temp file in the same folder, then rename. SAF can't rename
  over an existing file, so the app writes `.YYYY-MM-DD.md.tmp`, deletes the original, then
  renames the temp file; a leftover temp file is recovered (or discarded if the original
  still exists) on the next listing. Saves and listings share one lock.
- **Preserve what we don't understand.** Keep unknown frontmatter keys (and their order)
  and any body text outside recognised entry sections, byte for byte.
- **Everything is optional.** Daily notes created in Obsidian may have no frontmatter,
  no headings, and no timestamps.
- **Sync is out of scope.** The app only reads/writes the folder; an external tool
  (e.g. Syncthing) keeps devices in sync.

## Vault layout (existing)

```
ROOT/
  Journal/
    YYYY/
      YYYY-MM-DD.md
```

- One file per **day**. The journal root (`ROOT/Journal`) is configurable in settings.
- No month subfolders. New year folders are created on demand.
- Attachments: follow Obsidian's own setting by reading `attachmentFolderPath` from
  `ROOT/.obsidian/app.json` (handle `./` = same folder as the note, a subfolder name,
  or a vault-relative path). Fall back to `ROOT/Attachments` if unset.
- Photo filenames: `YYYY-MM-DD-HHmm-N.jpg`, embedded as `![[YYYY-MM-DD-HHmm-N.jpg]]`.
- Never overwrite an existing attachment; increment `N`.

## Daily note format

A daily note holds zero or more **entries**. Day-level metadata lives in frontmatter;
each entry made by the app is a `## HH:mm` section with optional per-entry metadata
as Dataview inline fields directly under the heading.

```markdown
---
date: 2026-10-23
tags: [journal, family]
mood: 4
starred: false
---

## 08:42
location:: Utrecht
weather:: 9 °C, Cloudy

Morning entry, plain markdown.

## 20:11
location:: Utrecht (52.0907, 5.1214)
weather:: 11 °C, Light rain

Evening entry.

![[2026-10-23-2011-1.jpg]]
```

### Frontmatter (day-level, all optional)

| Field     | Type            | Notes                                              |
|-----------|-----------------|----------------------------------------------------|
| `date`    | `YYYY-MM-DD`    | Fallback: filename                                 |
| `tags`    | list of strings | Union of day tags; also recognise inline `#tags`   |
| `mood`    | int 1–5         | Day mood                                           |
| `starred` | bool            | Default false                                      |

Only add frontmatter keys the user actually sets; don't add empty keys to notes
written in Obsidian.

### Entries

- An entry starts at a level-2 heading matching `## HH:mm` (24h) and runs until the
  next such heading or end of file.
- Inline fields recognised directly under the heading: `location::`, `weather::`,
  `mood::` (per-entry override). Unknown `key:: value` lines are preserved.
- **Untimed content:** text before the first `## HH:mm` heading (or a note with no such
  headings at all, i.e. typical Obsidian-written notes) is treated as one untimed entry
  for that day and shown in the timeline at the start of the day. It's editable but its
  position and formatting are preserved.
- Other headings (`#`, `###`, or `##` that isn't a time) are part of the current
  entry's body, not new entries.
- Two entries in the same minute are allowed; identify entries by
  `(date, heading time, ordinal)`, not by time alone.

### Writing rules

- **New entry:** append a new `## HH:mm` section to the day's file (create the file,
  and the year folder, if missing). Keep sections in chronological order; if the
  new entry is earlier than existing ones (e.g. backdated), insert it in place.
- **Edit entry:** replace only that section's text; leave everything else untouched.
- **Delete entry:** remove only that section. If the note becomes empty (no body, no
  frontmatter the user set), delete the file only after confirming.
- Normalise line endings to `\n`, keep the file's trailing newline behaviour.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Room for the index cache
- WorkManager for background rescans
- Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE` on the vault root + persisted
  URI permission; root is needed to read `.obsidian/app.json`)
- YAML: a small, lenient frontmatter parser (e.g. snakeyaml-engine or kaml)

## Package structure

```
data/
  VaultAccess        // SAF picker, persisted permission, DocumentFile helpers
  ObsidianConfig     // reads .obsidian/app.json (attachment folder)
  DailyNoteParser    // file ⇄ DailyNote(frontmatter, preamble, entries, raw spans)
  DailyNoteWriter    // section-level edits that preserve untouched bytes
  JournalRepository  // load day, add/edit/delete entry, conflict check, atomic write
  IndexDb (Room)     // cache rows per entry: date, time, ordinal, path, tags,
                     //   snippet, hasMedia, fileLastModified
  MediaStore         // copy/compress photos into the attachment folder
sync/
  VaultScanner       // scan Journal/YYYY/*.md by mtime, update IndexDb
ui/
  timeline/          // reverse-chronological feed of entries
  calendar/          // month grid with markers on days that have a note
  onthisday/         // same date in previous years
  editor/            // markdown editor + chips (tags, mood)
  media/             // photo grid across all entries
  search/            // full-text + tag search over the index
```

## MVP order

1. Vault picker (SAF on vault root, persisted permission, journal folder setting)
2. DailyNoteParser + DailyNoteWriter with unit tests (see Testing)
3. Timeline (read-only, from a direct scan)
4. Editor (add/edit entry, atomic save, conflict check)
5. IndexDb + VaultScanner

Then: calendar, On This Day, media grid, search, quick capture (home-screen widget +
share target).

Location and weather are not planned: the app doesn't record them (no location permission, no
network calls). Existing `location::` / `weather::` fields are still read, shown and preserved.

## Next step

MVP is done (steps 1–5), plus calendar, On This Day, the media grid, adding photos, search,
quick capture (share target, widget, app shortcuts), a formatted entry view, the app lock and
the daily reminder. All planned features are in.

The parser/writer, `VaultPaths`, the timeline model (`Timeline`, `PlainText`), the save/merge
logic (`NoteEdits`), the rescan diff (`IndexPlan`), the search syntax (`SearchQuery`) and the
reminder time (`Reminder`) live in the pure-Kotlin `:core` module; `VaultAccess` (SAF via `DocumentsContract` queries), `SettingsStore` (DataStore),
`JournalRepository` (load, save, atomic replace, temp-file recovery) and `IndexDb` (Room) are
in `:app`, with `VaultScanner` and the periodic `ScanWorker` in `sync/`.

Index: `notes` rows (keyed by document id; mtime, size, day metadata, `readFailed`) and
`entries` rows (everything a `TimelineEntry` holds, incl. plain text for search). The scanner
lists the folder, re-reads only files whose mtime or size changed (or that failed last time),
newest first in batches, and drops rows for removed files; the index is reset when the vault or
journal folder changes. The timeline observes the index and triggers a scan on resume, on
pull-to-refresh and after saves.

UI: bottom tabs Journal (timeline) and Calendar (month pager over `observeMarkedDays`, the
selected day's entries below, + writes a backdated entry). The entry screen loads its day from
the index (`TimelineViewModel.day`), so entries open from anywhere, not only loaded pages.
On This Day: a banner on top of the timeline when earlier years have entries on today's
date, opening a screen grouped by year with day arrows (`OnThisDay` in `:core`; in non-leap
years 28 Feb includes 29 Feb).
Media: entries' media embeds are stored as `media` rows (by file name); each scan lists the
attachment folder from `.obsidian/app.json` (`ObsidianConfig`, `AttachmentScanner`) plus the
vault root, `Attachments` and the year folders into `attachments`, and the grid joins the two
by name (case-insensitive), like Obsidian resolves `![[name.jpg]]`. Thumbnails via Coil
(video frames via coil-video); a full-screen pager viewer links to the entry, videos open in
another app.
Look: a sky-blue header in both themes; Settings → Appearance picks System / Light / Dark
(`SettingsStore.appearance`, `JournalTheme(dark, serif)`) and the entry font. Entry text (the
entry view's `MarkdownView` and the editor) is set in Literata via `TextStyle.reading()` unless
sans serif is chosen; the font is a Latin subset of the variable font with optical size fixed at
12 (`res/font`, OFL). Lists (timeline, calendar day, On This Day, search) show each day as one
rounded card on a tinted ground (`JournalColors.ground`/`card`): `EntryRow` items draw the card's
segments (`firstOfDay`/`lastOfDay`), the timeline's first entry carries the day header, and
`ListHeader` sits between cards. An entry's first resolvable photo/video is its thumbnail and a
day's first one fills its calendar circle (`EntryPhotos`, built from the media grid's list and
provided via `LocalEntryPhotos`). `TimelineEntry.hasTitle` decides whether the first line is shown
as a bold title or the text runs on.
Entry view: the index stores each entry's Markdown `body`; `MarkdownDoc` (`:core`) parses it into
blocks/inlines (CommonMark basics plus Obsidian's wikilinks, embeds, `==highlight==`, `#tags`,
callouts, tasks, tables; `%%comments%%` hidden; single newlines are line breaks) and
`MarkdownView` renders them. Photos/videos appear full width where they're embedded (a
paragraph is split around them; unresolved embeds are skipped). Web links open the browser,
`#tags` open Search, `[[YYYY-MM-DD]]` links open that day in the calendar; other wikilinks are
styled only. Table column alignment is ignored.
Adding photos: the editor's gallery (system photo picker) and camera (TakePicture via a
FileProvider cache file) buttons; `PhotoImporter` writes upright JPEGs (long edge ≤ 2560) into
the attachment folder as `YYYY-MM-DD-HHmm-N.jpg` (`Photos.nextName`, never overwriting; the
untimed entry uses the current time) and `Photos.insertEmbeds` puts the embeds at the cursor as
their own paragraph. Photos added in an editor session but no longer embedded when it closes
(or all of them on discard) are deleted again.

Quick capture (`capture/`): `QuickCapture.from(intent)` turns a share (`SEND` text/plain or
image/*, `SEND_MULTIPLE` image/*) or the widget/shortcut actions (`NEW_ENTRY`, `NEW_PHOTO`)
into a new entry for today. Shared text goes through `SharedText.compose` (a page title + bare
URL becomes `[title](url)`); shared images are imported like gallery photos. A shared entry
finishes the activity when it closes, returning to the sharing app. `CaptureWidget` is a
RemoteViews widget (New entry + camera); `res/xml/shortcuts.xml` has the same two actions.

Search: a Search tab. `entries_fts` is an FTS4 table (unicode61: case- and accent-insensitive)
over the entries' plain `text` and `location`, kept in step with `entries` by Room's triggers
(entries have an explicit `id` rowid for it). `SearchQuery` parses words (prefix matches),
`"phrases"` and `#tags` (inline or frontmatter, nested tags included, via `LIKE`); `SearchSql`
builds the raw query, observed so results follow rescans. Results are grouped by day with FTS
`snippet()`s highlighted; with an empty field the tab lists all tags as chips.

App lock (`lock/`, `ui/lock/`): a Settings switch (`SettingsStore.appLock`; turning it on needs a
successful unlock and a device screen lock). `AppLock` holds the process's unlocked state; a new
process starts locked and `MainActivity.onStop` locks again, except while an activity started for
a result (photo picker, camera, folder picker) is open, for up to 5 minutes (a plain
`startActivity`, e.g. a link, passes `requestCode` -1 and does lock). BiometricPrompt
(`BIOMETRIC_WEAK or DEVICE_CREDENTIAL`, hence `FragmentActivity`). While locked `JournalRoot`
shows only `LockScreen` (screens keep their saveable state); a quick capture instead opens the
new entry's editor `writeOnly` (the day's star/tags neither loaded nor shown) and finishes the
activity when it closes. With the lock on, recents show a blank snapshot
(`setRecentsScreenshotEnabled`, `FLAG_SECURE` below API 33).

Daily reminder (`reminder/`): a Settings switch and time (`SettingsStore.reminder`, default 20:00;
turning it on asks for `POST_NOTIFICATIONS`, and a warning links to Android's notification
settings while they're off). `DailyReminder.schedule` sets an inexact `setWindow` alarm (10 min
window, no exact-alarm permission) at `Reminder.nextAt`; `JournalApp` reschedules whenever the
setting changes and on every process start. `ReminderReceiver` reads today's note from the vault
(not the index) and posts the notification only if `Reminder.isWritten` is false, then sets
tomorrow's alarm; it also reschedules on boot, app update and time/time zone changes. Tapping the
notification opens a new entry like the widget (write-only while locked); a Photo action starts
with the camera.

Conflict handling: every save re-reads the file and applies the change to what is on disk
now. Adds always merge; an edit/delete only applies if the entry's raw text is unchanged
since it was opened, otherwise the user chooses (keep mine / discard mine).

## Build

- `./gradlew :core:test` — parser/writer unit tests (fixtures in `core/src/test/resources/fixtures`)
- `./gradlew :app:assembleDebug` — Android app (package `nl.markbroeders.journal`,
  minSdk 26, targetSdk 36, compileSdk 37)

## Testing

JVM unit tests with fixture files, including notes in the style Obsidian writes:

- Note with no frontmatter and no headings → one untimed entry.
- Note with preamble text plus `## HH:mm` sections.
- Non-time `##` headings and `###` headings stay inside entries.
- Unknown frontmatter keys and unknown `key::` lines survive edits.
- Round-trip: parse → serialize with no changes must be byte-identical.
- Editing one entry leaves all other bytes in the file unchanged.
- Adding a backdated entry inserts it in chronological order.
- Two entries with the same time are both kept and addressable.
