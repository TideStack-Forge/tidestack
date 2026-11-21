import { describe, expect, test, vi } from 'vitest'

const { ConstantValueEditorCore } = await import(
  '../../../src/property-transpiler/components/ConstantValueEditor.js'
)

function createEditor(props = {}) {
  const editor = new ConstantValueEditorCore({
    data: { dataType: 'string' },
    value: '',
    onChange() {},
    render(_region, body) {
      return body
    },
    ...props,
  })
  editor.setState = (nextState) => {
    const patch = typeof nextState === 'function' ? nextState(editor.state, editor.props) : nextState
    editor.state = {
      ...editor.state,
      ...patch,
    }
  }
  return editor
}

describe('ConstantValueEditorCore', () => {
  test('keeps local draft while external form value refreshes during editing', () => {
    const onChange = vi.fn()
    const editor = createEditor({
      data: { dataType: 'string' },
      value: 'a',
      onChange,
    })

    editor.handleFocus()
    editor.handleChange('abc')
    editor.props = {
      ...editor.props,
      value: 'ab',
    }
    editor.componentDidUpdate({ ...editor.props, value: 'a' })

    expect(editor.state.draftValue).toBe('abc')
    expect(onChange).toHaveBeenLastCalledWith('abc')
  })

  test('resets draft when selected data type changes', () => {
    const editor = createEditor({
      data: { dataType: 'string' },
      value: 'true',
    })

    editor.handleFocus()
    editor.handleChange('typing')
    editor.props = {
      ...editor.props,
      data: { dataType: 'boolean' },
      value: true,
    }
    editor.componentDidUpdate({ ...editor.props, data: { dataType: 'string' }, value: 'true' })

    expect(editor.state.dataType).toBe('boolean')
    expect(editor.state.draftValue).toBe(true)
  })

  test('commits current draft on blur instead of formatting stale external value', () => {
    const onChange = vi.fn()
    const editor = createEditor({
      data: { dataType: 'integer' },
      value: 12,
      onChange,
    })

    editor.handleFocus()
    editor.handleChange('123')
    editor.props = {
      ...editor.props,
      value: 12,
    }
    editor.handleBlur()

    expect(editor.state.draftValue).toBe('123')
    expect(onChange).toHaveBeenLastCalledWith(123)
  })
})
