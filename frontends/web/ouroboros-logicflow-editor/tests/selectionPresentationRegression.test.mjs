import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

const stylesPath = new URL('../src/logicFlowEditor/styles.scss', import.meta.url)

test('logicflow editor styles keep selected nodes visibly emphasized', () => {
  const source = fs.readFileSync(stylesPath, 'utf8')

  assert.match(source, /\.logicflow-canvas\s*\{[\s\S]*\.x6-node\s*\{[\s\S]*&\.x6-node-selected\s*\{/)
  assert.match(source, /stroke:\s*#1890ff;/)
  assert.match(source, /stroke-width:\s*2;/)
  assert.match(source, /drop-shadow\(1px 1px 4px #999\)/)
})

test('logicflow editor styles anchor the mini-map to the usable canvas bottom-right area', () => {
  const source = fs.readFileSync(stylesPath, 'utf8')

  assert.match(source, /\.logicflow-canvas-container\s*\{[\s\S]*position:\s*relative;/)
  assert.match(
    source,
    /\.mini-map\s*\{[\s\S]*right:\s*var\(--logicflow-mini-map-right,\s*16px\);[\s\S]*bottom:\s*var\(--logicflow-mini-map-bottom,\s*16px\);[\s\S]*transform:\s*none;/
  )
  assert.doesNotMatch(source, /left:\s*calc\(20px \+ 230px \+ 20px\)/)
})

test('logicflow editor styles keep the navigator cluster as a unified minimap-plus-controls surface', () => {
  const source = fs.readFileSync(stylesPath, 'utf8')

  assert.match(source, /\.mini-map\s*\{[\s\S]*&\.logicflow-viewport-cluster--collapsed\s*\{/)
  assert.match(source, /> \.mini-map-preview\s*\{/)
  assert.match(source, /> \.mini-map-controls\s*\{[\s\S]*display:\s*flex;/)
  assert.match(source, /\.mini-map-control-button\s*\{[\s\S]*background:\s*rgba\(255,\s*255,\s*255,\s*0\.98\);/)
  assert.doesNotMatch(source, /mini-map-preview-hidden/)
})
