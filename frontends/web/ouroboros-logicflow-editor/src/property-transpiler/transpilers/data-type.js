import { registerTranspiler } from '../transpiler'
import { buildDataTypeSchema } from './dataTypeSchema.mjs'

registerTranspiler('data-type', buildDataTypeSchema)
