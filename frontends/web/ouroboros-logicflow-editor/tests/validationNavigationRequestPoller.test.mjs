import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createValidationNavigationRequestPoller,
} from '../src/logicFlowEditor/orchestration/validationNavigationRequestPoller.mjs'

test('validation navigation request poller detects a fresh request carried only by formStore data', () => {
  let props = {
    formStore: {
      data: {},
    },
  }
  const seenNodeNames = []
  const poller = createValidationNavigationRequestPoller({
    getProps: () => props,
    getLastRequest: () => ({}),
    onPendingRequest(nextProps) {
      seenNodeNames.push(nextProps.formStore.data.navigationTargetNodeName)
    },
  })

  assert.equal(poller.poll(), false)

  props = {
    formStore: {
      data: {
        navigationTargetNodeName: 'caller',
        navigationTargetRequestKey: 1,
      },
    },
  }

  assert.equal(poller.poll(), true)
  assert.deepEqual(seenNodeNames, ['caller'])
})

test('validation navigation request poller suppresses duplicate requests that were already handled', () => {
  const props = {
    formStore: {
      data: {
        navigationTargetNodeName: 'caller',
        navigationTargetRequestKey: 1,
      },
    },
  }
  const seenNodeNames = []
  const poller = createValidationNavigationRequestPoller({
    getProps: () => props,
    getLastRequest: () => ({
      navigationTargetNodeName: 'caller',
      navigationTargetRequestKey: 1,
    }),
    onPendingRequest(nextProps) {
      seenNodeNames.push(nextProps.formStore.data.navigationTargetNodeName)
    },
  })

  assert.equal(poller.poll(), false)
  assert.deepEqual(seenNodeNames, [])
})

test('validation navigation request poller start and stop manage the timer lifecycle', () => {
  const intervals = []
  const clearedIntervals = []
  const seenNodeNames = []
  const poller = createValidationNavigationRequestPoller({
    getProps: () => ({
      formStore: {
        data: {
          navigationTargetNodeName: 'caller',
          navigationTargetRequestKey: 2,
        },
      },
    }),
    getLastRequest: () => ({}),
    onPendingRequest(nextProps) {
      seenNodeNames.push(nextProps.formStore.data.navigationTargetNodeName)
    },
    intervalMs: 25,
    setIntervalFn(callback, delay) {
      intervals.push({ callback, delay })
      return 'timer-1'
    },
    clearIntervalFn(intervalId) {
      clearedIntervals.push(intervalId)
    },
  })

  assert.equal(poller.start(), 'timer-1')
  assert.equal(intervals.length, 1)
  assert.equal(intervals[0].delay, 25)

  intervals[0].callback()

  assert.deepEqual(seenNodeNames, ['caller'])

  poller.stop()

  assert.deepEqual(clearedIntervals, ['timer-1'])
})
