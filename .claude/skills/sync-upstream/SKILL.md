---
name: sync-upstream
description: Pull new commits from the original open-source repo (github.com/quran/quran_android, remote `upstream`) into our Tasnim branch while keeping all our own code and features on top. Run it right after the `save-work` skill finishes, or when the user says "زامن مع الأصل", "حدّث من المستودع الأصلي", "sync upstream", "pull from origin repo", or "update from quran_android".
---

# Sync upstream — مزامنة مستودعنا مع المستودع الأصلي

Our app (تسنیم) is built on top of the open-source **quran/quran_android**. This skill brings their new commits (bug fixes, dependency bumps, features) into our branch **without losing anything we added** (rebrand, Persian UI, tafsir, library, live video/radio, theme, launcher icon …).

- `origin`   = our repo → `https://github.com/sa431322-creator/quran_android.git`
- `upstream` = the original → `https://github.com/quran/quran_android.git` (branch `main`)

**Shell variables do not persist between Bash tool calls.** Wherever `$BR` (our branch, e.g. `tasnim-rebrand-v2`) or `$STAMP` appear below, substitute the literal values you got in steps 2–3, or redefine them at the top of each command.

**Strategy: merge, never rebase.** Our branches are already pushed, so rebasing onto upstream would rewrite them and need a force push. A merge commit keeps our history intact and records exactly what came from upstream.

Run it after `save-work` (step 6 there). If `save-work` reported a failure (review not done, build broken, push failed), fix that first.

## 1. Preconditions

```bash
git status --short
git branch --show-current
```

- The working tree must have **no modified tracked files** (untracked files like `*.csv` are fine). If there are changes, run `save-work` first — do not stash and forget them.
- Work on our feature branch (currently `tasnim-rebrand-v2`). Never merge upstream into `main` here: our `main` is a single unrelated "Baseline" commit and shares no history with upstream.
- Make sure repeated conflicts are remembered (once per clone):

```bash
git config rerere.enabled true
git config rerere.autoupdate true
```

## 2. Fetch upstream and see what is new

```bash
git remote get-url upstream || git remote add upstream https://github.com/quran/quran_android.git
git fetch upstream main --no-tags
BR=$(git branch --show-current)
git log --oneline HEAD..upstream/main          # new upstream commits
git rev-list --count HEAD..upstream/main
```

- If the count is `0`: say "already up to date with upstream" and stop.
- Preview conflicts without touching the working tree:

```bash
git merge-tree --write-tree --name-only HEAD upstream/main
```

The lines after the tree hash are the files that will conflict.

## 3. Safety net, then merge on a temporary branch

```bash
STAMP=$(date +%Y%m%d-%H%M)
git branch "backup/pre-sync-$STAMP"            # restore point (local only)
git switch -c "sync/upstream-$STAMP"
git merge --no-ff --no-edit upstream/main -m "Merge upstream quran_android into $BR"
```

If the merge stops with conflicts, resolve them using the rules in step 4, then `git add <files>` and `git commit --no-edit`.

To abandon at any point: `git merge --abort` (during the merge) or `git switch "$BR"` and delete the sync branch. The backup branch is the last-resort restore point.

## 4. Conflict rules — our code stays on top

For each conflicted file, read **both sides** (`git diff`, or `git show :2:<file>` = ours, `:3:<file>` = upstream) before deciding. Never resolve a whole file blindly with `--ours`/`--theirs` unless a rule below says so.

| Area | Rule |
|---|---|
| Our identity: app name, `applicationId`, launcher icon, splash, colors/theme tokens, Persian strings (`values-fa`), Tasnim screens (library, live video/radio, tafsir panel, bottom nav) | **Keep ours.** Add upstream's change only if it is a real fix that does not undo our design. |
| Quranic script fonts and mushaf rendering (`uthmanic_hafs*`, `UthmanTN1*`, `kitab.ttf`, `quran_titles.ttf`, warsh fonts, page image rendering) | **Keep ours exactly** (memory `quran-fonts-locked`). If upstream changes them, keep ours and tell the user. |
| Bug fixes / logic in files we also edited | **Combine both**: keep our feature code and apply upstream's fix inside it. |
| Files only upstream changed | Taken automatically by git — nothing to do. |
| Dependencies, Gradle wrapper, `libs.versions.toml`, `build.gradle.kts`, `minSdk`/`targetSdk` | **Take upstream's versions**, keep our own added dependencies, `applicationId`, flavors and signing config. |
| Upstream feature announcements / "what's new" / new-icon announcements for quran.com branding | Keep the code, but make sure it does not show quran.com branding or override our icon; disable or re-word it for Tasnim if needed. |
| Other languages' `strings.xml` | Take upstream; keep any keys we added. |
| `AndroidManifest.xml` | Keep our activities/permissions/app label and icon; add upstream's new entries. |
| Version code / name | If we set our own version, keep it. While we still use upstream's numbering (true as of 2026-10-16), take their bump so updates install over older builds. |

After resolving, make sure no conflict markers remain:

```bash
git diff --check HEAD
git grep -nE '^(<<<<<<<|>>>>>>>)( |$)' -- . ':!*.md' || echo "no markers"
```

## 5. Verify before it reaches our branch

1. **Build** (compiles Kotlin + Java):
   ```bash
   JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileMadaniDebugJavaWithJavac -q --max-workers=2 -Dorg.gradle.jvmargs=-Xmx1536m -Pkotlin.compiler.execution.strategy=in-process
   ```
   Not `--offline` here: upstream may have bumped dependencies that must be downloaded. The low-memory flags matter on this PC: the default 4G heap fails with "paging file is too small". Expect errors where upstream renamed/removed functions our code calls — switch to the replacement that gives the same behavior. Run it in the background if slow. Fix any errors (usually API changes in code we touched) and commit the fixes on the sync branch.
2. **Our features still present** — quick sanity check that the merge did not drop our work:
   ```bash
   git diff --stat "$BR" HEAD -- . ':!**/values-*/strings.xml' | tail -5
   git log --oneline upstream/main..HEAD | wc -l   # must be >= our previous count
   ```
   Skim `git diff "$BR" HEAD` for any of our files that were deleted or reverted.
3. **Code review**: invoke `code-review` on the merge result vs. our branch (`git diff "$BR"...HEAD` — effort `medium`, `high` if conflicts touched rendering/audio/video). Focus on the conflict resolutions and on upstream changes that interact with our features. Fix confirmed findings (one re-review max, same as `save-work`).

If the build cannot run (no SDK/network) say so — never claim it passed.

## 6. Land it and push

```bash
git switch "$BR"
git merge --ff-only "sync/upstream-$STAMP"
git push origin "$BR"
git branch -d "sync/upstream-$STAMP"
```

- Never `push --force`. If `--ff-only` fails, someone pushed to `$BR` meanwhile: `git pull --no-rebase origin "$BR"` on the sync branch, re-verify, then retry.
- Keep the `backup/pre-sync-*` branch locally until the user confirms the app works; delete older backups (`git branch -D`) only when the user agrees.
- Do not push `upstream` tags or the backup branch.

## 7. Report

Short summary (Arabic if the user wrote in Arabic):
- how many upstream commits came in + a few notable ones (fixes/features, not every dependency bump)
- conflicted files and how each was resolved (ours / upstream / combined)
- anything from upstream we deliberately kept out (fonts, branding, version bumps) and why
- build + review result
- merge commit hash, branch, and link `https://github.com/sa431322-creator/quran_android/tree/<branch>`
- backup branch name for rollback: `git reset --hard backup/pre-sync-<stamp>` (only if the user asks to roll back — it is destructive)
