import test from 'node:test'
import assert from 'node:assert/strict'

import {
  hasPendingValidationNavigationRequest,
  readValidationNavigationRequest,
  resolveValidationNavigationNodeName,
} from '../src/logicFlowEditor/orchestration/validationNavigationRequest.mjs'

test('resolveValidationNavigationNodeName returns the target node for a fresh request', () => {
  assert.equal(
    resolveValidationNavigationNodeName(
      {},
      {
        navigationTargetNodeName: 'node-a',
      }
    ),
    'node-a'
  )
})

test('resolveValidationNavigationNodeName suppresses duplicate requests without a new request key', () => {
  assert.equal(
    resolveValidationNavigationNodeName(
      {
        navigationTargetNodeName: 'node-a',
      },
      {
        navigationTargetNodeName: 'node-a',
      }
    ),
    null
  )
})

test('resolveValidationNavigationNodeName allows repeated navigation when the request key changes', () => {
  assert.equal(
    resolveValidationNavigationNodeName(
      {
        navigationTargetNodeName: 'node-a',
        navigationTargetRequestKey: 'req-1',
      },
      {
        navigationTargetNodeName: 'node-a',
        navigationTargetRequestKey: 'req-2',
      }
    ),
    'node-a'
  )
})

test('readValidationNavigationRequest prefers direct renderer props when available', () => {
  assert.deepEqual(
    readValidationNavigationRequest({
      navigationTargetNodeName: 'node-a',
      navigationTargetRequestKey: 2,
      formStore: {
        data: {
          navigationTargetNodeName: 'node-b',
          navigationTargetRequestKey: 3,
        },
      },
    }),
    {
      navigationTargetNodeName: 'node-a',
      navigationTargetRequestKey: 2,
    }
  )
})

test('readValidationNavigationRequest falls back to formStore data when renderer props are omitted', () => {
  assert.deepEqual(
    readValidationNavigationRequest({
      formStore: {
        data: {
          navigationTargetNodeName: 'node-b',
          navigationTargetRequestKey: 3,
        },
      },
    }),
    {
      navigationTargetNodeName: 'node-b',
      navigationTargetRequestKey: 3,
    }
  )
})

test('hasPendingValidationNavigationRequest detects a fresh request carried only by formStore data', () => {
  assert.equal(
    hasPendingValidationNavigationRequest(
      {},
      {
        formStore: {
          data: {
            navigationTargetNodeName: 'node-b',
            navigationTargetRequestKey: 3,
          },
        },
      }
    ),
    true
  )
})
