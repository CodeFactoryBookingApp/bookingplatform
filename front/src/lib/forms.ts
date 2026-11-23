import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { fieldErrors } from './errors'

/**
 * Puts the backend's per-field messages (details = field → message, already validated server side)
 * on the form. `rename` maps API field names to form paths when the form groups them differently.
 * Returns true when at least one message was placed.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  rename: (field: string) => string = (field) => field,
): boolean {
  const entries = fieldErrors(error)
  for (const [field, message] of entries) {
    // Spring reports "locations[0].name"; react-hook-form paths use "locations.0.name"
    const path = rename(field).replace(/\[(\d+)\]/g, '.$1')
    setError(path as Path<T>, { type: 'server', message })
  }
  return entries.length > 0
}
