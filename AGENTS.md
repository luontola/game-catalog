# AI Agent Instructions

## Code Style

Use `game-catalog.infra.hiccup/html` instead of `hiccup2.core/html`.

Call `h/html` at the top level of every HTML fragment whenever possible.
This allows Hiccup to compile the HTML to a string at build time rather than runtime.
`h/html` can optimize nested `for`, `if`, `when`, `let` calls, but for other macros a new `h/html` call is needed.

## After Code Changes

Run tests after writing or modifying code, including after adding new tests:

```
lein test
```
