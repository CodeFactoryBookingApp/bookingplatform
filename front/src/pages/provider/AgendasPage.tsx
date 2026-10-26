import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { organizationApi } from '@/api/endpoints'
import type { Agenda, Block, DayOfWeek } from '@/api/types'
import { ApiError } from '@/api/client'
import { Alert, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { DAYS, formatDate } from '@/lib/format'
import { BlocksEditor, blocksProblem, hhmm, normalizeBlocks } from './BlocksEditor'
import { useOrganization } from './OrganizationLayout'

const SLOTS = [15, 30, 60]

function summarize(blocks: Block[]): string {
  return DAYS.map((day) => {
    const ranges = blocks
      .filter((b) => b.dayOfWeek === day.value)
      .sort((a, b) => a.startTime.localeCompare(b.startTime))
      .map((b) => `${hhmm(b.startTime)}–${hhmm(b.endTime)}`)
    return ranges.length ? `${day.short} ${ranges.join(', ')}` : null
  })
    .filter(Boolean)
    .join(' · ')
}

/** HAS_FUTURE_BOOKINGS lists the bookings that would break; the message says what to do. */
function agendaError(error: unknown): string {
  if (error instanceof ApiError && error.errorCode === 'HAS_FUTURE_BOOKINGS') {
    return 'Hay reservas futuras que quedarían fuera de este cambio. Resuélvelas primero.'
  }
  return errorMessage(error)
}

export default function AgendasPage() {
  const org = useOrganization()
  const queryClient = useQueryClient()
  const agendas = useQuery({ queryKey: ['agendas', org.id], queryFn: () => organizationApi.agendas(org.id) })
  const services = useQuery({ queryKey: ['services', org.id], queryFn: () => organizationApi.services(org.id) })
  const resources = useQuery({ queryKey: ['resources', org.id], queryFn: () => organizationApi.resources(org.id) })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['agendas', org.id] })

  // ---- create
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [locationId, setLocationId] = useState(org.locations.length === 1 ? org.locations[0].id : '')
  const [resourceId, setResourceId] = useState('')
  const [slotMinutes, setSlotMinutes] = useState(30)
  const [validFrom, setValidFrom] = useState(new Date().toISOString().slice(0, 10))
  const [validUntil, setValidUntil] = useState('')
  const [serviceIds, setServiceIds] = useState<string[]>([])
  const [blocks, setBlocks] = useState<Block[]>([])
  const [formError, setFormError] = useState<string | null>(null)

  const create = useMutation({
    mutationFn: () =>
      organizationApi.createAgenda(org.id, {
        name: name.trim(),
        locationId,
        resourceId: resourceId || undefined,
        slotMinutes,
        validFrom,
        validUntil: validUntil || undefined,
        serviceIds,
        blocks,
      }),
    onSuccess: (created) => {
      refresh()
      toast.success(`Agenda «${created.name}» creada`)
      setCreating(false)
      setName('')
      setBlocks([])
      setServiceIds([])
    },
    onError: (error) => setFormError(agendaError(error)),
  })

  const submitCreate = (event: React.FormEvent) => {
    event.preventDefault()
    const problem =
      (!name.trim() && 'Ingresa un nombre.') ||
      (!locationId && 'Elige una sede.') ||
      (!validFrom && 'Elige desde cuándo es válida.') ||
      (validUntil && validUntil < validFrom && 'La fecha final no puede ser anterior a la inicial.') ||
      (serviceIds.length === 0 && 'Elige al menos un servicio que se pueda reservar en esta agenda.') ||
      blocksProblem(blocks)
    setFormError(problem || null)
    if (!problem) create.mutate()
  }

  // ---- manage one
  const [managing, setManaging] = useState<Agenda | null>(null)
  const [draft, setDraft] = useState<Block[]>([])
  const [manageError, setManageError] = useState<string | null>(null)
  const [copyFrom, setCopyFrom] = useState<DayOfWeek>('MONDAY')
  const [copyTo, setCopyTo] = useState<DayOfWeek[]>([])

  const open = (agenda: Agenda) => {
    setManaging(agenda)
    setDraft(normalizeBlocks(agenda.blocks))
    setManageError(null)
    setCopyTo([])
  }
  const applyResult = (agenda: Agenda, message: string) => {
    refresh()
    setManaging(agenda)
    setDraft(normalizeBlocks(agenda.blocks))
    setManageError(null)
    toast.success(message)
  }
  const onManageError = (error: unknown) => setManageError(agendaError(error))

  const saveBlocks = useMutation({
    mutationFn: () => organizationApi.replaceBlocks(org.id, managing!.id, draft),
    onSuccess: (agenda) => applyResult(agenda, 'Horarios guardados'),
    onError: onManageError,
  })
  const changeGranularity = useMutation({
    mutationFn: (minutes: number) => organizationApi.changeGranularity(org.id, managing!.id, minutes),
    onSuccess: (agenda) => applyResult(agenda, 'Intervalo actualizado'),
    onError: onManageError,
  })
  const copyDay = useMutation({
    mutationFn: () => organizationApi.copyDay(org.id, managing!.id, copyFrom, copyTo),
    onSuccess: (agenda) => applyResult(agenda, 'Horario copiado'),
    onError: onManageError,
  })

  const serviceName = (id: string) => services.data?.find((s) => s.id === id)?.name ?? '—'
  const locationName = (id: string) => org.locations.find((l) => l.id === id)?.name ?? '—'
  const locationResources = resources.data?.filter((r) => r.locationId === locationId && r.condition !== 'RETIRED') ?? []
  const noServices = services.isSuccess && services.data.length === 0
  const draftProblem = managing ? blocksProblem(draft) : null

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-prose text-muted">
          Una agenda define cuándo atiendes en una sede, o con un recurso en particular, y cada cuánto se ofrecen los horarios.
        </p>
        <Button onClick={() => { create.reset(); setFormError(null); setCreating(true) }} disabled={noServices}>
          <Plus size={16} aria-hidden="true" /> Nueva agenda
        </Button>
      </div>

      {noServices && (
        <div className="mb-5">
          <Alert tone="info" title="Primero crea un servicio">
            <Link to={`/negocio/${org.id}/servicios`} className="underline underline-offset-4">
              Ir a servicios
            </Link>
          </Alert>
        </div>
      )}

      {agendas.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : agendas.isError ? (
        <ErrorState error={agendas.error} onRetry={() => agendas.refetch()} />
      ) : agendas.data.length === 0 ? (
        <EmptyState title="Aún no tienes agendas" description="Sin agenda, tus servicios no tienen horarios para reservar." />
      ) : (
        <ul className="grid gap-4 lg:grid-cols-2">
          {agendas.data.map((agenda) => (
            <li key={agenda.id} className="flex flex-col rounded-lg border border-line bg-surface p-5">
              <h2 className="text-lg">{agenda.name}</h2>
              <p className="mt-1 text-sm text-muted">
                {locationName(agenda.locationId)}
                {agenda.resourceId && ` · ${resources.data?.find((r) => r.id === agenda.resourceId)?.name ?? 'recurso'}`} · cada{' '}
                {agenda.slotMinutes} min
              </p>
              <p className="mt-1 text-sm text-muted">
                Desde {formatDate(agenda.validFrom)}
                {agenda.validUntil ? ` hasta ${formatDate(agenda.validUntil)}` : ', sin fecha de fin'}
              </p>
              <p className="mt-3 text-sm">{summarize(agenda.blocks)}</p>
              <p className="mt-2 text-sm text-muted">Servicios: {agenda.serviceIds.map(serviceName).join(', ')}</p>
              <div className="mt-auto pt-4">
                <Button variant="secondary" size="sm" onClick={() => open(agenda)}>
                  Gestionar horarios
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="Nueva agenda">
        <form noValidate onSubmit={submitCreate} className="flex flex-col gap-4">
          <Input label="Nombre" required value={name} onChange={(e) => setName(e.target.value)} maxLength={120} />
          <Select
            label="Sede"
            required
            value={locationId}
            onChange={(e) => { setLocationId(e.target.value); setResourceId('') }}
          >
            <option value="">Elige una sede</option>
            {org.locations.map((l) => (
              <option key={l.id} value={l.id}>{l.name}</option>
            ))}
          </Select>
          <Select label="Recurso" hint="Opcional: déjalo vacío para toda la sede." value={resourceId} onChange={(e) => setResourceId(e.target.value)} disabled={!locationId}>
            <option value="">Toda la sede</option>
            {locationResources.map((r) => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </Select>
          <div className="grid gap-4 sm:grid-cols-3">
            <Select label="Intervalo" value={slotMinutes} onChange={(e) => setSlotMinutes(Number(e.target.value))}>
              {SLOTS.map((s) => (
                <option key={s} value={s}>{s} minutos</option>
              ))}
            </Select>
            <Input label="Válida desde" type="date" required value={validFrom} onChange={(e) => setValidFrom(e.target.value)} />
            <Input label="Hasta" type="date" hint="Opcional" value={validUntil} min={validFrom} onChange={(e) => setValidUntil(e.target.value)} />
          </div>

          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">Servicios que se reservan aquí *</legend>
            {services.data?.map((service) => (
              <label key={service.id} className="flex min-h-11 items-center gap-3 rounded-md border border-line px-3">
                <input
                  type="checkbox"
                  className="size-4 accent-accent"
                  checked={serviceIds.includes(service.id)}
                  onChange={(e) => setServiceIds((ids) => (e.target.checked ? [...ids, service.id] : ids.filter((id) => id !== service.id)))}
                />
                <span className="text-sm">{service.name}</span>
              </label>
            ))}
          </fieldset>

          <div>
            <p className="mb-2 text-sm font-medium">Horario semanal *</p>
            <BlocksEditor value={blocks} onChange={setBlocks} />
          </div>

          {formError && <Alert tone="danger" title={formError} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setCreating(false)}>Cancelar</Button>
            <Button type="submit" loading={create.isPending}>Crear agenda</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={managing !== null}
        onClose={() => setManaging(null)}
        title={managing ? `Horarios de ${managing.name}` : 'Horarios'}
        description="Los cambios se validan contra las reservas futuras."
      >
        {managing && (
          <div className="flex flex-col gap-5">
            {manageError && <Alert tone="danger" title={manageError} />}

            <BlocksEditor value={draft} onChange={setDraft} />
            {draftProblem && <p className="text-sm text-danger">{draftProblem}</p>}
            <div className="flex justify-end">
              <Button loading={saveBlocks.isPending} disabled={draftProblem !== null} onClick={() => saveBlocks.mutate()}>
                Guardar horarios
              </Button>
            </div>

            <div className="grid gap-4 border-t border-line pt-4 sm:grid-cols-[1fr_auto] sm:items-end">
              <Select
                label="Intervalo entre horarios"
                value={managing.slotMinutes}
                onChange={(e) => changeGranularity.mutate(Number(e.target.value))}
                disabled={changeGranularity.isPending}
              >
                {SLOTS.map((s) => (
                  <option key={s} value={s}>{s} minutos</option>
                ))}
              </Select>
            </div>

            <fieldset className="flex flex-col gap-3 border-t border-line pt-4">
              <legend className="mb-1 text-sm font-medium">Copiar el horario de un día a otros (se guarda al instante)</legend>
              <Select label="Copiar desde" value={copyFrom} onChange={(e) => setCopyFrom(e.target.value as DayOfWeek)}>
                {DAYS.map((d) => (
                  <option key={d.value} value={d.value}>{d.label}</option>
                ))}
              </Select>
              <div className="flex flex-wrap gap-2" role="group" aria-label="Días de destino">
                {DAYS.filter((d) => d.value !== copyFrom).map((d) => (
                  <label key={d.value} className="inline-flex min-h-11 items-center gap-2 rounded-md border border-line px-3 text-sm">
                    <input
                      type="checkbox"
                      className="size-4 accent-accent"
                      checked={copyTo.includes(d.value)}
                      onChange={(e) => setCopyTo((days) => (e.target.checked ? [...days, d.value] : days.filter((x) => x !== d.value)))}
                    />
                    {d.short}
                  </label>
                ))}
              </div>
              <Button variant="secondary" className="self-start" disabled={copyTo.length === 0} loading={copyDay.isPending} onClick={() => copyDay.mutate()}>
                Copiar
              </Button>
            </fieldset>
          </div>
        )}
      </Modal>
    </div>
  )
}
