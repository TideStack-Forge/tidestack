export type SchemaChangeSource = 'code'

export interface SchemaChangeSourceTracker {
  decorateOnBuildPanels(event: unknown): void
  consume(): SchemaChangeSource | undefined
}

export function reorderSchemaLike<T>(previous: unknown, next: T): T
export function createSchemaChangeSourceTracker(): SchemaChangeSourceTracker
