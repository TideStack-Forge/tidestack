import { test } from 'vitest'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import {
  buildConstantValueInnerSchema,
  formatConstantValueForEditor,
  parseConstantValueForCommit,
  validateConstantValueDraft,
} from '../../../src/property-transpiler/components/constantValueEditorModel.mjs'
import {
  parseConstantValue,
  stringifyConstantValue,
} from '../../../src/property-transpiler/transpilers/constantValueConversion.mjs'
import { buildConstantValueSchema } from '../../../src/property-transpiler/transpilers/constantValueSchema.mjs'
import { buildDataTypeSchema } from '../../../src/property-transpiler/transpilers/dataTypeSchema.mjs'

const editorRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..')

test('data-type transpiler keeps concrete types disabled by default and opt-in explicit types', () => {
  const defaultSchema = buildDataTypeSchema({ type: 'data-type', name: 'dataType' })
  const explicitSchema = buildDataTypeSchema({
    type: 'data-type',
    name: 'dataType',
    allowConcreteTypes: true,
  })

  assert.equal(defaultSchema.type, 'select')
  assert.equal(defaultSchema.options.find((option) => option.value === 'any').disabled, false)
  assert.equal(defaultSchema.options.find((option) => option.value === 'string').disabled, true)
  assert.equal(defaultSchema.options.find((option) => option.value === 'integer').disabled, true)
  assert.equal(defaultSchema.options.find((option) => option.value === 'map').disabled, true)
  assert.equal(explicitSchema.options.find((option) => option.value === 'string').disabled, false)
  assert.equal(explicitSchema.options.find((option) => option.value === 'integer').disabled, false)
  assert.equal(explicitSchema.options.find((option) => option.value === 'double').disabled, false)
  assert.equal(explicitSchema.options.find((option) => option.value === 'array').disabled, false)
  assert.equal(explicitSchema.options.find((option) => option.value === 'map').disabled, false)
})

test('data-type transpiler filters to explicit enabled values when provided', () => {
  const schema = buildDataTypeSchema({
    type: 'data-type',
    name: 'dataType',
    allowConcreteTypes: true,
    enabledValues: ['string', 'integer', 'long', 'float', 'double', 'boolean', 'date', 'datetime', 'time', 'array', 'map'],
  })
  const optionValues = schema.options.map((option) => option.value)

  assert.deepEqual(optionValues, [
    'string',
    'integer',
    'long',
    'float',
    'double',
    'boolean',
    'date',
    'datetime',
    'time',
    'array',
    'map',
  ])
  assert.equal(optionValues.includes('any'), false)
  assert.equal(optionValues.includes('object'), false)
})

test('constant-value conversion parses values according to the explicit dataType', () => {
  assert.deepEqual(parseConstantValue('integer', '123'), { ok: true, value: 123 })
  assert.deepEqual(parseConstantValue('long', '9223372036854775807'), {
    ok: true,
    value: '9223372036854775807',
  })
  assert.deepEqual(parseConstantValue('double', '12.5'), { ok: true, value: 12.5 })
  assert.deepEqual(parseConstantValue('boolean', 'true'), { ok: true, value: true })
  assert.deepEqual(parseConstantValue('array', '[1,2]'), { ok: true, value: [1, 2] })
  assert.deepEqual(parseConstantValue('map', '{"a":1}'), { ok: true, value: { a: 1 } })
  assert.deepEqual(parseConstantValue('string', 'hello'), { ok: true, value: 'hello' })
  assert.deepEqual(parseConstantValue('date', '2024-02-29'), { ok: true, value: '2024-02-29' })
  assert.deepEqual(parseConstantValue('datetime', '2024-02-29 12:00:00'), {
    ok: true,
    value: '2024-02-29 12:00:00',
  })
  assert.deepEqual(parseConstantValue('time', '12:59:59'), { ok: true, value: '12:59:59' })
})

test('constant-value conversion rejects values that cannot convert to the explicit dataType', () => {
  assert.deepEqual(parseConstantValue('integer', 'abc'), { ok: false, message: '常量值必须是整数' })
  assert.deepEqual(parseConstantValue('integer', '12.5'), { ok: false, message: '常量值必须是整数' })
  assert.deepEqual(parseConstantValue('integer', '2147483648'), { ok: false, message: '常量值必须是 32 位整数' })
  assert.deepEqual(parseConstantValue('long', '9223372036854775808'), { ok: false, message: '常量值必须是 64 位整数' })
  assert.deepEqual(parseConstantValue('boolean', 'yes'), { ok: false, message: '常量值必须是 true 或 false' })
  assert.deepEqual(parseConstantValue('array', '{"a":1}'), { ok: false, message: '常量值必须是 JSON 数组' })
  assert.deepEqual(parseConstantValue('map', '[1,2]'), { ok: false, message: '常量值必须是 JSON 对象' })
  assert.equal(parseConstantValue('date', '2026-99-99').ok, false)
  assert.equal(parseConstantValue('date', '2026-02-29').ok, false)
  assert.equal(parseConstantValue('time', '25:00:00').ok, false)
  assert.equal(parseConstantValue('time', '12:60:00').ok, false)
  assert.equal(parseConstantValue('datetime', '2026-02-29 12:00:00').ok, false)
  assert.equal(parseConstantValue('datetime', '2024-02-29 25:00:00').ok, false)
})

