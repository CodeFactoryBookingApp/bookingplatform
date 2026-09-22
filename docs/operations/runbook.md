# Runbook operativo — Sprint 1

Guía para operar y diagnosticar la plataforma en producción.
Todo lo aquí descrito fue verificado E2E contra Supabase + Render.

## 1. URLs de producción

Base: `https://bookingplatform-81wi.onrender.com`

| Uso | URL | Auth |
|---|---|---|
| Health (liveness + readiness) | `/actuator/health` | pública |
| Readiness (health check de Render) | `/actuator/health/readiness` | pública |
| Swagger UI | `/swagger-ui/index.html` | pública |
| Contrato OpenAPI | `/v3/api-docs` | pública |
| API | `/api/v1/...` | según endpoint |

> Plan Free de Render: la instancia se duerme tras ~15 min sin tráfico;
> la primera petición puede tardar ~1 min (cold start). No es un error.

## 2. Contrato de errores

Toda respuesta de error es `application/problem+json` con `errorCode`,
`traceId` (también en header `X-Trace-Id`), `timestamp` e `instance`.

La API es **cerrada por defecto**: cualquier ruta no pública sin
`Authorization: Bearer <jwt>` responde `401 AUTH_REQUIRED`
(`{"detail":"Authentication is required",...}`). Ver `/` sin token
devolver 401 es el comportamiento esperado, no una falla.

## 3. Setup de Supabase (proyecto `booking-platform`)

1. **Conexión BD:** panel **Connect → Session pooler** (puerto **5432**,
   usuario `postgres.<ref>`). Copiar el host exacto del panel (el índice
   `aws-0`/`aws-1` varía por proyecto). Nunca puerto 6543 para JDBC/Hibernate.
   La conexión directa `db.<ref>.supabase.co` requiere IPv6; en redes solo
   IPv4 no resuelve.
2. **API Keys:** Settings → API Keys → `SUPABASE_URL` y `SUPABASE_SECRET_KEY`
   (`sb_secret_...`, solo backend). Las secret keys son rechazadas si el
   `User-Agent` parece navegador, incluso fuera de uno.
3. **Auth → Providers → Email:** confirmación de correo activada,
   longitud mínima 8.
4. **Auth → Settings → JWT expiry:** `900` segundos (15 min, HU-021).
5. **Tablas:** ejecutar `docs/database/schema.sql` en el SQL Editor. Es
   idempotente, así que se puede aplicar sobre una base que ya tenga las tablas
   de Hibernate: se las salta y crea las restantes (ADR-0005). Verificar:
   ```sql
   select count(*) from pg_class
   where relnamespace = 'public'::regnamespace and relkind = 'r';
   -- deben ser 19
   ```
6. **RLS (después de las tablas):** ejecutar `docs/database/rls.sql` en el
   SQL Editor y verificar con:
   ```sql
   select relname as tabla, relrowsecurity as rls_habilitado from pg_class
   where relname in ('clients','login_attempts');
   ```
   El backend conecta como dueño y bypasea RLS; el Data API público queda
   bloqueado.

## 4. Despliegue en Render

Servicio Docker desde `main` (`render.yaml` + `Dockerfile` multi-stage).
Variables requeridas: `SPRING_PROFILES_ACTIVE=cloud`, `DATABASE_URL`
(JDBC con `?sslmode=require`), `DATABASE_USER`, `DATABASE_PASSWORD`,
`SUPABASE_URL`, `SUPABASE_SECRET_KEY`. Sin Secret Files (la app lee entorno,
no archivos). **Health Check Path:** `/actuator/health/readiness`.

Restricciones del free tier aplicadas en `application-cloud.yml`:
`server.port=${PORT:8080}` (Render inyecta `PORT`) y pool Hikari de 5/1,
porque el session pooler gratuito admite **15 clientes totales** entre todas
las conexiones (instancias, dashboard, corridas locales).

## 5. Flujo E2E manual (colección en `docs/api/sprint1.http`)

1. `POST /api/v1/registrations` → **201** `PENDING_VERIFICATION` (el correo
   se dispara en la misma transacción; si GoTrue lo rechaza, el registro
   se revierte con compensación).
2. **No hacer click** en el enlace del correo (apunta al Site URL, pensado
   para un frontend que aún no existe): copiar la URL y extraer `token`.
3. `POST /api/v1/registrations/email-verifications` con `tokenHash` → **200**
   `ACTIVE`. Los tokens son de un solo uso; cada reenvío invalida el anterior
   (`otp_expired`).
4. `POST /api/v1/auth/login` → **200** con `accessToken`/`refreshToken`.
5. `GET /api/v1/auth/me` con Bearer → **200** (`id`, `email`, `role` desde
   `app_metadata`).
6. `POST /api/v1/auth/logout` con Bearer → **204**.

## 6. Troubleshooting

| Síntoma | Causa | Acción |
|---|---|---|
| `FATAL: (EMAXCONNSESSION) max clients reached`, JPA no arranca | Pool de 15 del pooler agotado (varias instancias/corridas locales) | Apagar corridas locales contra Supabase; el pool cloud es 5; reintentar deploy tras 3-4 min |
| `FATAL: (ENOIDENTIFIER) no tenant identifier` con `psql` | El libpq local no negocia SNI con el pooler nuevo; el usuario/host están bien | No usar `psql` local: SQL Editor o el propio backend (JDBC sí conecta) |
| `Forbidden use of secret API key in browser` | Heurística por `User-Agent` (p. ej. PowerShell) | Llamar desde backend/curl con UA no-navegador; nunca exponer la key |
| Click en el correo → `localhost:3000` o `otp_expired` | El enlace es para frontend (Site URL) y de un solo uso | Confirmar por API con el `token` del enlace (paso 5) |
| Reenvío responde 202 pero no llega correo | Anti-enumeración: 202 también para correos inexistentes/borrados, o límite SMTP gratuito (~2 correos/hora) | Verificar que el usuario exista en Auth → Users; esperar ventana horaria |
| Registro 409 `DUPLICATE_EMAIL` en reprobas | Fila local huérfana (usuario Auth borrado a mano) | `delete from clients where email = '...'` y re-registrar |

## 7. Limpieza de datos de prueba

```sql
delete from clients where email like 'tu-patron-de-prueba%';
```
más borrado de los usuarios en Authentication → Users del dashboard.
