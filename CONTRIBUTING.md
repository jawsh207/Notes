# Contributing

See README.md for the build setup.

## Commits

Commit as the repo owner, `jawsh207 <76641448+jawsh207@users.noreply.github.com>`, with no
co-author trailers or session links. Don't credit any tools or services in files, commit
messages, release notes or pull requests.

## Code

- Kotlin and Jetpack Compose, Material 3 Expressive (material3 1.5).
- Original code only. Code from Contacts and Dialtone (Apache 2.0, same owner) can be reused
  freely.
- Keep the look in step with Contacts and Dialtone: theme, bottom bar, search bar and grouped
  cards should match them exactly.
- Never change a note's text behind the user's back. The app reads and writes files exactly as
  they are; formatting is only ever drawn, not saved.

## Documentation

Describing the code is part of every change, not a follow-up:

- Every Kotlin file, and every class, object, interface and function in it that isn't private,
  starts with a `/** … */` description: in plain words, what it is and why it's there, plus
  anything surprising (an Android rule it works around, why it runs where it does).
- When you change code, re-read its description and fix anything the change made wrong.
- Comments inside functions explain why, not what: Android behaviour, choices between
  alternatives. Don't narrate the lines.
- Describe only what the code does. Check each claim against the code.
- Update `README.md` when a change affects building, installing, permissions or how a feature
  behaves.
- Update `docs/HOW-IT-WORKS.md` in the same commit when a change adds, removes, renames or
  moves a feature, screen, setting, permission or file. It's the guide for people who don't read
  code: keep it free of code and keep its tables (screen → file, job → file) accurate.

## Tests

Logic that doesn't need a phone (Markdown, front matter, checklists, pictures, file names, sorting,
search, reminders, sync decisions) has
unit tests in `app/src/test`. When you add or change such logic, add or update its tests. Keep
that logic in plain functions outside the screens so it can be tested. CI runs the tests on
every build and stops on a failure; run them locally with `./gradlew :app:testDebugUnitTest`.

`python3 scripts/check-docs.py` lists anything missing a description. CI runs it on every
build and stops the build if something is missing.

## Releases

When the owner says "Build release":

1. Collect the changes since the last release: `git log --format=%s origin/release..main`.
2. In `version.txt`, raise line 1 (version code: today's date as YYYYMMDD, or +1 if that is not
   higher) and line 2 (version name, MAJOR.MINOR.PATCH: every release bumps the patch number;
   minor or major only when asked, resetting the parts after it to 0).
3. Add a `## <version name>` section at the top of the sections in `CHANGELOG.md` (one line per
   bullet or paragraph, no hard wraps), in plain, user-facing words, grouped by area.
4. Commit, push `main`, then `git push origin main:release`. CI builds, signs, tags
   `v<version>` and publishes the release with that section as its notes. Before publishing it
   opens the release APK on an emulator; if the app crashes, nothing is published and the
   crash trace is on the run page.

The **Build release** workflow in the Actions tab does the same, writing the notes from commit
titles.
