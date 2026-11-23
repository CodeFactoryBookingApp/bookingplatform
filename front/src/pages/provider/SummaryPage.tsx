import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { PencilSimple } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { z } from 'zod'
import { organizationApi } from '@/api/endpoints'
import { useCategories, useCities } from '@/api/hooks'
import { Alert, Card } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { formatDate } from '@/lib/format'
import { policySchema } from '@/lib/schemas'
import { useOrganization } from './OrganizationLayout'

type PolicyInput = z.input<typeof policySchema>
type PolicyOutput = z.output<typeof policySchema>

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
    resolver: zodResolver(policySchema),
    values: {
      bookingNoticeMinutesMin: policy.bookingNoticeMinutesMin,
      bookingNoticeMinutesMax: policy.bookingNoticeMinutesMax,
      freeCancellationWindowMinutes: policy.freeCancellationWindowMinutes,
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
          <Button variant="secondary" size="sm" onClick={() => setEditing(true)}>
            <PencilSimple size={16} aria-hidden="true" /> Editar
          </Button>
        </div>
        <dl className="divide-y divide-line">
          <Row label="Anticipación mínima">{humanMinutes(policy.bookingNoticeMinutesMin)}</Row>
          <Row label="Anticipación máxima">{humanMinutes(policy.bookingNoticeMinutesMax)}</Row>
          <Row label="Cancelación gratuita">hasta {humanMinutes(policy.freeCancellationWindowMinutes)} antes</Row>
          <Row label="Versión">{policy.version}</Row>
        </dl>
      </Card>

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
