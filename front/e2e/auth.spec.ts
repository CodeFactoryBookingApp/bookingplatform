import { expect, test } from '@playwright/test'
import { createVerifiedClient } from './support/api'
import { RECOVERY_SUBJECT, waitForEmailToken } from './support/mailpit'
import { loginViaUi, submitLogin } from './support/ui'

// S7: password recovery, lockout and logout.
test.describe('password recovery', () => {
  // S9 / defect L5: the backend answers 204 to the reset but GoTrue never changes the password
  // (GoTrueClient.resetPasswordWithToken sends it inside /verify, which ignores it). This test asserts the
  // correct behavior, so it fails until that defect is fixed. Do not skip it.
  test('a new password set from the recovery email replaces the old one', async ({ page }) => {
    const client = await createVerifiedClient()
    const newPassword = 'Nueva456!y'

    await page.goto('/recuperar')
    await page.getByLabel('Correo').fill(client.email)
    await page.getByRole('button', { name: 'Enviar enlace' }).click()
    await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible()

    const token = await waitForEmailToken(client.email, RECOVERY_SUBJECT)
    await page.goto(`/restablecer-clave?token=${token}`)
    await page.getByLabel('Nueva contraseña').fill(newPassword)
    await page.getByLabel('Repite la contraseña').fill(newPassword)
    await page.getByRole('button', { name: 'Guardar contraseña' }).click()
    await expect(page.getByRole('heading', { name: 'Contraseña actualizada' })).toBeVisible()

    await submitLogin(page, { email: client.email, password: newPassword })
    await expect(page.getByRole('button', { name: 'Salir' }), 'the new password must log in (defect L5)').toBeVisible()

    await page.getByRole('button', { name: 'Salir' }).click()
    await submitLogin(page, client)
    await expect(page.getByText('Correo o contraseña incorrectos.'), 'the old password must be rejected').toBeVisible()
  })
})

test('five wrong passwords lock the account', async ({ page }) => {
  const client = await createVerifiedClient()
  const locked = page.getByText('Cuenta bloqueada por intentos fallidos. Vuelve a intentarlo en 15 minutos.')

  for (let attempt = 1; attempt <= 5; attempt++) {
    await submitLogin(page, { email: client.email, password: `Wrong${attempt}23!x` })
    await expect(page.getByText('Correo o contraseña incorrectos.')).toBeVisible()
    await expect(locked).toHaveCount(0)
  }

  // The lock applies from the next attempt on: even the right password is refused now
  await submitLogin(page, client)
  await expect(locked).toBeVisible()
  await expect(page.getByRole('button', { name: 'Salir' })).toHaveCount(0)
})

test('logging out returns to a logged-out state and protected routes ask for a login', async ({ page }) => {
  const client = await createVerifiedClient()
  await loginViaUi(page, client)
  await expect(page.getByRole('link', { name: 'Mis reservas' })).toBeVisible()

  await page.getByRole('button', { name: 'Salir' }).click()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('link', { name: 'Ingresar' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Salir' })).toHaveCount(0)

  for (const route of ['/mis-reservas', '/bandeja']) {
    await page.goto(route)
    await expect(page).toHaveURL('/ingresar')
    await expect(page.getByRole('heading', { name: 'Ingresar' })).toBeVisible()
  }

  // A reload must not bring the session back
  await page.reload()
  await expect(page.getByRole('link', { name: 'Ingresar' }).first()).toBeVisible()
})
