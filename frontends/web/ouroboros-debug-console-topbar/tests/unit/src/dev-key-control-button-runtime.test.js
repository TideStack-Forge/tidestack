import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'

import { transformSync } from '@babel/core'
import { expect, test, vi } from 'vitest'

const packageRoot = path.resolve(__dirname, '..', '..', '..')
const repoRoot = path.resolve(packageRoot, '..', '..', '..')

function loadDevKeyControlButton({ renderAmisSchema }) {
  const componentPath = path.join(packageRoot, 'src/components/DevKeyControlButton.jsx')
  const source = fs.readFileSync(componentPath, 'utf8')
  const refs = []

  const { code } = transformSync(source, {
    filename: componentPath,
    babelrc: false,
    configFile: false,
    presets: [
      ['@babel/preset-env', { modules: 'commonjs', targets: { node: 'current' } }],
      '@babel/preset-react',
    ],
  })

  const module = { exports: {} }
  const context = {
    module,
    exports: module.exports,
    require(specifier) {
      if (specifier === 'react') {
        return {
          Component: class {
            constructor(props) {
              this.props = props
            }
          },
          createElement(type, props) {
            return { type, props }
          },
          createRef() {
            const ref = { current: null }
            refs.push(ref)
            return ref
          },
        }
      }

      if (specifier === 'ouroboros-sdk') {
        return { renderAmisSchema }
      }

      throw new Error(`Unexpected dependency in dev key button runtime test: ${specifier}`)
    },
  }

  vm.runInNewContext(code, context, { filename: componentPath })
  return {
    Component: module.exports.default,
    refs,
  }
}

test('dev key topbar button renders the shared schema with the route app name', async () => {
  const renderAmisSchema = vi.fn(() => Promise.resolve({ destroy: vi.fn() }))
  const { Component, refs } = loadDevKeyControlButton({ renderAmisSchema })
  const component = new Component({ appName: 'crm' })
  const host = {}
  refs[0].current = host

  await component.renderContent()

  expect(renderAmisSchema).toHaveBeenCalledWith(
    host,
    {
      type: 'amis-schema',
      schemaId: '/dev/dev-key-control-button',
    },
    {
      data: {
        appName: 'crm',
      },
    }
  )
})

test('dev key topbar schema opens the existing management dialog for the current app', () => {
  const schemaPath = path.join(
    repoRoot,
    'dev-module/ouroboros-dev-app-management/src/main/metadata/dev/dev-key-control-button.ui-schema.json'
  )
  const metadata = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))
  const button = metadata.schema.buttons[0]

  assert.equal(metadata.name, '/dev/dev-key-control-button')
  assert.equal(button.label, '接入IDE')
  assert.equal(button.actionType, 'dialog')
  assert.equal(button.dialog.title, '接入IDE')
  assert.equal(button.dialog.url, '/dev/container/debug-key-dialog')
  assert.deepEqual(button.dialog.data, { appName: '${appName}' })
})

test('dev key dialog can generate, rotate, and copy the full key', () => {
  const schemaPath = path.join(
    repoRoot,
    'dev-module/ouroboros-dev-app-management/src/main/metadata/dev/container/debug-key-dialog.ui-schema.json'
  )
  const metadata = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))

  const currentAppLabel = findNode(metadata, (node) => node.type === 'tpl' && node.tpl === '<span>当前应用：${appName}</span>')
  assert.ok(currentAppLabel)
  assert.equal(findNode(metadata, (node) => node.type === 'select' && node.name === 'appName'), null)

  const copyButton = findNode(metadata, (node) => node.type === 'button' && node.label === '复制 Dev Key')
  const copyActions = copyButton.onEvent.click.actions
  assertFullKeyCopyChain(copyActions, 'keyData')

  const generateForm = findNode(metadata, (node) => (
    node.type === 'form'
    && node.api?.method === 'post'
    && node.api?.url === '/api/dev/${appName}/dev-keys'
  ))
  assert.equal(generateForm.actions[0].label, '生成并复制 Dev Key')
  assertFullKeyCopyChain(generateForm.onEvent.submitSucc.actions, 'generatedKeyData')

  const rotateForm = findNode(metadata, (node) => (
    node.type === 'form'
    && node.api?.method === 'put'
    && node.api?.url === '/api/dev/${appName}/dev-keys'
  ))
  assertFullKeyCopyChain(rotateForm.onEvent.submitSucc.actions, 'rotatedKeyData')
})

function assertFullKeyCopyChain(actions, outputVar) {
  const ajaxAction = actions.find((action) => action.actionType === 'ajax' && action.outputVar === outputVar)
  assert.equal(ajaxAction.api.method, 'get')
  assert.equal(ajaxAction.api.url, '/api/dev/${appName}/dev-keys/plugin-config')
  assert.deepEqual(ajaxAction.api.responseData, { devKeyForCopy: '${devKey}' })

  const copyAction = actions.find((action) => action.actionType === 'copy')
  assert.deepEqual(copyAction.args, { content: '${' + outputVar + '.devKeyForCopy}' })
}

function findNode(root, predicate) {
  if (root && typeof root === 'object') {
    if (predicate(root)) {
      return root
    }
    for (const value of Object.values(root)) {
      if (Array.isArray(value)) {
        for (const item of value) {
          const matched = findNode(item, predicate)
          if (matched) {
            return matched
          }
        }
      } else if (value && typeof value === 'object') {
        const matched = findNode(value, predicate)
        if (matched) {
          return matched
        }
      }
    }
  }
  return null
}
