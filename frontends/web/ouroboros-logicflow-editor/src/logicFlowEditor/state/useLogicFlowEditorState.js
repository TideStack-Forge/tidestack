import { useEffect, useMemo, useState } from 'react'

import {
  fetchNodeDefines,
  buildNodeOptionsByCapability,
} from '@/node-define'
import { createObject } from '@/utils'
import { toast, loadSvgAsSymbol } from '@/utils'

import {
  buildEditorWrapData,
  collectNodeDefineSvgs,
} from './editorStateHelpers.mjs'

export function useLogicFlowEditorState({ store, getProps, getRootData }) {
  const initialReadonly = !!(getProps?.()?.static || getProps?.()?.readonly || store?.getters?.isReadOnly)
  const [allNodeDefines, setAllNodeDefines] = useState([])
  const [flowData, setFlowData] = useState(() => store?.state?.flowData || null)
  const [isReadOnly, setIsReadOnly] = useState(() => initialReadonly)

  const entryNodes = useMemo(() => {
    const options = buildNodeOptionsByCapability(
      flowData?.nodes || [],
      allNodeDefines,
      'canBeEntry'
    )
    console?.debug?.('[logicflow] entryNodes', {
      flowNodeCount: (flowData?.nodes || []).length,
      nodeDefineCount: allNodeDefines.length,
      optionCount: options.length,
      optionValues: options.map((option) => option?.value),
      optionLabels: options.map((option) => option?.label),
      flowNodes: (flowData?.nodes || []).map((node) => ({ name: node?.name, type: node?.type })),
      nodeDefines: allNodeDefines.map((nodeDefine) => ({
        type: nodeDefine?.type,
        label: nodeDefine?.label,
        editorCapabilities: nodeDefine?.editorCapabilities,
      })),
      options,
    })
    return options
  }, [flowData, allNodeDefines])
  const exitNodes = useMemo(() => {
    const options = buildNodeOptionsByCapability(
      flowData?.nodes || [],
      allNodeDefines,
      'canBeExit'
    )
    console?.debug?.('[logicflow] exitNodes', {
      flowNodes: (flowData?.nodes || []).map((node) => ({ name: node?.name, type: node?.type })),
      nodeDefines: allNodeDefines.map((nodeDefine) => ({
        type: nodeDefine?.type,
        label: nodeDefine?.label,
        editorCapabilities: nodeDefine?.editorCapabilities,
      })),
      options,
    })
    return options
  }, [flowData, allNodeDefines])
  const errorEntryNodes = useMemo(() => {
    const options = buildNodeOptionsByCapability(
      flowData?.nodes || [],
      allNodeDefines,
      'canBeErrorEntry'
    )
    console?.debug?.('[logicflow] errorEntryNodes', {
      flowNodes: (flowData?.nodes || []).map((node) => ({ name: node?.name, type: node?.type })),
      nodeDefines: allNodeDefines.map((nodeDefine) => ({
        type: nodeDefine?.type,
        label: nodeDefine?.label,
        editorCapabilities: nodeDefine?.editorCapabilities,
      })),
      options,
    })
    return options
  }, [flowData, allNodeDefines])
  const nodeTypes = useMemo(() => {
    return allNodeDefines.map((item) => ({
      label: item.label,
      value: item.type,
    }))
  }, [allNodeDefines])
  const propertyPanelDataVersion = useMemo(() => {
    const serializeOptions = (items) =>
      items.map(({ label, value }) => `${label}\u0000${value}`).join('\u0001')

    return [
      serializeOptions(entryNodes),
      serializeOptions(exitNodes),
      serializeOptions(errorEntryNodes),
      serializeOptions(nodeTypes),
    ].join('\u0002')
  }, [entryNodes, exitNodes, errorEntryNodes, nodeTypes])

  async function getNodeDefinesFromApi(request, api, data) {
    try {
      return await fetchNodeDefines(request, api, data)
    } catch (e) {
      toast(e.message, 'error')
      return []
    }
  }

  async function getAllNodeDefines() {
    const extProps = getProps?.() || {}
    const {
      nodeDefineApi = '',
      data,
      env: { fetcher: request } = {},
    } = extProps
    const finalNodes = await getNodeDefinesFromApi(request, nodeDefineApi, data)

    await loadSvgAsSymbol(collectNodeDefineSvgs(finalNodes))

    return finalNodes
  }

  useEffect(() => {
    const syncState = async () => {
      const value = store?.state?.flowData
      if (!value) return
      const nextNodeDefines = allNodeDefines.length
        ? allNodeDefines
        : await getAllNodeDefines()
      if (!allNodeDefines.length && nextNodeDefines.length) {
        setAllNodeDefines(nextNodeDefines)
      }
      setFlowData(value)
      setIsReadOnly(!!(getProps?.()?.static || getProps?.()?.readonly || store?.getters?.isReadOnly))
    }

    const unsubscribe = store?.subscribe?.(() => {
      void syncState()
    })

    void syncState()

    return () => {
      unsubscribe?.()
    }
  }, [store, allNodeDefines, getProps])

  function wrapData(data, superProps = {}) {
    return buildEditorWrapData({
      createObjectFn: createObject,
      entryNodes,
      exitNodes,
      errorEntryNodes,
      nodeTypes,
      isReadOnly,
      flowModel: flowData,
      rootData: getRootData?.() || {},
      data,
      superProps,
    })
  }

  return {
    isReadOnly,
    allNodeDefines,
    flowData,
    entryNodes,
    exitNodes,
    errorEntryNodes,
    nodeTypes,
    propertyPanelDataVersion,
    wrapData,
  }
}
