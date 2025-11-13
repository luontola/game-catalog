# Rapid Application Template (WIP)

Spreadsheet/MS Access/Airtable replacement. Something which is quick to get started like a spreadsheet, but enables an
incremental transition to a custom application when the solution grows to require a general purpose language.

## Developing

Start the app

    lein repl
    (reset)
    open http://localhost:8080/

Run tests once or automatically

    lein kaocha
    lein kaocha --watch
