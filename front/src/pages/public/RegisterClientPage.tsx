import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { EnvelopeSimple } from '@phosphor-icons/react'
import { registrationApi } from '@/api/endpoints'
import { AuthPage } from '@/components/layout/Shell'
import { Alert } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input, Select } from '@/components/ui/Form'
import { errorMessage, traceOf } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { CHANNELS } from '@/lib/format'
import { clientSchema, type ClientForm } from '@/lib/schemas'

export default function RegisterClientPage() {
  const [formError, setFormError] = useState<unknown>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<ClientForm>({ resolver: zodResolver(clientSchema), defaultValues: { notificationChannel: 'EMAIL' } })

  const signUp = useMutation({
    mutationFn: registrationApi.client,
    onError: (error) => {
      if (!applyServerErrors(error, setError)) setFormError(error)
      else setFormError(null)
    },
  })

  if (signUp.isSuccess) {
    return (
      <AuthPage title="Revisa tu correo">
        <div className="flex flex-col items-start gap-4">
          <span className="grid size-11 place-items-center rounded-full bg-accent-soft text-accent-ink">
            <EnvelopeSimple size={24} aria-hidden="true" />
          </span>
          <p>
            Te enviamos un enlace de verificación a <strong>{signUp.data.email}</strong>. Tu cuenta queda pendiente hasta que
            lo abras; después podrás ingresar.
          </p>
          <p className="text-sm text-muted">¿No llega? Revisa spam o pide otro enlace desde la pantalla de verificación.</p>
          <div className="flex flex-wrap gap-2">
            <Link to="/verificar-correo" className="text-sm font-medium text-accent-ink underline underline-offset-4">
              Ir a verificación
            </Link>
          </div>
        </div>
      </AuthPage>
    )
  }

  return (
    <AuthPage title="Crear cuenta" description="Regístrate para reservar servicios. Debes ser mayor de 18 años.">
      <form
        noValidate
        onSubmit={handleSubmit((values) => {
          setFormError(null)
          signUp.mutate(values)
        })}
        className="flex flex-col gap-4"
      >
        {formError != null && (
          <Alert tone="danger" title={errorMessage(formError)}>
            {traceOf(formError) && <span className="text-xs">Código de soporte: {traceOf(formError)}</span>}
          </Alert>
        )}
        <Input label="Nombre completo" autoComplete="name" required error={errors.fullName?.message} {...register('fullName')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Documento"
            autoComplete="off"
            required
            hint="Cédula o pasaporte, sin puntos."
            error={errors.document?.message}
            {...register('document')}
          />
          <Input
            label="Fecha de nacimiento"
            type="date"
            autoComplete="bday"
            required
            max={new Date().toISOString().slice(0, 10)}
            error={errors.birthDate?.message}
            {...register('birthDate')}
          />
        </div>
        <Input label="Correo" type="email" inputMode="email" autoComplete="email" required error={errors.email?.message} {...register('email')} />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="Teléfono"
            type="tel"
            inputMode="tel"
            autoComplete="tel"
            required
            placeholder="+573001234567"
            error={errors.phone?.message}
            {...register('phone')}
          />
          <Input label="Ciudad" autoComplete="address-level2" required error={errors.city?.message} {...register('city')} />
        </div>
        <Select label="¿Cómo quieres recibir avisos?" required error={errors.notificationChannel?.message} {...register('notificationChannel')}>
          {Object.entries(CHANNELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <Input
          label="Contraseña"
          type="password"
          autoComplete="new-password"
          required
          hint="8 a 72 caracteres con mayúscula, minúscula, número y un carácter especial."
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" size="lg" loading={signUp.isPending}>
          {signUp.isPending ? 'Creando cuenta…' : 'Crear cuenta'}
        </Button>
      </form>
      <p className="mt-5 border-t border-line pt-5 text-center text-sm text-muted">
        ¿Ya tienes cuenta?{' '}
        <Link to="/ingresar" className="font-medium text-accent-ink underline underline-offset-4">
          Ingresar
        </Link>
      </p>
    </AuthPage>
  )
}
