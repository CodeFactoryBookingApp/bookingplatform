import { expect, test } from '@playwright/test'
import { approveOrganization, organizationOf, PASSWORD, uniqueEmail, uniqueName } from './support/api'
import { SIGNUP_SUBJECT, waitForEmailToken } from './support/mailpit'
import { loginViaUi } from './support/ui'

// S5: provider onboarding and catalog setup.
test('provider registers, gets approved, configures a service and shows up in the catalog', async ({ page }) => {
  const account = { email: uniqueEmail('provider'), password: PASSWORD }
  const businessName = uniqueName('Spa E2E')
  const resourceTypeName = uniqueName('Cabina')
  const resourceName = uniqueName('Cabina 1')
  const serviceName = uniqueName('Masaje UI')
  const agendaName = uniqueName('Agenda UI')

  await test.step('register the business in three steps', async () => {
    await page.goto('/registro-negocio')

    await page.getByLabel('Correo de la cuenta').fill(account.email)
    await page.getByLabel('Contraseña').fill(account.password)
    await page.getByRole('button', { name: 'Continuar' }).click()

    await page.getByLabel('Nombre del negocio').fill(businessName)
    await page.getByLabel('Categoría').selectOption({ index: 1 })
    await page.getByLabel('Número').fill(uniqueName('9').replace(/\W/g, '').slice(0, 15))
    await page.getByLabel('Teléfono de contacto').fill('+573001112233')
    await page.getByRole('button', { name: 'Continuar' }).click()

    await page.getByLabel(/^Nombre\s*\*?$/).fill('Sede Principal')
    await page.getByLabel('Ciudad').selectOption({ index: 1 })
    await page.getByLabel('Dirección').fill('Calle 10 # 20-30')
    await page.getByRole('button', { name: 'Enviar solicitud' }).click()
    await expect(page.getByRole('heading', { name: 'Recibimos tu solicitud' })).toBeVisible()
  })

  await test.step('confirm the email and wait for the administrator (seeded through the API)', async () => {
    const token = await waitForEmailToken(account.email, SIGNUP_SUBJECT)
    await page.goto(`/verificar-correo?token=${token}`)
    await expect(page.getByRole('heading', { name: 'Correo confirmado' })).toBeVisible()
    await approveOrganization(await organizationOf(account))
  })

  await test.step('log in and open the business', async () => {
    await loginViaUi(page, account)
    await expect(page).toHaveURL('/negocio')
    await page.getByRole('link', { name: new RegExp(businessName) }).click()
    await expect(page.getByRole('heading', { name: businessName, level: 1 })).toBeVisible()
  })

  const tabs = page.getByRole('navigation', { name: 'Secciones' })
  const dialog = page.getByRole('dialog')

  await test.step('create a resource type', async () => {
    await tabs.getByRole('link', { name: 'Tipos de recurso' }).click()
    await page.getByRole('button', { name: 'Nuevo tipo' }).click()
    await dialog.getByLabel('Nombre').fill(resourceTypeName)
    await dialog.getByRole('button', { name: 'Crear' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('main').getByText(resourceTypeName)).toBeVisible()
  })

  await test.step('create a resource of that type', async () => {
    await tabs.getByRole('link', { name: 'Recursos' }).click()
    await page.getByRole('button', { name: 'Nuevo recurso' }).click()
    await dialog.getByLabel('Nombre').fill(resourceName)
    await dialog.getByLabel('Tipo').selectOption({ label: resourceTypeName })
    await dialog.getByRole('button', { name: 'Registrar' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('row', { name: new RegExp(resourceName) })).toContainText('Disponible')
  })

  await test.step('create a service and activate it', async () => {
    await tabs.getByRole('link', { name: 'Servicios' }).click()
    await page.getByRole('button', { name: 'Nuevo servicio' }).click()
    await dialog.getByLabel(/^Nombre\s*\*?$/).fill(serviceName)
    await dialog.getByLabel('Duración (min)').fill('60')
    await dialog.getByLabel('Precio (COP)').fill('80000')
    await dialog.getByLabel('Tipo de recurso').selectOption({ label: resourceTypeName })
    await dialog.getByRole('button', { name: 'Crear servicio' }).click()
    await expect(dialog).toBeHidden()

    // Scoped to <main>: the success toast also mentions the service name
    const service = page.getByRole('main').getByRole('listitem').filter({ hasText: serviceName })
    await expect(service).toContainText('Inactivo')
    await service.getByRole('button', { name: 'Activar' }).click()
    await expect(service).toContainText('Activo')
  })

  await test.step('create a weekly agenda for the service', async () => {
    await tabs.getByRole('link', { name: 'Agendas' }).click()
    await page.getByRole('button', { name: 'Nueva agenda' }).click()
    await dialog.getByLabel(/^Nombre\s*\*?$/).fill(agendaName)
    await dialog.getByLabel('Intervalo').selectOption('60')
    await dialog.getByLabel(serviceName).check()
    // One morning block on Monday, copied to the other weekdays
    await dialog.getByRole('button', { name: 'Horario', exact: true }).first().click()
    await dialog.getByRole('button', { name: 'Copiar a días hábiles' }).click()
    await dialog.getByRole('button', { name: 'Crear agenda' }).click()
    await expect(dialog).toBeHidden()
    await expect(page.getByRole('heading', { name: agendaName })).toBeVisible()
  })

  await test.step('the service is listed in the public catalog', async () => {
    await page.goto('/')
    await page.getByRole('searchbox', { name: 'Buscar servicios' }).fill(serviceName)
    await page.getByRole('button', { name: 'Buscar' }).click()
    await expect(page.getByRole('heading', { name: serviceName })).toBeVisible()
    await expect(page.getByText(businessName)).toBeVisible()
  })
})
