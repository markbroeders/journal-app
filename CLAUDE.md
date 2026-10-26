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
- **Atomic writes.** Write to a temp file in the same folder, then rename.
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
- Weather: Open-Meteo (no API key)
- Location: Fused Location Provider + Geocoder

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
services/
  LocationService    // current location → place name
  WeatherService     // current weather from Open-Meteo
sync/
  VaultScanner       // scan Journal/YYYY/*.md by mtime, update IndexDb
ui/
  timeline/          // reverse-chronological feed of entries
  calendar/          // month grid with markers on days that have a note
  onthisday/         // same date in previous years
  editor/            // markdown editor + chips (tags, mood, location, weather)
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
share target). Location and weather last.

## Next step

Scaffold the Gradle project and implement `DailyNoteParser` and `DailyNoteWriter`
with tests.

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
