function createNoopDisposable() {
  return {
    dispose() {},
  }
}

function createNoopModel() {
  return {
    dispose() {},
    getFullModelRange() {
      return {}
    },
    pushEditOperations() {},
  }
}

function createNoopEditorInstance() {
  return {
    dispose() {},
    focus() {},
    layout() {},
    updateOptions() {},
    getValue() {
      return ''
    },
    getModel() {
      return createNoopModel()
    },
    getModifiedEditor() {
      return this
    },
    getPosition() {
      return null
    },
    setPosition() {},
    pushUndoStop() {},
    onDidChangeModelContent() {
      return createNoopDisposable()
    },
    onDidFocusEditorWidget() {
      return createNoopDisposable()
    },
    onDidBlurEditorWidget() {
      return createNoopDisposable()
    },
  }
}

export const editor = {
  create() {
    return createNoopEditorInstance()
  },
}

export const languages = {
  json: {
    jsonDefaults: {
      diagnosticsOptions: {},
      setDiagnosticsOptions() {},
    },
  },
}

export default {
  editor,
  languages,
}
