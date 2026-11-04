# Changelog

All notable changes to this library are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/). The library ports the Swift library
[lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift), whose names and
behaviour it follows; major and minor versions match it.

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
