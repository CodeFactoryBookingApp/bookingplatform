import { expect, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** Mi negocio > Reservas (OrganizationsPage.tsx, OrganizationLayout.tsx y BookingsPage.tsx), HU-018 y HU-016. */
export class ProviderBookingsPage extends BasePage {
  private row(code: string): Locator {
    return this.page.getByRole('table', { name: 'Reservas del negocio' }).getByRole('row').filter({ hasText: code });
  }

  async openBusiness(name: string) {
    await this.goToNav('Mi negocio');
    await expect(this.heading('Mis negocios')).toBeVisible();
    await this.page.getByRole('main').getByRole('link', { name }).click();
    await expect(this.page.getByRole('heading', { name })).toBeVisible();
  }

  async openBookings() {
    await this.page.getByRole('main').getByRole('link', { name: 'Reservas', exact: true }).click();
    await expect(this.page.getByRole('region', { name: 'Filtros' })).toBeVisible();
  }

  async filterByCode(code: string) {
    const input = this.page.getByLabel('Código de reserva');
    await input.fill(code);
    await input.press('Enter');
    await expect(this.page).toHaveURL(/codigo=/);
  }

  async expectBooking(code: string, texts: string[]) {
    const row = this.row(code);
    await expect(row).toBeVisible();
    for (const text of texts) await expect(row).toContainText(text);
  }

  async expectTotal(total: number) {
    const stat = this.page.getByRole('region', { name: 'Resumen' }).locator('div', { hasText: /^Reservas/ }).first();
    await expect(stat).toContainText(String(total));
  }

  // ---- HU-016: asistencia ----
  async markAttendance(code: string, outcome: 'Asistió' | 'No asistió') {
    await this.row(code).getByRole('button', { name: new RegExp(outcome) }).click({ timeout: 5_000 });
  }
}
