# Notes

A note-taking app for Android where you own your notes. There's no account and no cloud: the
app reads and writes plain Markdown (`.md`) files in a folder on your phone that you choose.

- **Your files, your way.** Every note is an ordinary text file named after its title. Copy the
  folder to a computer, back it up on a thumb drive, or keep it in sync with whatever tool you
  like (Syncthing, a cloud drive app, git).
- **Live updates.** When a note changes on another device and syncs into the folder, or another
  app edits it, the app picks it up by itself, including the note you're looking at. If you were
  typing in it at the time, it asks which version to keep instead of overwriting either.
- **Modern look.** Jetpack Compose and Material 3 Expressive: wallpaper colours, Google Sans
  Flex, grouped cards, spring animations. It shares its look with
  [Contacts](https://github.com/jawsh207/Contacts) and [Dialtone](https://github.com/jawsh207/dialtone).

## Using it

On first launch, pick (or create) a folder with the system folder picker. The app asks for no
permissions: the picker gives it access to that folder and nothing else.

- **Notes tab:** every note in the folder and its sub-folders, newest first (or A–Z, in
  Settings). Pull down to look at the folder again right away.
- **Folders tab:** the folder's sub-folders and the notes at its top level. Sub-folders open into
  their own page, where new notes and folders are made inside them.
- **Writing:** a note saves itself a moment after you stop typing, and when you leave it or
  switch apps. The title is the file name; changing it renames the file. A new note left
  without a title is named after its first line; one left completely empty is removed.
- **Formatted view:** the eye button shows headings, lists, checkboxes, quotes, code and links.
  Ticking a checkbox there writes the tick into the file (`- [ ]` becomes `- [x]`).
- **Search:** finds notes containing every word typed, in the title or text, ignoring case and
  accents.
- Long-press a note in any list to share or delete it. Deleting offers Undo.

Files ending in `.md` or `.markdown` count as notes. Hidden folders (names starting with a dot,
such as `.git`, `.obsidian` or `.stfolder`) are skipped. Notes are read and written as UTF-8,
exactly as typed; the app never reformats a file.

Changes are noticed straight away on the phone's own storage, which reports them. Other storage
providers may not, so the app also looks at the folder every few seconds while it's on screen,
and at the open note more often.

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Notes Dev" (`app.folio.notes.dev`) | `notes-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Notes" (`app.folio.notes`) | attached to a GitHub Release |

A release is tagged with the version name on line 2 of `version.txt` (line 1 is the version
code, which must go up with every release), and its notes come from that version's section of
`CHANGELOG.md`. Each run's page shows the APK's signing certificate SHA-256.

To build locally (JDK 21 and the Android SDK required):

```sh
./gradlew :app:assembleDev       # test build
./gradlew :app:assembleRelease   # release
./gradlew :app:testDebugUnitTest   # unit tests
```

Every CI build runs the unit tests (`app/src/test`) and `scripts/check-docs.py` first, and
stops if either fails.

For what each part of the app does and which file it's in, see
[docs/HOW-IT-WORKS.md](docs/HOW-IT-WORKS.md).

### Signing keys

Set as repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `DEV_KEYSTORE_BASE64` | base64 of the dev keystore (alias `notes-dev`) |
| `DEV_KEYSTORE_PASSWORD` | its password (store and key) |
| `RELEASE_KEYSTORE_BASE64` | base64 of the release keystore (alias `notes`) |
| `RELEASE_KEYSTORE_PASSWORD` | its password (store and key) |

Without the dev key, test builds are signed with a throwaway key (the run says so) and won't
install over each other. A release refuses to build without its key.

## License

Apache License 2.0 (see `LICENSE`). Third-party parts are listed in `NOTICE`.
