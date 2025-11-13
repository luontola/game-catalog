htmx.config.transitions = false

// Spreadsheet arrow key navigation
document.addEventListener('keydown', (e) => {
    // Don't intercept if any modifier keys are held down
    if (e.metaKey || e.ctrlKey || e.altKey || e.shiftKey) {
        return
    }

    // Check if we're in an input field within a spreadsheet row (edit mode)
    if (e.target.matches('.spreadsheet input')
        && e.key === 'Enter') {
        // Exit edit mode for this row
        const row = e.target.closest('tr')
        const cell = e.target.closest('td');
        const cellIndex = Array.from(row.children).indexOf(cell)
        const gameId = row.dataset.gameId
        const url = `/games/${gameId}/view?focusIndex=${cellIndex}`
        htmx.ajax('POST', url, {target: row, swap: 'outerHTML'})
        e.preventDefault()
        return
    }

    // Intercept only when a spreadsheet cell has the focus
    const cell = e.target
    if (!cell.matches('.spreadsheet td')) {
        return
    }

    if (e.key === 'Enter') {
        // Enter edit mode for this row
        const row = cell.parentElement
        const cellIndex = Array.from(row.children).indexOf(cell)
        const gameId = row.dataset.gameId
        const url = `/games/${gameId}/edit?focusIndex=${cellIndex}`
        htmx.ajax('POST', url, {target: row, swap: 'outerHTML'})
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
            // Focus has left the row, exit edit mode
            const gameId = row.dataset.gameId
            const url = `/games/${gameId}/view`
            htmx.ajax('POST', url, {target: row, swap: 'outerHTML'})
        }
    }, 0)
})
