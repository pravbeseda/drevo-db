# Correctness — audit 2026-10-22

## COR-1 · blocking · An empty export crashes after the databases are in place; in `full` mode an empty `main` is left as the newest

- **Where:** `src/main/kotlin/ru/drevoinfo/db/BuildStats.kt:10`, `src/main/kotlin/ru/drevoinfo/db/Main.kt:35-37`,
  `src/main/kotlin/ru/drevoinfo/db/Generator.kt:21,33`, `make.sh:40,90`
- **What this is:** `Generator.build` writes each database to `<name>.tmp` and renames it into `DB/`; its KDoc
  promises that "a failed build leaves none behind". After that, `Main` prints `BuildStats.summary()`, which computes
  `100 * added / all`.
- **Problem:** If `DB/drevo.txt` is empty or holds only blank lines, `build` accepts it, writes an empty index and an
  empty `tblTexts`, renames both files into place, and then `summary()` throws `ArithmeticException: / by zero`.
  Reproduced at `d7e799a`: `drevo-db full 900 900` with a blank-lines-only export exits 1 and leaves
  `main.900.ru.drevoinfo.obb` (2 KB) and `patch.900.ru.drevoinfo.obb` (8 KB) in `DB/`; `patch 254 900` with an empty
  export leaves an 8 KB `patch.900`. `make.sh` stops at line 90 with no history line. In `full` mode the empty
  `main.N` is now the newest main: the next `make.sh patch` (line 40) builds `patch.N+1` against it, every article
  goes in as a full text, history records `N/N+1`, and `publish.sh` accepts the pair and attaches the empty `main.N`.
  The stack trace never says the export was empty. `DB/download.sh` checks only `curl -f` and `tar`, so nothing
  upstream rejects an empty file.
- **Fix direction:** Make `Generator.build` refuse an export with no articles before the rename, with a message that
  says so; `summary()` then cannot divide by zero. Add a `GeneratorTest` case next to
  `failedFullBuildLeavesNoDatabaseBehind`.
- **If we skip it:** A bad export leaves an empty `main` as the newest; the failure is loud (a stack trace, a
  `Count:` line at ~100 % added, a prompt saying `main.N` is attached), but nothing stops a maintainer who clicks
  through two confirmations from shipping a full-corpus patch on an empty `main`.
- **Effort:** S · **Touches:** `Generator.kt`, `GeneratorTest.kt` (optionally `BuildStats.kt`) · **Depends on:** — · **Status:** new · **Verified:** confirmed
