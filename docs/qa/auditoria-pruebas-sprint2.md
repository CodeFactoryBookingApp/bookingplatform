# Auditoría de calidad de las pruebas — Sprint 2

En el Sprint 1 la suite llegó a 100 % de cobertura. En el Sprint 2 se revisó prueba por prueba
con una pregunta distinta: no si ejecuta el código, sino si **verifica la solución**. Una prueba
que solo existe para subir el porcentaje no protege nada (ley de Goodhart), y una prueba verde que
asevera un comportamiento defectuoso es peor que no tenerla, porque lo presenta como correcto.

El inventario completo, con el veredicto y el motivo de cada prueba, está en
[`auditoria-pruebas-sprint2.csv`](auditoria-pruebas-sprint2.csv).

## Criterio

Cada método de prueba, y cada fila de las pruebas parametrizadas, recibió uno de tres veredictos.

**Mantener** cuando verifica una regla de negocio, un criterio de aceptación, un contrato HTTP, una
decisión de seguridad o una ruta de error real, con aserciones que fallarían si el comportamiento
cambia.

**Reforzar** cuando verifica algo valioso pero su aserción era más débil que lo que promete su
nombre. Por ejemplo, la prueba de endpoints públicos aceptaba cualquier estado distinto de 401
(también un 500); ahora exige exactamente el 400 de la validación.

**Quitar** cuando cae en alguna de estas reglas:

| Regla | Qué detecta | Quitadas |
|---|---|---:|
| 1 | Prueba el lenguaje o el framework: getters, enums, records, constructores por reflexión, `assertNotNull(getBean(...))` | 88 |
| 2 | Solo cubre configuración declarativa o código sin lógica | 25 |
| 3 | Tautología o aserción que no puede fallar | 31 |
| 4 | Duplicada: otra prueba verifica lo mismo con igual o mayor rigor | 214 |
| 5 | Fija como correcto un defecto ya reportado en Mantis | 48 |
| 6 | El mock devuelve exactamente lo que la aserción comprueba, sin lógica de por medio | 16 |

En total: **394 se mantienen, 34 se refuerzan y 422 se quitan**, sobre 850 casos evaluados.

### Sobre la regla 5

Las pruebas que fijaban defectos se escribieron en el Sprint 1 a propósito, para que fallaran el
día que alguien corrigiera el bug. En la práctica pasaban en verde aseverando, por ejemplo, que un
método no permitido responde 500, o que el token sigue sirviendo después del logout. Leídas por
alguien nuevo, dicen que eso es lo esperado. Esos defectos viven ahora donde corresponde, en Mantis
(BUG-001 a BUG-039), y la prueba correcta se escribirá cuando desarrollo los corrija.

## Resultado

| | Antes de la auditoría | Después |
|---|---:|---:|
| Pruebas unitarias | 1050 | 619 |
| Pruebas de integración | 112 | 60 |
| Cobertura de instrucciones | 100 % | 99,7 % |
| Cobertura de ramas | 100 % | 97,6 % |
| Mutación (mutantes detectados) | 98,4 % (1016 / 1033) | 95 % (980 / 1033) |

Medido el 20 de octubre de 2026 con `mvnw clean verify` y PIT.

La suite se redujo casi a la mitad y la cobertura apenas se movió: lo que se quitó era redundante.
La mutación es la medida que importa, porque dice cuántos cambios en el código detectan las
pruebas. Bajó 36 mutantes, y se revisó cada uno comparando contra la versión anterior:

- **Fijaban defectos** (mensajes internos del proveedor que llegan al cliente, BUG-028; el 429 que
  delata qué correos existen, BUG-017; el título del 502, BUG-027). Se pierden a propósito.
- **Artefactos de PIT**: adaptadores de base de datos que solo se ejercitan en las pruebas de
  integración (PIT no las ejecuta) y constructores de controladores que Spring construye una sola
  vez por contexto. Siguen probados.
- **Pérdidas reales**, tres, que se restauraron: que el registro devuelva el identificador que
  emite el proveedor, que la confirmación devuelva el usuario confirmado, y la prueba de punta a
  punta de escalada de privilegios (`app_metadata` gana a un rol plantado en `user_metadata`).

Lo que queda sin cubrir es un `if` redundante en `UserProvisioningService` (repite lo que hace el
`switch` siguiente), una rama del manejador de errores y ramas nulas del mapper que genera MapStruct.

## Umbrales del build

| Umbral | Antes | Ahora | Por qué |
|---|---:|---:|---|
| Cobertura (instrucciones y ramas) | 90 % | 65 % | Es la meta de la rúbrica. La cifra real es más alta; el umbral es el piso, no un objetivo a perseguir con relleno. |
| Mutación | 95 % | 90 % | Los supervivientes están analizados; subirlo obligaría a escribir pruebas que aseveran bugs. |

## Cómo reproducirlo

```
./mvnw clean verify
./mvnw test-compile org.pitest:pitest-maven:mutationCoverage
```

Informes en `target/site/jacoco/index.html` y `target/pit-reports/index.html`.
