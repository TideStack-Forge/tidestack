import manifest from '../schema-resources/metadataTypeContributions.json'
import type { MetadataTypeContributionDefinition } from './metadataTypeContributionDefinition'

export const metadataTypeContributionManifest = manifest as { types: MetadataTypeContributionDefinition[]; schemas: Record<string, unknown>[] }
export const metadataTypeContributionDefinitions = metadataTypeContributionManifest.types
