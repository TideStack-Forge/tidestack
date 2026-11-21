import capabilityManifest from '../schema-resources/tidestackDevelopCapabilities.json'

export type TideStackDevelopCapabilityStatus = 'available' | 'planned' | 'not-supported'
export type TideStackDevelopIde = 'intellij' | 'vscode'

export interface TideStackDevelopCapability {
  id: string
  titleKey: string
  descriptionKey: string
  intellij: TideStackDevelopCapabilityStatus
  vscode: TideStackDevelopCapabilityStatus
}

export const tideStackDevelopCapabilities = capabilityManifest.capabilities as TideStackDevelopCapability[]

export function capabilitiesForIde(ide: TideStackDevelopIde): TideStackDevelopCapability[] {
  return tideStackDevelopCapabilities.filter((capability) => capability[ide] !== 'not-supported')
}

export function capabilityStatus(
  capabilityId: string,
  ide: TideStackDevelopIde,
): TideStackDevelopCapabilityStatus | undefined {
  return tideStackDevelopCapabilities.find((capability) => capability.id === capabilityId)?.[ide]
}
