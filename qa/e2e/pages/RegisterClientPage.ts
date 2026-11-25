import { expect } from '@playwright/test';
import { BasePage } from './BasePage.ts';

export interface ClientForm {
  fullName: string;
  document: string;
  birthDate: string;
  email: string;
  phone: string;
  city: string;
  password: string;
}

/** /registro (RegisterClientPage.tsx), HU-001. */
export class RegisterClientPage extends BasePage {
  async open() {
    await this.page.goto('/registro');
    await expect(this.heading('Crear cuenta')).toBeVisible();
  }

  async fill(form: ClientForm) {
    await this.page.getByLabel('Nombre completo').fill(form.fullName);
    await this.page.getByLabel('Documento').fill(form.document);
    await this.page.getByLabel('Fecha de nacimiento').fill(form.birthDate);
    await this.page.getByLabel('Correo').fill(form.email);
    await this.page.getByLabel('Teléfono').fill(form.phone);
    await this.page.getByLabel('Ciudad').fill(form.city);
    await this.page.getByLabel('¿Cómo quieres recibir avisos?').selectOption('EMAIL');
    await this.page.getByLabel('Contraseña').fill(form.password);
  }

  async submit() {
    await this.page.getByRole('button', { name: 'Crear cuenta' }).click();
  }

  async expectCheckYourEmail(email: string) {
    await expect(this.heading('Revisa tu correo')).toBeVisible();
    await expect(this.page.getByText(email)).toBeVisible();
  }

  async expectFieldError(message: string) {
    await expect(this.page.getByRole('alert').filter({ hasText: message })).toBeVisible();
    await expect(this.heading('Crear cuenta')).toBeVisible();
  }
}
