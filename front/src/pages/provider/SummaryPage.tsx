import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PencilSimple, Plus, X } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { z } from 'zod'
import { organizationApi } from '@/api/endpoints'
import { useCategories, useCities } from '@/api/hooks'
import { Alert, Card, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { formatDate } from '@/lib/format'
import { MAX_LEAD_MINUTES, policyEditSchema } from '@/lib/schemas'
import { Skeleton } from '@/components/ui/Spinner'
import { useOrganization } from './OrganizationLayout'
import { useAuth } from '@/auth/AuthContext'

type PolicyInput = z.input<typeof policyEditSchema>
type PolicyOutput = z.output<typeof policyEditSchema>

/** 90 -> "1 h 30 min"; the API stores minutes, people think in hours and days. */
function humanMinutes(total: number): string {
  if (total === 0) return '0 min'
  const days = Math.floor(total / 1440)
  const hours = Math.floor((total % 1440) / 60)
  const minutes = total % 60
  return [days && `${days} d`, hours && `${hours} h`, minutes && `${minutes} min`].filter(Boolean).join(' ')
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-0.5 py-2.5 sm:grid-cols-[11rem_1fr] sm:gap-4">
      <dt className="text-sm text-muted">{label}</dt>
      <dd className="min-w-0 break-words">{children}</dd>
    </div>
  )
}

export default function SummaryPage() {
  const org = useOrganization()
  const readOnly = useAuth().user?.role === 'ADMIN'
  const categories = useCategories()
  const cities = useCities()
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const policy = org.operationPolicy

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<PolicyInput, unknown, PolicyOutput>({
    resolver: zodResolver(policyEditSchema),
    values: {
      bookingNoticeMinutesMin: policy.bookingNoticeMinutesMin,
      bookingNoticeMinutesMax: policy.bookingNoticeMinutesMax,
      freeCancellationWindowMinutes: policy.freeCancellationWindowMinutes,
      manualConfirmation: policy.manualConfirmation ?? false,
    },
  })

  const save = useMutation({
    mutationFn: (values: PolicyOutput) => organizationApi.changePolicy(org.id, values),
    onSuccess: (updated) => {
      queryClient.setQueryData(['organization', org.id], updated)
      setEditing(false)
      toast.success('Reglas actualizadas. Aplican a las reservas nuevas.')
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError)) toast.error(errorMessage(error))
    },
  })

  const category = categories.data?.find((c) => c.id === org.categoryId)?.name
  const cityName = (id: string) => cities.data?.find((c) => c.id === id)?.name

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <Card>
        <h2 className="mb-2 text-lg">Datos del negocio</h2>
        <dl className="divide-y divide-line">
          <Row label="Categoría">{category ?? '—'}</Row>
          <Row label="Identificación">
            {org.identificationType} {org.identificationNumber}
          </Row>
          <Row label="Correo de contacto">{org.contactEmail}</Row>
          <Row label="Teléfono">{org.contactPhone}</Row>
          <Row label="Zona horaria">{org.timezone}</Row>
          {org.approvedAt && <Row label="Aprobado el">{formatDate(org.approvedAt)}</Row>}
        </dl>
        <p className="mt-3 text-xs text-muted">Los datos del negocio y las sedes se definen al registrarlo.</p>
      </Card>

      <Card>
        <div className="mb-2 flex items-center justify-between gap-3">
          <h2 className="text-lg">Reglas de reserva</h2>
          {!readOnly && (
            <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
              <PencilSimple size={16} aria-hidden="true" /> Editar
            </Button>
          )}
        </div>
        <dl className="divide-y divide-line">
          <Row label="Anticipación mínima">{humanMinutes(policy.bookingNoticeMinutesMin)}</Row>
          <Row label="Anticipación máxima">{humanMinutes(policy.bookingNoticeMinutesMax)}</Row>
          <Row label="Cancelación gratuita">hasta {humanMinutes(policy.freeCancellationWindowMinutes)} antes</Row>
          <Row label="Confirmación">
            {policy.manualConfirmation ? 'Manual: tú confirmas cada reserva' : 'Automática al reservar'}
          </Row>
          <Row label="Versión">{policy.version}</Row>
        </dl>
      </Card>

      {!readOnly && <RemindersCard organizationId={org.id} />}

      <Card className="lg:col-span-2">
        <h2 className="mb-3 text-lg">Sedes</h2>
        <ul className="grid gap-3 sm:grid-cols-2">
          {org.locations.map((location) => (
            <li key={location.id} className="rounded-md border border-line p-4">
              <p className="font-medium">{location.name}</p>
              <p className="text-sm text-muted">
                {location.address}
                {cityName(location.cityId) && `, ${cityName(location.cityId)}`}
              </p>
            </li>
          ))}
        </ul>
      </Card>

      <Modal
        open={editing}
        onClose={() => setEditing(false)}
        title="Editar reglas de reserva"
        description="Se crea una nueva versión. Las reservas existentes conservan las reglas con las que se hicieron."
      >
        <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="flex flex-col gap-4">
          <Input
            label="Anticipación mínima (minutos)"
            type="number"
            inputMode="numeric"
            min={0}
            error={errors.bookingNoticeMinutesMin?.message}
            {...register('bookingNoticeMinutesMin')}
          />
          <Input
            label="Anticipación máxima (minutos)"
            type="number"
            inputMode="numeric"
            min={0}
            error={errors.bookingNoticeMinutesMax?.message}
            {...register('bookingNoticeMinutesMax')}
          />
          <Input
            label="Cancelación gratuita hasta (minutos antes)"
            type="number"
            inputMode="numeric"
            min={0}
            error={errors.freeCancellationWindowMinutes?.message}
            {...register('freeCancellationWindowMinutes')}
          />
          <label className="flex items-start gap-3 rounded-md border border-line p-3 text-sm">
            <input type="checkbox" className="mt-0.5 size-4 accent-accent" {...register('manualConfirmation')} />
            <span>
              <span className="font-medium">Confirmar cada reserva a mano</span>
              <span className="block text-muted">
                Las reservas quedan «Reservada» hasta que las confirmes en la pestaña Reservas.
              </span>
            </span>
          </label>
          {save.isError && !Object.keys(errors).length && <Alert tone="danger" title={errorMessage(save.error)} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setEditing(false)}>
              Cancelar
            </Button>
            <Button type="submit" loading={save.isPending}>
              Guardar
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}

