import { registerTranspiler } from '../transpiler'
import { buildConstantValueSchema } from './constantValueSchema.mjs'

registerTranspiler('constant-value', buildConstantValueSchema)
