# Base de datos — guía de la carpeta

Qué hay aquí, en qué orden se ejecuta y cómo levantarlo para trabajar.

## Dónde está el esquema

El modelo físico son las migraciones de Flyway en
`src/main/resources/db/migration/` (ADR-0006). La aplicación las aplica al
arrancar, en orden de versión:

| Migración | Qué hace |
|---|---|
| `V1__esquema_inicial.sql` | Las diecinueve tablas del Sprint 1 con sus restricciones, triggers e índices. Es el antiguo `schema.sql` (ADR-0005) |
| `V2__rls.sql` | Habilita Row Level Security en las diecinueve tablas. Es el antiguo `rls.sql` |
| `V3__correcciones_reservas_y_servicios.sql` | Un servicio nuevo nace inactivo; las reglas de reservas ya no impiden cancelar ni cerrar las de un proveedor suspendido o un servicio desactivado; el aforo grupal resiste reservas simultáneas |
| `V4__identificacion_del_proveedor.sql` | El proveedor se identifica con tipo (NIT, CC, CE, PASAPORTE) y número; `nit` pasa a `identification_number` y la unicidad es por la pareja |
| `V5__datos_de_referencia.sql` | Categorías, ciudades y la moneda COP. Son datos de referencia que la aplicación necesita en toda base, no datos de prueba |
| `afterMigrate__rls_historial_flyway.sql` | Callback, no migración: corre al final de cada `migrate` y habilita RLS en `flyway_schema_history`, que Flyway tiene bloqueada mientras migra |

Una migración aplicada no se modifica nunca: Flyway guarda su checksum y no
arranca si cambia. Un cambio de esquema es una migración nueva, con la versión
siguiente. Si crea tablas, habilita RLS en ellas en la misma migración;
`DatabaseMigrationsIT` y el workflow de base de datos fallan si alguna tabla de
`public` queda sin RLS.

## Los archivos de esta carpeta

| Archivo | Qué es | ¿Se ejecuta? |
|---|---|---|
| `seed.sql` | Datos de prueba. Tres organizaciones, cinco servicios, ocho reservas | Sí, después de las migraciones |
| `consultas-clave.sql` | Las catorce preguntas de negocio, ejecutables de corrido | Sí, para ver resultados |
| `pruebas-integridad.sql` | Diecinueve instrucciones que violan una regla cada una y deben ser rechazadas | Sí, para comprobar |
| `modelo-logico.md` | Diagrama entidad-relación, decisiones de modelado y normalización | No, se lee |
| `consultas-clave.md` | Las mismas catorce preguntas con su explicación | No, se lee |
| `diagrama-er.drawio` | Diagrama editable. La primera página, **Modelo completo**, tiene las diecinueve entidades, las notas y las historias de usuario en una sola hoja; las cuatro siguientes son las tres vistas por dominio y la hoja de historias de usuario | No |
| `der-0-modelo-completo.png` y `der-1..4-*.png` | Las cinco páginas del `.drawio` exportadas | No |
| `er-identidad.png`, `er-catalogo.png`, `er-reservas.png` | Los diagramas de `modelo-logico.md` exportados, por si Mermaid no carga | No |

Los dos `.sql` de consultas usan metacomandos de `psql` (`\echo`), así que se
ejecutan con `psql -f` y no pegándolos en el editor SQL de Supabase.

## Levantarlo en local

El `docker-compose.yml` de la raíz ya trae un PostgreSQL 16. Arranca la
aplicación contra él (`DATABASE_URL=jdbc:postgresql://localhost:5432/postgres`)
y Flyway crea el esquema. Si solo quieres el esquema y los datos, sin la
aplicación, aplica las migraciones a mano en orden de versión:

```bash
docker compose up -d db
for f in $(ls src/main/resources/db/migration/V*__*.sql | sort -V); do
  docker compose exec -T db psql -U postgres -d postgres -v ON_ERROR_STOP=1 < "$f"
done
docker compose exec -T db psql -U postgres -d postgres -v ON_ERROR_STOP=1 < docs/database/seed.sql
docker compose exec -T db psql -U postgres -d postgres                     < docs/database/consultas-clave.sql
```

Y para mirar los datos:

```bash
docker compose exec db psql -U postgres -d postgres
```

Cuando termines: `docker compose down -v` borra también el volumen.

**`seed.sql` empieza con un `truncate`.** Tiene una guarda que aborta si la base
no parece local, pero aun así no lo ejecutes contra la base compartida.

Si aplicaste las migraciones a mano y después arrancas la aplicación contra
esa misma base, no pasa nada: Flyway la marca con la línea base 0 y vuelve a
aplicar V1 y V2, que son idempotentes.

## Supabase

Ya no se ejecuta nada a mano en el SQL Editor. En cada despliegue, la
aplicación aplica a Supabase las migraciones pendientes. La primera vez que
arranque con Flyway, la base ya tiene el esquema del Sprint 1 pero no el
historial: Flyway la marca con la línea base 0 y vuelve a aplicar V1 y V2, que
se saltan lo que ya existe (lo comprueba `CloudProfileContextIT`).

Por lo mismo, no arranques la aplicación desde tu máquina apuntando a Supabase
con una rama que traiga migraciones nuevas: se aplicarían a la base compartida
antes de la revisión.

No ejecutes `seed.sql` ni `pruebas-integridad.sql` contra Supabase.

## Qué verifica la integración continua

El workflow `.github/workflows/database.yml` corre en cada pull request que
toque esta carpeta, las migraciones o el mapeo JPA. Comprueba que:

- las migraciones ejecuten contra una base vacía;
- V1 y V2 sigan siendo idempotentes;
- se creen las diecinueve tablas;
- los datos de prueba carguen;
- las catorce consultas devuelvan filas;
- las diecinueve pruebas de integridad sean rechazadas;
- ninguna tabla quede sin RLS.

Las pruebas de integración del build corren sobre el esquema que crea Flyway,
así que los triggers y las restricciones también actúan en ellas.

Si tocas el esquema y el workflow se pone rojo, lo más probable es que haya que
actualizar `seed.sql` o alguno de los conteos del workflow.

## Qué falta

Está listado al final de `V1__esquema_inicial.sql` y de `modelo-logico.md`: reglas de negocio
que el esquema no puede garantizar de forma declarativa y siguen viviendo en la
aplicación. Conviene leerlo antes de dar algo por cubierto.
