import { expect, test } from '@playwright/test'
import { createApprovedProviderWithService, PASSWORD, uniqueEmail } from './support/api'
import { SIGNUP_SUBJECT, waitForEmailToken } from './support/mailpit'
import { dateInDays, loginViaUi } from './support/ui'

// S4: client registration, login, catalog, booking, My bookings and cancellation.
test('client registers, books a service from the catalog and cancels the booking', async ({ page }) => {
  const { service } = await createApprovedProviderWithService()
  const email = uniqueEmail('client')

  await test.step('register through the form', async () => {
    await page.goto('/registro')
    await page.getByLabel('Nombre completo').fill('Cliente Prueba')
    await page.getByLabel('Documento').fill(`CC${Date.now()}`)
    await page.getByLabel('Fecha de nacimiento').fill('1990-01-01')
    await page.getByLabel(/^Correo\b/).fill(email)
    await page.getByLabel('Teléfono').fill('+573001234567')
    await page.getByLabel('Ciudad').fill('Bogotá')
    await page.getByLabel('Contraseña').fill(PASSWORD)
    await page.getByRole('button', { name: 'Crear cuenta' }).click()
    await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible()
  })

  await test.step('confirm the email with the token of the message', async () => {
    const token = await waitForEmailToken(email, SIGNUP_SUBJECT)
    await page.goto(`/verificar-correo?token=${token}`)
    await expect(page.getByRole('heading', { name: 'Correo confirmado' })).toBeVisible()
  })

  await test.step('log in', async () => {
    await loginViaUi(page, { email, password: PASSWORD })
    await expect(page.getByRole('link', { name: 'Mis reservas' })).toBeVisible()
  })

  await test.step('find the seeded service in the catalog', async () => {
    await page.goto('/')
    await page.getByRole('searchbox', { name: 'Buscar servicios' }).fill(service.name)
    await page.getByRole('button', { name: 'Buscar' }).click()
    await expect(page.getByRole('heading', { name: service.name })).toBeVisible()
    await page.getByRole('link', { name: new RegExp(`^Reservar ${service.name}`) }).click()
  })

  await test.step('pick a slot for tomorrow and confirm the booking', async () => {
    await expect(page.getByRole('heading', { name: service.name, level: 1 })).toBeVisible()
    await page.getByLabel('Día').fill(dateInDays(1))
    await page.getByRole('list', { name: 'Horarios' }).getByRole('button').first().click()
    // The summary only renders once the preview answered
    await expect(page.getByText(/80\.000/)).toBeVisible()
    await page.getByRole('button', { name: 'Confirmar reserva' }).click()
    await expect(page.getByRole('heading', { name: 'Reserva confirmada' })).toBeVisible()
  })

  await test.step('see it in My bookings', async () => {
    await page.getByRole('main').getByRole('link', { name: 'Mis reservas' }).click()
    await expect(page).toHaveURL('/mis-reservas')
    const booking = page.getByRole('listitem').filter({ hasText: service.name })
    await expect(booking).toContainText('Confirmada')
  })

  await test.step('cancel it and see it cancelled', async () => {
    const booking = page.getByRole('listitem').filter({ hasText: service.name })
    await booking.getByRole('button', { name: 'Cancelar' }).click()
    // The first request only asks the backend whether a penalty applies; the user then confirms
    const dialog = page.getByRole('dialog')
    await dialog.getByRole('button', { name: 'Cancelar reserva' }).click()
    await expect(dialog.getByText('Cancelación sin penalidad')).toBeVisible()
    await dialog.getByRole('button', { name: 'Sí, cancelar la reserva' }).click()
    await expect(dialog).toBeHidden()
    await expect(booking).toContainText('Cancelada')
    await expect(booking.getByRole('button', { name: 'Cancelar', exact: true })).toHaveCount(0)
  })
})
