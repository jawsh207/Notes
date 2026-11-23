package app.slate.notes.data

/** The title of the note made the first time the app opens a folder. */
const val WELCOME_TITLE = "Welcome to Notes"

/**
 * The welcome note's text: a short hello, and an example of each kind of Markdown the app
 * formats, so people can learn by looking at it in both views. It's pinned so it's seen first,
 * and it's an ordinary note: editing or deleting it is fine.
 */
val WELCOME_TEXT = """
---
pinned: true
---
# Hello, and welcome!

Your notes are **plain text files** in the folder you picked, so they're always yours. Copy them, back them up, or sync them with any tool you like.

Tap the **eye** at the top to see this note formatted, and the **pencil** to see how it's written. Then make it your own, or delete it when you're done.

## Making text stand out

- **Bold** with two stars on each side
- *Italic* with one star
- ~~Crossed out~~ with two tildes
- `Code` with backticks

## Headings

Start a line with # for a big heading, ## for a smaller one, and ### for smaller still.

## Lists

- A bullet starts with a dash
  - Indent it to nest it
- Another bullet

1. Numbered lists
2. Count themselves

## Checkboxes

- [ ] Try ticking this box in the formatted view
- [x] This one is already done

A note that's nothing but checkboxes becomes a checklist. Pick **New list** from the + button to start one.

## Quotes and links

> A line starting with > is a quote.

Links look like [this](https://commonmark.org/help/), or just paste an address: https://commonmark.org

## Code blocks

```
Three backticks above and below
keep text exactly as typed.
```

---

A line of three dashes draws a line across the page, like the one above.

## Notebooks, pins and colours

Notes live in notebooks, which you'll find on the **Organize** tab. This note is in **Unsorted**. Make your own and move notes into them from a note's menu.

This note is also **pinned**, so it stays at the top. Use the pin and palette buttons to make notes stand out; they're saved at the very top of each file.
""".trimStart()
