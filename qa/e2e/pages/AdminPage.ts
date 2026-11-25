import { expect, type Locator } from '@playwright/test';
import { BasePage } from './BasePage.ts';

/** /admin/proveedores y /admin/clientes (AdminProvidersPage.tsx, AdminClientsPage.tsx, RoleModal.tsx), HU-002 y HU-022. */
export class AdminPage extends BasePage {
  private get dialog(): Locator {
    return this.page.getByRole('dialog');
  }

  private providerRow(name: string): Locator {
    return this.page.getByRole('table', { name: 'Proveedores registrados' }).getByRole('row').filter({ hasText: name });
  }

  private clientRow(fullName: string): Locator {
    return this.page.getByRole('table', { name: 'Clientes registrados' }).getByRole('row').filter({ hasText: fullName });
  }

  async openProviders() {
    await this.page.goto('/admin/proveedores');
    await expect(this.heading('Administración')).toBeVisible();
    await expect(this.page.getByRole('table', { name: 'Proveedores registrados' })).toBeVisible();
  }

  async openClients() {
    await this.page.goto('/admin/clientes');
    await expect(this.heading('Administración')).toBeVisible();
    await expect(this.page.getByRole('table', { name: 'Clientes registrados' })).toBeVisible();
  }

  async expectProviderStatus(name: string, status: string) {
    await expect(this.providerRow(name)).toContainText(status);
  }

  async approveProvider(name: string, reason: string) {
    await this.page.getByRole('button', { name: `Aprobar ${name}` }).click();
    await expect(this.dialog.getByRole('heading', { name: `Aprobar proveedor: ${name}` })).toBeVisible();
    await this.dialog.getByLabel('Motivo').fill(reason);
    await this.dialog.getByRole('button', { name: 'Aprobar', exact: true }).click();
    await expect(this.dialog).toBeHidden();
  }

  async expectClientListed(fullName: string) {
    await expect(this.clientRow(fullName)).toBeVisible();
  }

  async changeRole(fullName: string, role: 'Cliente' | 'Proveedor' | 'Administrador') {
    await this.page.getByRole('button', { name: `Cambiar el rol de ${fullName}` }).click();
    await expect(this.dialog.getByRole('heading', { name: `Cambiar el rol de ${fullName}` })).toBeVisible();
    await this.dialog.getByLabel('Nuevo rol').selectOption({ label: role });
    await expect(this.dialog.getByText('Se cierran sus sesiones abiertas')).toBeVisible();
    await this.dialog.getByRole('button', { name: 'Cambiar rol' }).click();
    await expect(this.dialog).toBeHidden();
  }
}
