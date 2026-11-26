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
reminder alarms it has set, and when it put notes in the trash.

## Notebooks, and working with Folio

The folder is laid out the way Folio, the GNOME notes app, keeps its notes, so the same folder
works in both:

- **Every note is in a notebook**, and a notebook is a folder at the top of the notes folder.
  Folio only looks one level deep and ignores notes that aren't in a notebook, so the app keeps
  to that: new notes go into the notebook being shown, or into **Unsorted**. Whenever the app
  finds notes loose at the top (from its first version, or synced in from another app), it
  moves them into Unsorted, with the pictures in a loose `attachments` folder, so their links
  still work. A note being edited is left until it's closed.
- **A notebook's colour** is kept in a hidden `.config/color` file inside it, written as
  `rgb(53,132,228)`, exactly as Folio does, so both apps show the same colour.
- **The trash** holds deleted notes in `.trash/<notebook>/`, as Folio's does. Putting one back
  returns it to that notebook, making the notebook again if it's gone. Deleting a notebook moves
  the whole thing there, as Folio does; if anything in it can't be moved, the notebook folder is
  kept rather than deleted along with it.
- Folio doesn't read front matter, so pins, colours and reminders show there as text. Labels and archiving were
  taken out of the app because Folio has neither.

## What a note's file holds

A note's file has up to three parts, top to bottom:

- **Front matter**, a block between two `---` lines that Obsidian and other Markdown tools
  also use. The app keeps the pin, colour and reminder there. It only ever changes those
  entries (and keeps any labels or archive mark an earlier version or another tool wrote) and leaves anything else in the block alone; a note with none
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

## Widgets and shortcuts

Widgets are drawn by the home screen, not the app, so each one reads the folder itself, the same
way the app does:

- **List** shows one checklist, ticked items after the others (unless Settings says otherwise),
  in the note's colour. Ticking a box reads the file again, and if that line still says what the
  widget showed, changes just its tick box and saves; if the file changed in the meantime,
  nothing is written and the widget shows the list as it is now. If the app has the list open,
  it takes in the change as it would one from another device.
- **Note** shows one note in its colour, with headings in bold and list marks and tick boxes as
  plain characters, since widgets can't draw formatted text. It scrolls when it's long.
- **Reminders** lists the next reminders, soonest first. It reads the list of alarms the app
  keeps, so it shows what the app found the last time it read the folder.
- **New note** has two buttons, New note and New list, which open the app on a fresh note or list
  in Unsorted. Small, they're icons only.

Placing a List or Note widget opens a page listing the notes (or only the lists) by notebook,
pinned first, to pick one. The app remembers each widget's note by its file and its place in the
folder, so it still finds the note after the app renames it, moves it to another notebook,
renames its notebook, or the trash puts it back. A note deleted, or renamed on another device,
can't be followed: the widget says so and offers to pick another.

Widgets are refreshed a second after the app sees any change in the folder (including its own
saves), right after a tick in a widget, and every half hour in the background while a widget that
reads the folder is on the home screen. The background refresh is how notes synced in while the
app is closed reach the widgets, and how "Today" becomes "Tomorrow" overnight; it stops when the
last such widget is removed.

A long press on the app icon offers **New note** and **New list**, which do the same as the
widget's buttons. Test builds have their own copy of the shortcut list, because a shortcut has
to name the app it opens.

## Screens

| Screen | What it's for | File |
| --- | --- | --- |
| First run | A welcome with three short points about how notes are kept, and the button that opens the folder picker; also shown if the folder can't be reached | `ui/setup/SetupScreen.kt` |
| Notes tab | Every note, as cards, pinned first; notebook chips to show one notebook or make one; pull to refresh |
| Lists tab | Only the checklist notes, with the same chips; the + makes a list straight away | `ui/notes/NotesTab.kt` | `ui/notes/NotesTab.kt`, `ui/notes/NoteGrid.kt` |
| Organize tab | The notebooks in their colours, New notebook, Reminders and the Trash | `ui/organize/OrganizeTab.kt` |
| Notebook page | One notebook's notes, the + menu, and editing (name, colour) or deleting the notebook | `ui/notebooks/NotebookScreen.kt` |
| Reminders page | Notes with reminders, soonest first | `ui/notes/CollectionScreen.kt`, `ui/NotesApp.kt` |
| Trash | Notes in the trash, to put back or delete for good; Empty trash | `ui/notes/TrashScreen.kt` |
| Note | Writing a note or checklist, pictures, the formatted view, pin, reminder, colour, notebook, copy, share, delete, sync cards | `ui/editor/EditorScreen.kt`, `ui/editor/ChecklistEditor.kt`, `ui/editor/MarkdownView.kt` |
| Search | Finding notes by words in the title or text | `ui/search/SearchScreen.kt` |
| Settings | Notes folder, sort order, open formatted, fixed-width font, grid view, ticked items to the bottom | `ui/settings/SettingsScreen.kt` |
| Widget setup | Picking the note or list a widget shows, by notebook | `widgets/WidgetSetupActivity.kt` |
| List widget | One checklist on the home screen, with tick boxes | `widgets/ChecklistWidget.kt` |
| Note widget | One note on the home screen | `widgets/NoteWidget.kt` |
| Reminders widget | The next reminders | `widgets/RemindersWidget.kt` |
| New note widget | New note and New list buttons | `widgets/NewNoteWidget.kt` |

