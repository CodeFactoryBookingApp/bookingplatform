# Auditoría y pruebas del Sprint 3

El Sprint 3 llegó a main entre el 21 y el 23 de noviembre (PR #7, #8 y #9, y los commits de HU-014,
HU-016 y HU-017 empujados directo): HU-007, 008, 010, 011, 012, 013, 014, 015, 016, 017, 018, 019,
020, 022 y 023, con dos módulos nuevos (`booking` y `audit`). Se aplicó el mismo criterio de los
sprints anteriores: pruebas que verifiquen la solución, no relleno para subir un porcentaje.

## La cobertura lo escondía otra vez

| | Al integrar el Sprint 3 (a0e9536) | Después del trabajo de calidad (1ffac8b) |
|---|---:|---:|
| Pruebas unitarias / de integración | 1019 / 211 | 1445 / 211 |
| Cobertura de líneas / ramas (JaCoCo) | 97,4 % / 87,0 % | 98,7 % / 95,1 % |
| Mutación total (PIT) | 40,0 % | 64,7 % |
| Mutación en dominio / aplicación | 79,9 % / 37,6 % | 98,4 % / 96,7 % |
| Mutación en booking / audit / notification | 5,9 % / 0 % / 16,1 % | 56,8 % / 51,9 % / 50,2 % |
| Fuerza de las pruebas (detectados entre los alcanzados) | 92,3 % | 96,6 % |

Medido el 24 de noviembre de 2026 con `./mvnw clean verify` y PIT.

Con la cobertura en 97 %, la mutación había caído de 69 % (27 de octubre) a 40 %: la lógica nueva solo
la recorrían las pruebas de integración con PostgreSQL. Se escribieron 413 pruebas unitarias para las
reglas que nadie verificaba: límites de reprogramación y de la ventana de cancelación, estados de la
asistencia, el último administrador, la invalidación de sesiones por cambio de rol, el horario nocturno
de los recordatorios, el enmascarado del CSV de auditoría, la ocupación y la disponibilidad por zona
horaria, entre otras. Ninguna acepta un comportamiento defectuoso: lo que resultó estar mal se reportó en
Mantis (el CSV de demanda con negativos, BUG-068, y las franjas en zonas con horario de verano, BUG-069).

Lo que sigue sin detectar está en adaptadores SQL y controladores que solo delegan (infraestructura
23 %, api 50 %): los verifican los IT, que PIT no ejecuta. No se escribieron unitarias de adaptadores.

## Auditoría de las pruebas nuevas

| Lote | Casos | Mantener | Reforzar | Quitar |
|---|---:|---:|---:|---:|
| booking | 45 | 45 | 0 | 0 |
| auth, audit, notification | 30 | 28 | 0 | 2 (duplicadas) |
| scheduling, organization | 128 | 123 | 5 | 0 |
| **Total** | **203** | **196** | **5** | **2** |

Inventarios con el veredicto y el motivo de cada prueba: `auditoria-pruebas-sprint3-*.csv`.

## Automatización (bonificación)

- `qa/e2e`: 12 escenarios Gherkin (español) con Playwright + playwright-bdd y Page Object Model, en
  Chromium, Firefox y Edge. 10 pasan; E2E-07 y E2E-08 fallan por BUG-070 y BUG-071 (el front no expone
  reprogramar, ni el historial ni el comprobante PDF) y quedan `@fixme` hasta que se corrijan.
- `qa/api`: 40 casos Postman/Newman (402 aserciones en 3 iteraciones), todos en verde.

## Umbrales del build

Sin cambios: cobertura ≥ 65 % (rúbrica), fuerza de las pruebas en PIT ≥ 90 %, mutación total ≥ 65 % como
piso. La mutación total quedó justo en el piso (64,7 %, que PIT redondea a 65 %) porque el Sprint 3 añadió
muchos adaptadores SQL que solo verifican los IT.

## Cómo reproducirlo

```
./mvnw clean verify
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```
