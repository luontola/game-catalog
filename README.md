# Rapid Application Template (WIP)

Spreadsheet/MS Access/Airtable replacement. Something which is quick to get started like a spreadsheet, but enables an
incremental transition to a custom application when the solution grows to require a general purpose language.

## Setup

After cloning the repository, set up the data directory:

    git worktree add data data

This creates a `data/` directory that contains the CSV data files from the `data` branch.

## Developing

Start the app

    lein repl
    (reset)
    open http://localhost:8080/

Run tests once or automatically

    lein kaocha
    lein kaocha --watch
