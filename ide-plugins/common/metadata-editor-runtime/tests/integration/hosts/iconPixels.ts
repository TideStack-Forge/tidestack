/** Render the real symbol and its caller's computed paint through the browser SVG renderer. */
export async function iconPixels(elements: Element[]) {
  const sprite = document.getElementById('__SVG_SPRITE_NODE__')!
  return Promise.all(elements.map(async element => {
    const use = element.matches('use') ? element : element.querySelector('use')!
    const href = use.getAttribute('href') || use.getAttribute('xlink:href') || ''
    const symbol = sprite.querySelector(href)
    if (!symbol) return {href, colors: [], pixels: 0}
    const style = getComputedStyle(element)
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    svg.setAttribute('width', '96')
    svg.setAttribute('height', '96')
    svg.setAttribute('viewBox', '0 0 96 96')
    svg.style.color = style.color
    svg.style.fill = style.fill
    svg.style.stroke = style.stroke
    const defs = document.createElementNS(svg.namespaceURI, 'defs')
    defs.appendChild(symbol.cloneNode(true))
    svg.appendChild(defs)
    const painted = use.cloneNode(true) as SVGElement
    painted.setAttribute('x', '0')
    painted.setAttribute('y', '0')
    painted.setAttribute('width', '96')
    painted.setAttribute('height', '96')
    svg.appendChild(painted)
    const image = new Image()
    image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(svg))
    await image.decode()
    const canvas = document.createElement('canvas')
    canvas.width = canvas.height = 96
    const context = canvas.getContext('2d')!
    context.drawImage(image, 0, 0)
    const data = context.getImageData(0, 0, 96, 96).data
    const colors = new Set<string>()
    let pixels = 0
    for (let i = 0; i < data.length; i += 4) if (data[i + 3] === 255) {
      colors.add(`${data[i]},${data[i + 1]},${data[i + 2]}`)
      pixels++
    }
    return {href, colors: [...colors], pixels}
  }))
}
