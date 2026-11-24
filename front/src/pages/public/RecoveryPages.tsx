import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import type { z } from 'zod'
import { authApi } from '@/api/endpoints'
import { AuthPage } from '@/components/layout/Shell'
import { Alert } from '@/components/ui/Blocks'
import { Button, LinkButton } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { errorMessage } from '@/lib/errors'
import { applyServerErrors } from '@/lib/forms'
import { resetSchema } from '@/lib/schemas'

export function ForgotPasswordPage() {
  const { register, handleSubmit, formState: { errors } } = useForm<{ email: string }>()
  const request = useMutation({ mutationFn: authApi.requestRecovery })

  if (request.isSuccess) {
    return (
      <AuthPage title="Revisa tu correo">
        <p>
          Si hay una cuenta con ese correo, te enviamos un enlace para restablecer la contraseña. Puede tardar unos minutos.
        </p>
        <Link to="/ingresar" className="mt-5 inline-block text-sm font-medium text-accent-ink underline underline-offset-4">
          Volver a ingresar
        </Link>
      </AuthPage>
    )
  }

  return (
    <AuthPage title="Recuperar contraseña" description="Te enviaremos un enlace para crear una nueva.">
      <form noValidate onSubmit={handleSubmit((v) => request.mutate(v.email))} className="flex flex-col gap-4">
        {request.isError && <Alert tone="danger" title={errorMessage(request.error)} />}
        <Input
          label="Correo"
          type="email"
          inputMode="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email', { required: 'Ingresa tu correo', pattern: { value: /^\S+@\S+\.\S+$/, message: 'Correo no válido' } })}
        />
        <Button type="submit" size="lg" loading={request.isPending}>
          Enviar enlace
        </Button>
      </form>
    </AuthPage>
  )
}

type ResetForm = z.infer<typeof resetSchema>

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const [pasted, setPasted] = useState('')
  const token = params.get('token_hash') ?? params.get('token') ?? pasted.trim()

  const { register, handleSubmit, setError, formState: { errors } } = useForm<ResetForm>({ resolver: zodResolver(resetSchema) })
  const reset = useMutation({
    mutationFn: (v: ResetForm) => authApi.resetPassword(token, v.newPassword),
    onError: (error) => applyServerErrors(error, setError),
  })

  if (reset.isSuccess) {
    return (
      <AuthPage title="Contraseña actualizada">
        <div className="flex flex-col items-start gap-4">
          <Alert tone="ok">Ya puedes ingresar con tu nueva contraseña.</Alert>
          <LinkButton to="/ingresar" size="lg">
            Ingresar
          </LinkButton>
        </div>
      </AuthPage>
    )
  }

  const hasLinkToken = Boolean(params.get('token_hash') ?? params.get('token'))
  return (
    <AuthPage title="Nueva contraseña" description="Elige una contraseña segura que no uses en otros sitios.">
      <form noValidate onSubmit={handleSubmit((v) => token && reset.mutate(v))} className="flex flex-col gap-4">
        {reset.isError && !Object.keys(errors).length && <Alert tone="danger" title={errorMessage(reset.error)} />}
        {!hasLinkToken && (
          <Input
            label="Código del enlace"
            autoComplete="off"
            hint="Es el valor token_hash (o token) de la dirección del correo."
            value={pasted}
            onChange={(e) => setPasted(e.target.value)}
          />
        )}
        <Input
          label="Nueva contraseña"
          type="password"
          autoComplete="new-password"
          hint="8 a 72 caracteres con mayúscula, minúscula, número y un carácter especial."
          error={errors.newPassword?.message}
          {...register('newPassword')}
        />
        <Input
          label="Repite la contraseña"
          type="password"
          autoComplete="new-password"
          error={errors.confirm?.message}
          {...register('confirm')}
        />
        <Button type="submit" size="lg" loading={reset.isPending} disabled={!token}>
          Guardar contraseña
        </Button>
      </form>
    </AuthPage>
  )
}
