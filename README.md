# Notes

A note-taking app for Android where you own your notes. There's no account and no cloud: the
app reads and writes plain Markdown (`.md`) files in a folder on your phone that you choose.

- **Your files, your way.** Every note is an ordinary text file named after its title. Copy the
  folder to a computer, back it up on a thumb drive, or keep it in sync with whatever tool you
  like (Syncthing, a cloud drive app, git).
- **Live updates.** When a note changes on another device and syncs into the folder, or another
  app edits it, the app picks it up by itself, including the note you're looking at. If you were
  typing in it at the time, it asks which version to keep instead of overwriting either.
- **Google Keep's features.** Checklists, pinned notes, colours, labels, archive, trash,
  reminders and pictures, kept inside the files themselves.
- **Modern look.** Jetpack Compose and Material 3 Expressive: wallpaper colours, Google Sans
  Flex, a grid of cards, spring animations. It shares its look with
  [Contacts](https://github.com/jawsh207/Contacts) and [Dialtone](https://github.com/jawsh207/dialtone).

## Using it

On first launch, pick (or create) a folder with the system folder picker. The app asks for no
storage permission: the picker gives it access to that folder and nothing else. It asks to show
notifications the first time you set a reminder. The app also puts a pinned "Welcome to Notes"
note at the top of the folder, with an example of each kind of Markdown it formats; edit or
delete it as you like. It's only made once per phone, and not if the folder already has one.

It works like Google Keep:

- **Notes tab:** every note that isn't archived, from the folder and its sub-folders, as a grid
  of cards (or a list; the button by the search bar switches). Pinned notes sit at the top. Pull
  down to look at the folder again right away.
- **Browse tab:** Reminders, Archive and Trash, every label, and your folders. A folder's page
  shows its sub-folders and notes, and new notes made there are saved in it.
- **New note or list:** the + button opens into New note and New list (and New folder on Browse).
- **Writing:** a note saves itself a moment after you stop typing, and when you leave it or
  switch apps. The title is the file name; changing it renames the file. A new note left
  without a title is named after its first line; one left completely empty is removed.
- **Checklists:** tick boxes, Enter for a new item, backspace in an empty item to remove it, and
  indent buttons. Ticked items fold away at the bottom (this can be turned off in Settings).
  "Show tick boxes" turns any note into a checklist and "Hide tick boxes" back.
- **Pin, colour, labels, archive, reminders:** from the note's top and bottom bars, or for several
  notes at once after a long press on a card.
- **Pictures:** Add image copies a picture into an `attachments` folder next to the note and puts
  it at the top of the note. A long press on it takes it out of the note.
- **Formatted view:** the eye button shows headings, lists, checkboxes, quotes, code, pictures and
  links. Ticking a checkbox there writes the tick into the file (`- [ ]` becomes `- [x]`).
- **Search:** finds notes containing every word typed, in the title, text or labels, ignoring case
  and accents.
- **Trash:** deleting moves a note to a hidden `.trash` folder (the one Obsidian uses), with Undo.
  The Trash page puts notes back or deletes them for good; notes deleted on this phone are
  removed after 7 days.

### What's in the files

Files ending in `.md` or `.markdown` count as notes, read and written as UTF-8. Hidden folders
(names starting with a dot, such as `.git`, `.obsidian` or `.stfolder`) are skipped. A checklist
is a note whose lines are all Markdown tasks (`- [ ] milk`). Pins, colours, labels, archiving
and reminders are kept in the YAML front matter at the top of the file, which Obsidian and
other Markdown tools understand:

```markdown
---
pinned: true
color: yellow
tags: [shopping, home]
reminder: 2026-11-20T18:00
---
![](attachments/image-20261118-031200.jpg)

- [ ] milk
- [x] eggs
```

The app only changes those keys and leaves anything else in the front matter as it was. A note
with none of them has no front matter added.

Reminders ring through Android alarms, which the app sets whenever it reads the folder, so a
reminder added on another device rings once the app has seen it. They're on the minute when
Android lets the app use exact alarms (Settings → Apps → Special app access → Alarms &
reminders); otherwise Android may hold them for a few minutes.

Changes are noticed straight away on the phone's own storage, which reports them. Other storage
providers may not, so the app also looks at the folder every few seconds while it's on screen,
and at the open note more often.

## Building

GitHub Actions builds two kinds of APK, each signed with its own key:

| | When | App | Artifact |
| --- | --- | --- | --- |
| **Test build** | every push to `main`, PRs, manual runs | "Notes Dev" (`app.slate.notes.dev`) | `notes-dev-apk` on the run page |
| **Release** | Actions → **Build release** → Run workflow; or pushing to the `release` branch | "Notes" (`app.slate.notes`) | attached to a GitHub Release |

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
