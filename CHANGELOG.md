# Changelog

All notable changes to this library are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

This library is the Kotlin version of the Swift library
[lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift), whose names and
behaviour it follows. Every minor version of the Swift library gets a Kotlin release with
the same major and minor number, and the two behave the same: given the same seed, both
build the same board. Patch numbers are counted separately in each library, and a patch
release never changes behaviour, so a seed gives the same board across patch releases; a
minor release may change the boards.

## [0.26.0] - 2026-11-05

Settings now come in small groups, and word directions are expressed only through rules,
matching lettergrid-swift 0.26.0. This breaks calls that create a `BoardGenerator`, a `Round`
or a `LetterBoard`. Boards built from the same settings and seed remain unchanged. Straight
selection keeps the old `directions` behaviour; callers already using straight `rules` get
the two selection changes described below.

Before:

```kotlin
val generator = BoardGenerator(
    columns = 7, rows = 7, maxRows = 9, directions = directions,
    filler = weights, downWords = 1..2, conditionalCharacters = setOf("-"),
    maxWordsPerDirection = 2,
)
val board = generator.board(words, seed = 42uL, firstDirection = Direction.RIGHT)
val round = Round(board, directions, seconds = 60, introduces = true)
LetterBoard(round, style, onTouch, onDrag, onRelease, onRejectionShown)
```

After:

```kotlin
val rules = listOf(WordRule.straight(directions))
val generator = BoardGenerator(
    size = BoardSize(columns = 7, rows = 7, maxRows = 9), rules = rules,
    filler = Filler(weights, conditionalCharacters = setOf("-")),
    limits = PlacementLimits(downWords = 1..2, maxWordsPerDirection = 2),
)
val board = generator.board(words, seed = 42uL, firstRule = WordRule.straight(listOf(Direction.RIGHT)))
val round = Round(board, rules, options = RoundOptions(seconds = 60, introduces = true))
LetterBoard(round, style, actions = LetterBoardActions(onTouch, onDrag, onRelease, onRejectionShown))
```

### Changed

- Both old generator constructors become `BoardGenerator(size, rules, filler, limits)`.
  `BoardSize(columns, rows, maxRows = null)` keeps the dimensions and optional maximum height.
  `Filler(weights, conditionalCharacters = emptySet())` keeps the weights and conditional
  characters. `limits` defaults to `PlacementLimits()`: `downWords = 0..Int.MAX_VALUE` and
  `maxWordsPerDirection = null`, so neither quota restricts placement by default.
- Read the generator's former public properties through their groups: `columns`, `rows` and
  `maxRows` through `size`; `conditionalCharacters` through `filler`; `downWords` and
  `maxWordsPerDirection` through `limits`. The former `generator.filler` is now
  `generator.filler.weights`. To collect enabled directions, use
  `generator.rules.flatMap { it.startDirections }.distinct()`.
- `directions` is removed from the generator and round; use
  `rules = listOf(WordRule.straight(directions))`. The helper makes a rule with no turns.
- `firstDirection` is removed from `request`, `checkPlacement`, `board` and `BoardRequest`.
  Pass `firstRule = WordRule.straight(listOf(direction))`, and read it as
  `request.firstRule?.startDirections?.firstOrNull()`. No first rule means `null`.
  `accepts(words, firstRule)` is unchanged. A first rule may narrow an enabled rule's start
  directions with the same turn count, just as a leading rule may. A disabled first rule
  throws `InvalidConfiguration`; a disabled first direction used to throw `NoRoom`, so update
  any catch that handled that case.
- `Round(board, rules, options, progress)` and `Round.restoring(progress, board, rules, options)`
  replace both old variants. `RoundOptions()` keeps the defaults: `seconds = null`,
  `introduces = false`, `hintOrder = HintOrder.FROM_START`. Progress stays separate; public
  round properties remain unchanged. `UnselectableWord` now mentions rules instead of
  directions; its type and word remain unchanged.
- Straight selection uses the order of start directions to break ties, and projects a drag
  beyond the board onto its last cell. This preserves the old `directions` path. The old
  straight `rules` path used `Direction.entries` order for ties and ignored drags beyond
  the board; both now follow the same straight-selection behaviour.
- `LetterBoard` takes its four callbacks as `actions = LetterBoardActions(onTouch, onDrag,
  onRelease, onRejectionShown)`, with all four required. Its other parameters keep their
  defaults. Compose uses the current callbacks after recomposition, including during a drag.
  The Compose view keeps seven parameters because it also takes the platform's `modifier`;
  with the owner's agreement, detekt's parameter-count check excludes `@Composable`
  functions. The check and its threshold remain unchanged for other functions.
- Forbidden equal-letter runs read `fill.runLength` from the package's `generation.yaml`,
  with a minimum of two, matching Swift. It remains three, so generated boards do not change.

## [0.25.0] - 2026-11-04

