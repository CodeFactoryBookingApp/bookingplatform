import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { z } from 'zod'
import { organizationApi } from '@/api/endpoints'
import type { OfferedService } from '@/api/types'
import { StatusBadge } from '@/components/ui/Badge'
import { Alert, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select, Textarea } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { formatDuration, formatPrice, SERVICE_STATUS } from '@/lib/format'
import { serviceSchema } from '@/lib/schemas'
import { useOrganization } from './OrganizationLayout'

type Input_ = z.input<typeof serviceSchema>
type Output_ = z.output<typeof serviceSchema>
interface Requirement {
  resourceTypeId: string
  quantity: number
}

const EMPTY: Input_ = {
  name: '',
  description: '',
  durationMinutes: 30,
  preparationMinutes: 0,
  cleanupMinutes: 0,
  price: 0,
  customerCapacity: 1,
}

function DetailFields({ register, errors }: { register: UseFormRegister<Input_>; errors: FieldErrors<Input_> }) {
  return (
    <>
      <Input label="Nombre" required error={errors.name?.message} {...register('name')} />
      <Textarea label="Descripción" hint="Opcional" error={errors.description?.message} {...register('description')} />
      <div className="grid gap-4 sm:grid-cols-3">
        <Input label="Duración (min)" type="number" inputMode="numeric" min={1} required error={errors.durationMinutes?.message} {...register('durationMinutes')} />
        <Input label="Preparación (min)" type="number" inputMode="numeric" min={0} error={errors.preparationMinutes?.message} {...register('preparationMinutes')} />
        <Input label="Limpieza (min)" type="number" inputMode="numeric" min={0} error={errors.cleanupMinutes?.message} {...register('cleanupMinutes')} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <Input label="Precio (COP)" type="number" inputMode="numeric" min={0} step={500} required error={errors.price?.message} {...register('price')} />
        <Input
          label="Cupos por reserva"
          type="number"
          inputMode="numeric"
          min={1}
          hint="1 es individual; más es una clase o sesión grupal."
          error={errors.customerCapacity?.message}
          {...register('customerCapacity')}
        />
      </div>
    </>
  )
}

