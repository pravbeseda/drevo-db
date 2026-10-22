# Lean — audit 2026-10-22

## LEAN-1 · suggestion · `make.sh`: a branch and a fallback that can never run after the generator finishes

- **Where:** `make.sh:94`, `make.sh:97-102`, `make.sh:109`, `make.sh:85`
- **What this is:** After the generator runs, `make.sh` pulls the `Count:` line out of its output; only if that line
  is there does it work out the patch size (with `"?"` when the file is missing) and append to `build-history.txt`.
- **Problem:** Under `set -eo pipefail` a failing generator stops the script at the `OUTPUT=$(… | tee …)` assignment
  (line 90), and a `grep "^Count:"` with no match stops it at line 93, so `[ -n "$STATS_LINE" ]` is always true.
  `Main.kt:37` always prints the line after a successful run. The `-f "$PATCH_FILE"` else branch is dead too: a
  successful build has already moved the patch onto exactly that path (`Main.kt:28`, `Generator.kt:33`). The
  `cd "$ROOT_DIR"` on line 85 repeats line 19 with no `cd` in between.
- **Fix direction:** Drop the `if`/`fi` around the history block and the `-f` / `SIZE_MB="?"` fallback, and delete the
  second `cd`.
- **If we skip it:** Readers keep believing a "no Count line" or "no patch file" outcome can happen there.
- **Effort:** S · **Touches:** `make.sh` · **Depends on:** ARCH-1 · **Status:** new · **Verified:** confirmed

## LEAN-2 · suggestion · `Generator`: `IF NOT EXISTS` on tables and indexes that never exist yet

- **Where:** `src/main/kotlin/ru/drevoinfo/db/Generator.kt:14`, `Generator.kt:47-49`, `Generator.kt:73-74`
- **What this is:** Every `CREATE TABLE` and `CREATE INDEX` the generator runs carries an `IF NOT EXISTS` guard.
- **Problem:** The statements run only against the `.tmp` files that `build` deletes just before (`Generator.kt:30`);
  `write` is private and called only from `build`, whose callers are `Main.kt:35` and `Fixtures.kt:27,32`. In patch
  mode the existing `main` is opened read-only (`Generator.kt:52`). No table or index can exist yet, so all six guards
  are for a case that cannot happen. SQLite does not store `IF NOT EXISTS` in `sqlite_master`, which is what
  `FixturesTest` compares, so removing them changes no output.
- **Fix direction:** Delete `IF NOT EXISTS` from the six statements, leave the rest of each string as it is (the two
  `tblTexts` definitions differ on purpose), and run `FixturesTest`.
- **If we skip it:** The code suggests the generator can append to an existing database, which `build` rules out.
- **Effort:** S · **Touches:** `Generator.kt` · **Depends on:** — · **Status:** new · **Verified:** confirmed

## LEAN-3 · suggestion · `make.sh`: the "newest version" pipeline is written out twice

- **Where:** `make.sh:39-42`, `publish.sh:11-13`
- **What this is:** Both scripts find the highest `N` among the `DB/{main,patch}.N.ru.drevoinfo.obb` files.
- **Problem:** `make.sh` writes the same `ls | sed | sort -n | tail -1 || true` pipeline twice, differing only in the
  prefix, each under a comment that restates the variable name; `publish.sh` already does the job in one `newest()`
  function that takes the prefix.
- **Fix direction:** Define `newest()` in `make.sh` as a one-line function (as `publish.sh` does, `$DB_DIR` is already
  set) and call it for `main` and `patch`, dropping the two comments on lines 39 and 41 — four lines become three.
  Sharing one copy between the scripts would need a sourced file and is not proposed.
- **If we skip it:** Two hand-kept copies of one pipeline in one script.
- **Effort:** S · **Touches:** `make.sh` · **Depends on:** ARCH-1 · **Status:** new · **Verified:** confirmed (fix direction narrowed by the verifier so the change shrinks the script)

## LEAN-4 · suggestion · Spotless `endWithNewline()` repeats what ktlint already enforces

- **Where:** `build.gradle:26`
- **What this is:** The Spotless Kotlin step runs ktlint and then also `endWithNewline()`.
- **Problem:** ktlint reads `insert_final_newline = true` from `.editorconfig:8` and its `final-newline` rule already
  enforces it on `src/**/*.kt`. Checked: with line 26 removed and a `.kt` file stripped of its final newline,
  `spotlessCheck` still fails on that file. The `endWithNewline()` on line 32 belongs to the `gradleScripts` format,
  which has no ktlint, and stays.
- **Fix direction:** Remove `endWithNewline()` from the `kotlin {}` block.
- **If we skip it:** One redundant formatter step and two places that decide the same rule.
- **Effort:** S · **Touches:** `build.gradle` · **Depends on:** — · **Status:** new · **Verified:** confirmed

## LEAN-5 · suggestion · `.gitignore` entries left over from the original Java tree

- **Where:** `.gitignore:9`, `.gitignore:10`
- **What this is:** The patterns `*.class` and `*.tar.gz`, there since the initial commit.
- **Problem:** Gradle writes classes only under `build/` (ignored on line 7). The only tarball, `drevo2.tar.gz`, is
  downloaded into `DB/` (`make.sh:17` changes into it before `DB/download.sh:4`), which `DB/*` already covers, and is
  deleted right after unpacking.
- **Fix direction:** Delete the two lines.
- **If we skip it:** Two patterns pointing at build outputs that no longer exist.
- **Effort:** S · **Touches:** `.gitignore` · **Depends on:** — · **Status:** new · **Verified:** confirmed
