import { expect, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /reservar/:serviceId/:locationId (BookingPage.tsx), HU-012. */
export class BookingPage extends BasePage {
  private get slots(): Locator {
    return this.page.getByRole('list', { name: 'Horarios' }).getByRole('button');
  }

  private get summary(): Locator {
    return this.page.locator('div', { has: this.page.getByRole('heading', { name: 'Resumen', level: 2 }) }).last();
  }

  async chooseDay(isoDate: string) {
    await this.page.getByLabel('Día').fill(isoDate);
    await expect(this.slots.first()).toBeVisible();
  }

  /** Elige la primera franja libre del día y devuelve la hora tal como se muestra. */
  async chooseFirstFreeSlot(): Promise<string> {
    const slot = this.slots.first();
    const label = (await slot.innerText()).trim();
    await slot.click();
    await expect(slot).toHaveAttribute('aria-pressed', 'true');
    return label;
  }

  async expectSummary(texts: string[]) {
    await expect(this.page.getByRole('button', { name: 'Confirmar reserva' })).toBeVisible();
    for (const text of texts) await expect(this.summary).toContainText(text);
  }

  async confirm() {
    await this.page.getByRole('button', { name: 'Confirmar reserva' }).click();
  }

  /** Comprueba el comprobante en pantalla y devuelve el código de la reserva. */
  async expectReceipt(title: 'Reserva confirmada' | 'Reserva recibida' | 'Reserva cancelada'): Promise<string> {
    await expect(this.heading(title)).toBeVisible();
    const code = (await this.page.getByText(/^Código/).first().locator('span').innerText()).trim();
    expect(code.length).toBeGreaterThan(5);
    return code;
  }

  async expectReceiptStatus(label: string) {
    await expect(this.page.getByRole('main').getByText(label, { exact: true })).toBeVisible();
  }

  async goToMyBookings() {
    await this.page.getByRole('main').getByRole('link', { name: 'Mis reservas' }).click();
  }
}
