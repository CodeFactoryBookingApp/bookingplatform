import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { preferencesApi } from '@/api/endpoints'
import type { ReminderPreferences } from '@/api/types'
import { Alert, Card, ErrorState, PageHeader } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { PageLoader } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { preferencesSchema, type PreferencesForm } from '@/lib/schemas'

const hhmm = (time: string) => time.slice(0, 5)
const browserZone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return ''
  }
}

/** HU-015: whether booking reminders reach this user, and the hours when they must wait. */
export default function PreferencesPage() {
  const queryClient = useQueryClient()
  const current = useQuery({ queryKey: ['booking-preferences'], queryFn: preferencesApi.get })

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors, isDirty },
  } = useForm<PreferencesForm>({
    resolver: zodResolver(preferencesSchema),
    values: current.data && {
      remindersEnabled: current.data.remindersEnabled,
      quietFrom: hhmm(current.data.quietFrom),
      quietUntil: hhmm(current.data.quietUntil),
      timezone: current.data.timezone ?? '',
    },
  })

  const save = useMutation({
    mutationFn: (v: PreferencesForm) =>
      preferencesApi.save({ ...v, timezone: v.timezone || null } satisfies ReminderPreferences),
    onSuccess: (saved) => {
      queryClient.setQueryData(['booking-preferences'], saved)
      toast.success('Preferencias guardadas')
    },
    onError: (error) => {
      if (!applyServerErrors(error, setError)) toast.error(errorMessage(error))
    },
  })

  if (current.isPending) return <PageLoader label="Cargando tus preferencias…" />
  if (current.isError) return <ErrorState error={current.error} onRetry={() => current.refetch()} />

  const enabled = watch('remindersEnabled')
  const from = watch('quietFrom')
  const until = watch('quietUntil')
  const zone = browserZone()

  return (
    <div className="animate-in mx-auto max-w-xl">
      <PageHeader title="Preferencias de avisos" description="Los avisos y recordatorios de reservas llegan a tu bandeja." />
      <Card>
        <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="flex flex-col gap-5">
          <label className="flex items-start gap-3 text-sm">
            <input type="checkbox" className="mt-0.5 size-4 accent-accent" {...register('remindersEnabled')} />
            <span>
              <span className="font-medium">Recibir recordatorios de mis reservas</span>
              <span className="block text-muted">Los avisos de confirmación o cancelación llegan siempre.</span>
            </span>
          </label>

          <fieldset disabled={!enabled} className="flex flex-col gap-4 disabled:opacity-60">
            <legend className="mb-1 text-sm font-medium">Horas de silencio</legend>
            <p className="-mt-2 text-sm text-muted">
              Un recordatorio que caiga en este intervalo se entrega al terminar. Si ambas horas son iguales, no hay silencio.
            </p>
            <div className="grid gap-4 sm:grid-cols-2">
              <Input label="Desde" type="time" error={errors.quietFrom?.message} {...register('quietFrom')} />
              <Input label="Hasta" type="time" error={errors.quietUntil?.message} {...register('quietUntil')} />
            </div>
            {from && until && from === until && <Alert tone="info" title="Sin horas de silencio" />}
            <div className="flex flex-wrap items-end gap-2">
              <Input
                label="Zona horaria"
                placeholder="La de cada cita"
                hint="Vacía: se usa la zona de la sede de cada cita. Ejemplo: America/Bogota"
                error={errors.timezone?.message}
                wrapperClassName="min-w-0 flex-1"
                {...register('timezone')}
              />
              {zone && (
                <Button variant="secondary" onClick={() => setValue('timezone', zone, { shouldDirty: true })}>
                  Usar {zone}
                </Button>
              )}
            </div>
          </fieldset>

          <div className="flex justify-end">
            <Button type="submit" disabled={!isDirty} loading={save.isPending}>
              Guardar
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