export default function ServicesPage() {
  const org = useOrganization()
  const queryClient = useQueryClient()
  const services = useQuery({ queryKey: ['services', org.id], queryFn: () => organizationApi.services(org.id) })
  const types = useQuery({ queryKey: ['resource-types', org.id], queryFn: () => organizationApi.resourceTypes(org.id) })
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['services', org.id] })

  const [creating, setCreating] = useState(false)
  const [editing, setEditing] = useState<OfferedService | null>(null)
  const [deleting, setDeleting] = useState<OfferedService | null>(null)
  const [locationIds, setLocationIds] = useState<string[]>(org.locations.length === 1 ? [org.locations[0].id] : [])
  const [requirements, setRequirements] = useState<Requirement[]>([{ resourceTypeId: '', quantity: 1 }])
  const [structureError, setStructureError] = useState<string | null>(null)

  const createForm = useForm<Input_, unknown, Output_>({ resolver: zodResolver(serviceSchema), defaultValues: EMPTY })
  const editForm = useForm<Input_, unknown, Output_>({ resolver: zodResolver(serviceSchema), defaultValues: EMPTY })

  const toDetails = (v: Output_) => ({
    name: v.name,
    description: v.description || undefined,
    durationMinutes: v.durationMinutes,
    preparationMinutes: v.preparationMinutes,
    cleanupMinutes: v.cleanupMinutes,
    price: v.price,
    customerCapacity: v.customerCapacity,
  })

  const create = useMutation({
    mutationFn: (v: Output_) =>
      organizationApi.createService(org.id, {
        details: toDetails(v),
        locationIds,
        requirements: requirements.filter((r) => r.resourceTypeId).map((r) => ({ resourceTypeId: r.resourceTypeId, quantity: r.quantity })),
      }),
    onSuccess: (created) => {
      refresh()
      toast.success(`Servicio «${created.name}» creado. Actívalo para que aparezca en el catálogo.`)
      createForm.reset(EMPTY)
      setCreating(false)
    },
    onError: (error) => {
      const placed = applyServerErrors(error, createForm.setError, (f) => f.replace(/^details\./, ''))
      if (!placed) createForm.setError('name', { message: errorMessage(error) })
    },
  })

  const update = useMutation({
    mutationFn: (v: Output_) => organizationApi.updateService(org.id, editing!.id, toDetails(v)),
    onSuccess: () => {
      refresh()
      toast.success('Servicio actualizado. Las reservas existentes conservan su horario.')
      setEditing(null)
    },
    onError: (error) => {
      if (!applyServerErrors(error, editForm.setError)) editForm.setError('name', { message: errorMessage(error) })
    },
  })

  const toggle = useMutation({
    mutationFn: (service: OfferedService) =>
      service.status === 'ACTIVE'
        ? organizationApi.deactivateService(org.id, service.id)
        : organizationApi.activateService(org.id, service.id),
    onSuccess: (updated) => {
      refresh()
      toast.success(updated.status === 'ACTIVE' ? 'Servicio activado' : 'Servicio desactivado')
    },
    onError: (error) => toast.error(errorMessage(error)),
  })

  const remove = useMutation({
    mutationFn: () => organizationApi.deleteService(org.id, deleting!.id),
    onSuccess: () => {
      refresh()
      toast.success('Servicio eliminado')
      setDeleting(null)
    },
  })

  const submitCreate = createForm.handleSubmit((v) => {
    const valid = requirements.filter((r) => r.resourceTypeId)
    if (locationIds.length === 0) return setStructureError('Elige al menos una sede.')
    if (valid.length === 0) return setStructureError('Elige al menos un tipo de recurso que necesita el servicio.')
    setStructureError(null)
    create.mutate(v)
  })

  const openEdit = (service: OfferedService) => {
    update.reset()
    editForm.reset({
      name: service.name,
      description: service.description ?? '',
      durationMinutes: service.durationMinutes,
      preparationMinutes: service.preparationMinutes,
      cleanupMinutes: service.cleanupMinutes,
      price: service.price,
      customerCapacity: service.customerCapacity,
    })
    setEditing(service)
  }

  const typeName = (id: string) => types.data?.find((t) => t.id === id)?.name ?? '—'
  const locationName = (id: string) => org.locations.find((l) => l.id === id)?.name ?? '—'
  const noTypes = types.isSuccess && types.data.length === 0

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-prose text-muted">
          Lo que ofreces a tus clientes. Un servicio nuevo nace inactivo; actívalo cuando tengas los recursos listos.
        </p>
        <Button onClick={() => { create.reset(); setStructureError(null); setCreating(true) }} disabled={noTypes}>
          <Plus size={16} aria-hidden="true" /> Nuevo servicio
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

      {services.isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : services.isError ? (
        <ErrorState error={services.error} onRetry={() => services.refetch()} />
      ) : services.data.length === 0 ? (
        <EmptyState title="Aún no tienes servicios" description="Crea el primero para que tus clientes lo encuentren en el catálogo." />
      ) : (
        <ul className="grid gap-4 lg:grid-cols-2">
          {services.data.map((service) => (
            <li key={service.id} className="flex flex-col rounded-lg border border-line bg-surface p-5">
              <div className="flex items-start justify-between gap-3">
                <h2 className="text-lg">{service.name}</h2>
                <StatusBadge map={SERVICE_STATUS} value={service.status} />
              </div>
              {service.description && <p className="mt-1 text-sm text-muted">{service.description}</p>}
              <dl className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
                <div><dt className="text-muted">Precio</dt><dd className="font-medium tabular-nums">{formatPrice(service.price)}</dd></div>
                <div><dt className="text-muted">Duración</dt><dd>{formatDuration(service.durationMinutes)}</dd></div>
                <div><dt className="text-muted">Sedes</dt><dd>{service.locationIds.map(locationName).join(', ')}</dd></div>
                <div>
                  <dt className="text-muted">Requiere</dt>
                  <dd>{service.requirements.map((r) => `${r.quantity} × ${typeName(r.resourceTypeId)}`).join(', ')}</dd>
                </div>
              </dl>
              <div className="mt-5 flex flex-wrap gap-2 border-t border-line pt-4">
                <Button
                  variant={service.status === 'ACTIVE' ? 'secondary' : 'primary'}
                  size="sm"
                  loading={toggle.isPending && toggle.variables?.id === service.id}
                  onClick={() => toggle.mutate(service)}
                >
                  {service.status === 'ACTIVE' ? 'Desactivar' : 'Activar'}
                </Button>
                <Button variant="secondary" size="sm" onClick={() => openEdit(service)}>Editar</Button>
                <Button variant="ghost" size="sm" onClick={() => { remove.reset(); setDeleting(service) }}>
                  <Trash size={16} aria-hidden="true" /> Eliminar
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={creating} onClose={() => setCreating(false)} title="Nuevo servicio">
        <form noValidate onSubmit={submitCreate} className="flex flex-col gap-4">
          <DetailFields register={createForm.register} errors={createForm.formState.errors} />

          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-sm font-medium">Sedes donde se presta *</legend>
            {org.locations.map((location) => (
              <label key={location.id} className="flex min-h-11 items-center gap-3 rounded-md border border-line px-3">
                <input
                  type="checkbox"
                  className="size-4 accent-accent"
                  checked={locationIds.includes(location.id)}
                  onChange={(e) =>
                    setLocationIds((ids) => (e.target.checked ? [...ids, location.id] : ids.filter((id) => id !== location.id)))
                  }
                />
                <span className="text-sm">{location.name}</span>
              </label>
            ))}
          </fieldset>

          <fieldset className="flex flex-col gap-3">
            <legend className="mb-1 text-sm font-medium">Recursos que necesita *</legend>
            {requirements.map((req, index) => (
              <div key={index} className="grid grid-cols-[1fr_5.5rem_auto] items-end gap-2">
                <Select
                  label={index === 0 ? 'Tipo de recurso' : `Tipo de recurso ${index + 1}`}
                  value={req.resourceTypeId}
                  onChange={(e) => setRequirements((rs) => rs.map((r, i) => (i === index ? { ...r, resourceTypeId: e.target.value } : r)))}
                >
                  <option value="">Elige un tipo</option>
                  {types.data?.map((t) => (
                    <option key={t.id} value={t.id}>{t.name}</option>
                  ))}
                </Select>
                <Input
                  label="Cantidad"
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={50}
                  value={req.quantity}
                  onChange={(e) => setRequirements((rs) => rs.map((r, i) => (i === index ? { ...r, quantity: Number(e.target.value) || 1 } : r)))}
                />
                <Button
                  variant="ghost"
                  disabled={requirements.length === 1}
                  aria-label={`Quitar recurso ${index + 1}`}
                  onClick={() => setRequirements((rs) => rs.filter((_, i) => i !== index))}
                >
                  <Trash size={18} aria-hidden="true" />
                </Button>
              </div>
            ))}
            <Button variant="secondary" size="sm" className="self-start" onClick={() => setRequirements((rs) => [...rs, { resourceTypeId: '', quantity: 1 }])}>
              <Plus size={14} aria-hidden="true" /> Agregar otro
            </Button>
          </fieldset>

          {structureError && <Alert tone="danger" title={structureError} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setCreating(false)}>Cancelar</Button>
            <Button type="submit" loading={create.isPending}>Crear servicio</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title="Editar servicio"
        description="Las sedes y los recursos requeridos se definen al crear el servicio."
      >
        <form noValidate onSubmit={editForm.handleSubmit((v) => update.mutate(v))} className="flex flex-col gap-4">
          <DetailFields register={editForm.register} errors={editForm.formState.errors} />
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setEditing(null)}>Cancelar</Button>
            <Button type="submit" loading={update.isPending}>Guardar cambios</Button>
          </div>
        </form>
      </Modal>

      <Modal
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title="¿Eliminar este servicio?"
        description={deleting ? `«${deleting.name}» se borra por completo. Si ya tiene reservas, desactívalo en su lugar.` : undefined}
      >
        <div className="flex flex-col gap-4">
          {remove.isError && <Alert tone="danger" title={errorMessage(remove.error)} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setDeleting(null)}>Cancelar</Button>
            <Button variant="danger" loading={remove.isPending} onClick={() => remove.mutate()}>Eliminar</Button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
