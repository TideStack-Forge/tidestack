import { t } from '../../i18n.js'
import React from 'react'
import isEqual from 'lodash/isEqual.js'

import {
  buildConstantValueInnerSchema,
  formatConstantValueForEditor,
  normalizeConstantValueDataType,
  parseConstantValueForCommit,
  validateConstantValueDraft,
} from './constantValueEditorModel.mjs'

function readDataType(props = {}) {
  const dataTypeField = props.dataTypeField || 'dataType'
  return normalizeConstantValueDataType(props.data?.[dataTypeField])
}

function getControlValue(valueOrEvent) {
  if (valueOrEvent && typeof valueOrEvent === 'object' && 'target' in valueOrEvent) {
    if (valueOrEvent.target?.type === 'checkbox') {
      return !!valueOrEvent.target.checked
    }
    return valueOrEvent.target?.value
  }
  return valueOrEvent
}

export class ConstantValueEditorCore extends React.Component {
  constructor(props) {
    super(props)
    const dataType = readDataType(props)
    this.editing = false
    this.lastExternalValue = props.value
    this.state = {
      dataType,
      draftValue: formatConstantValueForEditor(props.value, dataType),
    }
    this.handleBlur = this.handleBlur.bind(this)
    this.handleChange = this.handleChange.bind(this)
    this.handleFocus = this.handleFocus.bind(this)
  }

  componentDidUpdate(prevProps) {
    const dataType = readDataType(this.props)
    const dataTypeChanged = dataType !== this.state.dataType
    const valueChanged = !isEqual(this.props.value, this.lastExternalValue)

    if (dataTypeChanged || (!this.editing && valueChanged)) {
      this.lastExternalValue = this.props.value
      this.setState({
        dataType,
        draftValue: formatConstantValueForEditor(this.props.value, dataType),
      })
      return
    }

    if (!isEqual(prevProps.value, this.props.value)) {
      this.lastExternalValue = this.props.value
    }
  }

  handleFocus() {
    this.editing = true
  }

  handleBlur() {
    this.editing = false
    const dataType = readDataType(this.props)
    const committedValue = parseConstantValueForCommit(dataType, this.state.draftValue)
    this.lastExternalValue = committedValue
    this.props.onChange?.(committedValue)
    this.setState({
      dataType,
      draftValue: formatConstantValueForEditor(committedValue, dataType),
    })
  }

  handleChange(valueOrEvent) {
    const draftValue = getControlValue(valueOrEvent)
    const dataType = readDataType(this.props)
    this.setState({ dataType, draftValue })
    this.props.onChange?.(parseConstantValueForCommit(dataType, draftValue))
  }

  validate() {
    return t(validateConstantValueDraft(this.state.dataType, this.state.draftValue))
  }

  render() {
    const { render } = this.props
    const schema = buildConstantValueInnerSchema({
      dataType: this.state.dataType,
      disabled: !!(this.props.static || this.props.readonly || this.props.disabled),
      draftValue: this.state.draftValue,
      onBlur: this.handleBlur,
      onChange: this.handleChange,
      onFocus: this.handleFocus,
      options: this.props.options,
      placeholder: this.props.placeholder,
    })

    return render('logicflow-constant-value-editor', [schema])
  }
}

export default ConstantValueEditorCore
