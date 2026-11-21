import {
  createMetadataFileTemplateDefaultValues,
  serializeMetadataFileTemplate,
  type MetadataFileTemplateValues,
} from '../core/serializer'
import { tideStackText } from '../core/i18n'
import { metadataCreationContract } from '../core/metadataCreationContract'
import type { MetadataFileTemplateField, MetadataTypeContribution } from '../core/types'

export const metadataSourceRoot = metadataCreationContract.sourceRoot
const pathRules = metadataCreationContract.pathRules

export type MetadataDirectoryScope = 'root' | 'nested'

export interface MetadataCreationPathResult {
  path?: string
  error?: string
}

export interface MetadataFileCreationFlowModel {
  metadataType: string
  displayName: string
  creationDirectory: string
  defaultPath: string
  defaultPathInput: string
  pathInputLocked: boolean
  pathPrompt: string
  fields: MetadataFileCreationFieldModel[]
}

export interface MetadataFileCreationFlowOptions {
  selectedDirectory?: string
  moduleRootDirectory?: string
  locale?: string
}

export interface MetadataFileCreationFieldModel {
  name: string
  label: string
  labelRemark?: string
  required: boolean
  defaultValue: unknown
  prompt: string
}

export interface MetadataFileCreationPlan {
  targetPath: string
  templateValues: MetadataFileTemplateValues
  text: string
}

export interface MetadataFileCreationPlanResult {
  plan?: MetadataFileCreationPlan
  error?: string
}

export function createMetadataFileCreationFlowModel(
  contribution: MetadataTypeContribution,
  options: MetadataFileCreationFlowOptions = {},
): MetadataFileCreationFlowModel {
  const creationDirectory = metadataCreationDirectoryForPath(
    options.selectedDirectory,
    contribution,
    options.moduleRootDirectory,
  )
  const defaultPath = defaultMetadataFilePath(creationDirectory, contribution)
  return {
    metadataType: contribution.type,
    displayName: contribution.displayName,
    creationDirectory,
    defaultPath,
    defaultPathInput: metadataFileCreationInputForPath(defaultPath, creationDirectory),
    pathInputLocked: Boolean(contribution.fixedFileName),
    pathPrompt: metadataFilePathPrompt(contribution, options.locale),
    fields: createMetadataFileCreationFieldModels(contribution, defaultPath, options.locale),
  }
}

export function createMetadataFileCreationFieldModels(
  contribution: MetadataTypeContribution,
  targetPath: string,
  locale?: string,
): MetadataFileCreationFieldModel[] {
  const fields = contribution.fileTemplate?.fields ?? []
  const defaults = createMetadataFileTemplateDefaultValues(contribution, targetPath)
  return fields.map((field) => ({
    name: field.name,
    label: field.label,
    labelRemark: field.labelRemark,
    required: field.required === true,
    defaultValue: defaults[field.name],
    prompt: metadataTemplateFieldPrompt(contribution, field, locale),
  }))
}

export function normalizeMetadataFileCreationPath(
  contribution: MetadataTypeContribution,
  model: Pick<MetadataFileCreationFlowModel, 'creationDirectory'>,
  input: string,
  locale?: string,
): MetadataCreationPathResult {
  return normalizeMetadataFilePath(input, contribution, model.creationDirectory, locale)
}

export function validateMetadataFileCreationField(
  field: Pick<MetadataFileCreationFieldModel, 'label' | 'required'>,
  value: unknown,
  locale?: string,
): string | undefined {
  if (field.required && String(value ?? '').trim() === '') {
    return tideStackText('metadata.error.fieldRequired', locale, { label: field.label })
  }
  return undefined
}

export function createMetadataFileCreationPlanFromFlow(
  contribution: MetadataTypeContribution,
  model: Pick<MetadataFileCreationFlowModel, 'creationDirectory'>,
  pathInput: string,
  templateValues: MetadataFileTemplateValues = {},
  locale?: string,
): MetadataFileCreationPlanResult {
  const pathResult = normalizeMetadataFileCreationPath(contribution, model, pathInput, locale)
  if (!pathResult.path) {
    return { error: pathResult.error ?? tideStackText('metadata.error.enterPath', locale) }
  }

  const fields = createMetadataFileCreationFieldModels(contribution, pathResult.path, locale)
  const values: MetadataFileTemplateValues = {}
  for (const field of fields) {
    const value = field.name in templateValues ? templateValues[field.name] : field.defaultValue
    const error = validateMetadataFileCreationField(field, value, locale)
    if (error) {
      return { error }
    }
    values[field.name] = typeof value === 'string' ? value.trim() : value
  }

  return {
    plan: createMetadataFileCreationPlan(contribution, pathResult.path, values),
  }
}

