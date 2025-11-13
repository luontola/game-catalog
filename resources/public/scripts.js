htmx.config.transitions = false

// Spreadsheet arrow key navigation
document.addEventListener('keydown', (e) => {
    // Don't intercept if any modifier keys are held down
    if (e.metaKey || e.ctrlKey || e.altKey || e.shiftKey) {
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
        htmx.trigger(row, 'edit')
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
