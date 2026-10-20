# AGENTS.md

`drevo-db` builds the offline databases of the Orthodox encyclopedia "Древо" from the site's export. It is the
producer of the database format; the Android app and the iOS app only read it. Its own code and docs are English;
`make.sh` and the export are in Russian.

## Related repositories

All checked out side by side under `~/Work/drevo/`.

| Repository | Role |
|---|---|
| `drevo-yii` | The site (legacy PHP) and the backend |
| `drevo-web` | The new web front end (Angular) |
| `drevo-db` | This repository: export → `main` + `patch` SQLite databases |
| `drevo-app` | Android reader; ships the databases as OBB expansion files, its `release-new-db.sh` reads them from `DB/` here |
| `drevo-ios` | iOS reader, planned; bundles the same two files |

## Pipeline

`./make.sh [patch|full]` runs `DB/download.sh` (fetches `https://drevo-info.ru/drevo2.tar.gz` and unpacks it into
`DB/drevo.txt` and `DB/stat.txt`; what produces that archive on the server is not recorded here),
picks the next version numbers from the files already in `DB/`, builds the generator with Gradle and runs it from the
repository root — it reads and writes `./DB/` relative to the working directory. `patch` writes
`DB/patch.<N>.ru.drevoinfo.obb` against the newest `main`; `full` writes a new `main` and `patch` pair. `DB/` is
git-ignored except `download.sh`.

`build-history.txt` is a log, not test output: `make.sh` appends the `Count:` line and the patch size after every
build, and the maintainer reads the patch's growth there to decide when to rebuild `main` with `full`. Commit the new
line with the next change.

Until `docs/format.md` exists, the format is described in drevo-app's `.github/tasks/ios-port.md` ("Format facts").

## Build & test

JDK 21; versions live in `gradle/libs.versions.toml` alone, and Renovate (`renovate.json5`) updates them.

```bash
./gradlew build            # compile, unit tests, spotlessCheck — what CI runs
./gradlew spotlessApply    # format; ktlint style comes from .editorconfig, shared with drevo-app
git config core.hooksPath .githooks   # once per checkout: refuses commits on main, runs spotlessCheck
```

New code is Kotlin. Logic lives in plain classes without JDBC and is written test-first with plain JUnit 4
(`org.junit.Assert`, no mocking library); `Main.kt` holds only the JDBC wiring.

## Changing what the generator writes

Both clients read these files, so every change to the generator is checked against the real export: build a patch
before and after the change in a scratch directory (a `DB/` holding a symlink to `DB/drevo.txt` and a copy of the
newest `main`, then `build/install/drevo-db/bin/drevo-db patch <main> 900` from there) and compare
`sqlite3 <file> .dump` of the two. Compare dumps, never file bytes: the page layout changes with every `sqlite-jdbc`
version. A difference is either the intended change or a bug.

Trim with `javaTrim()`, not Kotlin's `trim()`: the original generator used Java's `String.trim()`, which keeps a
no-break space that Kotlin's would strip, and the stored titles and texts depend on it.