export function defaultMetadataFilePath(
  selectedDirectory: string | undefined,
  contribution: MetadataTypeContribution,
): string {
  if (contribution.fixedFileName) {
    if (selectedDirectory && isMetadataSourceRootDirectory(selectedDirectory)) {
      return `${selectedDirectory}/${contribution.fixedFileName}`
    }
    return `${metadataSourceRoot}/${contribution.fixedFileName}`
  }
  const defaultFileName = contribution.fileTemplate?.defaultFileName ?? contribution.fixedFileName ?? pathRules.fallbackFileName
  if (selectedDirectory) {
    return `${selectedDirectory}/${defaultFileName}`
  }
  return `${metadataSourceRoot}/${defaultFileName}`
}

export function metadataFileCreationInputForPath(targetPath: string, creationDirectory: string): string {
  const normalizedTargetPath = trimSlashes(normalizePath(targetPath))
  const normalizedCreationDirectory = trimSlashes(normalizePath(creationDirectory))
  const creationPrefix = `${normalizedCreationDirectory}/`
  return normalizedTargetPath.startsWith(creationPrefix)
    ? normalizedTargetPath.slice(creationPrefix.length)
    : normalizedTargetPath
}

export function metadataCreationDirectoryForPath(
  selectedDirectory: string | undefined,
  contribution: MetadataTypeContribution,
  moduleRootDirectory?: string,
): string {
  const sourceRoot = metadataSourceRootForDirectory(selectedDirectory, moduleRootDirectory)
  const scope = selectedDirectory ? metadataDirectoryScopeForPath(selectedDirectory) : undefined
  if (contribution.fixedFileName || !scope || !selectedDirectory) {
    return sourceRoot
  }
  return trimSlashes(normalizePath(selectedDirectory))
}

export function metadataDirectoryScopeForPath(path: string | undefined): MetadataDirectoryScope | undefined {
  if (!path) {
    return undefined
  }
  if (isMetadataSourceRootDirectory(path)) {
    return 'root'
  }
  return metadataSourceRootForExistingPath(path) ? 'nested' : undefined
}

export function metadataSourceRootForDirectory(
  selectedDirectory: string | undefined,
  moduleRootDirectory?: string,
): string {
  const existingSourceRoot = selectedDirectory && metadataSourceRootForExistingPath(selectedDirectory)
  if (existingSourceRoot) {
    return existingSourceRoot
  }

  const sourceRootFromSrcMain = selectedDirectory && metadataSourceRootFromSrcMainPath(selectedDirectory)
  if (sourceRootFromSrcMain) {
    return sourceRootFromSrcMain
  }

  const normalizedModuleRoot = moduleRootDirectory && trimSlashes(normalizePath(moduleRootDirectory))
  if (normalizedModuleRoot) {
    return `${normalizedModuleRoot}/${metadataSourceRoot}`
  }
  return metadataSourceRoot
}

export function normalizeMetadataFilePath(
  input: string,
  contribution: MetadataTypeContribution,
  selectedDirectory?: string,
  locale?: string,
): MetadataCreationPathResult {
  const normalizedInput = stripRelativeCurrentDirectoryPrefix(normalizePath(input.trim()))
  const directoryInput = normalizedInput.endsWith('/')
  const trimmed = trimSlashes(normalizedInput)
  if (!trimmed) {
    return { error: tideStackText('metadata.error.enterPath', locale) }
  }

  const defaultFileName = contribution.fileTemplate?.defaultFileName ?? contribution.fixedFileName
  if (contribution.fixedFileName) {
    const creationDirectory = metadataCreationDirectoryForPath(selectedDirectory, contribution)
    const path = trimmed.includes('/') ? trimmed : `${creationDirectory}/${trimmed}`
    if (baseName(path) !== contribution.fixedFileName || !isFixedMetadataSourceRootFile(path, contribution.fixedFileName)) {
      return {
        error: tideStackText('metadata.error.fixedFilePath', locale, {
          displayName: contribution.displayName,
          fixedFileName: contribution.fixedFileName,
        }),
      }
    }
    return { path }
  }

  if (!contribution.fileSuffix) {
    return defaultFileName
      ? { path: `${metadataSourceRoot}/${defaultFileName}` }
      : { error: tideStackText('metadata.error.noTemplate', locale) }
  }

  const filePath = directoryInput ? `${trimmed}/${defaultFileName}` : trimmed
  const withSuffix = filePath.endsWith(pathRules.jsonExtension) ? filePath : `${filePath}${contribution.fileSuffix}`
  if (!withSuffix.endsWith(contribution.fileSuffix)) {
    return {
      error: tideStackText('metadata.error.fileSuffix', locale, {
        displayName: contribution.displayName,
        fileSuffix: contribution.fileSuffix,
      }),
    }
  }
  const creationDirectory = metadataCreationDirectoryForPath(selectedDirectory, contribution)
  return {
    path: metadataSourceRootForExistingPath(withSuffix) ? withSuffix : `${creationDirectory}/${withSuffix}`,
  }
}

