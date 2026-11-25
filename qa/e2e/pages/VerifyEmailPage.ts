import { expect } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /verificar-correo (VerifyEmailPage.tsx): destino del enlace del correo de verificación. */
export class VerifyEmailPage extends BasePage {
  /** Abre el enlace tal como lo arma Supabase: Site URL + ?token_hash=... */
  async openLink(tokenHash: string) {
    await this.page.goto(`/verificar-correo?token_hash=${encodeURIComponent(tokenHash)}`);
  }

  async expectConfirmed() {
    await expect(this.heading('Correo confirmado')).toBeVisible();
  }

  async continueToLogin() {
    await this.page.getByRole('main').getByRole('link', { name: 'Ingresar' }).click();
    await expect(this.heading('Ingresar')).toBeVisible();
  }
}
