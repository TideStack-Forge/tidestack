export const LOGICFLOW_SURFACE_INSETS = Object.freeze({
  top: 16,
  left: 16,
  right: 16,
  bottom: 16,
})

export const LOGICFLOW_PALETTE_SURFACE_CONTRACT = Object.freeze({
  width: 240,
  topInset: LOGICFLOW_SURFACE_INSETS.top,
  leftInset: LOGICFLOW_SURFACE_INSETS.left,
  bottomInset: LOGICFLOW_SURFACE_INSETS.bottom,
  toolbarGap: 16,
})

export function resolveLogicFlowMiniMapRightOffset({
  collapsed = true,
  panelWidth = 450,
  baseOffset = LOGICFLOW_SURFACE_INSETS.right,
  panelGap = LOGICFLOW_SURFACE_INSETS.right,
} = {}) {
  const normalizedBaseOffset = Number.isFinite(Number(baseOffset))
    ? Number(baseOffset)
    : LOGICFLOW_SURFACE_INSETS.right
  const normalizedPanelGap = Number.isFinite(Number(panelGap))
    ? Number(panelGap)
    : LOGICFLOW_SURFACE_INSETS.right
  const normalizedPanelWidth = Number.isFinite(Number(panelWidth)) ? Number(panelWidth) : 450
  return collapsed
    ? normalizedBaseOffset
    : normalizedPanelWidth + normalizedPanelGap + normalizedBaseOffset
}

export function createLogicFlowToolbarDockingContext({
  paletteAvailable = true,
  paletteVisible = true,
  paletteOccupiedWidth,
  topInset = LOGICFLOW_PALETTE_SURFACE_CONTRACT.topInset,
  leftInset,
} = {}) {
  const normalizedPaletteAvailable = Boolean(paletteAvailable)
  const normalizedPaletteVisible = normalizedPaletteAvailable && Boolean(paletteVisible)
  const normalizedPaletteOccupiedWidth = Math.max(
    0,
    Number.isFinite(Number(paletteOccupiedWidth))
      ? Number(paletteOccupiedWidth)
      : normalizedPaletteVisible
        ? LOGICFLOW_PALETTE_SURFACE_CONTRACT.width
        : 0
  )
  const defaultLeftInset = normalizedPaletteVisible
    ? LOGICFLOW_PALETTE_SURFACE_CONTRACT.leftInset +
      normalizedPaletteOccupiedWidth +
      LOGICFLOW_PALETTE_SURFACE_CONTRACT.toolbarGap
    : LOGICFLOW_PALETTE_SURFACE_CONTRACT.leftInset

  return {
    followsPalette: true,
    paletteAvailable: normalizedPaletteAvailable,
    paletteVisible: normalizedPaletteVisible,
    paletteOccupiedWidth: normalizedPaletteOccupiedWidth,
    topInset: Number.isFinite(Number(topInset))
      ? Number(topInset)
      : LOGICFLOW_PALETTE_SURFACE_CONTRACT.topInset,
    leftInset: Number.isFinite(Number(leftInset)) ? Number(leftInset) : defaultLeftInset,
  }
}

export function resolveLogicFlowToolbarDockingGeometry(dockingContext) {
  const normalizedDockingContext = createLogicFlowToolbarDockingContext(dockingContext)
  return {
    top: normalizedDockingContext.topInset,
    left: normalizedDockingContext.leftInset,
  }
}

export function createLogicFlowNavigatorDockingContext({
  inspectorVisible = false,
  inspectorOccupiedWidth = 0,
  rightInset = LOGICFLOW_SURFACE_INSETS.right,
  bottomInset = LOGICFLOW_SURFACE_INSETS.bottom,
} = {}) {
  const normalizedInspectorVisible = Boolean(inspectorVisible)
  const normalizedInspectorOccupiedWidth = Math.max(
    0,
    Number.isFinite(Number(inspectorOccupiedWidth)) ? Number(inspectorOccupiedWidth) : 0
  )
  const normalizedRightInset = Number.isFinite(Number(rightInset))
    ? Number(rightInset)
    : LOGICFLOW_SURFACE_INSETS.right
  const normalizedBottomInset = Number.isFinite(Number(bottomInset))
    ? Number(bottomInset)
    : LOGICFLOW_SURFACE_INSETS.bottom

  return {
    followsInspector: true,
    inspectorVisible: normalizedInspectorVisible,
    inspectorOccupiedWidth: normalizedInspectorOccupiedWidth,
    rightInset: normalizedInspectorVisible
      ? normalizedInspectorOccupiedWidth + LOGICFLOW_SURFACE_INSETS.right + normalizedRightInset
      : normalizedRightInset,
    bottomInset: normalizedBottomInset,
  }
}
