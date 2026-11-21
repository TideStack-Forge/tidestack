export function unsupported(capability: string): never {
  throw new Error(`IDE editor does not support host capability: ${capability}`)
}