The main screen (the three tabs with the search bar, the + menu and the bottom bar), moving
between screens with their sliding, springy transitions, and the Undo bar are in
`ui/NotesApp.kt` and `ui/Navigation.kt`. Picking several notes with a long press, and the bar
of actions that then replaces the search bar, are in `ui/notes/NoteGrid.kt` and
`ui/notes/SelectedNotesBar.kt`. Shared pieces are in `ui/components/`: note cards and chips
(`NoteCard.kt`), pictures (`NoteImage.kt`), the + menu (`NewNoteFab.kt`), the colour and
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
note in Unsorted, even if the folder already has notes: a hello and an example of every kind of
formatting, to compare in the editing and formatted views. It's an ordinary note to edit or
delete. It's only made once per phone, and not when the folder already has a "Welcome to Notes"
note (one made by another phone that shares the folder, say).

**New notes and lists.** New note makes "Untitled.md" in the notebook being shown (or Unsorted); New list makes it
with one empty checklist item. Either opens with the title ready to type. Leave the title blank
and the note is named after its first line when you leave; leave the whole note empty and the
file is removed.

**Saving.** Typing is saved a little over half a second after you stop, and again whenever you
leave the note or switch away from the app. Pin, colour and reminder changes are saved
straight away.

**Checklists.** Enter starts a new item; backspace in an empty item removes it; the item being
typed in shows buttons to indent it and remove it. Ticked items gather under a heading at the
bottom that folds away (Settings can turn that off). The menu can untick everything or delete
the ticked items. Show and Hide tick boxes turn a note into a checklist and back.

**Pins and colours.** Pinned notes come first in every list. A colour tints the card and the
whole note screen, in light and dark versions.

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

**Search.** Every word typed must appear in the title or the text; case and accents don't
matter. Title matches come first, and each card shows the text
around its match.

**Picking several notes.** A long press on a card picks it, and taps then pick more. The search
bar becomes a bar to pin, colour or delete them all, with move to a notebook, a reminder, select
all, copy and share in its menu.

**Trash and Undo.** Deleting moves a note into `.trash/<its notebook>/`, as Folio does, and
offers Undo. The Trash page puts notes back in their notebooks, or deletes
them for good. Notes this phone put in the trash are
deleted for good after 7 days. Notes trashed by the app's first version, loose in the trash, go
back to Unsorted.

## Jobs and where they're done

| Job | File |
| --- | --- |
| Listing the folder and the trash, reading, writing, creating, renaming, moving and deleting files and notebooks, notebook colours, saving pictures, and hearing about changes | `data/NoteStore.kt` |
| Watching the folder, reading notes, the open note, new notes, notebooks, moving loose notes into Unsorted, front matter changes for many notes, trash, Undo, setting reminder alarms | `ui/NotesViewModel.kt` |
| One open note: its three parts, saving after typing, checklist rows, pictures, taking in changes from elsewhere, conflicts, renaming | `ui/editor/NoteSession.kt` |
| Reading and writing the front matter (pin, colour, reminder) | `data/FrontMatter.kt` |
| Checklists: recognising them, reading and writing items, turning text into a checklist and back | `data/Checklist.kt` |
| Pictures at the top of a note, and where a picture's file is | `data/Images.kt` |
| What the lists show about each note | `data/NoteInfo.kt` |
| Quick reminder times, reminder and trash records, how long the trash keeps notes | `data/Reminders.kt` |
| Setting and cancelling alarms; the reminder notification and setting alarms again after a restart | `reminders/ReminderAlarms.kt`, `reminders/ReminderReceiver.kt` |
| Opening a note from a reminder notification or widget; New note and New list from a shortcut or widget | `MainActivity.kt` |
| Which note each widget shows, reading it, following it when the app renames or moves it, refreshing widgets, and the taps that open the app | `widgets/Widgets.kt` |
| The widgets' shared look: panel, title row, messages | `widgets/WidgetParts.kt` |
| Telling Android about each kind of widget; starting and stopping the background refresh | `widgets/WidgetReceivers.kt` |
| The half-hourly background refresh of widgets | `widgets/WidgetRefreshWorker.kt` |
| A widget's note: following renames and moves, the lines a note widget shows, ticking one item in the file, the reminders the widget lists | `data/Widgets.kt` |
| The long-press shortcuts, and each widget's size and description in the widget picker | `app/src/main/res/xml/shortcuts.xml`, `app/src/main/res/xml/widget_*.xml` |
| Deciding what to do when the open note's file changes | `data/Sync.kt` |
| Reading Markdown for the formatted view, card previews and ticking checkboxes | `data/Markdown.kt` |
| Which files are notes, titles, and safe, unique file names | `data/Names.kt` |
| Sorting | `data/Sorting.kt` |
| Search | `data/Search.kt` |
| Settings (and which note each widget shows) | `data/Settings.kt` |
| The welcome note's text | `data/Welcome.kt` |
| Sharing a note and opening links | `util/Share.kt` |
| What a note, folder and the whole folder's contents are | `data/Models.kt` |
| Notebook rules: which folders are notebooks, names, Folio's colour format, Unsorted | `data/Notebooks.kt` |
| Notebook chips, the new/edit notebook dialog, Move to notebook and the delete question | `ui/notebooks/NotebookPieces.kt` |