const PRESETS = [
  { minutes: 10080, label: '1 semana' },
  { minutes: 1440, label: '24 h' },
  { minutes: 120, label: '2 h' },
  { minutes: 60, label: '1 h' },
]

/** HU-015: how long before each appointment the clients get a reminder. Empty list = no reminders. */
function RemindersCard({ organizationId }: { organizationId: string }) {
  const queryClient = useQueryClient()
  const key = ['booking-reminders', organizationId]
  const current = useQuery({ queryKey: key, queryFn: () => organizationApi.reminders(organizationId) })
  const [draft, setDraft] = useState<number[] | null>(null)
  const [custom, setCustom] = useState('')
  const [error, setError] = useState<string | null>(null)

  const values = draft ?? current.data?.leadMinutes ?? []
  const sorted = [...values].sort((a, b) => b - a)

  const save = useMutation({
    mutationFn: (leadMinutes: number[]) => organizationApi.saveReminders(organizationId, leadMinutes),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved)
      setDraft(null)
      toast.success(saved.leadMinutes.length ? 'Recordatorios guardados.' : 'Recordatorios desactivados.')
    },
    onError: (e) => toast.error(errorMessage(e)),
  })

  const add = (minutes: number) => {
    setError(null)
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > MAX_LEAD_MINUTES) {
      return setError(`Entre 1 y ${MAX_LEAD_MINUTES} minutos (7 días).`)
    }
    if (values.includes(minutes)) return setError('Ese recordatorio ya está en la lista.')
    if (values.length >= 10) return setError('Máximo 10 recordatorios.')
    setDraft([...values, minutes])
    setCustom('')
  }

  return (
    <Card className="lg:col-span-2">
      <h2 className="text-lg">Recordatorios a clientes</h2>
      <p className="mt-1 mb-4 text-sm text-muted">
        Cuánto antes de cada cita llega un recordatorio a la bandeja del cliente. Sin recordatorios, no se envía ninguno.
      </p>
      {current.isPending ? (
        <Skeleton className="h-16 w-full" />
      ) : current.isError ? (
        <ErrorState error={current.error} onRetry={() => current.refetch()} />
      ) : (
        <div className="flex flex-col gap-4">
          {sorted.length === 0 ? (
            <Alert tone="warn" title="Los recordatorios están desactivados" />
          ) : (
            <ul className="flex flex-wrap gap-2" aria-label="Recordatorios configurados">
              {sorted.map((m) => (
                <li key={m} className="inline-flex items-center gap-1 rounded-full border border-line-strong bg-sunken py-1 pl-3 pr-1 text-sm">
                  {humanMinutes(m)} antes
                  <button
                    type="button"
                    aria-label={`Quitar recordatorio de ${humanMinutes(m)}`}
                    onClick={() => setDraft(values.filter((v) => v !== m))}
                    className="inline-flex size-7 items-center justify-center rounded-full text-muted hover:bg-surface hover:text-ink"
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="flex flex-wrap items-end gap-2">
            {PRESETS.filter((p) => !values.includes(p.minutes)).map((p) => (
              <Button key={p.minutes} variant="secondary" size="sm" onClick={() => add(p.minutes)}>
                <Plus size={14} aria-hidden="true" /> {p.label}
              </Button>
            ))}
            <form
              className="flex items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault()
                add(Number(custom))
              }}
            >
              <Input
                label="Otro (minutos)"
                type="number"
                inputMode="numeric"
                min={1}
                max={MAX_LEAD_MINUTES}
                value={custom}
                onChange={(e) => setCustom(e.target.value)}
                wrapperClassName="w-36"
              />
              <Button type="submit" variant="secondary" disabled={!custom}>
                Agregar
              </Button>
            </form>
          </div>
          {error && (
            <p role="alert" className="text-sm text-danger">
              {error}
            </p>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            {draft !== null && (
              <Button variant="ghost" onClick={() => setDraft(null)}>
                Descartar cambios
              </Button>
            )}
            {values.length > 0 && (
              <Button variant="ghost" onClick={() => setDraft([])}>
                Desactivar todos
              </Button>
            )}
            <Button disabled={draft === null} loading={save.isPending} onClick={() => save.mutate(values)}>
              Guardar recordatorios
            </Button>
          </div>
        </div>
      )}
    </Card>
  )
}
