# Changelog

All notable changes to this library are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). The library ports the Swift library
[lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift), whose names and
behaviour it follows; major and minor versions match it.

## 0.24.0

What a game shows around the board belongs to the game, so the list of words to find
leaves the library and `LetterBoard` is its only board view. This breaks source for apps
that used the list; the board, the round and their data are unchanged.

### Removed

- `HiddenWordList`, which listed the round's words numbered, one per line, showing each
  letter the round has revealed and `•` for the others, coloured a found word with
  `foundText`, and read a hidden word to TalkBack with the label the app's lambda built
  from its letter count.
- `LetterGridStyle.foundText` and the `foundText` parameter of `LetterGridStyle` and its
  `copy`, which coloured only that list.

An app that used the list draws its own from the existing `Round` API:
`round.shownLetters(placement)` gives one `String?` per letter, `null` where the letter is
still hidden, and `round.found` is the `Set<Placement>` of found words. Collecting
`round.changes` makes the list update as the round is played:

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
round.changes.collectAsState().value
round.board.placements.forEachIndexed { index, placement ->
    val shown = round.shownLetters(placement)
    val found = placement in round.found
    Text(
        "${index + 1}. " + shown.joinToString("") { it ?: "•" },
        color = if (found) Color.Green else LocalContentColor.current,
        modifier = Modifier.semantics { contentDescription = if (found) placement.word else "${shown.size} letters" },
    )
}
```

## 0.23.0

The first release: lettergrid-swift 0.23.0 in Kotlin. A seed gives the board it gives in
Swift, and the shared cases of both ports pass on each.

### Added

- `lettergrid-kotlin`: the board and its generation for straight and turned word paths,
  word rules, filler letter weights, seeded randomness, selection, the round with its hints
  and progress, the countdown, and restoring a saved board with `Board.validating` and
  `Round.restoring`.
- `lettergrid-kotlin-ui`: `LetterBoard`, `HiddenWordList`, `LetterHighlight` and
  `LetterGridStyle` in Compose, coloured from the app's theme.
