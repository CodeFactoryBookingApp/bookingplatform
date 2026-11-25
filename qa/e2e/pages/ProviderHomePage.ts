import { expect } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /negocio (OrganizationsPage.tsx): a donde llega un usuario con rol PROVIDER al ingresar. */
export class ProviderHomePage extends BasePage {
  async expectOpen() {
    await expect(this.page).toHaveURL(/\/negocio$/);
    await expect(this.heading('Mis negocios')).toBeVisible();
  }
}
