export function createMountMicroFrontendHarness() {
  const cleanups = []
  const container = document.createElement('div')
  document.body.appendChild(container)

  function addCleanup(fn) {
    cleanups.push(fn)
  }

  async function flush() {
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }

  async function destroy() {
    while (cleanups.length) {
      const cleanup = cleanups.pop()
      await cleanup?.()
    }
    container.remove()
  }

  return {
    container,
    addCleanup,
    flush,
    destroy,
  }
}
