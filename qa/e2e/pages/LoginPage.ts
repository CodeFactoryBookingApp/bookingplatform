import { expect } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /ingresar (LoginPage.tsx), HU-021. */
export class LoginPage extends BasePage {
  async open() {
    await this.page.goto('/ingresar');
    await expect(this.heading('Ingresar')).toBeVisible();
  }

  async signIn(email: string, password: string) {
    await this.page.getByLabel('Correo').fill(email);
    await this.page.getByLabel('Contraseña').fill(password);
    await this.page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  }

  /** Abre la pantalla, ingresa y espera a que la app redirija según el rol. */
  async signInSuccessfully(email: string, password: string) {
    await this.open();
    await this.signIn(email, password);
    await expect(this.page.getByRole('button', { name: 'Salir' })).toBeVisible();
    await expect(this.page).not.toHaveURL(/\/ingresar$/);
  }

  async expectError(message: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: message })).toBeVisible();
    await expect(this.page).toHaveURL(/\/ingresar$/);
    await expect(this.page.getByRole('button', { name: 'Salir' })).toHaveCount(0);
  }
}
