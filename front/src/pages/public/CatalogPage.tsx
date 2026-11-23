import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Clock, MagnifyingGlass, MapPin, UsersThree } from '@phosphor-icons/react'
import { catalogApi } from '@/api/endpoints'
import { useCategories, useCities } from '@/api/hooks'
import type { CatalogQuery, ServiceCard } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { Badge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Pagination } from '@/components/ui/Blocks'
import { Skeleton } from '@/components/ui/Spinner'
import { formatDuration, formatPrice } from '@/lib/format'

const PAGE_SIZE = 12
const SORTS: { value: string; label: string; sort: NonNullable<CatalogQuery['sort']>; direction: 'asc' | 'desc' }[] = [
  { value: 'name-asc', label: 'Nombre (A–Z)', sort: 'name', direction: 'asc' },
  { value: 'price-asc', label: 'Precio: menor a mayor', sort: 'price', direction: 'asc' },
  { value: 'price-desc', label: 'Precio: mayor a menor', sort: 'price', direction: 'desc' },
  { value: 'duration-asc', label: 'Duración: más corta', sort: 'duration', direction: 'asc' },
  { value: 'provider-asc', label: 'Proveedor (A–Z)', sort: 'provider', direction: 'asc' },
]

function ServiceItem({ item }: { item: ServiceCard }) {
  return (
    <li className="flex flex-col rounded-lg border border-line bg-surface p-5 transition-colors hover:border-line-strong">
      <div className="flex items-start justify-between gap-3">
        <p className="text-sm text-muted">{item.provider.category}</p>
        {item.group && (
          <Badge tone="info">
            <UsersThree size={14} className="mr-1" aria-hidden="true" />
            Grupal, {item.customerCapacity} cupos
          </Badge>
        )}
      </div>
      <h3 className="mt-1 text-lg">{item.name}</h3>
      <p className="mt-0.5 text-sm font-medium text-ink">{item.provider.name}</p>
      {item.description && <p className="mt-2 line-clamp-3 text-sm text-muted">{item.description}</p>}

      <dl className="mt-4 flex flex-wrap gap-x-4 gap-y-1.5 text-sm text-muted">
        <div className="flex items-center gap-1.5">
          <dt className="sr-only">Ubicación</dt>
          <MapPin size={16} aria-hidden="true" />
          <dd>
            {item.location.name}, {item.location.city}
          </dd>
        </div>
        <div className="flex items-center gap-1.5">
          <dt className="sr-only">Duración</dt>
          <Clock size={16} aria-hidden="true" />
          <dd>{formatDuration(item.durationMinutes)}</dd>
        </div>
      </dl>

      <p className="mt-auto pt-5 font-display text-xl font-semibold tabular-nums">{formatPrice(item.price)}</p>
    </li>
  )
}

function ResultsSkeleton() {
  return (
    <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
      {Array.from({ length: 6 }, (_, i) => (
        <li key={i} className="rounded-lg border border-line bg-surface p-5">
          <Skeleton className="h-3 w-1/3" />
          <Skeleton className="mt-3 h-5 w-3/4" />
          <Skeleton className="mt-2 h-4 w-1/2" />
          <Skeleton className="mt-6 h-4 w-full" />
          <Skeleton className="mt-8 h-6 w-1/3" />
        </li>
      ))}
    </ul>
  )
}

