import { expect, test } from '@playwright/test'
import { createAdmin, createPendingProvider, createVerifiedClient } from './support/api'
import { loginViaUi } from './support/ui'

// S6: administration panel.
test('admin approves a pending provider and sees clients and audit events', async ({ page }) => {
  const admin = await createAdmin()
  const provider = await createPendingProvider()
  const client = await createVerifiedClient()

  await loginViaUi(page, admin)
  await expect(page).toHaveURL('/admin/proveedores')
  await expect(page.getByRole('heading', { name: 'Administración' })).toBeVisible()

  await test.step('approve the pending provider', async () => {
    const row = page.getByRole('row', { name: new RegExp(provider.organizationName) })
    await expect(row).toContainText('Pendiente de aprobación')
    await row.getByRole('button', { name: `Aprobar ${provider.organizationName}` }).click()

    const dialog = page.getByRole('dialog')
    await dialog.getByLabel('Motivo').fill('Documentos verificados')
    await dialog.getByRole('button', { name: 'Aprobar', exact: true }).click()
    await expect(dialog).toBeHidden()

    await expect(row).toContainText('Activo')
    await expect(row).not.toContainText('Pendiente de aprobación')
  })

  await test.step('list the registered client', async () => {
    await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Clientes' }).click()
    await expect(page).toHaveURL('/admin/clientes')
    await expect(page.getByRole('row', { name: new RegExp(client.email) })).toContainText('Activo')
  })

  await test.step('show audit events', async () => {
    await page.getByRole('navigation', { name: 'Secciones' }).getByRole('link', { name: 'Auditoría' }).click()
    await expect(page).toHaveURL('/admin/auditoria')
    const events = page.getByRole('table', { name: 'Eventos de auditoría' })
    await expect(events).toBeVisible()
    // The header row plus at least one event (registrations and approvals are audited by database triggers)
    await expect.poll(() => events.getByRole('row').count()).toBeGreaterThan(1)
  })
})

test('a client cannot reach the admin panel', async ({ page }) => {
  const client = await createVerifiedClient()
  await loginViaUi(page, client)

  await page.goto('/admin/proveedores')

  // RequireAuth sends a user without the role to the home of its own role
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'Administración' })).toHaveCount(0)
  await expect(page.getByRole('link', { name: 'Administración' })).toHaveCount(0)
})
