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

The app keeps only a small settings file for itself: which folder, display choices, which
reminder alarms it has set, and where notes it put in the trash came from.

## What a note's file holds

A note's file has up to three parts, top to bottom:

- **Front matter**, a block between two `---` lines that Obsidian and other Markdown tools
  also use. The app keeps the pin, colour, labels ("tags"), archive and reminder there. It only
  ever changes those five entries and leaves anything else in the block alone; a note with none
  of them gets no block. The editor never shows this block as text.
- **Pictures** at the top: lines like `![](attachments/photo.jpg)`. They're drawn as pictures
  across the top of the note. Pictures added in the app are copied into an `attachments` folder
  next to the note.
- **The text**, which is what you type. When every line is a Markdown task (`- [ ] milk`), the
  note is a checklist and is edited as rows of tick boxes instead.

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
| First run | A welcome with three short points about how notes are kept, and the button that opens the folder picker; also shown if the folder can't be reached | `ui/setup/SetupScreen.kt` |
| Notes tab | Every note that isn't archived, as cards, pinned first; pull to refresh | `ui/notes/NotesTab.kt`, `ui/notes/NoteGrid.kt` |
| Browse tab | Reminders, Archive, Trash, every label and the top folders | `ui/browse/BrowseTab.kt` |
| Folder page | One folder's sub-folders and notes, with the + menu and New folder | `ui/folders/FolderScreen.kt` |
| Label, Archive and Reminders pages | The notes with a label (rename or delete the label here), archived notes, notes with reminders soonest first | `ui/notes/CollectionScreen.kt`, `ui/NotesApp.kt` |
| Trash | Notes in the trash, to put back or delete for good; Empty trash | `ui/notes/TrashScreen.kt` |
| Note | Writing a note or checklist, pictures, the formatted view, pin, reminder, archive, colour, labels, copy, share, delete, sync cards | `ui/editor/EditorScreen.kt`, `ui/editor/ChecklistEditor.kt`, `ui/editor/MarkdownView.kt` |
| Search | Finding notes by words in the title, text or labels | `ui/search/SearchScreen.kt` |
| Settings | Notes folder, sort order, open formatted, fixed-width font, grid view, ticked items to the bottom | `ui/settings/SettingsScreen.kt` |

The main screen (the two tabs with the search bar, the + menu and the bottom bar), moving
between screens with their sliding, springy transitions, and the Undo bar are in
`ui/NotesApp.kt` and `ui/Navigation.kt`. Picking several notes with a long press, and the bar
of actions that then replaces the search bar, are in `ui/notes/NoteGrid.kt` and
`ui/notes/SelectedNotesBar.kt`. Shared pieces are in `ui/components/`: note cards and chips
(`NoteCard.kt`), pictures (`NoteImage.kt`), the + menu (`NewNoteFab.kt`), the colour, label and
reminder pickers (`Pickers.kt`), reminder wording (`Labels.kt`), rows, grouped cards, the press
"give" animation and empty states. The app's colours and fonts are in `ui/theme/Theme.kt`,
which matches Contacts and Dialtone, and the note colours in `ui/theme/NoteColors.kt`.

## Features

**Notes and titles.** Files ending in `.md` or `.markdown` are notes; their title is the file
name without the ending. Hidden folders (starting with a dot, like `.git` or `.obsidian`) are
skipped. Characters that some computers don't allow in file names (such as `/` or `:`) are
turned into spaces when a title becomes a file name, and a number is added if the name is
taken ("Ideas 2.md").

**Welcome note.** The first time the app opens a folder, it makes a pinned "Welcome to Notes"
note at its top, even if the folder already has notes: a hello and an example of every kind of
formatting, to compare in the editing and formatted views. It's an ordinary note to edit or
delete. It's only made once per phone, and not when the folder already has a "Welcome to Notes"
note (one made by another phone that shares the folder, say).

**New notes and lists.** New note makes "Untitled.md" in the current folder; New list makes it
with one empty checklist item. Either opens with the title ready to type. Leave the title blank
and the note is named after its first line when you leave; leave the whole note empty and the
file is removed. Made from a label's page, the note gets that label.

