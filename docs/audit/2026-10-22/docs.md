# Docs — audit 2026-10-22

## DOC-1 · blocking · AGENTS.md "Related repositories" describes how drevo-android used to get the databases

- **Where:** `AGENTS.md:9` (the sentence "All checked out side by side under `~/Work/drevo/`"), `AGENTS.md:11-17`
  (the table; `drevo-android` row line 16, `drevo-ios` row line 17)
- **What this is:** The table tells the reader which sibling repositories use this one's output, and how.
- **Problem:** It says drevo-android's `release-new-db.sh` "reads them from `DB/` here". Since drevo-android
  `a6fa70e` ("Build releases from drevo-db's GitHub Releases instead of a local folder") the script sources
  `release-db-fetch.sh` and downloads the `db-<main>-<patch>` release (tag `^db-[0-9]+-[0-9]+$`; assets
  `patch.<M>.ru.drevoinfo.obb`, `stat.txt`, `SHA256SUMS`; `main.<N>.ru.drevoinfo.obb` found through the releases API)
  into a temp directory; it never reads `DB/`. drevo-ios pins `db-254-258` in `db.lock` with hashes from
  `SHA256SUMS`. So the release tag and asset names `publish.sh` produces are the contract both clients depend on, and
  the table does not say so. drevo-ios is also no longer "planned", and the "all checked out" sentence describes one
  machine, not the repositories.
- **Fix direction:** Say in the drevo-android and drevo-ios rows that both clients fetch the GitHub Release
  `publish.sh` creates, so its tag and asset names are a contract; drop "planned" and the "all checked out" claim.
- **If we skip it:** The next change to `publish.sh` (tag format, asset names, `SHA256SUMS` layout) gets no warning
  that it is the clients' input, and both release scripts break.
- **Effort:** S · **Touches:** `AGENTS.md` · **Depends on:** — · **Status:** new · **Verified:** confirmed

## DOC-2 · blocking · The real-export comparison recipe in AGENTS.md does not run as written

- **Where:** `AGENTS.md:62-66`
- **What this is:** The steps for checking a parsing change against the real export: build a patch before and after
  the change in a scratch directory, then compare `sqlite3 .dump` output.
- **Problem:** It says to run `build/install/drevo-db/bin/drevo-db patch <main> 900` "from there", meaning the
  scratch directory. That path is relative to the repository root, so it does not resolve there; and it must run from
  the scratch directory because `Main.kt:6-7` reads `./DB/` relative to the working directory. Both builds also write
  the same `DB/patch.900.ru.drevoinfo.obb`, and `Generator.kt:30-33` replaces it with `Files.move`, so followed
  literally the "after" build overwrites the "before" one.
- **Fix direction:** Give the binary path from the repository root or as an absolute path, and say to dump or rename
  the "before" output first (or use two patch numbers).
- **If we skip it:** An agent following the recipe either fails at the first command or compares a file with itself
  and reports "no difference".
- **Effort:** S · **Touches:** `AGENTS.md` · **Depends on:** — · **Status:** new · **Verified:** confirmed

## DOC-3 · suggestion · `renovate.json5` justifies the monthly schedule with "this repository is private"

- **Where:** `renovate.json5:6-8`
- **What this is:** The comment explains why Renovate runs monthly instead of weekly.
- **Problem:** The repository is public (`gh repo view --json visibility` → `PUBLIC`; drevo-ios
  `.github/tasks/ios-port.md:26` records the switch), so Actions minutes are free and "every Actions minute counts
  against the account's limit" is no longer true. Only the second reason, the monthly rebuild, still holds.
- **Fix direction:** Delete the private / Actions-minutes clause and keep the monthly-rebuild reason; whether to change
  the schedule is the owner's call.
- **If we skip it:** A reader keeps or copies the schedule for a cost reason that no longer exists.
- **Effort:** S · **Touches:** `renovate.json5` · **Depends on:** — · **Status:** new · **Verified:** confirmed (label lowered from blocking by the verifier: nothing behaves wrongly)
