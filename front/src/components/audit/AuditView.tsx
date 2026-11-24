import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import { DownloadSimple } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { AuditEvent, AuditQuery, Page } from '@/api/types'
import { Badge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Pagination } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { endOfDayInstant, formatDateTime, startOfDayInstant } from '@/lib/format'
import { useFilterParams } from '@/lib/useFilterParams'

const PAGE_SIZE = 20
const FILTERS = ['desde', 'hasta', 'usuario', 'accion']
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const short = (id: string | null) => (id ? `…${id.slice(-8)}` : '—')

interface Props {
  scope: string
  showOrganization?: boolean
  load: (query: AuditQuery) => Promise<Page<AuditEvent>>
  exportCsv: (query: AuditQuery) => Promise<void>
}

/** HU-023: audit trail with date, actor and action filters; the CSV masks personal identifiers. */
export function AuditView({ scope, showOrganization = false, load, exportCsv }: Props) {
  const { get, update, page, setPage, hasFilters, reset } = useFilterParams(FILTERS)
  const actor = get('usuario')
  const actorValid = !actor || UUID.test(actor)

  const query: AuditQuery = {
    from: get('desde') ? startOfDayInstant(get('desde')) : undefined,
    to: get('hasta') ? endOfDayInstant(get('hasta')) : undefined,
    actorId: actorValid ? actor || undefined : undefined,
    action: get('accion') || undefined,
    page,
    size: PAGE_SIZE,
  }

  const list = useQuery({
    queryKey: ['audit', scope, query],
    queryFn: () => load(query),
    placeholderData: keepPreviousData,
  })
  const download = useMutation({
    mutationFn: () => exportCsv(query),
    onError: (error) => toast.error(errorMessage(error)),
  })

  const textFilter = (key: string) => ({
    key: `${key}-${get(key)}`,
    defaultValue: get(key),
    spellCheck: false,
    onBlur: (e: React.FocusEvent<HTMLInputElement>) =>
      e.target.value.trim() !== get(key) && update({ [key]: e.target.value.trim() || null }),
    onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => e.key === 'Enter' && e.currentTarget.blur(),
  })

  const data = list.data
  return (
    <div>
      <section aria-label="Filtros" className="rounded-lg border border-line bg-surface p-4 sm:p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Input label="Desde" type="date" value={get('desde')} max={get('hasta') || undefined} onChange={(e) => update({ desde: e.target.value || null })} />
          <Input label="Hasta" type="date" value={get('hasta')} min={get('desde') || undefined} onChange={(e) => update({ hasta: e.target.value || null })} />
          <Input label="Acción" placeholder="p. ej. BOOKING_CANCELLED" {...textFilter('accion')} />
          <Input
            label="Id del usuario"
            hint="Identificador completo (UUID)"
            error={actorValid ? undefined : 'No es un identificador válido'}
            {...textFilter('usuario')}
          />
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={reset}>
              Restablecer filtros
            </Button>
          )}
          <Button variant="secondary" size="sm" className="ml-auto" loading={download.isPending} onClick={() => download.mutate()}>
            <DownloadSimple size={16} aria-hidden="true" /> Exportar CSV
          </Button>
        </div>
      </section>

      <div className="mt-6" aria-busy={list.isFetching}>
        {list.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : data && data.items.length === 0 ? (
          <EmptyState title="Sin eventos" description="Ninguna operación coincide con estos filtros." />
        ) : (
          data && (
            <>
              <div className={`overflow-x-auto rounded-lg border border-line bg-surface ${list.isFetching ? 'opacity-70' : ''}`}>
                <table className="w-full min-w-[52rem] text-left text-sm">
                  <caption className="sr-only">Eventos de auditoría</caption>
                  <thead className="border-b border-line bg-sunken/60 text-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-medium">Fecha</th>
                      <th scope="col" className="px-4 py-3 font-medium">Usuario</th>
                      <th scope="col" className="px-4 py-3 font-medium">Acción</th>
                      <th scope="col" className="px-4 py-3 font-medium">Recurso</th>
                      {showOrganization && <th scope="col" className="px-4 py-3 font-medium">Negocio</th>}
                      <th scope="col" className="px-4 py-3 font-medium">Resultado</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.items.map((event) => (
                      <tr key={event.id} className="align-top">
                        <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(event.occurredAt)}</td>
                        <td className="px-4 py-3">
                          {event.actorId ? (
                            <button
                              type="button"
                              className="font-mono text-xs underline decoration-line-strong underline-offset-4 hover:text-accent-ink"
                              title={`Filtrar por ${event.actorId}`}
                              onClick={() => update({ usuario: event.actorId })}
                            >
                              {short(event.actorId)}
                            </button>
                          ) : (
                            <span className="text-muted">Sistema</span>
                          )}
                          {event.actorRole && <p className="text-xs text-muted">{event.actorRole}</p>}
                        </td>
                        <td className="px-4 py-3 font-mono text-xs">{event.action}</td>
                        <td className="px-4 py-3">
                          <p className="text-xs">{event.resourceType}</p>
                          <p className="font-mono text-xs text-muted" title={event.resourceId}>{short(event.resourceId)}</p>
                        </td>
                        {showOrganization && (
                          <td className="px-4 py-3 font-mono text-xs text-muted" title={event.organizationId ?? undefined}>
                            {short(event.organizationId)}
                          </td>
                        )}
                        <td className="px-4 py-3">
                          <Badge tone={/fail|denied|error|rejected/i.test(event.result) ? 'danger' : 'ok'}>{event.result}</Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={data.page} size={data.size} total={data.total} onPage={setPage} />
            </>
          )
        )}
      </div>
    </div>
  )
}
