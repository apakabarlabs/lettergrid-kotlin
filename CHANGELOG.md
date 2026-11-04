# Changelog

All notable changes to this library are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

This library is the Kotlin version of the Swift library
[lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift), whose names and
behaviour it follows. Every minor version of the Swift library gets a Kotlin release with
the same major and minor number, and the two behave the same: given the same seed, both
build the same board. Patch numbers are counted separately in each library, and a patch
release never changes behaviour.

## [0.24.0] - 2026-11-04

What an app shows around the board, such as the list of words still to find, now belongs to
the app: `HiddenWordList` is removed, and `LetterBoard` is the only view the library draws.
Code that passes `foundText` to `LetterGridStyle` or its `copy` no longer compiles, and since
the parameter had no default, that is every app which builds a style. Code that calls
`HiddenWordList` no longer compiles either. The board, the round and their data are
unchanged.

### Removed

- `HiddenWordList` from `lettergrid-kotlin-ui`. It listed `round.board.placements` in their
  order, one per line, numbered from 1 as `1.`. Each line showed the letters the round has
  revealed and `•` for the others, coloured a found word with `foundText` and left the rest
  in the current content colour. TalkBack, Android's screen reader, read a found word as
  `placement.word` and a hidden one with the label the app's `hiddenWordLabel` lambda built
  from its letter count.
- `LetterGridStyle.foundText` and the `foundText` parameter of `LetterGridStyle` and its
  `copy`, which coloured only that list.

An app that used the list draws its own from the existing `Round` API. A `Placement` is one
hidden word with the cells it occupies. `round.shownLetters(placement)` gives one `String?`
per letter, `null` where the letter is still hidden, and `round.found` is the
`Set<Placement>` of found words. `round.changes` is a `StateFlow` that emits a new value on
every change of the round; a composable that reads it with `collectAsState()` recomposes as
the round is played, even though it ignores the value itself. This composable shows each
word with the same order, letters, colour and TalkBack label as the removed list. The rest
is now the app's to choose: the removed list was centred, used a monospaced `titleLarge` font
with a fixed width per letter and showed the number as a separate, muted element that
TalkBack read on its own, while this sketch puts the number into the word's line:

```kotlin
// 0.23.0
val style =
    LetterGridStyle(
        found = Color.Green, selected = Color.Blue, rejected = Color.Red, hinted = Color.Yellow,
        foundText = Color.Green, rejectionSeconds = 1.2,
    )
HiddenWordList(round, style, hiddenWordLabel = { "$it letters" })

// 0.24.0
val style =
    LetterGridStyle(
        found = Color.Green, selected = Color.Blue, rejected = Color.Red, hinted = Color.Yellow,
        rejectionSeconds = 1.2,
    )
WordsToFind(round, foundColor = Color.Green, hiddenWordLabel = { "$it letters" })

@Composable
fun WordsToFind(round: Round, foundColor: Color, hiddenWordLabel: (Int) -> String) {
    round.changes.collectAsState().value
    Column {
        round.board.placements.forEachIndexed { index, placement ->
            val shown = round.shownLetters(placement)
            val found = placement in round.found
            val label = if (found) placement.word else hiddenWordLabel(shown.size)
            Text(
                "${index + 1}. " + shown.joinToString("") { it ?: "•" },
                color = if (found) foundColor else LocalContentColor.current,
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}
```

## [0.23.0] - 2026-11-02

The first release: lettergrid-swift 0.23.0 in Kotlin. The library builds a word search, a
grid of letters with words hidden in it, and runs the game of finding them. A seed gives the
same board as in Swift, and both libraries pass the same set of test cases.

### Added

- `lettergrid-kotlin`, plain Kotlin on the JVM:
  - `BoardGenerator` builds a board from a list of words and places each one as a
    `Placement`: the word with the cells it occupies. A `WordRule` says which directions
    a word may run in and how many times its path may turn, so a word can read in a straight
    line or bend at right angles. `LetterWeights` sets how often each letter fills the cells
    the hidden words leave empty. A seed makes the board reproducible.
  - `Round` is one game played on one board: the player selects letters by touch and drag,
    and the round accepts the selection when it reads a hidden word and rejects it
    otherwise. On request it reveals a letter of a word not yet found, as a hint, and it
    keeps the found words and revealed letters as `round.progress`. A round can have a time
    limit, and is lost when it runs out. `Countdown` drives a round's time: it shows the
    first word letter by letter at the pace the app sets, then counts the round's seconds
    down.
  - A seed alone does not give the same board after a library update. To save a game, save
    the board's `letters` and `placements` and the round's `progress`; to restore it, pass
    them to `Board.validating`, `Placement.validating` and `Round.restoring`. These throw a
    `Board.Failure`, `RoundProgress.Failure` or `Round.Failure` that names what is wrong
    with the saved data, so the app can catch it and show it. The plain constructors throw
    `IllegalArgumentException` instead, the exception Kotlin uses for a programming mistake.
- `lettergrid-kotlin-ui`, Compose views on Android 9 (API 28) or later:
  - `LetterBoard` draws the round and takes the player's touches.
  - `HiddenWordList` lists the words to find.
  - `LetterGridStyle` holds the colours from the app's theme: found and selected words, a
    rejected selection, which flashes for `rejectionSeconds`, and hinted letters.
  - `LetterHighlight` draws a thick line with rounded ends through the cells of a selected
    or found word.
