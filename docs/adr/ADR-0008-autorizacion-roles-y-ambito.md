# ADR-0008: Autorización en dos niveles, rol de plataforma y rol dentro del proveedor

- **Estado:** Aceptado
- **Fecha:** 2026-10-25
- **Prioridad:** Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software, con el Product Owner para los roles

## Contexto

HU-022 nombra cinco roles: administrador, gerente, proveedor, recepcionista y
cliente. Además exige que "cada acción valide el rol del usuario y además que
el recurso solicitado pertenezca a su ámbito", y que el acceso fuera de ámbito
"no revele la existencia del recurso".

El JWT de Supabase lleva un único rol en `app_metadata.role`, que el usuario no
puede editar y que `SupabaseJwtAuthConverter` traduce a `ROLE_*`. El esquema ya
tenía `organization_members` con los roles `OWNER`, `MANAGER` y `STAFF`.
Hasta HU-002 no existía ninguna regla de autorización: cualquier usuario
autenticado podía llamar a cualquier endpoint protegido.

La pregunta era si el gerente y el recepcionista son roles de la plataforma o
roles dentro de cada proveedor. Una misma persona puede ser recepcionista de
una clínica y no tener nada que ver con otra.

## Decisión

1. **Rol de plataforma, en el JWT:** `ADMIN`, `PROVIDER` y `CLIENT`. Dice qué
   tipo de usuario es alguien y qué rutas puede usar. Se comprueba en
   `SecurityConfig`:
   - `/api/v1/admin/**` solo `ADMIN`;
   - `/api/v1/organizations/**` solo `PROVIDER` o `ADMIN`;
   - lo demás, autenticado o público según el caso.
2. **Rol dentro del proveedor, en `organization_members`:** dice qué puede
   hacer alguien en un proveedor concreto. Correspondencia con HU-022:
   `OWNER` = proveedor (quien registró el negocio), `MANAGER` = gerente,
   `STAFF` = recepcionista y demás personal. Lo comprueban los casos de uso a
   través de `OrganizationAccess`, que también usarán los módulos `catalog` y
   `scheduling`.
3. **Fuera de ámbito, 404; rol insuficiente, 403.** Quien no es miembro de un
   proveedor recibe `404 RESOURCE_NOT_FOUND`, la misma respuesta que para un
   proveedor que no existe; así no se revela su existencia. Un miembro cuyo rol
   no permite la acción recibe `403 ACCESS_DENIED` con un mensaje que nombra su
   rol, que es el "mensaje descriptivo" que pide HU-022.
4. **El primer administrador se crea a mano.** No hay endpoint para crear
   administradores: se asigna `app_metadata.role = ADMIN` a un usuario desde
   Supabase (runbook, sección 3, paso 6). Así no existe ninguna superficie de ataque para
   escalar a administrador.
5. **El rol de un proveedor nuevo es `PROVIDER` desde el registro** (HU-002),
   con una cuenta propia, distinta de cualquier cuenta de cliente.

## Alternativas consideradas

1. **Cinco roles de plataforma en el JWT:** con un solo rol por token, alguien
   que es gerente de un proveedor y cliente de otro no cabría, y el rol global
   no dice a qué proveedor se aplica. El ámbito habría que resolverlo igual.
2. **Responder 403 también fuera de ámbito:** es más simple, pero confirma que
   el recurso existe, y HU-022 lo prohíbe expresamente.
3. **Un endpoint para crear administradores protegido por otro administrador:**
   es útil más adelante (HU-022 "gestionar roles"), pero no resuelve cómo se
   crea el primero y añade una superficie sensible antes de necesitarla.

## Consecuencias

- (+) El ámbito por proveedor queda resuelto en un solo lugar, reutilizable por
  los módulos del sprint.
- (+) Un usuario puede pertenecer a varios proveedores con roles distintos sin
  tocar el token.
- (−) El rol de plataforma se lee del JWT: un cambio de rol no toma efecto hasta
  que el token expira (15 min). HU-022 pide que sea inmediato; queda pendiente
  junto con D8 (revocación de tokens).
- (−) Cada caso de uso de un proveedor debe acordarse de llamar a
  `OrganizationAccess`. Las pruebas de integración de cada HU tienen que cubrir
  el 404 y el 403.
- (−) Los usuarios `STAFF` y `MANAGER` tienen rol de plataforma `PROVIDER`;
  invitarlos a un proveedor es trabajo de una HU posterior.

Ver `OrganizationAccess`, `SecurityConfig`, `AuthenticatedUser` y
`ProviderOnboardingIT` (las reglas en acción).
