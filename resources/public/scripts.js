htmx.config.transitions = false

function getCellIndex(row, cell) {
    return Array.from(row.children).indexOf(cell);
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

    // Check if we're in an input field within a spreadsheet row (edit mode)
    if (e.target.matches('.spreadsheet input')) {
        if (e.key === 'Enter' || e.key === 'F2') {
            // Exit edit mode for this row and save changes
            const row = e.target.closest('tr')
            const cell = e.target.closest('td')
            saveAndExitEditMode(row, cell)
            e.preventDefault()
            return
        } else if (e.key === 'Escape') {
            // Cancel edit mode for this row without saving
            const row = e.target.closest('tr')
            const cell = e.target.closest('td')
            cancelEditMode(row, cell)
            e.preventDefault()
            return
        }
    }

    // Intercept only when a spreadsheet cell has the focus
    const cell = e.target
    if (!cell.matches('.spreadsheet td')) {
        return
    }

    const row = cell.parentElement

    // Check if we're in a read-only cell in edit mode
    if (row.classList.contains('editing')) {
        if (e.key === 'Enter' || e.key === 'F2') {
            // Exit edit mode for this row and save changes
            saveAndExitEditMode(row, cell)
            e.preventDefault()
            return
        } else if (e.key === 'Escape') {
            // Cancel edit mode for this row without saving
            cancelEditMode(row, cell)
            e.preventDefault()
            return
        }
    }

    if (e.key === 'Enter' || e.key === 'F2') {
        // Enter edit mode for this row
        enterEditMode(row, cell)
        e.preventDefault()
        return
    }

    let targetCell = null
    if (e.key === 'ArrowLeft') {
        targetCell = cell.previousElementSibling
    } else if (e.key === 'ArrowRight') {
        targetCell = cell.nextElementSibling
    } else if (e.key === 'ArrowUp' || e.key === 'ArrowDown') {
        const row = cell.parentElement
        const cellIndex = Array.from(row.children).indexOf(cell)
        const targetRow = e.key === 'ArrowUp' ? row.previousElementSibling : row.nextElementSibling
        if (targetRow) {
            targetCell = targetRow.children[cellIndex]
        }
    }
    if (targetCell) {
        e.preventDefault()
        targetCell.focus()
    }
})

// Avoid multiple autofocus attributes in the DOM after htmx swaps.
// Otherwise, the first autofocus in the DOM always grabs the focus.
document.addEventListener('focusin', e => {
    if (e.target.matches('input') && e.target.hasAttribute('autofocus')) {
        // When autofocus focuses a text input element, the cursor will be
        // in the front of any existing text. We want to select all the text.
        // Then the user can easily replace it or press the right arrow to append text.
        e.target.select()
    }
    e.target.removeAttribute('autofocus')
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
            saveAndExitEditMode(row)
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
