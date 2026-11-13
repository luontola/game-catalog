// The reference picker has a table inside the spreadsheet's cells,
// so these selectors must not match its rows and cells
const spreadsheetRow = '.spreadsheet > tbody > tr'
const spreadsheetCell = `${spreadsheetRow} > td`

function getEntityInfo(row) {
    const entityType = row.dataset.entityType
    const entityId = row.dataset.entityId
    return {entityType, entityId}
}

function enterEditMode(row, cell) {
    const {entityType, entityId} = getEntityInfo(row)
    const cellIndex = cell.cellIndex
    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/edit`, {
        target: row,
        swap: 'outerHTML',
        values: {focusIndex: cellIndex}
    })
}

// Every request from a row's form sends all the fields of the row.
// The form's hx-sync attribute sends them one at a time, in order,
// so that an old save cannot overwrite a newer one.

function saveRow(row) {
    const {entityType, entityId} = getEntityInfo(row)
    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/save`, {
        source: row.querySelector('form'),
        swap: 'none'
    })
}

function saveAndExitEditMode(row, cell = null) {
    // This function can be called twice for the same row,
    // by the keyboard handler and the focus-loss handler,
    // so we must guard against duplicate form submit attempts.
    if (row.dataset.exiting === 'true') {
        return
    }
    row.dataset.exiting = 'true'

    const {entityType, entityId} = getEntityInfo(row)
    const values = {}
    if (cell) {
        values.focusIndex = cell.cellIndex
    }
    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/view`, {
        source: row.querySelector('form'),
        target: row,
        swap: 'outerHTML',
        values
    })
}

// Autosave: a row in edit mode is saved every time one of its fields changes,
// and the row stays in edit mode. Text fields fire the change event when they
// lose focus. Scripts which change the form must dispatch a change event.
document.addEventListener('change', (e) => {
    const row = e.target.closest(`${spreadsheetRow}.editing`)
    if (!row || e.target.closest('.reference-picker')) {
        // The reference picker's search field is not part of the row's form
        return
    }
    // Wait for the focus to move, to know whether the focus is leaving the row
    setTimeout(() => {
        if (row.dataset.exiting === 'true' || !row.contains(document.activeElement)) {
            // Exiting edit mode saves the row
            return
        }
        saveRow(row)
    }, 0)
})

// Failed requests show an error message in the status bar. When the request
// was from a row's form, the row is marked as not saved and it stays in edit
// mode, so that the user's changes are not lost and saving can be retried.

const statusBar = document.querySelector('.status-bar')
const rowWithSaveError = `${spreadsheetRow}[data-save-error]`

function showError(message) {
    if (!statusBar) {
        alert(message)
        return
    }
    statusBar.querySelector('.status-bar-message').textContent = message
    statusBar.hidden = false
}

function hideError() {
    statusBar.hidden = true
    statusBar.querySelector('.status-bar-message').textContent = ''
}

statusBar?.querySelector('.status-bar-close').addEventListener('click', hideError)

// The row whose form made the request, if any
function requestingRow(e) {
    return e.target.closest?.(`${spreadsheetRow}.editing form`)?.closest('tr')
}

function requestFailed(e, message) {
    const row = requestingRow(e)
    if (row) {
        row.dataset.saveError = 'true'
        // Exiting edit mode failed, so let the user try it again
        delete row.dataset.exiting
    }
    showError(message)
}

document.addEventListener('htmx:response:error', (e) => {
    const ctx = e.detail.ctx
    // htmx swaps also error responses, which would for example replace the row with the error message
    ctx.swap = 'none'
    // Our backend's error messages are plain text. Other errors could be anything, for example a stack trace.
    const contentType = ctx.response.headers.get('content-type') ?? ''
    const message = contentType.startsWith('text/plain') && ctx.text.trim() !== ''
        ? ctx.text
        : `Request failed (status ${ctx.response.status})`
    requestFailed(e, message)
})

document.addEventListener('htmx:error', (e) => {
    const {ctx, error} = e.detail
    if (!ctx) {
        // Not about a request
        return
    }
    const message = ctx.response
        ? `Request failed (${error})`
        : 'Could not reach the server'
    requestFailed(e, requestingRow(e) ? `${message}. The row is not saved.` : message)
})

// A successful request from a row's form means that the row is now saved
document.addEventListener('htmx:after:request', (e) => {
    const row = requestingRow(e)
    if (row?.dataset.saveError && e.detail.ctx.response.status < 400) {
        delete row.dataset.saveError
        if (!document.querySelector(rowWithSaveError)) {
            hideError()
        }
    }
})

function focusCell(cell) {
    // In edit mode, editors mark their main form element with the class.
    // In view mode and for read-only cells, the cell itself is focusable.
    (cell.querySelector('.editor-entry-point') ?? cell).focus()
}

// The focused cell is stored in the history entry: the URL hash has the row's
// entity ID and the history state has the column index. Reloading the page or
// going back in history restores the focus to the same cell. Links to a row
// (e.g. /games#<entity-id>) create a new history entry without state,
// so they focus the row's first cell.

const cellIndexKey = 'spreadsheetCellIndex'

function focusCellFromHistory() {
    if (!location.hash) {
        return
    }
    // Entity IDs are not always valid CSS selectors, so avoid querySelector
    const row = document.getElementById(decodeURIComponent(location.hash.slice(1)))
    if (row?.matches(spreadsheetRow)) {
        const cellIndex = history.state?.[cellIndexKey] ?? 0
        focusCell(row.cells[cellIndex] ?? row.cells[0])
    }
}

window.addEventListener('load', focusCellFromHistory)
window.addEventListener('hashchange', () => {
    // The pending focus belongs to the previous history entry
    cancelSaveFocusToHistory()
    focusCellFromHistory()
})

// Browsers limit how often the history can be modified (e.g. Safari throws
// after 100 calls in 10 seconds), so holding down an arrow key must not
// modify the history on every focus change.
let pendingFocus = null
let pendingFocusTimeout = null

function saveFocusToHistory() {
    const focus = pendingFocus
    cancelSaveFocusToHistory()
    if (!focus) {
        return
    }
    const url = location.pathname + location.search + focus.hash
    if (location.hash !== focus.hash || history.state?.[cellIndexKey] !== focus.cellIndex) {
        history.replaceState({...history.state, [cellIndexKey]: focus.cellIndex}, '', url)
    }
}

function cancelSaveFocusToHistory() {
    clearTimeout(pendingFocusTimeout)
    pendingFocusTimeout = null
    pendingFocus = null
}

document.addEventListener('focusin', (e) => {
    const cell = e.target.closest(spreadsheetCell)
    if (!cell) {
        return
    }
    const row = cell.closest('tr')
    pendingFocus = {
        hash: row.id ? '#' + encodeURIComponent(row.id) : '',
        cellIndex: cell.cellIndex
    }
    clearTimeout(pendingFocusTimeout)
    pendingFocusTimeout = setTimeout(saveFocusToHistory, 200)
})

// Save the focus before following a link, so that it's saved to the history
// entry which we are leaving, instead of being lost or saved to the new entry.
// Saving on beforeunload or pagehide would be too late for a reload,
// because the browser has already captured the history entry to reload.
document.addEventListener('click', (e) => {
    if (e.target.closest('a[href]')) {
        saveFocusToHistory()
    }
}, {capture: true})

// Cmd-Enter adds a new row, the same as clicking the add row button
document.addEventListener('keydown', (e) => {
    if (!(e.key === 'Enter' && (e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey)) {
        return
    }
    const button = document.querySelector('.add-row-button')
    if (!button || isDropdownOpen(e.target)) {
        return
    }
    e.preventDefault()
    if (e.repeat) {
        // Holding down the keys must not fill the table with blank rows
        return
    }
    // The new row gets the focus in the same column as the focused cell.
    // Focus loss will save any row which is in edit mode.
    const cell = e.target.closest(spreadsheetCell)
    htmx.ajax('POST', button.getAttribute('hx-post'), {
        source: button,
        values: cell ? {focusIndex: cell.cellIndex} : {}
    })
})

function isDropdownOpen(element) {
    if (element.closest('dialog[open]')) {
        return true
    }
    // When a multiselect dropdown is open, the focus is on one of its options
    try {
        return element.closest(':open') !== null
    } catch {
        return false // the browser doesn't support :open
    }
}

// Spreadsheet arrow key navigation
document.addEventListener('keydown', (e) => {
    // Don't intercept if any modifier keys are held down
    if (e.metaKey || e.ctrlKey || e.altKey || e.shiftKey) {
        return
    }

    // Determine if we're in a spreadsheet context
    const cell = e.target.closest(spreadsheetCell)
    if (!cell) {
        return
    }
    const row = cell.closest('tr')
    const inEditMode = row.classList.contains('editing')

    // Handle edit mode (both inputs and read-only cells)
    if (inEditMode) {
        if (isDropdownOpen(e.target)) {
            // Let the dropdown handle the keys, e.g. Escape only closes the dropdown
            return
        }
        if (e.key === 'Enter' && e.target.matches('button')) {
            // Let the browser click the focused button
            return
        }
        if (e.key === 'Enter' || e.key === 'F2' || e.key === 'Escape') {
            saveAndExitEditMode(row, cell)
            e.preventDefault()
            return
        }
        // For other keys in edit mode (like arrow keys in inputs), let browser handle
        return
    }

    // Handle view mode
    if (e.key === 'Enter' && e.target !== cell && e.target.matches('a[href]')) {
        // Let the browser follow the focused link
        return
    }
    if (e.key === 'Enter' || e.key === 'F2') {
        enterEditMode(row, cell)
        e.preventDefault()
        return
    }

    // Handle arrow key navigation
    let targetCell = null
    if (e.key === 'ArrowLeft') {
        targetCell = cell.previousElementSibling
    } else if (e.key === 'ArrowRight') {
        targetCell = cell.nextElementSibling
    } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
        const cellIndex = cell.cellIndex
        const targetRow = e.key === 'ArrowUp' ? row.previousElementSibling : row.nextElementSibling
        if (targetRow) {
            targetCell = targetRow.cells[cellIndex]
        }
    }

    if (targetCell) {
        e.preventDefault()
        focusCell(targetCell)
    }
})

// Exit edit mode when focus leaves the row
document.addEventListener('focusout', (e) => {
    const row = e.target.closest(`${spreadsheetRow}.editing`)
    if (!row) {
        return
    }
    // Check if the new focus target is outside the row
    setTimeout(() => {
        const newFocus = document.activeElement
        if (!row.contains(newFocus)) {
            saveAndExitEditMode(row)
        }
    }, 0)
})

// Clicking a button in some browsers (e.g. Safari) doesn't focus it,
// so the focus would leave the row, which would exit edit mode.
document.addEventListener('mousedown', (e) => {
    if (e.target.closest(`${spreadsheetRow}.editing button`)) {
        e.preventDefault()
    }
    // Clicking the picker's non-focusable content would move the focus out of the row
    if (e.target.closest('.reference-picker') && !e.target.matches('input')) {
        e.preventDefault()
    }
    // Closing the error message must not exit edit mode
    if (e.target.closest('.status-bar-close')) {
        e.preventDefault()
    }
})

// Remove a pill by clicking its remove button
document.addEventListener('click', (e) => {
    const button = e.target.closest('.spreadsheet .pill-remove')
    if (!button) {
        return
    }
    const pill = button.closest('.pill')
    const cell = button.closest('td')
    // Move the focus before removing the focused button,
    // so that the focus doesn't leave the row, which would exit edit mode
    const nextFocus = pill.nextElementSibling?.querySelector('.pill-remove')
        ?? cell.querySelector('.editor-entry-point')
    nextFocus.focus()
    pill.remove()
    cell.dispatchEvent(new Event('change', {bubbles: true}))
})

// Reference picker: a dialog for searching the target collection

function openReferencePicker(editor) {
    const dialog = editor.querySelector('.reference-picker')
    const search = dialog.querySelector('.reference-search')
    search.value = ''
    // Closing the dialog returns the focus to the element which had it before opening.
    // Clicking the button doesn't focus it, so focus it explicitly.
    editor.querySelector('.pill-button').focus()
    dialog.showModal()
    search.focus()
    search.dispatchEvent(new Event('reference-picker-open'))
}

const pickerOption = '.reference-results tbody tr, .reference-results .reference-create'

function pickerOptions(dialog) {
    return Array.from(dialog.querySelectorAll(pickerOption))
}

function highlightPickerOption(dialog, option) {
    for (const o of pickerOptions(dialog)) {
        o.classList.toggle('highlighted', o === option)
    }
    option?.scrollIntoView({block: 'nearest'})
}

function highlightedPickerOption(dialog) {
    return dialog.querySelector('.reference-results .highlighted')
}

async function pickReference(dialog, option) {
    const editor = dialog.closest('.reference-editor')
    const button = editor.querySelector('.pill-button')
    dialog.close()
    if (editor.dataset.multiple === 'false') {
        editor.querySelectorAll(':scope > .pill').forEach(p => p.remove())
    }
    if (option.matches('.reference-create')) {
        // The server creates the entity and returns its pill
        await htmx.ajax('POST', option.dataset.url, {
            source: option,
            target: button,
            swap: 'beforebegin'
        })
    } else {
        button.before(option.querySelector('template').content.cloneNode(true))
    }
    editor.dispatchEvent(new Event('change', {bubbles: true}))
}

document.addEventListener('click', (e) => {
    const button = e.target.closest('.reference-editor > .pill-button')
    if (button) {
        openReferencePicker(button.closest('.reference-editor'))
        return
    }
    const dialog = e.target.closest('.reference-picker')
    if (!dialog) {
        return
    }
    if (e.target === dialog || e.target.closest('.reference-picker-close')) {
        // The dialog has no padding, so only clicks on the backdrop target it
        dialog.close()
        return
    }
    const option = e.target.closest(pickerOption)
    if (option) {
        pickReference(dialog, option)
    }
})

document.addEventListener('keydown', (e) => {
    const dialog = e.target.closest('.reference-picker')
    if (!dialog) {
        return
    }
    const options = pickerOptions(dialog)
    const index = options.indexOf(highlightedPickerOption(dialog))
    if (e.key === 'ArrowDown') {
        highlightPickerOption(dialog, options[Math.min(index + 1, options.length - 1)])
        e.preventDefault()
    } else if (e.key === 'ArrowUp') {
        highlightPickerOption(dialog, options[Math.max(index - 1, 0)])
        e.preventDefault()
    } else if (e.key === 'Enter') {
        const option = highlightedPickerOption(dialog)
        if (option) {
            pickReference(dialog, option)
        }
        e.preventDefault()
    }
})

// Highlight the first search result
document.addEventListener('htmx:after:swap', (e) => {
    const dialog = e.target.closest?.('.reference-picker')
    if (dialog) {
        highlightPickerOption(dialog, pickerOptions(dialog)[0])
    }
})

// Don't leave old search results in the DOM.
// The close event doesn't bubble, so it must be captured.
document.addEventListener('close', (e) => {
    if (e.target.matches?.('.reference-picker')) {
        e.target.querySelector('.reference-results').replaceChildren()
    }
}, {capture: true})

// Enter edit mode on double-click
document.addEventListener('dblclick', (e) => {
    const cell = e.target.closest(spreadsheetCell)
    if (!cell) {
        return
    }
    const row = cell.closest('tr')
    if (row.classList.contains('editing')) {
        // Already in edit mode
        return
    }
    enterEditMode(row, cell)
})

// Avoid multiple autofocus attributes in the DOM after htmx swaps.
// Otherwise, the first autofocus in the DOM always grabs the focus.
document.addEventListener('focusin', e => {
    const node = e.target;
    if (node.hasAttribute('autofocus')) {
        if (typeof node.select === 'function') {
            // When autofocus focuses an element with selectable text, the cursor will be
            // in the front of any existing text. We want to select all the text.
            // Then the user can easily replace it or press the right arrow to append text.
            node.select()
        }
        node.removeAttribute('autofocus')
    }
})

// Watch for elements with auto-scroll-into-view and scroll them into view
const autoScrollObserver = new MutationObserver((mutations) => {
    for (const mutation of mutations) {
        for (const node of mutation.addedNodes) {
            if (node.nodeType === Node.ELEMENT_NODE) {
                const attr = 'auto-scroll-into-view';
                if (node.hasAttribute(attr)) {
                    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                    node.scrollIntoView({behavior: prefersReducedMotion ? 'instant' : 'smooth', block: 'nearest'})
                    node.removeAttribute(attr)
                }
            }
        }
    }
})
autoScrollObserver.observe(document.body, {
    childList: true,
    subtree: true
})

// Context menu for spreadsheet cells
const contextMenu = document.getElementById('context-menu')
if (contextMenu) {

    function openContextMenu(x, y, row) {
        const {entityType, entityId} = getEntityInfo(row)

        const deleteButton = contextMenu.querySelector('#context-menu-delete')
        deleteButton.clickHandler = () => {
            htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/delete`, {
                target: row,
                swap: 'delete'
            })
            closeContextMenu()
        }

        contextMenu.style.left = `${x + window.scrollX}px`
        contextMenu.style.top = `${y + window.scrollY}px`
        contextMenu.classList.add('visible')
    }

    function closeContextMenu() {
        contextMenu.classList.remove('visible')
    }

    // Open context menu by right-clicking a table cell
    document.addEventListener('contextmenu', (e) => {
        const cell = e.target.closest(spreadsheetCell)
        if (!cell) {
            return
        }
        const row = cell.closest('tr')
        // Don't show context menu for rows in edit mode
        if (row.classList.contains('editing')) {
            return
        }
        e.preventDefault()
        openContextMenu(e.clientX, e.clientY, row)
    })

    // Close context menu by clicking outside
    document.addEventListener('click', (e) => {
        if (!contextMenu.contains(e.target)) {
            closeContextMenu()
        }
    })

    // Close context menu by pressing Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            closeContextMenu()
        }
    })
}
