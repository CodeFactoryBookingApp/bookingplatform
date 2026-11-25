/**
 * Cliente HTTP de apoyo para preparar datos por la API (no por la UI) y para hablar con el
 * simulador de Supabase Auth. Lo usan global-setup.ts y los hooks de los steps; los escenarios
 * no lo usan para verificar resultados que la UI debe mostrar.
 */
import { env } from './env.ts';

export const PASSWORD = 'ClaveSegura2026!';

type Json = Record<string, unknown> | unknown[];

async function call<T>(method: string, url: string, body?: Json, token?: string): Promise<{ status: number; data: T }> {
  const res = await fetch(url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let data: unknown = text;
  try {
    data = text ? JSON.parse(text) : undefined;
  } catch {
    /* respuesta no JSON (PDF, CSV) */
  }
  return { status: res.status, data: data as T };
}

/** Llamada a /api/v1 que debe responder 2xx; si no, lanza con el detalle para el log del setup. */
export async function api<T = any>(method: string, path: string, body?: Json, token?: string): Promise<T> {
  const { status, data } = await call<T>(method, `${env.apiUrl}/api/v1${path}`, body, token);
  if (status >= 300) throw new Error(`${method} ${path} -> ${status}: ${JSON.stringify(data)}`);
  return data;
}

/** Igual que api() pero devuelve el estado sin lanzar, para pasos que esperan un error. */
export async function apiRaw<T = any>(method: string, path: string, body?: Json, token?: string) {
  return call<T>(method, `${env.apiUrl}/api/v1${path}`, body, token);
}

// ---- Simulador de Supabase Auth (/_ctl/*) ----
async function ctl<T = any>(path: string, body: Json): Promise<T> {
  const { status, data } = await call<T>('POST', `${env.stubUrl}/_ctl/${path}`, body);
  if (status >= 300) throw new Error(`stub ${path} -> ${status}`);
  return data;
}

/** Crea (o reemplaza) un usuario confirmado en el simulador con el rol indicado. */
export const stubUser = (email: string, role: 'CLIENT' | 'PROVIDER' | 'ADMIN') => ctl('user', { email, role, confirmed: true });
/** token_hash de un solo uso para confirmar el correo de `email` (lo que llevaría el enlace del correo). */
export const stubConfirmHash = async (email: string) => (await ctl<{ token_hash: string }>('confirm', { email })).token_hash;

/** Inicia sesión por la API (como haría la UI) y devuelve el access token. */
export async function loginToken(email: string, password = PASSWORD): Promise<string> {
  return (await api<{ accessToken: string }>('POST', '/auth/login', { email, password })).accessToken;
}

export const unique = (prefix: string) => `${prefix}-${Date.now().toString(36)}${Math.floor(Math.random() * 1e4).toString(36)}`;
