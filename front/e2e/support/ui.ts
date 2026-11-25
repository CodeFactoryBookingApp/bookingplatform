import { expect, type Page } from '@playwright/test'
import type { Account } from './api'

/** Fills the sign-in form and submits it; the caller decides what to expect afterwards. */
export async function submitLogin(page: Page, { email, password }: Account): Promise<void> {
  await page.goto('/ingresar')
  await page.getByLabel('Correo').fill(email)
  await page.getByLabel('Contraseña').fill(password)
  await page.getByRole('button', { name: 'Ingresar' }).click()
}

/** Signs in through the UI and waits until the shell shows the signed-in user. */
export async function loginViaUi(page: Page, account: Account): Promise<void> {
  await submitLogin(page, account)
  await expect(page.getByRole('button', { name: 'Salir' })).toBeVisible()
}

/** Local calendar date ("2026-11-25") `days` from now, as the booking page's date input takes it. */
export function dateInDays(days: number): string {
  const date = new Date()
  date.setDate(date.getDate() + days)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
