import contract from '../schema-resources/metadataCreationContract.json'
import type { MetadataFileTemplateFieldDefault } from './types'

export interface MetadataCreationPathRules {
  relativeCurrentDirectoryPrefix: string
  jsonExtension: string
  fallbackFileName: string
  sourceMainSegments: string[]
}

export interface MetadataCreationTemplateDefaultRules {
  fixedFileNameStem: string
  fallbacks: Record<MetadataFileTemplateFieldDefault, string>
}

export interface MetadataCreationContract {
  schemaVersion: number
  sourceRoot: string
  packagedResourceRoot: string
  metadataResourceDirectory: string
  pathRules: MetadataCreationPathRules
  templateDefaultRules: MetadataCreationTemplateDefaultRules
}

export const metadataCreationContract = contract as MetadataCreationContract
