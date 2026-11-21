import test from 'node:test'
import assert from 'node:assert/strict'

import {
  getTerminalDisplayState,
  isTerminalNodeDefine,
  resolveTerminalKind,
} from '../src/node-define/displayState.mjs'

test('resolveTerminalKind prefers explicit terminal kind metadata', () => {
  assert.equal(
    resolveTerminalKind({
      isTerminal: true,
      terminalKind: 'exception',
      editorCapabilities: {
        isSuccessfulTerminal: true,
      },
    }),
    'exception'
  )
})

test('resolveTerminalKind treats successful terminal capabilities as successful terminals', () => {
  assert.equal(
    resolveTerminalKind({
      editorCapabilities: {
        isSuccessfulTerminal: true,
      },
    }),
    'successful'
  )
})

test('resolveTerminalKind treats plain terminal metadata as generic terminal', () => {
  assert.equal(resolveTerminalKind({ isTerminal: true }), 'generic')
})

test('getTerminalDisplayState returns non-terminal state for ordinary nodes', () => {
  assert.deepEqual(
    getTerminalDisplayState({
      editorCapabilities: {
        canBeEntry: true,
      },
    }),
    {
      isTerminal: false,
      terminalKind: null,
      badgeText: '',
    }
  )
})

test('isTerminalNodeDefine keeps ordinary nodes non-terminal', () => {
  assert.equal(
    isTerminalNodeDefine({
      editorCapabilities: {
        canBeEntry: true,
      },
    }),
    false
  )
})
