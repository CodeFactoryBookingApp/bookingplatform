import { expect, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /bandeja (InboxPage.tsx): correos que la plataforma deja en la bandeja de la app, HU-015. */
export class InboxPage extends BasePage {
  private message(subject: string, contains: string): Locator {
    return this.page.getByRole('main').getByRole('listitem').filter({ hasText: subject }).filter({ hasText: contains });
  }

  async open() {
    await this.page.goto('/bandeja');
    await expect(this.heading('Bandeja')).toBeVisible();
  }

  /**
   * Los avisos de reserva los envía un proceso programado (BookingNoticeScheduler), así que se
   * recarga la bandeja hasta que llegue o se agote la espera.
   */
  async waitForMessage(subject: string, contains: string, timeoutMs = 45_000) {
    await expect(async () => {
      await this.page.reload();
      await expect(this.heading('Bandeja')).toBeVisible();
      await expect(this.message(subject, contains)).toBeVisible({ timeout: 2_000 });
    }).toPass({ timeout: timeoutMs, intervals: [1_000, 2_000, 3_000] });
  }

  async openMessage(subject: string, contains: string) {
    await this.message(subject, contains).getByText(subject).click();
  }

  async expectMessageBody(subject: string, contains: string, texts: string[]) {
    const item = this.message(subject, contains);
    for (const text of texts) await expect(item).toContainText(text);
  }
}