**Saving.** Typing is saved a little over half a second after you stop, and again whenever you
leave the note or switch away from the app. Pin, colour, label and reminder changes are saved
straight away.

**Checklists.** Enter starts a new item; backspace in an empty item removes it; the item being
typed in shows buttons to indent it and remove it. Ticked items gather under a heading at the
bottom that folds away (Settings can turn that off). The menu can untick everything or delete
the ticked items. Show and Hide tick boxes turn a note into a checklist and back.

**Pins, colours, labels, archive.** Pinned notes come first in every list. A colour tints the card
and the whole note screen, in light and dark versions. Labels are kept as the note's tags; every
label any note has appears in Browse. Archived notes leave the main list for the Archive page.

**Reminders.** A reminder is a time in the note's front matter. The app sets an Android alarm
for every future reminder it finds when it reads the folder, cancels alarms for reminders that
are gone, and sets them again after the phone restarts. When one goes off, a notification
shows the note's title; tapping it opens the note.

**Pictures.** Add image copies a picture from the gallery into the `attachments` folder next to
the note and adds it at the top. A long press on a picture takes it out of the note; the image
file stays in the folder.

**Formatted view.** Headings, bullet and numbered lists, checkboxes, quotes, code blocks, rules,
pictures, bold, italic, strikethrough, inline code and links. Ticking a checkbox changes only that
one character in the file. Links open in the browser.

**Search.** Every word typed must appear in the title, the text or a label; case and accents don't
matter. Archived notes are included. Title matches come first, and each card shows the text
around its match.

**Picking several notes.** A long press on a card picks it, and taps then pick more. The search
bar becomes a bar to pin, colour, archive or delete them all, with labels, a reminder, select
all, copy and share in its menu.

**Trash and Undo.** Deleting moves a note into a hidden `.trash` folder at the top of the notes
folder (the one Obsidian uses), and offers Undo, as archiving does. The Trash page puts notes
back where they came from, or deletes them for good. Notes this phone put in the trash are
deleted for good after 7 days; it remembers where each came from so it can put it back. Notes
trashed elsewhere go back to the top folder.

## Jobs and where they're done

| Job | File |
| --- | --- |
| Listing the folder and the trash, reading, writing, creating, renaming, moving and deleting files, saving pictures, and hearing about changes | `data/NoteStore.kt` |
| Watching the folder, reading notes, the open note, new notes, front matter changes for many notes, trash, Undo, setting reminder alarms | `ui/NotesViewModel.kt` |
| One open note: its three parts, saving after typing, checklist rows, pictures, taking in changes from elsewhere, conflicts, renaming | `ui/editor/NoteSession.kt` |
| Reading and writing the front matter (pin, colour, labels, archive, reminder) | `data/FrontMatter.kt` |
| Checklists: recognising them, reading and writing items, turning text into a checklist and back | `data/Checklist.kt` |
| Pictures at the top of a note, and where a picture's file is | `data/Images.kt` |
| What the lists show about each note | `data/NoteInfo.kt` |
| Quick reminder times, reminder and trash records, how long the trash keeps notes | `data/Reminders.kt` |
| Setting and cancelling alarms; the reminder notification and setting alarms again after a restart | `reminders/ReminderAlarms.kt`, `reminders/ReminderReceiver.kt` |
| Opening a note from a reminder notification | `MainActivity.kt` |
| Deciding what to do when the open note's file changes | `data/Sync.kt` |
| Reading Markdown for the formatted view, card previews and ticking checkboxes | `data/Markdown.kt` |
| Which files are notes, titles, and safe, unique file names | `data/Names.kt` |
| Sorting | `data/Sorting.kt` |
| Search | `data/Search.kt` |
| Settings | `data/Settings.kt` |
| The welcome note's text | `data/Welcome.kt` |
| Sharing a note and opening links | `util/Share.kt` |
| What a note, folder and the whole folder's contents are | `data/Models.kt` |
