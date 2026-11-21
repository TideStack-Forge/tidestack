export function shouldEmitNodeCreated({ isHydrating = false } = {}) {
  return !isHydrating
}

export function shouldEmitEdgeCreated({ isHydrating = false, isNew = false } = {}) {
  return !isHydrating && !!isNew
}
