# LetterGrid for Kotlin

A grid of letters with words hidden along paths that may turn: generating a board for a set of
words, selecting a path with a finger, and the round of finding them. This is the Kotlin port of
[lettergrid-swift](https://github.com/apakabarlabs/lettergrid-swift); the same seed gives the
same board on both.

The port is in progress and nothing is released yet.

## Build and test

The `Makefile` is the way in: `make build` runs the lint, the tests and the documentation and
assembles both modules, `make test` runs the tests, and `make test TEST=<class>` one class.
