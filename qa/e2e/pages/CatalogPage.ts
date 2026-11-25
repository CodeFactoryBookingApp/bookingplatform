import { expect, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** Catálogo público en / (CatalogPage.tsx), HU-005. */
export class CatalogPage extends BasePage {
  private get results(): Locator {
    return this.page.getByRole('region', { name: 'Resultados' });
  }

  private card(service: string, provider: string): Locator {
    return this.results
      .getByRole('listitem')
      .filter({ has: this.page.getByRole('heading', { name: service }) })
      .filter({ hasText: provider });
  }

  async open() {
    await this.page.goto('/');
    await expect(this.heading('Encuentra y reserva el servicio que necesitas')).toBeVisible();
  }

  async search(text: string) {
    await this.page.getByRole('searchbox', { name: 'Buscar servicios' }).fill(text);
    await this.page.getByRole('search').getByRole('button', { name: 'Buscar' }).click();
    await expect(this.page).toHaveURL(/[?&]q=/);
  }

  async expectServiceListed(service: string, provider: string, details: string[] = []) {
    const card = this.card(service, provider);
    await expect(card).toBeVisible();
    for (const text of details) await expect(card).toContainText(text);
  }

  async expectNoResults() {
    await expect(this.results.getByRole('heading', { name: 'No encontramos servicios' })).toBeVisible();
  }

  async startBooking(service: string, provider: string) {
    await this.card(service, provider).getByRole('link', { name: new RegExp(`^Reservar ${service}`) }).click();
    await expect(this.page).toHaveURL(/\/reservar\//);
  }
}
