# Base de datos — guía de la carpeta

Qué hay aquí, en qué orden se ejecuta y cómo levantarlo para trabajar.

## Los archivos

| Archivo | Qué es | ¿Se ejecuta? |
|---|---|---|
| `schema.sql` | Modelo físico. Diecinueve tablas con sus restricciones e índices. Es la fuente del esquema (ADR-0005) | Sí, primero |
| `seed.sql` | Datos de prueba. Tres organizaciones, cinco servicios, ocho reservas | Sí, después del esquema |
| `consultas-clave.sql` | Las catorce preguntas de negocio, ejecutables de corrido | Sí, para ver resultados |
| `pruebas-integridad.sql` | Dieciocho instrucciones que violan una regla cada una y deben ser rechazadas | Sí, para comprobar |
| `rls.sql` | Habilita Row Level Security en las diecinueve tablas | Sí, al final |
| `modelo-logico.md` | Diagrama entidad-relación, decisiones de modelado y normalización | No, se lee |
| `consultas-clave.md` | Las mismas catorce preguntas con su explicación | No, se lee |
| `diagrama-er.drawio` | Diagrama editable. La primera página, **Modelo completo**, tiene las diecinueve entidades, las notas y las historias de usuario en una sola hoja; las tres siguientes son vistas por dominio para leerlo por partes | No |
| `der-0-modelo-completo.png` y `der-1..3-*.png` | Las páginas del `.drawio` exportadas | No |
| `er-identidad.png`, `er-catalogo.png`, `er-reservas.png` | Los diagramas de `modelo-logico.md` exportados, por si Mermaid no carga | No |

Los dos `.sql` de consultas usan metacomandos de `psql` (`\echo`), así que se
ejecutan con `psql -f` y no pegándolos en el editor SQL de Supabase.

## Levantarlo en local

El `docker-compose.yml` de la raíz ya trae un PostgreSQL 16. No hace falta nada
más:

```bash
docker compose up -d db
docker compose exec -T db psql -U postgres -d postgres -v ON_ERROR_STOP=1 < docs/database/schema.sql
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

## Aplicarlo en Supabase

`schema.sql` es idempotente: se salta `clients` y `login_attempts`, que ya
existen porque las crea el mapeo JPA, y crea las diecisiete restantes sin tocar
ninguna columna de las dos primeras. Después hay que ejecutar `rls.sql`, porque
sin él las tablas nuevas quedan expuestas por PostgREST con el anon key, que es
público (ADR-0002).

No ejecutes `seed.sql` ni `pruebas-integridad.sql` contra Supabase.

## Qué verifica la integración continua

El workflow `.github/workflows/database.yml` corre en cada pull request que
toque esta carpeta o el mapeo JPA, y comprueba que el DDL ejecute contra una
base vacía, que sea idempotente, que se creen las diecinueve tablas, que los
datos de prueba carguen, que las catorce consultas devuelvan filas y que las
dieciocho pruebas de integridad sean rechazadas.

Si tocas el esquema y el workflow se pone rojo, lo más probable es que haya que
actualizar `seed.sql` o alguno de los conteos del workflow.

## Qué falta

Está listado al final de `schema.sql` y de `modelo-logico.md`: reglas de negocio
que el esquema no puede garantizar de forma declarativa y siguen viviendo en la
aplicación. Conviene leerlo antes de dar algo por cubierto.
