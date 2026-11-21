const TRANSFORM_PATTERN = /([a-zA-Z]+)\(([^)]*)\)/g
const SVG_TRANSFORM_MATRIX = 1
const SVG_TRANSFORM_TRANSLATE = 2
const SVG_TRANSFORM_SCALE = 3
const SVG_TRANSFORM_ROTATE = 4
const SVG_TRANSFORM_SKEWX = 5
const SVG_TRANSFORM_SKEWY = 6

function parseNumbers(value = '') {
  return value
    .split(/[\s,]+/)
    .map((item) => Number(item))
    .filter((item) => Number.isFinite(item))
}

function matrixToTransformString(matrix) {
  return `matrix(${matrix.a},${matrix.b},${matrix.c},${matrix.d},${matrix.e},${matrix.f})`
}

function readNumericAttribute(element, name, fallback = 0) {
  const rawValue = element.getAttribute?.(name)
  if (rawValue == null || rawValue === '') {
    return fallback
  }
  const value = Number(rawValue)
  return Number.isFinite(value) ? value : fallback
}

function unionBounds(boxes) {
  const validBoxes = boxes.filter(Boolean)
  if (!validBoxes.length) {
    return { x: 0, y: 0, width: 0, height: 0 }
  }

  const minX = Math.min(...validBoxes.map((box) => box.x))
  const minY = Math.min(...validBoxes.map((box) => box.y))
  const maxX = Math.max(...validBoxes.map((box) => box.x + box.width))
  const maxY = Math.max(...validBoxes.map((box) => box.y + box.height))

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  }
}

function boundsFromPoints(points) {
  if (!points.length) {
    return { x: 0, y: 0, width: 0, height: 0 }
  }

  const xs = points.map((point) => point.x)
  const ys = points.map((point) => point.y)
  const minX = Math.min(...xs)
  const minY = Math.min(...ys)
  const maxX = Math.max(...xs)
  const maxY = Math.max(...ys)

  return {
    x: minX,
    y: minY,
    width: maxX - minX,
    height: maxY - minY,
  }
}

function parsePointsAttribute(value = '') {
  const numbers = parseNumbers(value)
  const points = []
  for (let index = 0; index < numbers.length; index += 2) {
    points.push({
      x: numbers[index] ?? 0,
      y: numbers[index + 1] ?? 0,
    })
  }
  return points
}

class TestDOMMatrix {
  constructor(init = {}) {
    this.a = 1
    this.b = 0
    this.c = 0
    this.d = 1
    this.e = 0
    this.f = 0

    Object.assign(this, init || {})
  }

  multiply(other) {
    const matrix = ensureMatrix(other)
    return new TestDOMMatrix({
      a: this.a * matrix.a + this.c * matrix.b,
      b: this.b * matrix.a + this.d * matrix.b,
      c: this.a * matrix.c + this.c * matrix.d,
      d: this.b * matrix.c + this.d * matrix.d,
      e: this.a * matrix.e + this.c * matrix.f + this.e,
      f: this.b * matrix.e + this.d * matrix.f + this.f,
    })
  }

  inverse() {
    const determinant = this.a * this.d - this.b * this.c
    if (!determinant) {
      return new TestDOMMatrix()
    }

    return new TestDOMMatrix({
      a: this.d / determinant,
      b: -this.b / determinant,
      c: -this.c / determinant,
      d: this.a / determinant,
      e: (this.c * this.f - this.d * this.e) / determinant,
      f: (this.b * this.e - this.a * this.f) / determinant,
    })
  }

  translate(tx = 0, ty = 0) {
    return this.multiply(new TestDOMMatrix({ e: tx, f: ty }))
  }

  scale(scaleX = 1, scaleY = scaleX) {
    return this.scaleNonUniform(scaleX, scaleY)
  }

  scaleNonUniform(scaleX = 1, scaleY = 1) {
    return this.multiply(
      new TestDOMMatrix({
        a: scaleX,
        d: scaleY,
      })
    )
  }

  rotate(angle = 0) {
    const radians = (angle * Math.PI) / 180
    const cos = Math.cos(radians)
    const sin = Math.sin(radians)
    return this.multiply(
      new TestDOMMatrix({
        a: cos,
        b: sin,
        c: -sin,
        d: cos,
      })
    )
  }

  skewX(angle = 0) {
    const radians = (angle * Math.PI) / 180
    return this.multiply(
      new TestDOMMatrix({
        c: Math.tan(radians),
      })
    )
  }

  skewY(angle = 0) {
    const radians = (angle * Math.PI) / 180
    return this.multiply(
      new TestDOMMatrix({
        b: Math.tan(radians),
      })
    )
  }
}

class TestDOMPoint {
  constructor(x = 0, y = 0) {
    this.x = x
    this.y = y
  }

  matrixTransform(matrix) {
    const nextMatrix = ensureMatrix(matrix)
    return new TestDOMPoint(
      this.x * nextMatrix.a + this.y * nextMatrix.c + nextMatrix.e,
      this.x * nextMatrix.b + this.y * nextMatrix.d + nextMatrix.f
    )
  }
}

