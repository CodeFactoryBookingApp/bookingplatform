import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Wrench } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { z } from 'zod'
import { organizationApi } from '@/api/endpoints'
import type { Resource } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { Alert, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { formatDateTime, RESOURCE_CONDITION, toInstant } from '@/lib/format'
import { maintenanceSchema, resourceSchema } from '@/lib/schemas'
import { useOrganization } from './OrganizationLayout'

type ResourceInput = z.input<typeof resourceSchema>
type ResourceOutput = z.output<typeof resourceSchema>
type MaintenanceForm = z.infer<typeof maintenanceSchema>

export default function ResourcesPage() {
  const org = useOrganization()
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [maintaining, setMaintaining] = useState<Resource | null>(null)
  const [retiring, setRetiring] = useState<Resource | null>(null)

  const types = useQuery({ queryKey: ['resource-types', org.id], queryFn: () => organizationApi.resourceTypes(org.id) })
  const resources = useQuery({ queryKey: ['resources', org.id], queryFn: () => organizationApi.resources(org.id) })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['resources', org.id] })

  const typeName = (id: string) => types.data?.find((t) => t.id === id)?.name ?? '—'
  const locationName = (id: string) => org.locations.find((l) => l.id === id)?.name ?? '—'

  // Create
  const createForm = useForm<ResourceInput, unknown, ResourceOutput>({
    resolver: zodResolver(resourceSchema),
    defaultValues: { resourceTypeId: '', locationId: org.locations.length === 1 ? org.locations[0].id : '', capacity: 1 },
  })
  const create = useMutation({
    mutationFn: (v: ResourceOutput) => organizationApi.createResource(org.id, v),
    onSuccess: (created) => {
      refresh()
      toast.success(`Recurso «${created.name}» registrado`)
      createForm.reset()
      setCreating(false)
    },
    onError: (error) => {
      if (!applyServerErrors(error, createForm.setError)) createForm.setError('name', { message: errorMessage(error) })
    },
  })

  // Maintenance
  const maintenanceForm = useForm<MaintenanceForm>({ resolver: zodResolver(maintenanceSchema) })
  const maintain = useMutation({
    mutationFn: (v: MaintenanceForm) =>
      organizationApi.scheduleMaintenance(org.id, maintaining!.id, {
        startsAt: toInstant(v.startsAt),
        endsAt: toInstant(v.endsAt),
        reason: v.reason || undefined,
      }),
    onSuccess: () => {
      refresh()
      toast.success('Mantenimiento programado')
      maintenanceForm.reset()
      setMaintaining(null)
    },
  })

  // Retire
  const retire = useMutation({
    mutationFn: () => organizationApi.retireResource(org.id, retiring!.id),
    onSuccess: () => {
      refresh()
      toast.success('Recurso retirado')
      setRetiring(null)
    },
  })

  const noTypes = types.isSuccess && types.data.length === 0

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-prose text-muted">
          Los recursos son lo concreto que atiende: cada silla, sala o profesional, en una sede y con su capacidad.
        </p>
        <Button onClick={() => setCreating(true)} disabled={noTypes}>
          <Plus size={16} aria-hidden="true" /> Nuevo recurso
        </Button>
      </div>

      {noTypes && (
        <div className="mb-5">
          <Alert tone="info" title="Primero crea un tipo de recurso">
            <Link to={`/negocio/${org.id}/tipos`} className="underline underline-offset-4">
              Ir a tipos de recurso
            </Link>
          </Alert>
        </div>
      )}

      {resources.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : resources.isError ? (
        <ErrorState error={resources.error} onRetry={() => resources.refetch()} />
      ) : resources.data.length === 0 ? (
        <EmptyState title="Aún no registras recursos" description="Agrega al menos uno de cada tipo que tus servicios necesiten." />
      ) : (
        <div className="overflow-x-auto rounded-lg border border-line bg-surface">
          <table className="w-full min-w-[40rem] text-left text-sm">
            <caption className="sr-only">Recursos del negocio</caption>
            <thead className="border-b border-line bg-sunken/60 text-muted">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Nombre</th>
                <th scope="col" className="px-4 py-3 font-medium">Tipo</th>
                <th scope="col" className="px-4 py-3 font-medium">Sede</th>
                <th scope="col" className="px-4 py-3 font-medium text-right">Capacidad</th>
                <th scope="col" className="px-4 py-3 font-medium">Estado</th>
                <th scope="col" className="px-4 py-3"><span className="sr-only">Acciones</span></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line">
              {resources.data.map((resource) => (
                <tr key={resource.id} className="align-top">
                  <td className="px-4 py-3">
                    <p className="font-medium">{resource.name}</p>
                    {resource.maintenances.length > 0 && (
                      <ul className="mt-1 text-xs text-muted">
                        {resource.maintenances.map((m) => (
                          <li key={m.id}>
                            Mantenimiento {formatDateTime(m.startsAt)} → {formatDateTime(m.endsAt)}
                            {m.reason && ` · ${m.reason}`}
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                  <td className="px-4 py-3">{typeName(resource.resourceTypeId)}</td>
                  <td className="px-4 py-3">{locationName(resource.locationId)}</td>
                  <td className="px-4 py-3 text-right tabular-nums">{resource.capacity}</td>
                  <td className="px-4 py-3">
                    <StatusBadge map={RESOURCE_CONDITION} value={resource.condition} />
                  </td>
                  <td className="px-4 py-3">
                    {resource.condition !== 'RETIRED' && (
                      <div className="flex justify-end gap-1">
                        <Button variant="ghost" size="sm" onClick={() => { maintain.reset(); setMaintaining(resource) }}>
                          <Wrench size={16} aria-hidden="true" /> Mantenimiento
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => { retire.reset(); setRetiring(resource) }}>
                          Retirar
                        </Button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="Nuevo recurso">
        <form noValidate onSubmit={createForm.handleSubmit((v) => create.mutate(v))} className="flex flex-col gap-4">
          <Input label="Nombre" required autoFocus error={createForm.formState.errors.name?.message} {...createForm.register('name')} />
          <Select label="Tipo" required error={createForm.formState.errors.resourceTypeId?.message} {...createForm.register('resourceTypeId')}>
            <option value="">Elige un tipo</option>
            {types.data?.map((t) => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </Select>
          <Select label="Sede" required error={createForm.formState.errors.locationId?.message} {...createForm.register('locationId')}>
            <option value="">Elige una sede</option>
            {org.locations.map((l) => (
              <option key={l.id} value={l.id}>{l.name}</option>
            ))}
          </Select>
          <Input
            label="Capacidad"
            type="number"
            inputMode="numeric"
            min={1}
            max={1000}
            hint="Personas que atiende a la vez."
            error={createForm.formState.errors.capacity?.message}
            {...createForm.register('capacity')}
          />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setCreating(false)}>Cancelar</Button>
            <Button type="submit" loading={create.isPending}>Registrar</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={maintaining !== null}
        onClose={() => setMaintaining(null)}
        title="Programar mantenimiento"
        description={maintaining ? `«${maintaining.name}» no ofrecerá horarios durante este periodo.` : undefined}
      >
        <form noValidate onSubmit={maintenanceForm.handleSubmit((v) => maintain.mutate(v))} className="flex flex-col gap-4">
          {maintain.isError && <Alert tone="danger" title={errorMessage(maintain.error)} />}
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Inicio" type="datetime-local" required error={maintenanceForm.formState.errors.startsAt?.message} {...maintenanceForm.register('startsAt')} />
            <Input label="Fin" type="datetime-local" required error={maintenanceForm.formState.errors.endsAt?.message} {...maintenanceForm.register('endsAt')} />
          </div>
          <Textarea label="Motivo" hint="Opcional" error={maintenanceForm.formState.errors.reason?.message} {...maintenanceForm.register('reason')} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setMaintaining(null)}>Cancelar</Button>
            <Button type="submit" loading={maintain.isPending}>Programar</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={retiring !== null}
        onClose={() => setRetiring(null)}
        title="¿Retirar este recurso?"
        description={retiring ? `«${retiring.name}» dejará de estar disponible y no podrás volver a activarlo.` : undefined}
      >
        <div className="flex flex-col gap-4">
          {retire.isError && <Alert tone="danger" title={errorMessage(retire.error)} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setRetiring(null)}>Cancelar</Button>
            <Button variant="danger" loading={retire.isPending} onClick={() => retire.mutate()}>Retirar recurso</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
