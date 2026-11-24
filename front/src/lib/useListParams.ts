import { useSearchParams } from 'react-router-dom'
import type { AdminListQuery } from '@/api/types'

export const PAGE_SIZE = 20

/** Admin listing filters live in the URL, so a filtered view can be shared and survives a reload. */
export function useListParams(defaultSort = 'registered') {
  const [params, setParams] = useSearchParams()
  const get = (key: string) => params.get(key) ?? ''

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (!('pagina' in changes)) next.delete('pagina')
    setParams(next, { replace: true })
  }

  const query: AdminListQuery = {
    status: get('estado') || undefined,
    categoryId: get('categoria') || undefined,
    city: get('ciudad') || undefined,
    registeredFrom: get('desde') || undefined,
    registeredTo: get('hasta') || undefined,
    sort: get('orden') || defaultSort,
    direction: get('dir') === 'asc' ? 'asc' : 'desc',
    page: Math.max(0, Number(get('pagina')) || 0),
    size: PAGE_SIZE,
  }
  const hasFilters = ['estado', 'categoria', 'ciudad', 'desde', 'hasta'].some((key) => params.has(key))

  return { get, update, query, hasFilters, reset: () => setParams({}, { replace: true }) }
}
