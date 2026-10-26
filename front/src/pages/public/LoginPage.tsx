import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { registrationApi } from '@/api/endpoints'
import { homeFor, useAuth } from '@/auth/AuthContext'
import { AuthPage } from '@/components/layout/Shell'
import { Alert } from '@/components/ui/Blocks'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { errorMessage, traceOf } from '@/lib/errors'
import { loginSchema } from '@/lib/schemas'
import type { z } from 'zod'

type LoginForm = z.infer<typeof loginSchema>

export default function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  const [formError, setFormError] = useState<unknown>(null)

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors },
  } = useForm<LoginForm>({ resolver: zodResolver(loginSchema) })

  const signIn = useMutation({
    mutationFn: (values: LoginForm) => login(values.email, values.password),
    onSuccess: (me) => navigate(from && from !== '/ingresar' ? from : homeFor(me.role), { replace: true }),
    onError: setFormError,
  })

  const resend = useMutation({
    mutationFn: () => registrationApi.resend(getValues('email')),
    onSuccess: () => toast.success('Si el correo existe, te enviamos un nuevo enlace de verificación.'),
    onError: (error) => toast.error(errorMessage(error)),
  })

  const notConfirmed = formError instanceof ApiError && formError.errorCode === 'EMAIL_NOT_CONFIRMED'

  return (
    <AuthPage title="Ingresar" description="Accede a tu cuenta para administrar tus reservas o tu negocio.">
      <form
        noValidate
        onSubmit={handleSubmit((values) => {
          setFormError(null)
          signIn.mutate(values)
        })}
        className="flex flex-col gap-4"
      >
        {formError != null && (
          <Alert tone="danger" title={errorMessage(formError)}>
            {traceOf(formError) && <span className="text-xs">Código de soporte: {traceOf(formError)}</span>}
            {notConfirmed && (
              <Button variant="secondary" size="sm" className="mt-2" loading={resend.isPending} onClick={() => resend.mutate()}>
                Reenviar enlace de verificación
              </Button>
            )}
          </Alert>
        )}
        <Input label="Correo" type="email" autoComplete="email" inputMode="email" error={errors.email?.message} {...register('email')} />
        <Input
          label="Contraseña"
          type="password"
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <div className="-mt-1 text-right text-sm">
          <Link to="/recuperar" className="text-accent-ink underline underline-offset-4 hover:text-accent">
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        <Button type="submit" size="lg" loading={signIn.isPending}>
          {signIn.isPending ? 'Ingresando…' : 'Ingresar'}
        </Button>
      </form>
      <p className="mt-5 border-t border-line pt-5 text-center text-sm text-muted">
        ¿Aún no tienes cuenta?{' '}
        <Link to="/registro" className="font-medium text-accent-ink underline underline-offset-4">
          Crear cuenta
        </Link>
      </p>
    </AuthPage>
  )
}
