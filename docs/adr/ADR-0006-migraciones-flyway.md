# ADR-0006: El esquema se versiona con migraciones de Flyway

- **Estado:** Aceptado; reemplaza la estrategia de esquema del ADR-0002 y del ADR-0005
- **Fecha:** 2026-10-24
- **Prioridad:** Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

El ADR-0005 hizo de `docs/database/schema.sql` la fuente del modelo de datos,
escrito a mano y aplicado con `psql` o desde el SQL Editor de Supabase, y dejó
Flyway para el Sprint 2. Esa estrategia tiene tres limitaciones que el Sprint 2
vuelve urgentes:

1. **Las pruebas de integración no usaban el esquema real.** El perfil de
   pruebas tenía `ddl-auto=create-drop`, así que Hibernate creaba las tablas a
   partir de las entidades. Los triggers, las restricciones `exclude` y los
   `check` de `schema.sql` no actuaban en ninguna prueba de la aplicación; solo
   los comprobaba el workflow de base de datos, por separado.
2. **Un único archivo editado por varias personas.** HU-002, 003, 004, 006 y
   009 cambian el esquema a la vez (agendas, mantenimientos, estados de
   servicio, documento del proveedor). Sobre un solo `schema.sql`, esos cambios
   chocan, y nada registra qué versión tiene aplicada cada base.
3. **El despliegue dependía de un paso manual.** Había que acordarse de aplicar
   el script en Supabase antes de desplegar; si se olvidaba, el `validate` del
   perfil `cloud` tumbaba el arranque.

## Decisión

Adoptar Flyway, integrado en Spring Boot, como la única vía de cambio del
esquema.

1. **Las migraciones son la fuente del modelo físico** y viven en
   `src/main/resources/db/migration/`. `V1__esquema_inicial.sql` es el
   `schema.sql` del Sprint 1, con el mismo DDL y el encabezado al día.
   `V2__rls.sql` es el `rls.sql`, también sin cambios en lo que ejecuta. Una
   migración aplicada no se edita nunca: los cambios van en una versión nueva.
2. **Hibernate solo valida.** `ddl-auto=validate` en todos los perfiles,
   pruebas incluidas, así que las pruebas de integración corren sobre el
   esquema que construye Flyway, con sus triggers y restricciones.
3. **Línea base 0 para las bases anteriores a Flyway.** Supabase y las bases
   locales ya tienen el esquema pero no la tabla de historial. Con
   `baseline-on-migrate` y `baseline-version: 0`, Flyway las marca como
   anteriores a V1 y aplica V1 y V2 encima. Como los dos scripts son
   idempotentes desde el ADR-0005, se saltan lo que ya existe y crean lo que
   falte. Una base vacía se migra desde V1 sin línea base.
4. **RLS también para el historial de Flyway.** Flyway crea
   `flyway_schema_history` en `public`, y en Supabase toda tabla nueva de
   `public` hereda permisos para `anon` y `authenticated`. No puede ir en una
   migración, porque Flyway tiene bloqueada esa tabla mientras migra y el
   `alter table` esperaría para siempre. La cubre el callback
   `afterMigrate__rls_historial_flyway.sql`, que corre al terminar cada
   `migrate`, cuando ya soltó el bloqueo, y es idempotente. Cada
   migración que cree tablas habilita RLS en ellas; `DatabaseMigrationsIT` y
   el workflow de base de datos fallan si alguna tabla de `public` queda sin RLS.
5. **El despliegue migra.** En Render, la aplicación aplica las migraciones
   pendientes al arrancar, antes de que Hibernate valide. Se acabó el paso
   manual en el SQL Editor.

## Alternativas consideradas

1. **Línea base 1 (`baseline-version: 1`):** es la configuración habitual,
   pero da V1 por aplicada sin ejecutarla. Si una base hubiera quedado a medias
   (por ejemplo, sin el trigger de motivo o sin RLS), la diferencia quedaría
   oculta para siempre. Con la línea base 0 y scripts idempotentes, V1 y V2
   completan lo que falte.
2. **Ejecutar las migraciones con el plugin de Maven o la CLI de Flyway, aparte
   de la aplicación:** separa el despliegue del esquema del de la aplicación,
   pero exige credenciales de Supabase en CI y un paso más que coordinar en
   Render. Con una sola instancia y migraciones pequeñas, migrar al arrancar
   es más simple y no deja ventanas en que el código y el esquema discrepen.
3. **Liquibase:** resuelve lo mismo, pero sus *changelogs* en XML o YAML
   ocultan el SQL, y este proyecto depende de SQL específico de PostgreSQL
   (`exclude using gist`, rangos, triggers en PL/pgSQL). Flyway ejecuta SQL
   plano, que es lo que el equipo ya escribe y revisa.

## Consecuencias

- (+) Las pruebas de integración ejercitan las reglas de la base de datos. Un
  caso de uso que viole un trigger o una restricción falla en las pruebas, no
  en producción.
- (+) Cada cambio de esquema es un archivo revisable en su pull request, y cada
  base registra qué versión tiene.
- (+) Desplegar ya no exige pasos manuales en Supabase.
- (−) V1 y V2 tienen que seguir siendo idempotentes, porque se vuelven a
  aplicar sobre las bases anteriores a Flyway. El workflow de base de datos
  los ejecuta dos veces para comprobarlo. Las migraciones siguientes no lo
  necesitan.
- (−) La aplicación migra la base a la que apunta `DATABASE_URL`. Arrancarla
  en local apuntando a Supabase con una rama que traiga migraciones nuevas las
  aplica a la base compartida antes de la revisión. El desarrollo local va
  contra el PostgreSQL de `docker compose`.
- (−) Ya no hay un único archivo con el esquema completo. Para leerlo entero
  están V1, las migraciones siguientes en orden y `modelo-logico.md`.
- (−) Una migración que falle en Render deja la instancia sin arrancar. Por
  eso cada migración se prueba primero en las pruebas de integración y en el
  workflow de base de datos, sobre PostgreSQL 16.

Ver `src/main/resources/db/migration/` (las migraciones),
`docs/database/README.md` (cómo trabajar con ellas),
`CloudProfileContextIT` (la primera migración de Supabase, simulada) y
`DatabaseMigrationsIT` (una base vacía migrada desde V1).