A word may now be one letter long, such as Hungarian `ó` or Swedish `ö`. Before, the
generator refused such a word and no selection could find one; now it takes one cell and the
player finds it with a tap. Boards of words that all have two letters or more are the same as
in 0.24.0 for every seed, and every board is the same as lettergrid-swift 0.25.0 builds from
the same seed. If your app relied on the library to refuse one-letter words, filter them out
yourself before the request.

### Changed

- `BoardGenerator.accepts` and every request accept a one-letter word. It fits only a rule
  without turns: `WordRule.minimumLength` is now 1 for a rule without turns (it was 2) and
  stays `turns + 2` for a turned rule, and `WordRule.minimumTurns(toFit = 1, ...)` is 0 where
  it was `null`.
- As for any target, a request refuses a one-letter word together with a word that contains
  its letter: `listOf("ó", "hó")` is refused, because the longer word would hold a second
  reading of `ó`.
- The letter of a one-letter word stands on the board once, even when the filler weights
  include it. Every other target character still stands at least twice, but a copy of this
  letter would be a second occurrence of the word, so the board gets none. A player who knows
  the word can therefore spot it by its letter alone.
- `Round.release()` finds a one-letter word when the selection is its one cell: a `touch`
  followed by `release` with no `drag`. `LetterBoard` already reports a tap that way. A
  one-cell selection that finds no word returns `null` and leaves `rejected` empty, as before,
  so a stray tap is not shown as a wrong word.
- A one-letter placement is selectable whatever the round's directions, so `Round(board,
  directions)` and `Round.restoring(progress, board, directions)` accept it with any nonempty
  list.
- The generator gives a one-letter placement the start direction of the rule that placed it,
  and `maxWordsPerDirection` and `downWords` count it by that direction.
  `Placement.validating(word, cells)` and `Board.validating(letters, placements)` accept a
  placement of one cell and give it `Direction.RIGHT`, since one cell shows no direction: a
  restored one-letter word may thus read `RIGHT` where the generator gave it `DOWN`. Nothing
  after generation depends on that direction, because a round accepts a one-letter word under
  any directions.
- Internal refactor, drop-in: code shaped to detekt's defaults without changing behaviour.

```kotlin
val generator =
    BoardGenerator(
        columns = 7, rows = 7, directions = listOf(Direction.RIGHT, Direction.DOWN),
        filler = LetterWeights(mapOf("x" to 1.0, "q" to 1.0, "z" to 1.0)),
    )
val board = generator.board(listOf("cat", "dog", "ó"), seed = 1uL)
val round = Round(board, listOf(Direction.RIGHT, Direction.DOWN))
val island = board.placements[2].cells[0] // placements follow the order of the words
round.touch(island)
round.release() // the placement of "Ó": placements hold words uppercased, as for any word
```

## [0.24.0] - 2026-11-04

What an app shows around the board, such as the list of words still to find, now belongs to
the app: `HiddenWordList` is removed, and the library draws only the board, with `LetterBoard`
and the `LetterHighlight` it uses.
Code that passes `foundText` to `LetterGridStyle` or its `copy` no longer compiles, and since
the parameter had no default, that is every app which builds a style. Code that calls
`HiddenWordList` no longer compiles either. The API and behaviour of the board and the round
are unchanged, and a seed gives the same board as in 0.23.0.

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
word with the same order, letters and colour as the removed list, and TalkBack reads the same
label for the word. The rest is now the app's to choose: the removed list was centred, used
a monospaced `titleLarge` font with a fixed width per letter and showed the number as a
separate, muted element that TalkBack read on its own, while this sketch puts the number into
the word's line, where the label replaces it and TalkBack does not read it:

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
    limit, and is lost when it runs out. A round can also open with an introduction: before
    play starts, it points out the first hidden word on the board one letter at a time, to
    show the player what a hidden word looks like. `Countdown` drives both: it calls the app
    back once per letter of the introduction, at the pace the app sets, and then once a
    second while the time limit runs down.
  - A patch release keeps the boards a seed gives, but a minor release may change them, so a
    seed alone does not bring back a saved board across a minor update. To save a game, save
    the board's `letters` and `placements` and the round's `progress`; to restore it, pass
    each saved word and its cells to `Placement.validating`, the letters and placements to
    `Board.validating`, and the progress and board to `Round.restoring`, together with the
    `WordRule`s or directions the round was started with. The first two throw a
    `Board.Failure`, and `Round.restoring` a `RoundProgress.Failure` for bad progress or a
    `Round.Failure` when a hidden word does not follow those rules. Each names what is
    wrong with the saved data, so the app can catch it and show it. The plain constructors throw
    `IllegalArgumentException` instead, the exception Kotlin uses for a programming mistake.
- `lettergrid-kotlin-ui`, Compose views on Android 9 (API 28) or later:
  - `LetterBoard` draws the round and takes the player's touches.
  - `HiddenWordList` lists the words to find.
  - `LetterGridStyle` holds the colours from the app's theme: found and selected words, a
    rejected selection, which flashes for `rejectionSeconds`, hinted letters, and
    `foundText` for found words in `HiddenWordList`.
  - `LetterHighlight` draws a thick line with rounded ends through the cells of a selected
    or found word.
