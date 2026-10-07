# Restore Instructions — Baseline Snapshot

`embedjnosql-baseline-before-public-release-audit-20260922` @ commit `b10b6cd` on `main`

## Restore option 1 — Git (preferred, exact)

The baseline is a **branch pointer**, not a detached state, so it is
non-destructive and always available:

```bash
# Inspect without leaving your branch
git log embedjnosql-baseline-before-public-release-audit-20260922 -1

# Compare any later state against the baseline
git diff embedjnosql-baseline-before-public-release-audit-20260922..HEAD --stat

# Recover a single file exactly as it was (does not touch your working tree)
git checkout embedjnosql-baseline-before-public-release-audit-20260922 -- path/to/file

# Work from the baseline in a throwaway worktree (recommended over checkout)
git worktree add ../embedjnosql-baseline-restore embedjnosql-baseline-before-public-release-audit-20260922
```

## Restore option 2 — Archive (repository-independent)

`source-snapshot.tar.gz` is a complete archive of the tracked tree at the
baseline commit (991 entries). It can be extracted anywhere, even outside a
Git checkout:

```bash
mkdir embedjnosql-baseline && tar -xzf source-snapshot.tar.gz -C embedjnosql-baseline
```

Note: this archive contains the tracked tree at `b10b6cd` only. The 29 files
that were modified-but-uncommitted at capture time are NOT in the archive in
their modified form — they are preserved as a patch (below).

## Restore option 3 — Uncommitted changes (the other session's work)

The 29 modified-but-uncommitted files (pages.yml, console-validation proof,
27 network-trace JSONs) are preserved in place in the live working tree AND
captured as a patch:

```bash
# View what was uncommitted at baseline time
less uncommitted-tracked-changes.patch

# Re-apply if ever lost (from the repository root)
git apply uncommitted-tracked-changes.patch
```

## Verification after any restore

1. `sha1sum -c FILE-CHECKSUMS.txt` — file-level integrity against baseline
   (run in a tree you expect to match the baseline; expect failures only for
   files intentionally changed since).
2. `mvn -DskipTests package` — must produce a ~3.11 MB shaded jar.
3. `mvn test` — must reproduce **785/785 green**.

## Integrity rules observed at capture

- No secrets or credentials are included (automated scan clean; no
  `.env`/keystore/key files tracked).
- No build caches, IDE files, or `target/` artifacts inside the archive.
- The baseline branch is a plain pointer; **no history was rewritten** and no
  `reset --hard` / `checkout --` / `clean -fd` was used.
