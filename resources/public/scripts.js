htmx.config.transitions = false

function getCellIndex(row, cell) {
    return Array.from(row.children).indexOf(cell);
}

// Track when forms are modified
document.addEventListener('input', (e) => {
    if (e.target.matches('.spreadsheet input')) {
        const row = e.target.closest('tr')
        if (row) {
            row.dataset.modified = 'true'
        }
    }
})

function isFormModified(row) {
    return row.dataset.modified === 'true'
}

function getEntityInfo(row) {
    const entityType = row.dataset.entityType
    const entityId = row.dataset.entityId
    return {entityType, entityId}
}

function enterEditMode(row, cell) {
    const {entityType, entityId} = getEntityInfo(row)
    const cellIndex = getCellIndex(row, cell)
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
    const formData = new FormData(form)
    if (cell) {
        const cellIndex = getCellIndex(row, cell)
        formData.append('focusIndex', `${cellIndex}`)
    }

    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/save`, {
        target: row,
        swap: 'outerHTML',
        values: Object.fromEntries(formData)
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
        const cellIndex = getCellIndex(row, cell)
        values.focusIndex = cellIndex
    }
    htmx.ajax('POST', `/spreadsheet/${entityType}/${entityId}/view`, {
        target: row,
        swap: 'outerHTML',
        values: values
    })
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
            cancelEditMode(row, cell)
            e.preventDefault()
            return
        } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
            // Move focus to adjacent row (focusout handler will save/cancel as needed)
            const cellIndex = getCellIndex(row, cell)
            const targetRow = e.key === 'ArrowUp' ? row.previousElementSibling : row.nextElementSibling
            if (targetRow) {
                const targetCell = targetRow.children[cellIndex]
                const input = targetCell.querySelector('input')
                if (input) {
                    input.focus()
                } else {
                    targetCell.focus()
                }
                e.preventDefault()
                return
            }
        }
        // For other keys in edit mode (like arrow left/right in inputs), let browser handle
        return
    }

    // Handle view mode
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
        const cellIndex = getCellIndex(row, cell)
        const targetRow = e.key === 'ArrowUp' ? row.previousElementSibling : row.nextElementSibling
        if (targetRow) {
            targetCell = targetRow.children[cellIndex]
        }
    }

    if (targetCell) {
        e.preventDefault()
        const input = targetCell.querySelector('input')
        if (input) {
            input.focus()
        } else {
            targetCell.focus()
        }
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
        if (node.matches('input')) {
            // When autofocus focuses a text input element, the cursor will be
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

        contextMenu.style.left = `${x}px`
        contextMenu.style.top = `${y}px`
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
        // Don't show context menu for the adding row
        if (row.classList.contains('adding')) {
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
