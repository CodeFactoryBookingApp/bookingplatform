import { expect, type Download, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /mis-reservas (MyBookingsPage.tsx y ClientCancelModal.tsx), HU-013, HU-014 y HU-017. */
export class MyBookingsPage extends BasePage {
  private item(code: string): Locator {
    return this.page.getByRole('main').getByRole('listitem').filter({ hasText: code });
  }

  private get dialog(): Locator {
    return this.page.getByRole('dialog');
  }

  async open() {
    await this.page.goto('/mis-reservas');
    await expect(this.heading('Mis reservas')).toBeVisible();
  }

  async expectBooking(code: string, texts: string[] = []) {
    const item = this.item(code);
    await expect(item).toBeVisible();
    for (const text of texts) await expect(item).toContainText(text);
  }

  async expectSection(name: 'Próximas' | 'Anteriores', code: string) {
    const section = this.page.locator('section', { has: this.page.getByRole('heading', { name, level: 2 }) });
    await expect(section.getByRole('listitem').filter({ hasText: code })).toBeVisible();
  }

  // ---- HU-013: cancelación ----
  async startCancellation(code: string) {
    await this.item(code).getByRole('button', { name: 'Cancelar' }).click();
    await expect(this.dialog.getByRole('heading', { name: `Cancelar la reserva ${code}` })).toBeVisible();
  }

  async requestCancellation(reason?: string) {
    if (reason) await this.dialog.getByLabel('Motivo').fill(reason);
    await this.dialog.getByRole('button', { name: 'Cancelar reserva' }).click();
  }

  async expectCancellationTerms(title: 'Cancelación sin penalidad' | 'Se aplica penalidad por cancelación tardía') {
    await expect(this.dialog.getByRole('alert').or(this.dialog.getByText(title)).first()).toContainText(title);
  }

  async confirmCancellation() {
    await this.dialog.getByRole('button', { name: 'Sí, cancelar la reserva' }).click();
    await expect(this.dialog).toBeHidden();
  }

  async expectStatus(code: string, status: string) {
    await expect(this.item(code)).toContainText(status);
  }

  // ---- HU-014: reprogramación ----
  async startReschedule(code: string) {
    await this.item(code).getByRole('button', { name: /Reprogramar/ }).click({ timeout: 5_000 });
  }

  // ---- HU-017: comprobante PDF ----
  async downloadReceipt(code: string): Promise<Download> {
    const download = this.page.waitForEvent('download', { timeout: 10_000 });
    await this.item(code).getByRole('button', { name: /comprobante/i }).or(this.item(code).getByRole('link', { name: /comprobante/i }))
      .click({ timeout: 5_000 });
    return download;
  }
}
