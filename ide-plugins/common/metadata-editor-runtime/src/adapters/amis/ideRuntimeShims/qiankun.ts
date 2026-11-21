import { unsupported } from './unsupported'
export type MicroApp = { mount?: () => Promise<void>; unmount?: () => Promise<void> }
export function loadMicroApp(): MicroApp { return unsupported('micro-app mounting') }