class TestSVGTransform {
  constructor(matrix = new TestDOMMatrix()) {
    this.type = SVG_TRANSFORM_MATRIX
    this.angle = 0
    this.matrix = ensureMatrix(matrix)
  }

  setMatrix(matrix) {
    this.type = SVG_TRANSFORM_MATRIX
    this.matrix = ensureMatrix(matrix)
  }

  setTranslate(x, y) {
    this.type = SVG_TRANSFORM_TRANSLATE
    this.matrix = new TestDOMMatrix().translate(x, y)
  }

  setScale(scaleX, scaleY) {
    this.type = SVG_TRANSFORM_SCALE
    this.matrix = new TestDOMMatrix().scaleNonUniform(scaleX, scaleY)
  }

  setRotate(angle) {
    this.type = SVG_TRANSFORM_ROTATE
    this.angle = angle
    this.matrix = new TestDOMMatrix().rotate(angle)
  }

  setSkewX(angle) {
    this.type = SVG_TRANSFORM_SKEWX
    this.angle = angle
    this.matrix = new TestDOMMatrix().skewX(angle)
  }

  setSkewY(angle) {
    this.type = SVG_TRANSFORM_SKEWY
    this.angle = angle
    this.matrix = new TestDOMMatrix().skewY(angle)
  }
}

function ensureMatrix(value) {
  return value instanceof TestDOMMatrix ? value : new TestDOMMatrix(value)
}

function createMatrixFromTransformAttribute(value) {
  let matrix = new TestDOMMatrix()
  if (!value) {
    return matrix
  }

  for (const match of value.matchAll(TRANSFORM_PATTERN)) {
    const [, type, rawArgs] = match
    const args = parseNumbers(rawArgs)
    switch (type) {
      case 'matrix':
        matrix = matrix.multiply(
          new TestDOMMatrix({
            a: args[0] ?? 1,
            b: args[1] ?? 0,
            c: args[2] ?? 0,
            d: args[3] ?? 1,
            e: args[4] ?? 0,
            f: args[5] ?? 0,
          })
        )
        break
      case 'translate':
        matrix = matrix.translate(args[0] ?? 0, args[1] ?? 0)
        break
      case 'scale':
        matrix = matrix.scale(args[0] ?? 1, args[1] ?? args[0] ?? 1)
        break
      case 'rotate':
        matrix = matrix.rotate(args[0] ?? 0)
        break
      case 'skewX':
        matrix = matrix.skewX(args[0] ?? 0)
        break
      case 'skewY':
        matrix = matrix.skewY(args[0] ?? 0)
        break
      default:
        break
    }
  }

  return matrix
}

function computeLocalBBox(element) {
  const tagName = element.tagName?.toLowerCase?.() || ''

  if (tagName === 'rect' || tagName === 'image' || tagName === 'foreignobject') {
    return {
      x: readNumericAttribute(element, 'x', 0),
      y: readNumericAttribute(element, 'y', 0),
      width: readNumericAttribute(element, 'width', element.clientWidth || 0),
      height: readNumericAttribute(element, 'height', element.clientHeight || 0),
    }
  }

  if (tagName === 'circle') {
    const radius = readNumericAttribute(element, 'r', 0)
    const cx = readNumericAttribute(element, 'cx', 0)
    const cy = readNumericAttribute(element, 'cy', 0)
    return {
      x: cx - radius,
      y: cy - radius,
      width: radius * 2,
      height: radius * 2,
    }
  }

  if (tagName === 'ellipse') {
    const rx = readNumericAttribute(element, 'rx', 0)
    const ry = readNumericAttribute(element, 'ry', 0)
    const cx = readNumericAttribute(element, 'cx', 0)
    const cy = readNumericAttribute(element, 'cy', 0)
    return {
      x: cx - rx,
      y: cy - ry,
      width: rx * 2,
      height: ry * 2,
    }
  }

  if (tagName === 'line') {
    const x1 = readNumericAttribute(element, 'x1', 0)
    const y1 = readNumericAttribute(element, 'y1', 0)
    const x2 = readNumericAttribute(element, 'x2', 0)
    const y2 = readNumericAttribute(element, 'y2', 0)
    return boundsFromPoints([
      { x: x1, y: y1 },
      { x: x2, y: y2 },
    ])
  }

  if (tagName === 'polygon' || tagName === 'polyline') {
    return boundsFromPoints(parsePointsAttribute(element.getAttribute('points') || ''))
  }

  if (tagName === 'text' || tagName === 'tspan') {
    const text = element.textContent || ''
    return {
      x: readNumericAttribute(element, 'x', 0),
      y: readNumericAttribute(element, 'y', 0),
      width: text.length * 8,
      height: 16,
    }
  }

  const children = Array.from(element.children || []).filter(
    (child) => child instanceof SVGElement
  )
  if (children.length) {
    return unionBounds(children.map((child) => computeLocalBBox(child)))
  }

  return {
    x: 0,
    y: 0,
    width:
      readNumericAttribute(element, 'width', 0) ||
      element.clientWidth ||
      element.getBoundingClientRect?.().width ||
      0,
    height:
      readNumericAttribute(element, 'height', 0) ||
      element.clientHeight ||
      element.getBoundingClientRect?.().height ||
      0,
  }
}

