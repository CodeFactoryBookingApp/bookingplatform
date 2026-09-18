# ADR-0001: Monolito modular con arquitectura limpia por módulo

- **Estado:** Aceptado
- **Fecha:** 2026-09-18
- **Prioridad:** Alta (decisión estructural del sistema)
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

La plataforma de reservas inicia con alcance de Sprint 1 reducido (HU-001 y
HU-021 mínima), equipo pequeño y un semestre de evolución por delante
(catálogo, reservas, reportes). El caso exige demostrar madurez arquitectónica,
organización "en dominios o módulos de negocio" y al menos un módulo transversal.

## Decisión

Adoptar un **monolito modular**: un único artefacto desplegable organizado en
módulos por dominio (`identity`, futuros `catalog`, `booking`) más un módulo
transversal (`auth`) y un kernel `shared`. Cada módulo sigue **arquitectura
limpia** en 4 paquetes:

- `domain`: modelo puro (POJOs), invariantes, políticas y *ports* (interfaces).
- `application`: casos de uso que orquestan el dominio.
- `infrastructure`: adaptadores (JPA/MapStruct, Supabase GoTrue, correo).
- `api`: adaptadores de entrada REST (controllers + DTOs).

Regla de dependencia hacia adentro (`api → application → domain ← infrastructure`)
y comunicación entre módulos exclusivamente vía fachadas `application` o tipos
`domain` públicos. Las reglas se hacen cumplir con **ArchUnit en el build**
(`ArchitectureTest`), no solo con convenciones.

## Alternativas consideradas

1. **Monolito tradicional por capas (controller/service/repository):** más
   rápido, pero el dominio queda acoplado a JPA y los límites entre dominios se
   erosionan; no demuestra la madurez arquitectónica que evalúa el caso.
2. **Microservicios desde el inicio:** complejidad operativa (red, despliegues,
   datos distribuidos) injustificada para el volumen y el equipo actual.

## Consecuencias

- (+) Límites explícitos y verificados automáticamente; el dominio es testeable
  sin Spring ni base de datos.
- (+) Un solo despliegue y una sola BD: operación simple en el free tier.
- (+) Migración futura a servicios independente por módulo si el negocio lo exige
  (los ports ya son fronteras naturales).
- (−) Más clases por agregado (modelo de dominio + entidad JPA + mapper); se
  acepta como costo de la separación explícita (ADR-0002).
- (−) La disciplina de no saltarse las capas depende de ArchUnit y peer review.
