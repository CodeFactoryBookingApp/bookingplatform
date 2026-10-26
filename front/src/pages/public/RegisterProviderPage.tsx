import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useFieldArray, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { Plus, Trash } from '@phosphor-icons/react'
import clsx from 'clsx'
import type { z } from 'zod'
import { registrationApi } from '@/api/endpoints'
import { useCategories, useCities } from '@/api/hooks'
import { Alert, Card } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { errorMessage, traceOf } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { providerSchema } from '@/lib/schemas'

type FormInput = z.input<typeof providerSchema>
type FormOutput = z.output<typeof providerSchema>

const STEPS = [
  { title: 'Tu cuenta', fields: ['email', 'password'] },
  { title: 'Tu negocio', fields: ['name', 'categoryId', 'identificationType', 'identificationNumber', 'contactEmail', 'contactPhone'] },
  { title: 'Sedes y reglas', fields: ['locations', 'policy'] },
] as const

const POLICY_FIELDS = ['bookingNoticeMinutesMin', 'bookingNoticeMinutesMax', 'freeCancellationWindowMinutes']

export default function RegisterProviderPage() {
  const [step, setStep] = useState(0)
  const [formError, setFormError] = useState<unknown>(null)
  const categories = useCategories()
  const cities = useCities()

  const {
    register,
    control,
    handleSubmit,
    trigger,
    setError,
    formState: { errors },
  } = useForm<FormInput, unknown, FormOutput>({
    resolver: zodResolver(providerSchema),
    defaultValues: {
      identificationType: 'NIT',
      contactEmail: '',
      locations: [{ name: '', address: '', cityId: '' }],
      policy: { bookingNoticeMinutesMin: 60, bookingNoticeMinutesMax: 43200, freeCancellationWindowMinutes: 1440 },
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'locations' })

  const signUp = useMutation({
    mutationFn: (values: FormOutput) =>
      registrationApi.provider({
        email: values.email,
        password: values.password,
        name: values.name,
        categoryId: values.categoryId,
        identificationType: values.identificationType,
        identificationNumber: values.identificationNumber,
        contactEmail: values.contactEmail || undefined,
        contactPhone: values.contactPhone,
        locations: values.locations,
        ...values.policy,
      }),
    onError: (error) => {
      const placed = applyServerErrors(error, setError, (field) =>
        POLICY_FIELDS.includes(field) ? `policy.${field}` : field,
      )
      setFormError(placed ? null : error)
      if (placed) setStep(0)
    },
  })

  const next = async () => {
    const valid = await trigger([...STEPS[step].fields] as never)
    if (valid) setStep((s) => s + 1)
  }

  if (signUp.isSuccess) {
    return (
      <div className="animate-in mx-auto max-w-xl py-4">
        <Card>
          <h1 className="text-2xl">Recibimos tu solicitud</h1>
          <div className="mt-4 flex flex-col gap-4">
            <Alert tone="warn" title="Tu negocio está pendiente de aprobación">
              Un administrador lo revisará. Mientras tanto no será visible para los clientes. Cuando lo apruebe, te llegará un
              correo a tu bandeja.
            </Alert>
            <p className="text-sm text-muted">
              Primero confirma el correo <strong className="text-ink">{signUp.data.email}</strong> con el enlace que te
              enviamos, para poder ingresar.
            </p>
            <div className="flex flex-wrap gap-3">
              <Link to="/verificar-correo" className="text-sm font-medium text-accent-ink underline underline-offset-4">
                Ir a verificación
              </Link>
              <Link to="/ingresar" className="text-sm font-medium text-accent-ink underline underline-offset-4">
                Ingresar
              </Link>
            </div>
          </div>
        </Card>
      </div>
    )
  }

  return (
    <div className="animate-in mx-auto w-full max-w-2xl py-2 sm:py-6">
      <h1 className="text-2xl sm:text-3xl">Registra tu negocio</h1>
      <p className="mt-2 text-muted">Publica tus servicios y recibe reservas. Un administrador aprueba cada negocio antes de mostrarlo.</p>

      <ol className="mt-6 flex gap-2" aria-label="Pasos">
        {STEPS.map((s, i) => (
          <li key={s.title} className="flex-1" aria-current={i === step ? 'step' : undefined}>
            <div className={clsx('h-1 rounded-full', i <= step ? 'bg-accent' : 'bg-line')} />
            <p className={clsx('mt-2 text-sm', i === step ? 'font-semibold text-ink' : 'text-muted')}>
              {i + 1}. {s.title}
            </p>
          </li>
        ))}
      </ol>

      <form
        noValidate
        onSubmit={handleSubmit(
          (values) => {
            setFormError(null)
            signUp.mutate(values)
          },
          (invalid) => {
            // Jump back to the first step that holds an invalid field
            const keys = Object.keys(invalid)
            const index = STEPS.findIndex((s) => s.fields.some((f) => keys.includes(f)))
            if (index >= 0) setStep(index)
          },
        )}
        className="mt-6 rounded-lg border border-line bg-surface p-5 sm:p-6"
      >
        {formError != null && (
          <div className="mb-4">
            <Alert tone="danger" title={errorMessage(formError)}>
              {traceOf(formError) && <span className="text-xs">Código de soporte: {traceOf(formError)}</span>}
            </Alert>
          </div>
        )}

        <div className={step === 0 ? 'flex flex-col gap-4' : 'hidden'}>
          <Input label="Correo de la cuenta" type="email" inputMode="email" autoComplete="email" required error={errors.email?.message} {...register('email')} />
          <Input
            label="Contraseña"
            type="password"
            autoComplete="new-password"
            required
            hint="8 a 72 caracteres con mayúscula, minúscula, número y un carácter especial."
            error={errors.password?.message}
            {...register('password')}
          />
        </div>

        <div className={step === 1 ? 'flex flex-col gap-4' : 'hidden'}>
          <Input label="Nombre del negocio" required error={errors.name?.message} {...register('name')} />
          <Select label="Categoría" required error={errors.categoryId?.message} {...register('categoryId')}>
            <option value="">Elige una categoría</option>
            {categories.data?.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </Select>
          <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
            <Select label="Tipo de documento" required error={errors.identificationType?.message} {...register('identificationType')}>
              <option value="NIT">NIT</option>
              <option value="CC">Cédula (CC)</option>
              <option value="CE">Cédula de extranjería</option>
              <option value="PASAPORTE">Pasaporte</option>
            </Select>
            <Input label="Número" required autoComplete="off" error={errors.identificationNumber?.message} {...register('identificationNumber')} />
          </div>
          <Input
            label="Correo de contacto"
            type="email"
            inputMode="email"
            hint="Opcional. Si lo dejas vacío usamos el de la cuenta."
            error={errors.contactEmail?.message}
            {...register('contactEmail')}
          />
          <Input label="Teléfono de contacto" type="tel" inputMode="tel" required placeholder="+573001234567" error={errors.contactPhone?.message} {...register('contactPhone')} />
        </div>

        <div className={step === 2 ? 'flex flex-col gap-6' : 'hidden'}>
          <fieldset className="flex flex-col gap-4">
            <legend className="mb-1 text-base font-semibold">Sedes</legend>
            {typeof errors.locations?.message === 'string' && <p role="alert" className="text-sm text-danger">{errors.locations.message}</p>}
            {fields.map((field, index) => (
              <div key={field.id} className="rounded-md border border-line p-4">
                <div className="mb-3 flex items-center justify-between">
                  <p className="text-sm font-medium">Sede {index + 1}</p>
                  {fields.length > 1 && (
                    <Button variant="ghost" size="sm" onClick={() => remove(index)} aria-label={`Quitar sede ${index + 1}`}>
                      <Trash size={16} aria-hidden="true" /> Quitar
                    </Button>
                  )}
                </div>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Input label="Nombre" required error={errors.locations?.[index]?.name?.message} {...register(`locations.${index}.name`)} />
                  <Select label="Ciudad" required error={errors.locations?.[index]?.cityId?.message} {...register(`locations.${index}.cityId`)}>
                    <option value="">Elige una ciudad</option>
                    {cities.data?.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name} ({c.region})
                      </option>
                    ))}
                  </Select>
                  <Input
                    wrapperClassName="sm:col-span-2"
                    label="Dirección"
                    required
                    autoComplete="off"
                    error={errors.locations?.[index]?.address?.message}
                    {...register(`locations.${index}.address`)}
                  />
                </div>
              </div>
            ))}
            {fields.length < 20 && (
              <Button variant="secondary" className="self-start" onClick={() => append({ name: '', address: '', cityId: '' })}>
                <Plus size={16} aria-hidden="true" /> Agregar otra sede
              </Button>
            )}
          </fieldset>

          <fieldset className="flex flex-col gap-4">
            <legend className="mb-1 text-base font-semibold">Reglas de reserva</legend>
            <p className="-mt-2 text-sm text-muted">Los valores son en minutos. Puedes cambiarlos más adelante; aplican a reservas nuevas.</p>
            <div className="grid gap-4 sm:grid-cols-3">
              <Input
                label="Anticipación mínima"
                type="number"
                inputMode="numeric"
                min={0}
                hint="60 = 1 hora"
                error={errors.policy?.bookingNoticeMinutesMin?.message}
                {...register('policy.bookingNoticeMinutesMin')}
              />
              <Input
                label="Anticipación máxima"
                type="number"
                inputMode="numeric"
                min={0}
                hint="43200 = 30 días"
                error={errors.policy?.bookingNoticeMinutesMax?.message}
                {...register('policy.bookingNoticeMinutesMax')}
              />
              <Input
                label="Cancelación gratuita hasta"
                type="number"
                inputMode="numeric"
                min={0}
                hint="1440 = 24 horas antes"
                error={errors.policy?.freeCancellationWindowMinutes?.message}
                {...register('policy.freeCancellationWindowMinutes')}
              />
            </div>
          </fieldset>
        </div>

        <div className="mt-6 flex items-center justify-between gap-3 border-t border-line pt-5">
          {step > 0 ? (
            <Button variant="ghost" onClick={() => setStep(step - 1)}>
              Atrás
            </Button>
          ) : (
            <span />
          )}
          {step < STEPS.length - 1 ? (
            <Button onClick={next}>Continuar</Button>
          ) : (
            <Button type="submit" size="lg" loading={signUp.isPending}>
              {signUp.isPending ? 'Enviando…' : 'Enviar solicitud'}
            </Button>
          )}
        </div>
      </form>
    </div>
  )
}
