# ADR-0003: Autenticación delegada en Supabase Auth con validación JWT por JWKS (API proxy)

- **Estado:** Aceptado
- **Fecha:** 2026-09-18
- **Prioridad:** Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

HU-021 exige login seguro por rol, política de contraseñas, bloqueo tras
intentos fallidos, recuperación con enlace de un solo uso y cierre de sesión que
invalida la sesión. HU-001 exige verificación de correo con reenvío de enlace
vigente. El lineamiento del caso pide contratos REST propios, versionados y
documentados, con reglas de negocio reales.

## Decisión

**Proxy de autenticación**: la API expone sus propios endpoints
`/api/v1/auth/*` y `/api/v1/registrations/*`; internamente delegan en GoTrue
mediante el port `IdentityProviderPort` (adaptador `GoTrueClient` con la secret
key, solo server-side). Supabase nunca queda expuesto como contrato público.

- **Validación de tokens:** Spring Security OAuth2 Resource Server con
  `NimbusJwtDecoder` contra el **JWKS** del proyecto
  (`{SUPABASE_URL}/auth/v1/.well-known/jwks.json`), algoritmos ES256/RS256,
  validando `iss` y expiración. Stateless: sin sesión HTTP.
- **Autorización:** rol de aplicación desde `app_metadata.role` mapeado a
  `ROLE_CLIENT/ROLE_PROVIDER/ROLE_ADMIN` (`SupabaseJwtAuthConverter`).
  `user_metadata` se ignora por ser editable por el usuario.
- **Bloqueo por intentos fallidos:** política local (`LoginLockPolicy` +
  tabla `login_attempts`): 5 fallos en ventana de 15 minutos → HTTP 429 con
  `retryAfterMinutes`. Los intentos se registran solo ante fallo de credenciales
  o correo no confirmado.
- **Logout:** revoca la sesión (refresh token) en GoTrue vía `POST /auth/v1/logout`;
  los access tokens tienen expiración corta (15 min, configurado en Supabase).
- **Anti-enumeración:** reenvío de verificación y recuperación de contraseña
  responden siempre 202, exista o no el correo.
- **MFA para administradores:** diferido a Sprint 2 (Supabase soporta TOTP
  nativo; aún no existe flujo de admins). Deuda registrada en el backlog.

## Alternativas consideradas

1. **JWT propios con jjwt:** control total, pero obliga a implementar hashing,
   verificación de correo, tokens de un solo uso y revocación; duplica lo que
   GoTrue ya resuelve y amplia la superficie de seguridad a auditar.
2. **Cliente directo a GoTrue (sin proxy):** menos código, pero rompe el
   contrato REST unificado/versionado del caso y deja el bloqueo por intentos
   sin dónde implementarse.

## Consecuencias

- (+) Contrato público estable y propio; Supabase es un detalle de
  implementación intercambiable detrás de un port.
- (+) Reglas de negocio de auth (bloqueo, política de contraseña) viven en
  nuestro dominio y son testeables sin proveedor.
- (−) Un access token robado sigue siendo válido hasta su expiración corta;
  mitigación: exp 15 min + revocación de refresh en logout.
- (−) Latencia adicional en login (doble salto). Aceptable para el MVP.
- (−) Dependencia de disponibilidad de GoTrue (mapeada a HTTP 502
  `UPSTREAM_AUTH_ERROR`).
