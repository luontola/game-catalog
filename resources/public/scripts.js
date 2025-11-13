htmx.config.transitions = false

// Spreadsheet arrow key navigation
document.addEventListener('keydown', (e) => {
    const cell = e.target
    if (!cell.matches('.spreadsheet td')) {
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
