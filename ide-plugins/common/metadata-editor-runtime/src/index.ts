export {
  getContributionByPackagedPath,
  getContributionBySuffix,
  getContributionByType,
  mapMetadataPackagedResourcePath,
  mapMetadataSourcePath,
  metadataTypeContributions,
  normalizePath,
} from './core/contributions'
export { InMemoryMetadataFileBridge, createMetadataEditorHost } from './usecases/host'
export { MetadataRevisionConflictError, metadataHostErrorCode } from './core/errors'
export { createBrowserMetadataEditorHost } from './adapters/browser/browserHost'
export {
  createMetadataEditorSession,
  handleMetadataEditorSessionRequest,
} from './usecases/editorSession'
export type {
  MetadataEditorSession,
  MetadataEditorSessionOptions,
  MetadataEditorSessionResponse,
} from './usecases/editorSession'
export { createMetadataEditorState, createMetadataEditorStateFromDocument } from './usecases/state'
export {
  applyMetadataFileTemplateValues,
  createMetadataFileTemplateDefaultValues,
  parseMetadataJson,
  serializeMetadataDocument,
  serializeMetadataFileTemplate,
} from './core/serializer'
export type { MetadataFileTemplateValues } from './core/serializer'
export {
  createMetadataFileCreationFieldModels,
  createMetadataFileCreationFlowModel,
  createMetadataFileCreationPlan,
  createMetadataFileCreationPlanFromFlow,
  defaultMetadataFilePath,
  isMetadataSourceRootDirectory,
  metadataCreationDirectoryForPath,
  metadataDirectoryScopeForPath,
  metadataFileCreationInputForPath,
  metadataFilePathPrompt,
  metadataSourceRoot,
  metadataSourceRootForDirectory,
  metadataTemplateFieldPrompt,
  normalizeMetadataFileCreationPath,
  normalizeMetadataFilePath,
  validateMetadataFileCreationField,
} from './usecases/metadataCreation'
export type {
  MetadataCreationPathResult,
  MetadataDirectoryScope,
  MetadataFileCreationFieldModel,
  MetadataFileCreationFlowModel,
  MetadataFileCreationFlowOptions,
  MetadataFileCreationPlan,
  MetadataFileCreationPlanResult,
} from './usecases/metadataCreation'
export { metadataCreationContract } from './core/metadataCreationContract'
export type { MetadataCreationContract } from './core/metadataCreationContract'
export {
  localizeMetadataTypeContribution,
  normalizeIdeLocale,
  tideStackText,
} from './core/i18n'
export type { TideStackIdeLocale } from './core/i18n'
export {
  metadataEditorRegistrationDefinitions,
  metadataEditorViewType,
  newMetadataFileCommandDefinitions,
  newMetadataFileCommandId,
} from './usecases/ideRegistration'
export type { MetadataEditorRegistrationDefinition, NewMetadataFileCommandDefinition } from './usecases/ideRegistration'
export {
  metadataEditorRegistrationManifest,
} from './core/metadataEditorRegistrations'
export {
  metadataTypeContributionManifest,
} from './core/metadataTypeContributions'
export {
  developConfigImportDocument,
  effectiveDevelopConfig,
  localDevelopConfigDocument,
  localDevelopConfigFromDocument,
  orderedContractDocument,
  projectDevelopConfigDocument,
  projectDevelopConfigFromDocument,
  readContractString,
} from './usecases/developConfig'
export type {
  JsonObject,
  TideStackDevelopConfigContract,
  TideStackDevelopConfigSectionContract,
  TideStackDevelopEffectiveConfig,
  TideStackDevelopLocalConfig,
  TideStackDevelopProjectConfig,
} from './usecases/developConfig'
export {
  capabilitiesForIde,
  capabilityStatus,
  tideStackDevelopCapabilities,
} from './core/capabilities'
export type {
  TideStackDevelopCapability,
  TideStackDevelopCapabilityStatus,
  TideStackDevelopIde,
} from './core/capabilities'
export { getPureSchema } from './core/pureSchema'
export {
  createMetadataEditorWebviewHtml,
  createVsCodeMetadataEditorHostScript,
  metadataEditorRuntimeFileName,
  metadataEditorWebviewTemplateFileName,
} from './adapters/browser/webRuntime'
export type { MetadataEditorWebviewHtmlOptions, VsCodeMetadataEditorHostScriptOptions } from './adapters/browser/webRuntime'
export type {
  Disposable,
  MetadataDiagnostic,
  MetadataDiagnosticSeverity,
  MetadataEditorContext,
  MetadataEditorDocumentBridge,
  MetadataEditorDocument,
  MetadataEditorHost,
  MetadataEditorKind,
  MetadataEditorRuntimeConfig,
  MetadataEditorState,
  MetadataDocumentShape,
  MetadataFileTemplate,
  MetadataFileTemplateField,
  MetadataFileTemplateFieldDefault,
  MetadataFileChange,
  MetadataFileRef,
  MetadataIdeFileBridge,
  MetadataPathMapping,
  MetadataReference,
  MetadataReferenceTarget,
  MetadataTypeContribution,
  MetadataValidationContext,
} from './core/types'

export * from './usecases/editorServices'