export function createMetadataFileCreationPlan(
  contribution: MetadataTypeContribution,
  targetPath: string,
  templateValues: MetadataFileTemplateValues = createMetadataFileTemplateDefaultValues(contribution, targetPath),
): MetadataFileCreationPlan {
  return {
    targetPath,
    templateValues,
    text: serializeMetadataFileTemplate(contribution, templateValues),
  }
}

export function metadataFilePathPrompt(contribution: MetadataTypeContribution, locale?: string): string {
  return tideStackText('metadata.new.filePath', locale, { displayName: contribution.displayName })
}

export function metadataTemplateFieldPrompt(
  contribution: MetadataTypeContribution,
  field: MetadataFileTemplateField,
  locale?: string,
): string {
  const title = tideStackText('metadata.new.field', locale, {
    displayName: contribution.displayName,
    label: field.label,
  })
  return field.labelRemark ? `${title}\n${field.labelRemark}` : title
}

export function normalizePath(path: string): string {
  return path.replace(/\\/g, '/')
}

export function trimSlashes(path: string): string {
  return path.replace(/^\/+|\/+$/g, '')
}

export function baseName(path: string): string {
  return normalizePath(path).split('/').pop() ?? ''
}

function directoryName(path: string): string {
  const normalizedPath = trimSlashes(normalizePath(path))
  const index = normalizedPath.lastIndexOf('/')
  return index >= 0 ? normalizedPath.slice(0, index) : ''
}

export function isMetadataSourceRootDirectory(path: string): boolean {
  const normalizedPath = trimSlashes(normalizePath(path))
  return normalizedPath === metadataSourceRoot || normalizedPath.endsWith(`/${metadataSourceRoot}`)
}

function metadataSourceRootForExistingPath(path: string): string | undefined {
  const normalizedPath = trimSlashes(normalizePath(path))
  if (isMetadataSourceRootDirectory(normalizedPath)) {
    return normalizedPath
  }
  if (normalizedPath.startsWith(`${metadataSourceRoot}/`)) {
    return metadataSourceRoot
  }
  const marker = `/${metadataSourceRoot}/`
  const markerIndex = normalizedPath.lastIndexOf(marker)
  if (markerIndex >= 0) {
    return normalizedPath.slice(0, markerIndex + marker.length - 1)
  }
  return undefined
}

function metadataSourceRootFromSrcMainPath(path: string): string | undefined {
  const normalizedPath = trimSlashes(normalizePath(path))
  const segments = normalizedPath.split('/').filter(Boolean)
  const markerSegments = pathRules.sourceMainSegments
  for (let index = segments.length - markerSegments.length; index >= 0; index -= 1) {
    if (markerSegments.every((segment, offset) => segments[index + offset] === segment)) {
      return [...segments.slice(0, index), ...metadataSourceRoot.split('/')].join('/')
    }
  }
  return undefined
}

function stripRelativeCurrentDirectoryPrefix(path: string): string {
  return path.startsWith(pathRules.relativeCurrentDirectoryPrefix)
    ? path.slice(pathRules.relativeCurrentDirectoryPrefix.length)
    : path
}

function isFixedMetadataSourceRootFile(path: string, fixedFileName: string): boolean {
  return baseName(path) === fixedFileName && isMetadataSourceRootDirectory(directoryName(path))
}
