export const dataTypeOptions = [
  {
    label: 'any',
    value: 'any',
  },
  {
    label: 'String',
    value: 'string',
  },
  {
    label: 'Integer',
    value: 'integer',
  },
  {
    label: 'Long',
    value: 'long',
  },
  {
    label: 'Float',
    value: 'float',
  },
  {
    label: 'Double',
    value: 'double',
  },
  {
    label: 'Boolean',
    value: 'boolean',
  },
  {
    label: 'Date',
    value: 'date',
  },
  {
    label: 'DateTime',
    value: 'datetime',
  },
  {
    label: 'Time',
    value: 'time',
  },
  {
    label: 'Array',
    value: 'array',
  },
  {
    label: 'Map',
    value: 'map',
  },
  {
    label: 'Object',
    value: 'object',
  },
]

function buildEnabledValues(property) {
  if (Array.isArray(property.enabledValues) && property.enabledValues.length > 0) {
    return new Set(property.enabledValues)
  }
  return null
}

export function buildDataTypeSchema(property) {
  const allowConcreteTypes = property.allowConcreteTypes === true
  const enabledValues = buildEnabledValues(property)
  return {
    ...property,
    type: 'select',
    options: dataTypeOptions
      .filter((option) => {
        if (enabledValues != null && !enabledValues.has(option.value)) {
          return false
        }
        if (Array.isArray(property.excludeValues) && property.excludeValues.includes(option.value)) {
          return false
        }
        return true
      })
      .map((option) => ({
        ...option,
        disabled: enabledValues == null && option.value !== 'any' && !allowConcreteTypes,
      })),
  }
}
