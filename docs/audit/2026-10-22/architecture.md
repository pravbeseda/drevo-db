# Architecture — audit 2026-10-22

## ARCH-1 · blocking · `publish.sh` takes `stat.txt` from the last download, not from the export the patch was built from

- **Where:** `make.sh:16-19` (download runs before the mode choice and the confirmation at `make.sh:77-80`),
  `DB/download.sh:5` (overwrites `DB/stat.txt` and `DB/drevo.txt`), `publish.sh:44` and `publish.sh:53` (hash and
  upload whatever `DB/stat.txt` holds now), `publish.sh:26` (main and patch are paired through `build-history.txt`;
  `stat.txt` is not)
- **What this is:** A release is the newest main + patch pair plus `stat.txt`, the site's article and picture counts
  and date. drevo-android's `release-new-db.sh:72-75` parses that file and writes it into `strings.xml`
  (`release-new-db.sh:84-86`: `app_date`, `count_articles`, `count_pictures`), which users see. `publish.sh` checks
  that main and patch belong together through `build-history.txt`; nothing ties `stat.txt` to that build.
- **Problem:** `DB/` is state shared by three separate runs — download, build, publish — and nothing holds the rule
  that `stat.txt` comes from the same export as the patch. Path: `./make.sh` builds `patch.258` and the maintainer
  answers `Publish? [y/N]` with the default N. Next month `./make.sh` downloads a new export first, then the run is
  cancelled at `Продолжить?` (`make.sh:79`, `exit 0`) or the generator fails. `./publish.sh` — documented as runnable
  on its own — then publishes `patch.258` with the newer `stat.txt`. The notes carry the old `Count:` line, while
  `stat.txt` and its checksum describe the new export. drevo-android's `release-db-fetch.sh:27-29` checks `stat.txt`
  only against the release's own `SHA256SUMS`, computed over the new file, so nothing downstream catches it.
- **Fix direction:** Pin `stat.txt` to the build when the build succeeds — keep a copy under the patch number (or
  record its hash in the `build-history.txt` line) and have `publish.sh` upload that copy. Moving the download after
  the confirmation in `make.sh` also closes the cancel path.
- **If we skip it:** A delayed publish can ship an Android release whose article count and date do not match the
  database it carries.
- **Effort:** S · **Touches:** `make.sh`, `publish.sh`, `PublishScriptTest.kt`, `AGENTS.md` (the publish paragraph) · **Depends on:** — · **Status:** new · **Verified:** confirmed
