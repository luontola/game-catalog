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
    }
    if (targetCell) {
        e.preventDefault()
        targetCell.focus()
    }
})
