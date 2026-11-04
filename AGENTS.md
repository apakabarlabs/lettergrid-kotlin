# LetterGrid for Kotlin

Before the first action read the whole wiki «Как мы пишем код»:
wikilayer://wikilayer.org/smee-again/codestyle

## Decisions that outlive the current code

- This is the Kotlin port of [lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift),
  which leads. Names, behaviour and the boards a seed gives follow it; a change starts there and is
  ported here. Major and minor versions match it.
- The library is public. Every release gets a tag, a GitHub release, a CHANGELOG entry and a
  Maven Central publication under `fm.apakabar`, made by the release workflow.
- Two modules, as the Swift package has two targets: `core` (`lettergrid-kotlin`) is plain
  Kotlin on the JVM, as `LetterGrid` is plain Swift, and is tested in CI; `ui`
  (`lettergrid-kotlin-ui`) holds the Compose board, as `LetterGridUI` holds the SwiftUI one,
  with colours from the app's theme. What a game shows around the board, such as the words to
  find, stays in that game.
- The shared cases and the generation tuning are copies of the Swift port's files, written here
  by its `make sync-yaml`; a test compares each with the Swift port's `main` byte for byte.
- Only what any app with a letter grid needs lives here. A game's own rules stay in that app.
