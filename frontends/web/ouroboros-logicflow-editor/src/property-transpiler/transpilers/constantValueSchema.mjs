import ConstantValueEditor from '../components/ConstantValueEditor.js'
import { parseConstantValue } from './constantValueConversion.mjs'

function buildValidation(dataTypeField) {
  return (data = {}, value) => {
    const parsed = parseConstantValue(data?.[dataTypeField], value)
    return parsed.ok ? true : parsed.message || '常量值不符合所选值类型'
  }
}

function buildPipeOut(dataTypeField) {
  return (value, _oldValue, data = {}) => {
    const parsed = parseConstantValue(data?.[dataTypeField], value)
    return parsed.ok ? parsed.value : value
  }
}

export function buildConstantValueSchema(property) {
  const dataTypeField = property.dataTypeField || 'dataType'
  const {
    dataTypeField: _dataTypeField,
    options,
    placeholder,
    type,
    ...sharedProperty
  } = property
  const validate = buildValidation(dataTypeField)
  const pipeOut = buildPipeOut(dataTypeField)

  return {
    ...sharedProperty,
    component: ConstantValueEditor,
    asFormItem: {
      strictMode: false,
    },
    dataTypeField,
    options,
    placeholder,
    validate,
    pipeOut,
  }
}