export default function CatalogPage() {
  const [params, setParams] = useSearchParams()
  const categories = useCategories()
  const cities = useCities()

  const q = params.get('q') ?? ''
  const categoryId = params.get('categoryId') ?? ''
  const cityId = params.get('cityId') ?? ''
  const minPrice = params.get('minPrice') ?? ''
  const maxPrice = params.get('maxPrice') ?? ''
  const order = params.get('orden') ?? 'name-asc'
  const page = Math.max(0, Number(params.get('pagina') ?? 0) || 0)
  const sort = SORTS.find((s) => s.value === order) ?? SORTS[0]

  // The keyword is typed freely and applied on submit; the other filters apply at once.
  const [keyword, setKeyword] = useState(q)
  useEffect(() => setKeyword(q), [q])

  const update = (changes: Record<string, string | null>) => {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) next.set(key, value)
      else next.delete(key)
    }
    if (!('pagina' in changes)) next.delete('pagina')
    setParams(next, { replace: true })
  }

  const query: CatalogQuery = {
    q: q || undefined,
    categoryId: categoryId || undefined,
    cityId: cityId || undefined,
    minPrice: minPrice || undefined,
    maxPrice: maxPrice || undefined,
    sort: sort.sort,
    direction: sort.direction,
    page,
    size: PAGE_SIZE,
  }
  const result = useQuery({
    queryKey: ['catalog', query],
    queryFn: ({ signal }) => catalogApi.search(query, signal),
    placeholderData: keepPreviousData,
  })

  const handleSearch = (event: FormEvent) => {
    event.preventDefault()
    update({ q: keyword.trim() || null })
  }
  const hasFilters = Boolean(q || categoryId || cityId || minPrice || maxPrice)
  const data = result.data

  return (
    <div className="animate-in">
      <section className="pb-8 pt-2 sm:pb-10">
        <h1 className="max-w-2xl text-3xl sm:text-4xl">Encuentra y reserva el servicio que necesitas</h1>
        <p className="mt-3 max-w-xl text-muted">
          Explora servicios de negocios aprobados en tu ciudad. Compara precios y duración antes de decidir.
        </p>
        <form onSubmit={handleSearch} role="search" className="mt-6 flex max-w-2xl gap-2">
          <div className="relative flex-1">
            <MagnifyingGlass
              size={20}
              className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-faint"
              aria-hidden="true"
            />
            <input
              type="search"
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="Corte de cabello, masaje, cancha…"
              aria-label="Buscar servicios"
              className="block min-h-12 w-full rounded-md border border-line-strong bg-surface pl-10 pr-3 text-ink placeholder:text-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/25"
            />
          </div>
          <Button type="submit" size="lg">
            Buscar
          </Button>
        </form>
      </section>

      <section aria-label="Filtros" className="rounded-lg border border-line bg-surface p-4 sm:p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          <Select label="Categoría" value={categoryId} onChange={(e) => update({ categoryId: e.target.value || null })}>
            <option value="">Todas</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <Select label="Ciudad" value={cityId} onChange={(e) => update({ cityId: e.target.value || null })}>
            <option value="">Todas</option>
            {cities.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name} ({c.region})
              </option>
            ))}
          </Select>
          <Input
            label="Precio mínimo"
            type="number"
            inputMode="numeric"
            min={0}
            step={1000}
            defaultValue={minPrice}
            key={`min-${minPrice}`}
            onBlur={(e) => e.target.value !== minPrice && update({ minPrice: e.target.value || null })}
            placeholder="0"
          />
          <Input
            label="Precio máximo"
            type="number"
            inputMode="numeric"
            min={0}
            step={1000}
            defaultValue={maxPrice}
            key={`max-${maxPrice}`}
            onBlur={(e) => e.target.value !== maxPrice && update({ maxPrice: e.target.value || null })}
            placeholder="Sin límite"
          />
          <Select label="Ordenar por" value={sort.value} onChange={(e) => update({ orden: e.target.value })}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
        </div>
        {hasFilters && (
          <div className="mt-3">
            <Button variant="ghost" size="sm" onClick={() => setParams({}, { replace: true })}>
              Restablecer filtros
            </Button>
          </div>
        )}
      </section>

      <section aria-label="Resultados" aria-live="polite" aria-busy={result.isFetching} className="mt-8">
        {result.isPending ? (
          <ResultsSkeleton />
        ) : result.isError ? (
          <ErrorState error={result.error} onRetry={() => result.refetch()} />
        ) : data && data.items.length === 0 ? (
          <EmptyState
            title="No encontramos servicios"
            description={data.message ?? 'Prueba con otras palabras o quita algunos filtros.'}
            action={
              hasFilters ? (
                <Button variant="secondary" onClick={() => setParams({}, { replace: true })}>
                  Restablecer filtros
                </Button>
              ) : undefined
            }
          />
        ) : (
          data && (
            <>
              <p className="mb-4 text-sm text-muted">
                {data.total} {data.total === 1 ? 'servicio' : 'servicios'}
              </p>
              <ul className={`grid gap-4 sm:grid-cols-2 lg:grid-cols-3 ${result.isFetching ? 'opacity-70' : ''}`}>
                {data.items.map((item) => (
                  <ServiceItem key={`${item.serviceId}-${item.location.id}`} item={item} />
                ))}
              </ul>
              <Pagination page={data.page} size={data.size} total={data.total} onPage={(p) => update({ pagina: p ? String(p) : null })} />
            </>
          )
        )}
      </section>
    </div>
  )
}
