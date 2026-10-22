# Database format

The contract between this generator and the readers (drevo-android, drevo-ios). The generator owns it: a
change to what it writes is a change to this file, and the fixtures below make the change visible to both clients'
tests.

## Files

| File | Holds |
|---|---|
| `main.<N>.ru.drevoinfo.obb` | Article texts. Rebuilt rarely (`make.sh full`), ~320 MB. |
| `patch.<M>.ru.drevoinfo.obb` | The index (titles, topics) and the texts changed since `main.<N>`. Rebuilt monthly (`make.sh patch`); ~8 MB right after a full build, growing to ~55 MB before `main` is rebuilt. |

The names are Android's expansion-file names; an iOS bundle keeps the same two files under constant names
(`main.sqlite`, `patch.sqlite`). A patch is valid only with the `main` it was built against, so whatever publishes a
pair names both numbers. A full build writes both files under one number (`N == M`).

Both are SQLite 3 databases with a page size of 1024 bytes, opened read-only by the clients.

## Version

`PRAGMA user_version` holds the format version.

| `user_version` | Meaning |
|---|---|
| `0` | Every file published before the version existed. Same as format 1. |
| `1` | This document. |

A client reads `0` and `1` and refuses to ship a database in any higher version. Each client opens only the databases
released with it, so the check runs when a release is built, not on the device: on Android `release-new-db.sh`
stops the build. A change that an existing client would misread raises the version.

## `patch`

```sql
CREATE TABLE tblSlovnik(_id INTEGER, title TEXT PRIMARY KEY ASC)
CREATE TABLE tblTopics(article_id INTEGER, topic INTEGER, PRIMARY KEY ([article_id], [topic]))
CREATE TABLE tblTexts(_id INTEGER PRIMARY KEY ASC, ziptext BLOB, isfulltext)
CREATE INDEX id ON tblSlovnik(_id)
CREATE INDEX title ON tblSlovnik(title)
```

- `tblSlovnik` is the index: one row per article. Titles are unique and in upper case, as the site exports them;
  clients upper-case a search before matching. Article `4` (the main page) is stored as ` ГЛАВНАЯ СТРАНИЦА`, with a
  leading space, so that `ORDER BY title` lists it first on the title index; clients trim it before showing it, and a
  prefix search for it has to allow the space.
- `tblTopics` puts an article in topics: `1` persons, `2` organisations, `3` places, `4` terms, `5` church, `6` bible,
  `7` books, `8` saints, `9` holidays, `10` church-slavonic, `11` documents; `13` and up are service topics.
  Topic `99` ("all articles") holds every article in no service topic, plus article `4` always.
- `tblTexts` holds the texts that changed since `main`; see [Texts](#texts).

## `main`

```sql
CREATE TABLE tblTexts(_id INTEGER PRIMARY KEY, ziptext BLOB, isfulltext)
```

Every row is a full text (`isfulltext = 1`). It may hold texts of articles that have since left the index; clients
reach a text only through `tblSlovnik`.

## Texts

`ziptext` is a zlib stream (RFC 1950: a 2-byte header, deflate data, an Adler-32 trailer) of the UTF-8 text.

An article's text, for an `_id` from the index:

| `patch` row | Text |
|---|---|
| none | the `main` row, which then exists |
| `isfulltext = 1` | the `patch` row itself (a text new since `main` has no `main` row) |
| `isfulltext = 0` | the `patch` row is a javaxdelta text delta against the `main` row of the same `_id` |

javaxdelta offsets count UTF-16 code units: apply a delta over UTF-16, never over grapheme clusters or Unicode
scalars. The generator stores a full text instead of a delta when the delta is 40 % of the new text's length or more.

A text is `<toc> <drevo> <body>`, and `<drevo>` occurs in it exactly once:

- `<toc>` is a sequence of `#anchor|title` entries; an entry written `#_anchor|title` is nested under the one before.
  The title `Начало статьи` stands for the article's own title. Titles may carry spaces at either end.
- `<body>` is the article's HTML.

## Special articles

| `_id` | Article |
|---|---|
| `1` | About the site |
| `2` | Calendar |
| `3` | Chronology |
| `4` | Main page |

A calendar day's article is titled `<day> <month in the genitive>`, e.g. `8 ОКТЯБРЯ`; clients look up "today" and
"tomorrow" by that title in the Julian calendar (13 and 12 days back).

## Fixtures

`fixtures/` holds a reference build both clients test against. `./gradlew generateFixtures` rebuilds it from
`fixtures/source/`, and `FixturesTest` fails while the committed files differ from a fresh build, so every change to
the generator's output shows up as a fixture diff in review.

| File | Content |
|---|---|
| `source/drevo.v1.txt`, `source/drevo.v2.txt` | Two tiny exports |
| `main.sqlite` | Built from v1 in full mode |
| `patch.sqlite` | Built from v2 against `main.sqlite` |
| `texts.tsv` | The texts for a client without SQLite: `_id`, `isfulltext` (empty: no `patch` row), base64 `main` and `patch` blobs (empty: no row) |
| `expected/<_id>.txt` | The text each article assembles to, UTF-8, byte for byte (no trailing newline) |

The articles cover an unchanged text (`4`, `101`, `105`), a delta under a nested table of contents (`102`), a full
replacement over the threshold (`103`), a delta inside text with characters outside the BMP (`104`), an article left
out of topic `99` (`105`), the main page in topic `99` despite a service topic (`4`), and an article new in v2 (`106`).

A client copies the files into its tests and records the drevo-db commit they came from.
