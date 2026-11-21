export { alert, confirm, prompt } from 'amis'
import { unsupported } from './unsupported'

type ModalSchema = {
  [key: string]: unknown
  onConfirm?: (values: unknown[]) => void
  onClose?: () => void
}
type ModalAction = { actionType: string; dialog?: ModalSchema; drawer?: ModalSchema }
type DoAction = (action: ModalAction, context: object) => unknown

// AMIS dialogs contribute their active scope through entered/exited lifecycle hooks.
const scopes: DoAction[] = []
export function pushModalMethods(doAction: DoAction) { scopes.push(doAction) }
export function popModalMethods() { scopes.pop() }
export function init(ref: { doAction: DoAction }) {
  scopes.length = 0
  pushModalMethods(ref.doAction.bind(ref))
}

async function openModal(type: 'dialog' | 'drawer', schema: ModalSchema, context: object) {
  const doAction = scopes.at(-1)
  if (!doAction) return unsupported(`${type} without an active Amis scope`)
  return new Promise<unknown[] | false>((resolve, reject) => {
    const modal = {
      ...schema, type,
      onConfirm(values: unknown[]) { resolve(values || []); schema.onConfirm?.(values) },
      onClose() { resolve(false); schema.onClose?.() },
    }
    Promise.resolve(doAction({ actionType: type, [type]: modal }, context)).catch(reject)
  })
}
export function openDialog(schema: ModalSchema = {}, context: object = {}) { return openModal('dialog', schema, context) }
export function openDrawer(schema: ModalSchema = {}, context: object = {}) { return openModal('drawer', schema, context) }
