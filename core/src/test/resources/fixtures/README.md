# Test fixtures

A miniature vault (`vault/`) laid out like the real one, so the same files serve the
parser/writer tests now and `VaultScanner` / `ObsidianConfig` tests later.

These are synthetic notes written from the format in `CLAUDE.md`. Add real (anonymised)
daily notes from the vault alongside them as they become available.

## `vault/.obsidian/app.json`

Copied from the real vault. `attachmentFolderPath` is `x - attachments`: a vault-relative
folder name containing spaces (resolves to `ROOT/x - attachments`).

## Daily notes

| File | Case | Expected parse |
|------|------|----------------|
| `2024/2024-03-14.md` | Obsidian-style: no frontmatter, no headings, inline `#tags`, task list | 1 untimed entry; tags `werk`, `wandelen` |
| `2024/2024-07-02.md` | Unknown frontmatter keys (`aliases`, `cssclasses`, `custom_rating`) in non-standard order; preamble text before first entry; per-entry `mood::` override | Untimed preamble + entries `07:15`, `21:40`; `starred: true`; unknown keys kept in order |
| `2025/2025-01-01.md` | `#` heading in preamble; `###`/`####` and non-time `##` headings (`## Plannen voor januari`, `## Meeting om 14:00`) | Untimed preamble (`# Nieuwjaarsdag`) + entries `11:05`, `16:30`; other headings stay in body |
| `2025/2025-06-21.md` | Block-style YAML tag list; unknown inline fields (`people::`, `energy::`); photo embeds incl. `![[…\|400]]` and plain `![](…)` | Entries `10:12`, `19:32`; both have media; unknown `key::` lines preserved |
| `2025/2025-10-23.md` | The example from `CLAUDE.md` verbatim | Entries `08:42`, `20:11` |
| `2025/2025-11-05.md` | Two entries at the same minute | Entries `(12:30, 0)`, `(12:30, 1)`, `(18:00, 0)` |
| `2026/2026-01-09.md` | Odd frontmatter types: quoted date, `tags` as a single string, `starred: yes` | Lenient parse: date 2026-01-09, tags `[journal]`, starred true; body is 1 untimed entry |
| `2026/2026-02-14.md` | No trailing newline at end of file | Entry `09:00`; round-trip must *not* add a newline |
| `2026/2026-03-08.md` | Frontmatter only, empty body | 0 entries (or 1 empty untimed entry — decide in parser) |
| `2026/2026-04-01.md` | Empty file (0 bytes) | 0 entries; date from filename |
| `2026/2026-05-17.md` | No frontmatter but a `---` thematic break later; fenced code block containing `## 10:00`; `key: value` (single colon) line | Untimed entry + entry `14:20` only; `## 10:00` inside the code fence is **not** an entry |

## Suggested test scenarios using these files

- Round-trip every file: parse → serialize unchanged is byte-identical.
- Edit `21:40` in `2024-07-02.md` → every byte outside that section unchanged.
- Add a `13:00` entry to `2024-07-02.md` → inserted between `07:15` and `21:40`.
- Add a `06:00` entry to `2025-01-01.md` → goes after the untimed preamble, before `11:05`.
- Edit the second `12:30` in `2025-11-05.md` → first `12:30` untouched.
- Delete the only timed entry in `2026-02-14.md` → frontmatter remains, file not deleted.
- Add an entry to `2026-04-01.md` (empty) → no frontmatter added unless the user set something.
