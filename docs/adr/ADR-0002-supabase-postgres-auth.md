# ADR-0002: PostgreSQL administrado en Supabase como base de datos y proveedor de identidad

- **Estado:** Aceptado
- **Fecha:** 2026-09-18
- **Prioridad:** Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

El caso permite PostgreSQL con Supabase o Neon como servicio administrado. El
equipo necesita: BD para el MVP desplegable sin costo, y capacidades de
autenticación (verificación de correo, recuperación con enlace de un solo uso,
JWT) requeridas por HU-001/HU-021 en el primer sprint.

## Decisión

Usar **un proyecto Supabase** (free tier) que aporta:

1. **PostgreSQL administrado** para los datos de negocio. El backend se conecta
   por JDBC (puerto directo/sesión 5432, no el transaction pooler 6543) como
   dueño del esquema.
2. **Supabase Auth (GoTrue)** como proveedor de identidad: almacena
   credenciales en `auth.users`, envía correos de verificación y recuperación,
   emite JWT (ES256 con JWKS público) y gestiona sesiones/refresh.

Estrategia de esquema (decisión del equipo para Sprint 1):
`hibernate.ddl-auto=update` en desarrollo y `validate` en la nube
(perfil `cloud`), con el modelo físico versionado como script exportado en
`docs/database/schema.sql`.

Seguridad obligatoria: **RLS activado** en todas las tablas de negocio y
`REVOKE` de `anon`/`authenticated` (`docs/database/rls.sql`), porque el anon key
es público y PostgREST expone el esquema `public`. La secret key de Supabase
vive solo en variables de entorno del backend.

Los perfiles de negocio (`clients`) se enlazan a la identidad por
`clients.id = auth.users.id`; el rol de aplicación se guarda en
`app_metadata.role` (nunca en `user_metadata`, que es editable por el usuario).

## Alternativas consideradas

1. **Neon + autenticación propia (spring-security + jjwt):** control total de la
   lógica de auth, pero exige implementar y securizar verificación de correo,
   tokens de un solo uso, hashing, recuperación y MFA en el Sprint 1. Riesgo
   alto de no cerrar el sprint.
2. **PostgreSQL local en Docker para todo:** reproducible, pero no cumple el
   entregable de "despliegue inicial en nube" sin infraestructura adicional.

## Consecuencias

- (+) Sprint 1 viable: correo de verificación/recuperación y JWT resueltos por
  la plataforma; el equipo se concentra en reglas de negocio.
- (+) BD y auth en el mismo proveedor: un solo aprovisionamiento para Render.
- (−) Dependencia de un proveedor externo (vendor lock-in moderado: los datos
  son PostgreSQL estándar y exportables; auth está detrás del port
  `IdentityProviderPort`, reemplazable).
- (−) `ddl-auto=update` no es apto para producción seria; se compensa con
  `validate` en la nube y el script exportado. Migrar a Flyway queda como deuda
  técnica priorizada para Sprint 2.
- (−) El bloqueo por intentos fallidos no lo provee GoTrue (solo rate limiting
  por IP), por lo que se implementa localmente (`login_attempts` +
  `LoginLockPolicy`).
