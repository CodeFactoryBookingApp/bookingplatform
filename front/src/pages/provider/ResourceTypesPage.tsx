import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus } from '@phosphor-icons/react'
import { toast } from 'sonner'
import type { z } from 'zod'
import { ApiError } from '@/api/client'
import { organizationApi } from '@/api/endpoints'
import { Alert, EmptyState, ErrorState } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Textarea } from '@/components/ui/Form'
import { Modal } from '@/components/ui/Modal'
import { Skeleton } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { resourceTypeSchema } from '@/lib/schemas'
import { useOrganization } from './OrganizationLayout'

type FormValues = z.infer<typeof resourceTypeSchema>

export default function ResourceTypesPage() {
  const org = useOrganization()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const types = useQuery({ queryKey: ['resource-types', org.id], queryFn: () => organizationApi.resourceTypes(org.id) })

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(resourceTypeSchema) })

  const create = useMutation({
    mutationFn: (v: FormValues) => organizationApi.createResourceType(org.id, { name: v.name, description: v.description || undefined }),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['resource-types', org.id] })
      toast.success(`Tipo «${created.name}» creado`)
      reset()
      setOpen(false)
    },
    onError: (error) => {
      if (applyServerErrors(error, setError)) return
      if (error instanceof ApiError && error.errorCode === 'DUPLICATE_NAME') {
        setError('name', { message: 'Ya tienes un tipo con ese nombre' })
      }
    },
  })

  return (
    <div>
      <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
        <p className="max-w-prose text-muted">
          Un tipo de recurso es una clase de cosa que tu negocio usa para atender: silla, sala, cancha, profesional.
        </p>
        <Button onClick={() => setOpen(true)}>
          <Plus size={16} aria-hidden="true" /> Nuevo tipo
        </Button>
      </div>

      {types.isPending ? (
        <Skeleton className="h-24 w-full" />
      ) : types.isError ? (
        <ErrorState error={types.error} onRetry={() => types.refetch()} />
      ) : types.data.length === 0 ? (
        <EmptyState
          title="Aún no tienes tipos de recurso"
          description="Créalos primero: los recursos y los servicios los necesitan."
          action={<Button onClick={() => setOpen(true)}>Crear el primero</Button>}
        />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {types.data.map((type) => (
            <li key={type.id} className="rounded-lg border border-line bg-surface p-4">
              <p className="font-medium">{type.name}</p>
              {type.description && <p className="mt-1 text-sm text-muted">{type.description}</p>}
            </li>
          ))}
        </ul>
      )}

      <Modal open={open} onClose={() => setOpen(false)} title="Nuevo tipo de recurso">
        <form noValidate onSubmit={handleSubmit((v) => create.mutate(v))} className="flex flex-col gap-4">
          <Input label="Nombre" required autoFocus placeholder="Silla de peluquería" error={errors.name?.message} {...register('name')} />
          <Textarea label="Descripción" hint="Opcional" error={errors.description?.message} {...register('description')} />
          {create.isError && !errors.name && <Alert tone="danger" title={errorMessage(create.error)} />}
          <div className="flex justify-end gap-2">
            <Button variant="ghost" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button type="submit" loading={create.isPending}>
              Crear
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  )
}
