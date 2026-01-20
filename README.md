# Rapid Application Template (WIP)

Spreadsheet/MS Access/Airtable replacement. Something which is quick to get started like a spreadsheet, but enables an
incremental transition to a custom application when the solution grows to require a general purpose language.

## Setup

After cloning the repository, set up the data directory:

    git worktree add data data

This creates a `data/` directory that contains the CSV data files from the `data` branch.

## Running with Docker

Run the latest published Docker image from GitHub Container Registry:

    docker run -p 8080:8080 ghcr.io/luontola/game-catalog:latest
    open http://localhost:8080/

Or run a [specific version](https://github.com/luontola/game-catalog/pkgs/container/game-catalog) (format: YYYY-MM-DD.BUILD):

    docker run -p 8080:8080 ghcr.io/luontola/game-catalog:2025-11-15.6
    open http://localhost:8080/

Or build and run locally:

    ./build.sh
    docker run -p 8080:8080 game-catalog:latest
    open http://localhost:8080/

## Developing

Start the app

    lein repl
    (reset)
    open http://localhost:8080/

Run tests once or automatically

    lein kaocha
    lein autotest
