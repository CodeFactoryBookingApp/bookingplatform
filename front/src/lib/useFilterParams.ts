import { useSearchParams } from 'react-router-dom'

/**
 * Filters of a listing kept in the URL (shareable, survive a reload). Changing any filter goes back
 * to the first page; `pagina` holds the zero-based page.
 */
export function useFilterParams(keys: string[]) {
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

  return {
    get,
    update,
    page: Math.max(0, Number(get('pagina')) || 0),
    setPage: (page: number) => update({ pagina: page ? String(page) : null }),
    hasFilters: keys.some((key) => params.has(key)),
    reset: () => setParams({}, { replace: true }),
  }
}
