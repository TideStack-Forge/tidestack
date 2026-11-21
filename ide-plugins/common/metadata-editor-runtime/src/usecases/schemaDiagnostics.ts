import { validators } from '../schema-resources/metadataValidators.cjs'
import type { ValidationError } from '../schema-resources/metadataValidators.cjs'
import type { MetadataDiagnostic } from '../core/types'

const diagnosticCodes: Record<string, string> = {
  required: 'schema-required-property',
  type: 'schema-property-type',
  enum: 'schema-enum-value',
}

export function validateMetadataSchema(schemaId: string, document: unknown): MetadataDiagnostic[] {
  const validate = validators[schemaId]
  if (!validate) {
    throw new Error(`Missing public metadata schema validator: ${schemaId}`)
  }
  if (validate(document)) {
    return []
  }
  return (validate.errors ?? []).map((error) => {
    const path = diagnosticPath(error)
    return {
      code: diagnosticCodes[error.keyword] ?? `schema-${error.keyword}`,
      message: `Metadata ${path ? `property '${path}'` : 'document'} ${error.message ?? 'does not match its schema'}.`,
      severity: 'error',
      path: path || undefined,
    }
  })
}

function diagnosticPath(error: ValidationError): string {
  const segments = error.instancePath.split('/').slice(1).map((segment) => segment.replace(/~1/g, '/').replace(/~0/g, '~'))
  if (error.keyword === 'required' && typeof error.params.missingProperty === 'string') {
    segments.push(error.params.missingProperty)
  }
  return segments.reduce((path, segment) => /^\d+$/.test(segment)
    ? `${path}[${segment}]`
    : path ? `${path}.${segment}` : segment, '')
}
