class DraggingProvider {
  dragging = false
  currentPoint = { x: 0, y: 0 }
  dragHandler = null
  constructor() {
    window?.document?.addEventListener?.('mousedown', this.onDragStart)
    window?.document?.addEventListener?.('mousemove', this.onDrag)
    window?.document?.addEventListener?.('mouseup', this.onDragEnd)
    // touch
    window?.document?.addEventListener?.('touchstart', this.onDragStart)
    window?.document?.addEventListener?.('touchmove', this.onDrag)
    window?.document?.addEventListener?.('touchend', this.onDragEnd)
  }

  init = (dom, dragHandler) => {
    this.dom = dom
    this.dragHandler = dragHandler
  }

  destroy = () => {
    window?.document?.removeEventListener?.('mousedown', this.onDragStart)
    window?.document?.removeEventListener?.('mousemove', this.onDrag)
    window?.document?.removeEventListener?.('mouseup', this.onDragEnd)
    // touch
    window?.document?.removeEventListener?.('touchstart', this.onDragStart)
    window?.document?.removeEventListener?.('touchmove', this.onDrag)
    window?.document?.removeEventListener?.('touchend', this.onDragEnd)
  }

  onDragStart = (e) => {
    if (this.dom !== e.target) return
    e.preventDefault()
    this.dragging = true
    this.currentPoint = { x: e.clientX, y: e.clientY }
  }

  onDrag = (e) => {
    if (!this.dragging) {
      return
    }
    const { clientX, clientY } = e
    const deltaX = clientX - this.currentPoint.x
    const deltaY = clientY - this.currentPoint.y
    this.currentPoint = { x: clientX, y: clientY }
    this.dragHandler({ x: deltaX, y: deltaY })
  }

  onDragEnd = () => {
    this.dragging = false
  }
}

export default DraggingProvider
