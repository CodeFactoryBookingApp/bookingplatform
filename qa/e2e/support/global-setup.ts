/**
 * Se ejecuta una vez antes de todas las pruebas: comprueba que el entorno esté arriba
 * (support/start-env.sh) y crea por la API el negocio aprobado que usan los escenarios.
 */
import { env } from './env.ts';
import { createSeed } from './data.ts';

async function up(url: string, name: string) {
  try {
    const res = await fetch(url);
    if (res.status >= 500) throw new Error(String(res.status));
  } catch (error) {
    throw new Error(`${name} no responde en ${url} (${error}). Levanta el entorno con support/start-env.sh`);
  }
}

export default async function globalSetup() {
  await up(`${env.apiUrl}/actuator/health`, 'Backend');
  await up(`${env.stubUrl}/auth/v1/.well-known/jwks.json`, 'Simulador de Supabase Auth');
  await up(env.baseUrl, 'Front');
  const seed = await createSeed();
  console.log(`[global-setup] negocio "${seed.organizationName}" aprobado, servicio "${seed.serviceName}" activo`);
}
