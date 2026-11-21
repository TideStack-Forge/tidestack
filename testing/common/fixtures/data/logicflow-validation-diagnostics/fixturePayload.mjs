import { fileURLToPath } from 'node:url'

export function buildValidationFixturePayload({ modelName, nodeName }) {
  return {
    name: modelName,
    description: 'Logicflow smoke validation fixture',
    isEnabled: true,
    inputDataType: 'any',
    outputDataType: 'any',
    entryNode: nodeName,
    nodes: [
      {
        name: nodeName,
        type: 'callFlow',
        description: '',
        flowName: '',
        inputPorts: [
          {
            name: 'input',
            description: '',
            dataType: 'any',
          },
        ],
        outputPorts: [
          {
            name: 'output',
            description: '',
            dataType: 'any',
          },
        ],
      },
    ],
    connections: [],
  }
}

function isCliEntryPoint() {
  return process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]
}

if (isCliEntryPoint()) {
  const [, , modelName, nodeName] = process.argv
  process.stdout.write(
    `${JSON.stringify(buildValidationFixturePayload({ modelName, nodeName }))}\n`
  )
}