function installTransformListPolyfill(SVGElementCtor) {
  const transformLists = new WeakMap()

  function syncTransformAttribute(element, items) {
    const matrix = items.reduce(
      (current, item) => current.multiply(item.matrix),
      new TestDOMMatrix()
    )
    element.setAttribute('transform', matrixToTransformString(matrix))
  }

  Object.defineProperty(SVGElementCtor.prototype, 'transform', {
    configurable: true,
    get() {
      if (!transformLists.has(this)) {
        const items = []
        transformLists.set(this, {
          baseVal: {
            appendItem(item) {
              const transform =
                item instanceof TestSVGTransform
                  ? item
                  : new TestSVGTransform(item?.matrix || item)
              items.push(transform)
              syncTransformAttribute(this.ownerElement || this.targetElement || this, items)
              return transform
            },
            clear() {
              items.length = 0
              syncTransformAttribute(this.ownerElement || this.targetElement || this, items)
            },
            consolidate() {
              const transform = new TestSVGTransform(
                items.reduce(
                  (current, item) => current.multiply(item.matrix),
                  new TestDOMMatrix()
                )
              )
              items.length = 0
              items.push(transform)
              syncTransformAttribute(this.ownerElement || this.targetElement || this, items)
              return transform
            },
            get numberOfItems() {
              return items.length
            },
            getItem(index) {
              return items[index] || null
            },
          },
        })

        const transformList = transformLists.get(this)
        transformList.baseVal.ownerElement = this
      }
      return transformLists.get(this)
    },
  })
}

export function ensureSvgGeometryPolyfills() {
  const view = globalThis.window
  if (!view || typeof view.SVGElement === 'undefined') {
    return
  }

   if (typeof globalThis.HTMLElement === 'undefined' && typeof view.HTMLElement !== 'undefined') {
    globalThis.HTMLElement = view.HTMLElement
  }
  if (typeof globalThis.SVGElement === 'undefined' && typeof view.SVGElement !== 'undefined') {
    globalThis.SVGElement = view.SVGElement
  }

  if (typeof globalThis.DOMMatrix === 'undefined') {
    globalThis.DOMMatrix = TestDOMMatrix
  }
  if (typeof view.DOMMatrix === 'undefined') {
    view.DOMMatrix = globalThis.DOMMatrix
  }

  if (typeof globalThis.DOMPoint === 'undefined') {
    globalThis.DOMPoint = TestDOMPoint
  }
  if (typeof view.DOMPoint === 'undefined') {
    view.DOMPoint = globalThis.DOMPoint
  }

  if (typeof globalThis.SVGTransform === 'undefined') {
    globalThis.SVGTransform = TestSVGTransform
  }
  if (typeof view.SVGTransform === 'undefined') {
    view.SVGTransform = globalThis.SVGTransform
  }

  const { SVGElement: SVGElementCtor, SVGSVGElement: SVGSVGElementCtor } = view

  if (!Object.getOwnPropertyDescriptor(SVGElementCtor.prototype, 'transform')) {
    installTransformListPolyfill(SVGElementCtor)
  }

  if (typeof SVGElementCtor.prototype.getCTM !== 'function') {
    SVGElementCtor.prototype.getCTM = function getCTM() {
      let matrix = new TestDOMMatrix()
      let current = this
      while (current && current instanceof view.SVGElement) {
        matrix = createMatrixFromTransformAttribute(current.getAttribute('transform')).multiply(
          matrix
        )
        current = current.parentNode
      }
      return matrix
    }
  }

  if (typeof SVGElementCtor.prototype.getScreenCTM !== 'function') {
    SVGElementCtor.prototype.getScreenCTM = function getScreenCTM() {
      return this.getCTM()
    }
  }

  if (typeof SVGElementCtor.prototype.getBBox !== 'function') {
    SVGElementCtor.prototype.getBBox = function getBBox() {
      return computeLocalBBox(this)
    }
  }

  if (typeof SVGSVGElementCtor.prototype.createSVGMatrix !== 'function') {
    SVGSVGElementCtor.prototype.createSVGMatrix = function createSVGMatrix() {
      return new TestDOMMatrix()
    }
  }

  if (typeof SVGSVGElementCtor.prototype.createSVGPoint !== 'function') {
    SVGSVGElementCtor.prototype.createSVGPoint = function createSVGPoint() {
      return new TestDOMPoint()
    }
  }

  if (typeof SVGSVGElementCtor.prototype.createSVGTransform !== 'function') {
    SVGSVGElementCtor.prototype.createSVGTransform = function createSVGTransform() {
      return new TestSVGTransform()
    }
  }

  if (typeof SVGSVGElementCtor.prototype.createSVGTransformFromMatrix !== 'function') {
    SVGSVGElementCtor.prototype.createSVGTransformFromMatrix =
      function createSVGTransformFromMatrix(matrix) {
        return new TestSVGTransform(matrix)
      }
  }
}
