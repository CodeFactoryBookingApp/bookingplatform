import { useEffect, useRef } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { registrationApi } from '@/api/endpoints'
import { AuthPage } from '@/components/layout/Shell'
import { Alert } from '@/components/ui/Blocks'
import { Button, LinkButton } from '@/components/ui/Button'
import { Input } from '@/components/ui/Form'
import { PageLoader } from '@/components/ui/Spinner'
import { errorMessage } from '@/lib/errors'

/**
 * Landing of the verification email. Supabase appends the one-time token to the Site URL
 * (docs/operations/runbook.md); without the dashboard template change the user pastes it here.
 */
export default function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token_hash') ?? params.get('token')
  const started = useRef(false)

  const confirm = useMutation({ mutationFn: registrationApi.confirm })
  const resend = useMutation({
    mutationFn: registrationApi.resend,
    onSuccess: () => toast.success('Si el correo existe, te enviamos un nuevo enlace.'),
    onError: (error) => toast.error(errorMessage(error)),
  })

  // The token is single use: the ref keeps React's dev double-effect from spending it twice.
  useEffect(() => {
    if (token && !started.current) {
      started.current = true
      confirm.mutate(token)
    }
  }, [token, confirm])

  const manual = useForm<{ token: string }>()
  const resendForm = useForm<{ email: string }>()

  if (confirm.isPending) return <PageLoader label="Confirmando tu correo…" />

  if (confirm.isSuccess) {
    return (
      <AuthPage title="Correo confirmado">
        <div className="flex flex-col items-start gap-4">
          <Alert tone="ok">{confirm.data.message}</Alert>
          <LinkButton to="/ingresar" size="lg">
            Ingresar
          </LinkButton>
        </div>
      </AuthPage>
    )
  }

  return (
    <AuthPage
      title="Verifica tu correo"
      description={token ? undefined : 'Abre el enlace que te enviamos, o pega aquí el código del enlace.'}
    >
      <div className="flex flex-col gap-6">
        {confirm.isError && <Alert tone="danger" title={errorMessage(confirm.error)} />}

        <form noValidate onSubmit={manual.handleSubmit((v) => confirm.mutate(v.token.trim()))} className="flex flex-col gap-3">
          <Input
            label="Código de verificación"
            autoComplete="off"
            hint="Es el valor token_hash (o token) de la dirección del enlace del correo."
            {...manual.register('token', { required: 'Pega el código' })}
            error={manual.formState.errors.token?.message}
          />
          <Button type="submit">Confirmar correo</Button>
        </form>

        <form
          noValidate
          onSubmit={resendForm.handleSubmit((v) => resend.mutate(v.email.trim()))}
          className="flex flex-col gap-3 border-t border-line pt-5"
        >
          <p className="text-sm font-medium">¿El enlace venció o ya se usó?</p>
          <Input
            label="Correo de tu cuenta"
            type="email"
            inputMode="email"
            autoComplete="email"
            {...resendForm.register('email', { required: 'Ingresa tu correo' })}
            error={resendForm.formState.errors.email?.message}
          />
          <Button type="submit" variant="secondary" loading={resend.isPending}>
            Reenviar enlace
          </Button>
        </form>
        <Link to="/ingresar" className="text-center text-sm text-muted underline underline-offset-4 hover:text-ink">
          Volver a ingresar
        </Link>
      </div>
    </AuthPage>
  )
}