test('constant-value conversion displays persisted structured values as editable text', () => {
  assert.equal(stringifyConstantValue({ a: 1 }, 'map'), '{\n  "a": 1\n}')
  assert.equal(stringifyConstantValue([1, 2], 'array'), '[\n  1,\n  2\n]')
  assert.equal(stringifyConstantValue('{"a":', 'map'), '{"a":')
  assert.equal(stringifyConstantValue('hello', 'string'), 'hello')
  assert.equal(stringifyConstantValue(123, 'integer'), '123')
  assert.equal(stringifyConstantValue(null, 'string'), '')
})

test('constant-value transpiler embeds a React component through AMIS form item wrapping', () => {
  const schema = buildConstantValueSchema({ type: 'constant-value', name: 'value' })

  assert.equal(typeof schema.component, 'function')
  assert.deepEqual(schema.asFormItem, { strictMode: false })
  assert.equal(schema.name, 'value')
  assert.equal(schema.dataTypeField, 'dataType')
  assert.equal(schema.pipeOut('hello', null, { dataType: 'string' }), 'hello')
  assert.equal(schema.pipeOut('123', null, { dataType: 'integer' }), 123)
  assert.equal(schema.pipeOut('abc', null, { dataType: 'integer' }), 'abc')
  assert.equal(schema.validate({ dataType: 'integer' }, '123'), true)
  assert.equal(schema.validate({ dataType: 'integer' }, 'abc'), '常量值必须是整数')
  assert.equal(schema.validate({ dataType: 'string' }, 'hello'), true)
})

test('constant-value editor model chooses controls by selected type without correcting invalid drafts', () => {
  const stringControl = buildConstantValueInnerSchema({ dataType: 'string' })
  const booleanControl = buildConstantValueInnerSchema({ dataType: 'boolean', draftValue: true })
  const dateControl = buildConstantValueInnerSchema({ dataType: 'date' })
  const arrayControl = buildConstantValueInnerSchema({ dataType: 'array' })
  const mapControl = buildConstantValueInnerSchema({ dataType: 'map' })

  assert.equal(stringControl.type, 'input-text')
  assert.equal(booleanControl.type, 'switch')
  assert.equal(dateControl.type, 'input-date')
  assert.equal(arrayControl.type, 'json-editor')
  assert.equal(mapControl.type, 'json-editor')
  assert.deepEqual(parseConstantValueForCommit('array', '[1,2]'), [1, 2])
  assert.deepEqual(parseConstantValueForCommit('map', '{"a":1}'), { a: 1 })
  assert.equal(parseConstantValueForCommit('array', '[1,'), '[1,')
  assert.equal(parseConstantValueForCommit('map', '{"a":'), '{"a":')
  assert.equal(validateConstantValueDraft('array', '[1,2]'), undefined)
  assert.equal(validateConstantValueDraft('array', '[1,'), '常量值必须是 JSON 数组')
  assert.equal(formatConstantValueForEditor({ a: 1 }, 'map'), '{\n  "a": 1\n}')
  assert.equal(formatConstantValueForEditor('{"a":', 'map'), '{"a":')
})

test('const node definition uses explicit value type and constant-value editor', () => {
  const constNodeDefine = JSON.parse(
    fs.readFileSync(
      path.join(
        editorRoot,
        '../../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/main/metadata/ConstNode.flow-node.json'
      ),
      'utf8'
    )
  )
  const dataTypeProperty = constNodeDefine.properties.find((property) => property.name === 'dataType')
  const valueProperty = constNodeDefine.properties.find((property) => property.name === 'value')

  assert.deepEqual(dataTypeProperty, {
    type: 'data-type',
    name: 'dataType',
    label: '值类型',
    value: 'string',
    clearable: false,
    allowConcreteTypes: true,
    enabledValues: [
      'string',
      'integer',
      'long',
      'float',
      'double',
      'boolean',
      'date',
      'datetime',
      'time',
      'array',
      'map',
    ],
  })
  assert.deepEqual(valueProperty, {
    type: 'constant-value',
    name: 'value',
    label: '常量值',
  })
})
