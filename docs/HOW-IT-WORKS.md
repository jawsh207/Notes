# How Notes works

A plain-language guide to the app: what each part does and where it lives in the code. No
programming knowledge needed. File paths are inside `app/src/main/java/app/slate/notes/`.

## The big picture

Notes doesn't keep its own copy of your notes. Each note is a Markdown file in a folder you
picked, and the app is a window onto that folder: the lists are what's in it, opening a note
reads its file, typing writes the file, and renaming a note renames the file. Nothing is stored
anywhere else, so whatever you do to the folder outside the app (copy, sync, edit on a
computer) is simply what the app shows.

Android doesn't let apps roam your storage freely. Instead, the **system folder picker** hands
the app access to the one folder you choose, and the app keeps that access across restarts.
If the folder is moved or the access is taken away, the app asks you to pick it again.

The only thing the app keeps for itself is a small settings file: which folder, how to sort,
and two display choices.

## Keeping up with changes

Notes edited elsewhere (another device syncing into the folder, or another app) show up on
their own, in three ways:

- **Straight away**, on the phone's own storage: Android tells anyone with a folder's listing
  open when a file in it is added, removed or saved. The app keeps a listing of each folder open
  and looks again as soon as it hears.
- **Every few seconds** while the app is on screen, because other storage providers (an SD card
  app, a cloud drive app) may not say. Only the file list is checked; a note's text is read again
  only if its date or size changed.
- **The open note** is checked every second and a half.

When the open note's file changes: if you have no unsaved typing, the new text replaces what's
on screen (an "Updated" label springs in at the top). If you were typing at the time, a card asks
whether to keep your version or use theirs, and nothing is saved until you choose. If the file
disappears (deleted, or renamed or moved elsewhere), a card offers to save what's on screen as
a new note.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| First run | Explains the folder idea and opens the folder picker; also shown if the folder can't be reached | `ui/setup/SetupScreen.kt` |
| Notes tab | Every note, newest first in Today / Yesterday / … groups, or A–Z; pull to refresh | `ui/notes/NotesTab.kt`, `ui/notes/NoteLists.kt` |
| Folders tab | The folder's sub-folders with note counts, and its top-level notes | `ui/folders/FolderContents.kt` |
| Folder page | One sub-folder's sub-folders and notes, with New note and New folder | `ui/folders/FolderScreen.kt` |
| Note | Writing a note, the formatted view, sync cards, share and delete | `ui/editor/EditorScreen.kt`, `ui/editor/MarkdownView.kt` |
| Search | Finding notes by words in the title or text | `ui/search/SearchScreen.kt` |
| Settings | Notes folder, sort order, open formatted, fixed-width font | `ui/settings/SettingsScreen.kt` |

The main screen (the two tabs with the search bar, the New note / New folder button and the
bottom bar), moving between screens with their sliding, springy transitions, and the Undo bar
are in `ui/NotesApp.kt` and `ui/Navigation.kt`. Shared pieces such as note cards, rows, grouped
cards, the press "give" animation and empty states are in `ui/components/`, and the colours and
fonts in `ui/theme/Theme.kt`, which matches Contacts and Dialtone.

## Features

**Notes and titles.** Files ending in `.md` or `.markdown` are notes; their title is the file
name without the ending. Hidden folders (starting with a dot, like `.git` or `.obsidian`) are
skipped. Characters that some computers don't allow in file names (such as `/` or `:`) are
turned into spaces when a title becomes a file name, and a number is added if the name is
taken ("Ideas 2.md").

**New notes.** New note makes "Untitled.md" in the current folder and opens it with the title
ready to type. Leave the title blank and the note is named after its first line when you leave;
leave the whole note empty and the file is removed.

**Saving.** Typing is saved a little over half a second after you stop, and again whenever you
leave the note or switch away from the app.

**Formatted view.** Headings, bullet and numbered lists, checkboxes, quotes, code blocks, rules,
bold, italic, strikethrough, inline code and links. Front matter (the `---` block some tools put
at the top) is hidden. Ticking a checkbox changes only that one character in the file. Links open
in the browser.

**Search.** Every word typed must appear in the title or the text; case and accents don't
matter. Title matches come first, and each result shows the text around its match.

**Delete and Undo.** Deleting removes the file; Undo writes it back with the same name and text.

## Jobs and where they're done

| Job | File |
| --- | --- |
| Listing the folder, reading, writing, creating, renaming and deleting files, and hearing about changes | `data/NoteStore.kt` |
| Watching the folder, reading texts, the open note, new notes, deleting and Undo | `ui/NotesViewModel.kt` |
| One open note: saving after typing, taking in changes from elsewhere, conflicts, renaming | `ui/editor/NoteSession.kt` |
| Deciding what to do when the open note's file changes | `data/Sync.kt` |
| Reading Markdown for the formatted view, list previews and ticking checkboxes | `data/Markdown.kt` |
| Which files are notes, titles, and safe, unique file names | `data/Names.kt` |
| Sorting and the date groups | `data/Sorting.kt` |
| Search | `data/Search.kt` |
| Settings | `data/Settings.kt` |
| Sharing a note and opening links | `util/Share.kt` |
| What a note, folder and the whole folder's contents are | `data/Models.kt` |
