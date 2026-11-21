import { getPureSchema } from '../../core/pureSchema'
import { reorderSchemaLike, type SchemaChangeSource } from './schemaKeyOrderBridge.js'

export function createVisualEditorChangeValue(
  previousValue: unknown,
  schema: unknown,
  source?: SchemaChangeSource,
): unknown {
  const pureSchema = getPureSchema(schema)
  return source === 'code' ? pureSchema : reorderSchemaLike(previousValue, pureSchema)
}
