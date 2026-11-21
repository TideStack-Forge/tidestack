function refreshNodeSelection(node, graph) {
  graph.select(node, { highlight: true })
  graph.trigger('node:click', { node })
}

function buildDeleteInputPortDialogConfig(inputPorts, minInputPort, translate) {
  return {
    title: '删除端口',
    body: {
      type: 'form',
      body: [
        {
          type: 'checkboxes',
          name: 'deletePorts',
          label: '删除端口',
          model: 'deletePorts',
          joinValues: false,
          extractValue: true,
          inline: false,
          validate: (_data, value) => {
            if (minInputPort == null) return
            if (!value || !value.length) return
            if (inputPorts.length - value.length < minInputPort) {
              return translate('删除后端口数量不能小于{{min}}', { min: minInputPort })
            }
          },
          options: inputPorts.map((port, index) => ({
            label: port.name
              ? translate('端口{{index}}（{{name}}）', { index, name: port.name })
              : translate('端口{{index}}', { index }),
            value: index,
          })),
        },
      ],
    },
  }
}

export async function confirmAndDeleteNode({ node, graph, confirmFn }) {
  if (!(await confirmFn('确定要删除该节点吗？'))) return false
  graph.removeNode(node)
  return true
}

export async function confirmAndDeleteEdge({ edge, graph, confirmFn }) {
  if (!(await confirmFn('确定要删除该连线吗？'))) return false
  graph.removeEdge(edge)
  return true
}

export function addInputPortToNode({ node, graph, createDefaultPortData }) {
  const data = node.getData()
  const inputRule =
    node.attr('meta/nodeDefine/portRules/input') ||
    node.attr('meta/nodeDefine')?.portRules?.input
  if (!inputRule || inputRule?.ruleType !== 'limit') return false
  if (inputRule.max != null && data.inputPorts?.length >= inputRule.max) return false

  const inputPorts = data.inputPorts || []
  const newData = { ...data, inputPorts: [...inputPorts, createDefaultPortData()] }
  node.setData(newData, { overwrite: true })
  refreshNodeSelection(node, graph)
  return true
}

export async function deleteInputPortsWithDialog({ node, graph, openDialogFn, translate }) {
  const data = node.getData()
  const nodeDefine = node.attr('meta/nodeDefine')
  const minInputPort = nodeDefine?.portRules?.input?.min
  const inputPorts = data.inputPorts || []
  const results = await openDialogFn(
    buildDeleteInputPortDialogConfig(inputPorts, minInputPort, translate)
  )
  if (!results) return false

  const [{ deletePorts: portsIndex } = {}] = results
  if (!portsIndex || !portsIndex.length) return false

  const newPorts = inputPorts.filter((_, index) => !portsIndex.includes(index))
  node.setData({ ...data, inputPorts: newPorts }, { overwrite: true })
  refreshNodeSelection(node, graph)
  return true
}
