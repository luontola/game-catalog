// The reference picker has a table inside the spreadsheet's cells,
// so these selectors must not match its rows and cells
const spreadsheetRow = '.spreadsheet > tbody > tr'
const spreadsheetCell = `${spreadsheetRow} > td`

// A row is modified when its form data differs from when the row got focus.
// Comparing the data instead of tracking input events also notices changes
// made by scripts, and ignores inputs which are not part of the form.
const initialFormData = new WeakMap()

function serializeForm(row) {
    const form = row.querySelector('form')
    return new URLSearchParams(new FormData(form)).toString()
}

document.addEventListener('focusin', (e) => {
    const row = e.target.closest(`${spreadsheetRow}.editing`)
    if (row && !initialFormData.has(row)) {
        initialFormData.set(row, serializeForm(row))
    }
})

function isFormModified(row) {
    return initialFormData.has(row) && initialFormData.get(row) !== serializeForm(row)
}

// Highlight modified rows. Scripts which change the form should dispatch
// a change event, but saving the row doesn't depend on it.
function updateModifiedHighlight(e) {
    const row = e.target.closest(`${spreadsheetRow}.editing`)
    if (row) {
        row.dataset.modified = isFormModified(row)
    }
}

document.addEventListener('input', updateModifiedHighlight)
document.addEventListener('change', updateModifiedHighlight)

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

function saveAndExitEditMode(row, cell = null) {
    // This function can be called twice for the same row,
    // by the Enter/F2 keyboard handler and the focus-loss click handler,
    // so we must guard against duplicate form submit attempts.
    if (row.dataset.exiting === 'true') {
        return
    }
    row.dataset.exiting = 'true'

    const {entityType, entityId} = getEntityInfo(row)
    const form = row.querySelector('form')
    const values = {}
    if (cell) {
        values.focusIndex = cell.cellIndex
    }

    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/save`, {
        source: form,
        target: row,
        swap: 'outerHTML',
        values
    })
}

function cancelEditMode(row, cell = null) {
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
        target: row,
        swap: 'outerHTML',
        values: values
    })
}

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

// Cmd-Enter moves focus to the adding row
document.addEventListener('keydown', (e) => {
    if (!(e.key === 'Enter' && (e.metaKey || e.ctrlKey) && !e.altKey && !e.shiftKey)) {
        return
    }
    const cell = e.target.closest(spreadsheetCell)
    const table = cell?.closest('.spreadsheet') ?? document.querySelector('.spreadsheet')
    if (!table) {
        return
    }
    const addingRow = table.querySelector('tr.adding')
    if (!addingRow) {
        return
    }
    e.preventDefault()
    if (addingRow.contains(e.target)) {
        return
    }
    const cellIndex = cell ? cell.cellIndex : 0
    // Focus loss will save or cancel any row which is in edit mode
    focusAddingRow(table, cellIndex)
})

function focusAddingRow(table, cellIndex) {
    const addingRow = table.querySelector('tr.adding')
    if (addingRow.dataset.exiting === 'true') {
        // The adding row was saved or canceled, and it will soon be replaced
        // with a new one. Focusing the old row would lose the focus on swap.
        document.addEventListener('htmx:after:swap', () => focusAddingRow(table, cellIndex), {once: true})
        return
    }
    focusCell(addingRow.cells[cellIndex])
}

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
        if (e.key === 'Enter' || e.key === 'F2') {
            // Exit edit mode and save changes (only if modified)
            if (isFormModified(row)) {
                saveAndExitEditMode(row, cell)
            } else {
                cancelEditMode(row, cell)
            }
            e.preventDefault()
            return
        } else if (e.key === 'Escape') {
            // Cancel edit mode without saving
            const lastRow = row.previousElementSibling
            if (row.classList.contains('adding') && lastRow) {
                // The adding row is always in edit mode,
                // so move focus to the grid to let the user navigate it
                const cellIndex = cell.cellIndex
                cancelEditMode(row)
                focusCell(lastRow.cells[cellIndex])
            } else {
                cancelEditMode(row, cell)
            }
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
        // The adding row is reachable only with Cmd-Enter
        if (targetRow && !targetRow.classList.contains('adding')) {
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
            if (isFormModified(row)) {
                saveAndExitEditMode(row)
            } else {
                cancelEditMode(row)
            }
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

// Scroll to bottom when the adding row receives focus
document.addEventListener('focusin', (e) => {
    if (e.target.closest(`${spreadsheetRow}.adding`)) {
        window.scrollTo({
            top: document.documentElement.scrollHeight,
            behavior: 'instant'
        })
    }
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
            row.addEventListener('htmx:response:error', (e) => {
                // htmx swaps also error responses, which would remove the row
                e.detail.ctx.swap = 'none'
                alert(e.detail.ctx.text)
            }, {once: true})
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
        // Don't show context menu for rows in edit mode (including the adding row)
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
