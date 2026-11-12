// A row is modified when its form data differs from when the row got focus.
// Comparing the data instead of tracking input events also notices changes
// made by scripts, and ignores inputs which are not part of the form.
const initialFormData = new WeakMap()

function serializeForm(row) {
    const form = row.querySelector('form')
    return new URLSearchParams(new FormData(form)).toString()
}

document.addEventListener('focusin', (e) => {
    const row = e.target.closest('.spreadsheet tr.editing')
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
    const row = e.target.closest('.spreadsheet tr.editing')
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
    if (row?.matches('.spreadsheet tr')) {
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
    const cell = e.target.closest('.spreadsheet td')
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
    const cell = e.target.closest('.spreadsheet td')
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
    focusCell(addingRow.cells[cellIndex])
})

function isDropdownOpen(element) {
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
    const cell = e.target.closest('.spreadsheet td')
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
    const row = e.target.closest('.spreadsheet tr.editing')
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

// Enter edit mode on double-click
document.addEventListener('dblclick', (e) => {
    const cell = e.target.closest('.spreadsheet td')
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
    if (e.target.closest('.spreadsheet tr.adding')) {
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
        const cell = e.target.closest('.spreadsheet td')
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
