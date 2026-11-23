import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { DownloadSimple } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints'
import { useCategories } from '@/api/hooks'
import type { ProviderRow } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, Pagination } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { ReasonModal } from '@/components/ui/ReasonModal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { formatDate, ORGANIZATION_STATUS } from '@/lib/format'
import { useListParams } from '@/lib/useListParams'

type Action = 'approval' | 'rejection' | 'suspension' | 'reactivation'
const ACTIONS: Record<Action, { title: string; confirm: string; danger: boolean; description: string; done: string }> = {
  approval: { title: 'Aprobar proveedor', confirm: 'Aprobar', danger: false, description: 'Pasa a estar activo y recibe un correo de aprobación.', done: 'Proveedor aprobado' },
  rejection: { title: 'Rechazar solicitud', confirm: 'Rechazar', danger: true, description: 'La solicitud queda rechazada.', done: 'Solicitud rechazada' },
  suspension: { title: 'Suspender proveedor', confirm: 'Suspender', danger: true, description: 'Deja de ser visible y reservable.', done: 'Proveedor suspendido' },
  reactivation: { title: 'Reactivar proveedor', confirm: 'Reactivar', danger: false, description: 'Vuelve a estar visible y reservable.', done: 'Proveedor reactivado' },
}
const SORTS = [
  { value: 'registered', label: 'Fecha de registro' },
  { value: 'name', label: 'Nombre' },
  { value: 'status', label: 'Estado' },
  { value: 'category', label: 'Categoría' },
]

export default function AdminProvidersPage() {
  const { get, update, query, hasFilters, reset } = useListParams()
  const categories = useCategories()
  const queryClient = useQueryClient()
  const [target, setTarget] = useState<{ row: ProviderRow; action: Action } | null>(null)

  const list = useQuery({
    queryKey: ['admin-providers', query],
    queryFn: () => adminApi.providers(query),
    placeholderData: keepPreviousData,
  })

  const exportCsv = useMutation({
    mutationFn: () => adminApi.exportProviders(query),
    onError: (error) => toast.error(errorMessage(error)),
  })

  const run = async (reason: string, confirm: boolean) => {
    if (!target) return
    await adminApi.providerAction(target.row.id, target.action, target.action === 'suspension' ? { reason, confirm } : { reason })
    toast.success(ACTIONS[target.action].done)
    queryClient.invalidateQueries({ queryKey: ['admin-providers'] })
  }

  const actionsFor = (status: ProviderRow['status']): Action[] =>
    status === 'PENDING_APPROVAL' ? ['approval', 'rejection'] : status === 'ACTIVE' ? ['suspension'] : status === 'SUSPENDED' ? ['reactivation'] : []

  const data = list.data
  return (
    <div>
      <section aria-label="Filtros" className="rounded-lg border border-line bg-surface p-4 sm:p-5">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Select label="Estado" value={get('estado')} onChange={(e) => update({ estado: e.target.value || null })}>
            <option value="">Todos</option>
            {Object.entries(ORGANIZATION_STATUS).map(([value, { label }]) => (
              <option key={value} value={value}>{label}</option>
            ))}
          </Select>
          <Select label="Categoría" value={get('categoria')} onChange={(e) => update({ categoria: e.target.value || null })}>
            <option value="">Todas</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>{c.name}</option>
            ))}
          </Select>
          <Input
            label="Ciudad"
            key={`city-${get('ciudad')}`}
            defaultValue={get('ciudad')}
            onBlur={(e) => e.target.value.trim() !== get('ciudad') && update({ ciudad: e.target.value.trim() || null })}
            onKeyDown={(e) => e.key === 'Enter' && e.currentTarget.blur()}
          />
          <Select label="Ordenar por" value={query.sort} onChange={(e) => update({ orden: e.target.value })}>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>{s.label}</option>
            ))}
          </Select>
          <Input label="Registrado desde" type="date" value={get('desde')} max={get('hasta') || undefined} onChange={(e) => update({ desde: e.target.value || null })} />
          <Input label="Registrado hasta" type="date" value={get('hasta')} min={get('desde') || undefined} onChange={(e) => update({ hasta: e.target.value || null })} />
          <Select label="Dirección" value={query.direction} onChange={(e) => update({ dir: e.target.value })}>
            <option value="desc">Más recientes primero</option>
            <option value="asc">Más antiguos primero</option>
          </Select>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {hasFilters && (
            <Button variant="ghost" size="sm" onClick={reset}>
              Restablecer filtros
            </Button>
          )}
          <Button variant="secondary" size="sm" className="ml-auto" loading={exportCsv.isPending} onClick={() => exportCsv.mutate()}>
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
          <EmptyState title="Sin proveedores" description="Ningún proveedor coincide con estos filtros." />
        ) : (
          data && (
            <>
              <div className={`overflow-x-auto rounded-lg border border-line bg-surface ${list.isFetching ? 'opacity-70' : ''}`}>
                <table className="w-full min-w-[56rem] text-left text-sm">
                  <caption className="sr-only">Proveedores registrados</caption>
                  <thead className="border-b border-line bg-sunken/60 text-muted">
                    <tr>
                      <th scope="col" className="px-4 py-3 font-medium">Negocio</th>
                      <th scope="col" className="px-4 py-3 font-medium">Categoría</th>
                      <th scope="col" className="px-4 py-3 font-medium">Contacto</th>
                      <th scope="col" className="px-4 py-3 font-medium">Ciudades</th>
                      <th scope="col" className="px-4 py-3 font-medium">Estado</th>
                      <th scope="col" className="px-4 py-3 font-medium">Registro</th>
                      <th scope="col" className="px-4 py-3"><span className="sr-only">Acciones</span></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.items.map((row) => (
                      <tr key={row.id} className="align-top">
                        <td className="px-4 py-3">
                          <p className="font-medium">{row.name}</p>
                          <p className="text-xs text-muted">{row.identificationType} {row.identificationNumber}</p>
                        </td>
                        <td className="px-4 py-3">{row.category}</td>
                        <td className="px-4 py-3">
                          <p>{row.contactEmail}</p>
                          <p className="text-xs text-muted">{row.contactPhone}</p>
                        </td>
                        <td className="px-4 py-3">{row.cities ?? '—'}</td>
                        <td className="px-4 py-3"><StatusBadge map={ORGANIZATION_STATUS} value={row.status} /></td>
                        <td className="px-4 py-3 whitespace-nowrap">{formatDate(row.registeredAt)}</td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-1">
                            {actionsFor(row.status).map((action) => (
                              <Button
                                key={action}
                                size="sm"
                                variant={action === 'approval' || action === 'reactivation' ? 'primary' : 'secondary'}
                                onClick={() => setTarget({ row, action })}
                                aria-label={`${ACTIONS[action].confirm} ${row.name}`}
                              >
                                {ACTIONS[action].confirm}
                              </Button>
                            ))}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <Pagination page={data.page} size={data.size} total={data.total} onPage={(p) => update({ pagina: p ? String(p) : null })} />
            </>
          )
        )}
      </div>

      {target && (
        <ReasonModal
          open
          title={`${ACTIONS[target.action].title}: ${target.row.name}`}
          description={ACTIONS[target.action].description}
          confirmLabel={ACTIONS[target.action].confirm}
          danger={ACTIONS[target.action].danger}
          onSubmit={run}
          onClose={() => setTarget(null)}
        />
      )}
    </div>
  )
}
