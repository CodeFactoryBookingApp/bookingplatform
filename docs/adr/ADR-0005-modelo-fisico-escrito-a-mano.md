# ADR-0005: El modelo físico se escribe a mano y se verifica en integración continua

- **Estado:** Aceptado
- **Fecha:** 2026-09-21
- **Prioridad:** Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

El ADR-0002 dejó `hibernate.ddl-auto=update` en desarrollo y `validate` en la
nube, con el modelo físico "versionado como script exportado en
`docs/database/schema.sql`". Esa estrategia cumplió para arrancar el Sprint 1,
pero produjo tres huecos: el script exportado documenta las dos tablas que hoy
tienen entidad JPA (`clients`, `login_attempts`) mientras `modelo-logico.md`
describe dieciocho; un script generado desde el mapeo nunca lleva claves
foráneas, índices de consulta ni restricciones de negocio; y las consultas de
`consultas-clave.md` no se pueden ejecutar porque las tablas que referencian no
existen en ningún lado. El caso exige además "comprobaciones automáticas
obligatorias" en los pull requests, y el repositorio no tenía ninguna.

## Decisión

Invertir la relación entre el código y el esquema: **`docs/database/schema.sql`
pasa a ser la fuente del modelo de datos**, escrita a mano, y deja de ser un
volcado de lo que Hibernate genera.

1. **Alcance completo del caso:** diecinueve tablas que cubren identidad,
   organización, catálogo de servicios, recursos, agenda y reservas. Las dos
   tablas ya implementadas se reproducen con los mismos nombres, tipos y largos
   que produce el mapeo actual, incluido el `timestamp(6)` sin zona de
   `AuditableEntity`, para no romper el `validate` del perfil `cloud`.
2. **Restricciones de negocio en la base:** las reglas de HU-001 a HU-004 que
   se pueden expresar de forma declarativa viven en el esquema. Entre ellas, la
   restricción `exclude using gist` que impide que un recurso quede asignado a
   dos sesiones solapadas, que es el problema de sobreocupación del enunciado.
3. **Verificación automática:** el workflow `.github/workflows/database.yml`
   levanta un PostgreSQL vacío en cada pull request que toque `docs/database/`
   y comprueba que el DDL ejecute, que sea idempotente, que los datos de prueba
   carguen, que las catorce consultas clave devuelvan filas y que las dieciocho
   violaciones de `pruebas-integridad.sql` sean rechazadas.

El script es idempotente (`if not exists` en todas las sentencias `create`), de
modo que se puede aplicar sobre la base actual de Supabase sin alterar las dos
tablas existentes. La política de **RLS activado en todas las tablas de
negocio** del ADR-0002 se extiende a las diecinueve en `docs/database/rls.sql`.

## Alternativas consideradas

1. **Mantener el script como export de `ddl-auto`:** cuesta cero esfuerzo y
   sigue el flujo actual, pero el esquema solo puede crecer al ritmo al que se
   escriben entidades JPA, y el entregable de datos del sprint quedaría sin
   claves foráneas ni consultas ejecutables.
2. **Adoptar Flyway ya en el Sprint 1:** es el destino correcto y el ADR-0002
   lo dejó como deuda priorizada, pero exige decidir la línea base, reescribir
   el arranque local y coordinarlo con el despliegue; se mantiene para el
   Sprint 2, y este ADR le prepara el terreno al dejar un esquema explícito que
   puede convertirse en la migración `V1__baseline.sql`.

## Consecuencias

- (+) El modelo de datos del caso queda completo y ejecutable, no solo dibujado.
- (+) Las reglas de negocio quedan garantizadas por el motor y no dependen de
  que cada caso de uso se acuerde de validarlas.
- (+) El repositorio gana su primera comprobación automática, que es un
  lineamiento explícito del caso.
- (−) Aparece la posibilidad de que el esquema escrito a mano y el mapeo JPA
  diverjan; se compensa con `ddl-auto=validate` en la nube, que falla al
  arrancar si una entidad no cuadra con su tabla.
- (−) Diecisiete de las diecinueve tablas todavía no tienen entidad JPA: son
  modelo de datos adelantado al código. Mientras no se implementen, `validate`
  las ignora porque solo comprueba las tablas mapeadas.
- (−) `seed.sql` y `pruebas-integridad.sql` usan datos ficticios acoplados a
  identificadores fijos; si el esquema cambia hay que mantenerlos, y el
  workflow avisa cuando se rompen.

Ver `docs/database/schema.sql` (modelo físico), `modelo-logico.md` (entidades y
normalización), `consultas-clave.md` (preguntas de negocio), `seed.sql` (datos
de prueba), `pruebas-integridad.sql` (restricciones en acción) y `rls.sql`
(seguridad Supabase).
