# Audit 2026-10-22 — drevo-db

Audited `d7e799a` on `docs/mit-license` (the same commit as `main`) · scope: whole repository

## State of the project

This repository turns the encyclopedia site's monthly text export into the two offline database files that the
Android reader ships and the iOS reader is being built on, and publishes each pair as a GitHub release. It is small,
well tested and in good health: the database writer is careful about half-written files, the format contract is
written down and guarded by reference builds, and no security problem was found. The risk sits at the edges of the
monthly run rather than in the generator itself. The site statistics shipped with a release are not tied to the
build they describe, so a release published late can carry numbers from a newer export. An empty export from the
server is accepted, written into place and only then crashes, which can leave an empty main database behind as the
newest one. The agent instructions still describe how the Android app used to fetch the databases and give a
comparison recipe that does not run as written. The rest is small cleanup. This is the first audit.

## What to do first

1. Tie the published site statistics to the build that produced the patch (ARCH-1) — S, architecture.md
2. Refuse an empty export before any database is renamed into place (COR-1) — S, correctness.md
3. Fix AGENTS.md: name the GitHub release as the clients' contract and make the real-export comparison recipe runnable (DOC-1, DOC-2) — S, docs.md
4. After ARCH-1, trim `make.sh`: the unreachable history branch, the second `cd`, the doubled version lookup (LEAN-1, LEAN-3) — S, lean.md
5. Drop the leftovers: impossible `IF NOT EXISTS` guards, the redundant Spotless step, two stale ignore patterns, the stale "private repository" reason (LEAN-2, LEAN-4, LEAN-5, DOC-3) — S, lean.md and docs.md

## Findings

| ID | Lens | Label | Finding | Effort | Depends on | Status | Verified |
|----|------|-------|---------|--------|------------|--------|----------|
| ARCH-1 | architecture | blocking | `publish.sh` takes `stat.txt` from the last download, not from the export the patch was built from | S | — | new | confirmed |
| COR-1 | correctness | blocking | An empty export crashes after the databases are in place; in `full` mode an empty `main` is left as the newest | S | — | new | confirmed |
| DOC-1 | docs | blocking | AGENTS.md "Related repositories" describes how drevo-android used to get the databases | S | — | new | confirmed |
| DOC-2 | docs | blocking | The real-export comparison recipe in AGENTS.md does not run as written | S | — | new | confirmed |
| LEAN-1 | lean | suggestion | `make.sh`: a branch and a fallback that can never run after the generator finishes | S | ARCH-1 | new | confirmed |
| LEAN-2 | lean | suggestion | `Generator`: `IF NOT EXISTS` on tables and indexes that never exist yet | S | — | new | confirmed |
| LEAN-3 | lean | suggestion | `make.sh`: the "newest version" pipeline is written out twice | S | ARCH-1 | new | confirmed |
| LEAN-4 | lean | suggestion | Spotless `endWithNewline()` repeats what ktlint already enforces | S | — | new | confirmed |
| LEAN-5 | lean | suggestion | `.gitignore` entries left over from the original Java tree | S | — | new | confirmed |
| DOC-3 | docs | suggestion | `renovate.json5` justifies the monthly schedule with "this repository is private" | S | — | new | confirmed |

## Fix order

- ARCH-1 before LEAN-1 and LEAN-3: pinning `stat.txt` to the build rewrites the download step and the
  `build-history.txt` block of `make.sh`, which is where both lean findings sit.

## Closed since the last audit

No previous audit.

## Shortcuts

No shortcut markers.

## Not included

Nothing.

Clean lenses: security, duplication (its one finding was rejected).

## Not checked

- The git-ignored contents of `DB/` (the export and the built databases) are not in the tree; finders sampled
  `DB/drevo.txt` only for field counts, topics, duplicate titles and line endings.
- Whether the server has ever produced an empty or incomplete `drevo2.tar.gz`: the export job lives in `drevo-yii`
  and is not recorded here (COR-1 is reachable regardless).
- `FixturesTest` was not run with the `IF NOT EXISTS` guards removed; LEAN-2 rests on the stored `sqlite_master` SQL.

## Method

6 finders (security, architecture, correctness, duplication, lean, docs) and one verifier per lens with findings,
each in a clean context, on Opus (the top tier's usage limit was reached before the run). Ledger: no earlier audits,
no open issues. 11 findings raised, 10 confirmed, 0 plausible, 1 rejected (see rejected.md).
