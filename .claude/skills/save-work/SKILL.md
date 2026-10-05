---
name: save-work
description: Save finished work after every completed task — review the diff at least once with /code-review, fix confirmed findings, commit, and push to GitHub (origin). Use automatically at the end of each task that changed files, or when the user says "احفظ", "احفظ العمل", "زامن", "save", "commit", "push", or "sync with GitHub".
---

# Save work — مراجعة ثم حفظ ثم مزامنة مع GitHub

Run this at the end of **every task that changed files**. Do the steps in order; do not skip the review.

## 1. Inspect what changed

```bash
git status --short
git diff --stat
git branch --show-current
```

- If nothing changed, say so and stop.
- Only stage files that belong to the task just finished. Never stage: `local.properties`, `build/`, `.gradle/`, `.kotlin/`, `.idea/` noise, keystores (`*.jks`, `*.keystore`), `google-services.json` with real keys, `.env`, or any file containing passwords/tokens. If one of these shows up as changed, leave it out and mention it.
- Large binaries (`*.docx`, APKs, zips) are left out unless the user asked to commit them.

## 2. Code review (at least once — mandatory)

1. Invoke the `code-review` skill on the current diff (effort `medium` for small changes, `high` for anything touching rendering, data, or audio/video playback).
2. For each confirmed finding: fix it, or explain briefly why it is not a real issue.
3. If you changed code because of the review, run the review **one more time** on the new diff. Stop after the second pass; list anything still open in the summary instead of looping.
4. Project rule: the Quranic script fonts and mushaf rendering must not change (see memory `quran-fonts-locked`). If the diff touches page fonts, glyph rendering, or mushaf images, flag it to the user before committing.

## 3. Quick build check (when Kotlin/Java/Gradle files changed)

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew :app:compileMadaniDebugJavaWithJavac --offline -q
```

(This compiles both Kotlin and Java. `java` is not on PATH, so JAVA_HOME must point at Android Studio's JBR. "Failed connecting to the daemon" from Kotlin is harmless — it falls back to in-process compilation.)

Run it in the background if it is slow. If it fails, fix the error before committing. If it cannot run (no SDK / network), say so in the summary — do not claim the build passed.

## 4. Commit

- If on `main` or `master`, create a branch first: `git switch -c <short-topic-name>`.
- Use the Bash tool with a heredoc for the commit message (PowerShell here-strings piped to `git commit -F -` break).
- Stage explicit paths (`git add <file> ...`), never `git add -A` blindly.
- Commit message: one short imperative subject line (English), optional body describing *why*, then the attribution line required by the current session (e.g. `Co-Authored-By: ...`).
- Never use `--no-verify` or amend an already-pushed commit.

## 5. Sync with GitHub

```bash
git fetch origin
git pull --rebase origin <branch>   # only if the remote branch exists
git push -u origin <branch>
```

- On a rebase conflict: resolve it, run `git rebase --continue`, and mention it in the summary. Never `push --force` unless the user explicitly asks.
- If the push fails for authentication, tell the user to sign in to Git (Git Credential Manager) — do not enter credentials yourself.

## 6. Report

Give the user a short summary (in Arabic if they wrote in Arabic):
- review result (findings fixed / dismissed / open)
- build check result
- commit hash + branch
- push result with the GitHub link: `https://github.com/sa431322-creator/quran_android/tree/<branch>`
