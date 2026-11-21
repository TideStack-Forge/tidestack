function createDefaultFlowData() {
  return {
    name: '',
    description: '',
    entryNode: '',
    exitNode: '',
    errorEntryNode: '',
    inputDataType: '',
    outputDataType: '',
    nodes: [],
    connections: [],
  }
}

export default function createStoreFn(value) {
  const listeners = new Set()
  const state = {
    flowData: value || createDefaultFlowData(),
    isReadOnly: false,
  }

  const store = {
    state,
    getters: {
      get flowData() {
        return state.flowData
      },
      get isReadOnly() {
        return state.isReadOnly
      },
    },
    commit(type, payload) {
      if (type === 'changeFlowData') {
        state.flowData = payload ? { ...payload } : createDefaultFlowData()
      } else if (type === 'changeIsReadOnly') {
        state.isReadOnly = !!payload
      }
      listeners.forEach((listener) => listener())
    },
    subscribe(listener) {
      if (typeof listener !== 'function') {
        return () => {}
      }
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
  }

  return store
}
