# Informe de calidad — cobertura de pruebas del Sprint 1

Trabajo de QA sobre la rama `feature/qa-unit-tests`, sacada de `main`. No se modificó lógica de
negocio: todo lo añadido vive bajo `src/test/java`, salvo la configuración de medición de cobertura
en `pom.xml`.

## Resultado

| Métrica | Antes | Después |
|---|---|---|
| Pruebas | 48 | **1067** |
| Instrucciones | 71,5 % | **100 %** (2976/2976) |
| Ramas | 45,5 % | **100 %** (156/156) |
| Líneas | 65,0 % | **100 %** (678/678) |
| Métodos | — | **100 %** (163/163) |
| Clases medidas | 38 | **52** |

La única clase excluida de la medición es `BookingPlatformApplication`: su `main()` solo delega en
`SpringApplication.run` y cubrirlo exigiría levantar el contexto entero sin probar nada propio. Las
ocho clases que no aparecen en el informe son interfaces y `ClientEntity`, que no tienen bytecode
propio que medir.

El `pom.xml` incorpora un umbral del 90 % en instrucciones **y** en ramas que hace fallar el build
por debajo de esa cifra. Se fija en 90 y no en el 100 actual para dejar margen al mantenimiento.
El umbral está comprobado en los dos sentidos: pasa con la suite completa y rompe el build cuando
la cobertura cae.

### Cómo reproducirlo

```bash
export JAVA_HOME=<ruta a un JDK 21 o superior>
./mvnw clean verify
```

El informe queda en `target/site/jacoco/index.html`. El `clean` no es opcional: sin él el fichero de
ejecución acumula corridas anteriores y la cifra sale inflada.

Dos notas de entorno. El proyecto exige **Java 21**: con un JDK 17 el build falla con
`release version 21 not supported`. Y las pruebas de integración necesitan **Docker** para
Testcontainers; se saltan con `-DskipITs`, pero entonces la cobertura que se mide es solo la de las
unitarias.

## Cómo se probó, y por qué así

La cobertura por sí sola no dice nada: se puede recorrer el 100 % de las líneas sin probar una sola
de las decisiones que toma el código. Por eso el criterio no fue tocar líneas sino aplicar técnicas
formales, y la métrica que importa aquí es la de **ramas**, que partía del 45,5 %.

**Valores límite.** Donde hay un umbral, se prueba justo antes, justo en él y justo después. El
bloqueo por intentos fallidos en `max-1`, `max` y `max+1`. La longitud de contraseña en 7, 8, 71, 72
y 73. La mayoría de edad el día antes del cumpleaños, el día mismo y el día después, más un nacido
un 29 de febrero. La ventana deslizante del bloqueo en su borde exacto: un intento que cae en
`now - lockWindow` cuenta, uno un milisegundo antes no, y uno con fecha posterior a `now` se
descarta.

**Tabla de decisión.** `GoTrueClient.mapError` traduce las respuestas del proveedor de identidad al
vocabulario de errores del dominio, y cada traducción equivocada se convierte en un código HTTP
equivocado. Se probó como lo que es: 30 filas sobre (contexto, estado HTTP, cuerpo), más 6 filas
cuyo único fin es fijar **en qué orden** se disparan las reglas. Ahí apareció el defecto D2.

**Transición de estados.** `Client` es una máquina de estados y se probó su tabla completa 3×3: los
tres estados contra las tres operaciones, las nueve celdas, incluidas las cuatro que deben rechazar
y las dos que son idempotentes. En las que rechazan se verifica además que el estado no se mutó.

**Verificación de interacciones.** Varias reglas de negocio no son sobre lo que pasa, sino sobre lo
que **no** debe pasar: una cuenta bloqueada nunca llega al proveedor de identidad, una contraseña
débil tampoco, y un correo desconocido en el reenvío ni llama fuera ni lanza, que es justo lo que
mantiene cerrada la enumeración de usuarios.

**Propiedades de seguridad como pruebas.** Tres reglas que nadie había escrito quedan ahora fijadas:
la clave secreta solo viaja a `/admin/**`; el rol solo se lee de `app_metadata` y un `role` puesto en
`user_metadata`, que el cliente sí puede editar, se ignora; y el manejador de errores no filtra el
mensaje interno de la excepción, comprobado pasándole una cadena de conexión con contraseña dentro.

## Defectos encontrados

No se corrigió ninguno: corregirlos es de otro rol. Las pruebas fijan el comportamiento **actual**
para que el arreglo sea visible cuando se haga.

### D1 — Un fallo del proveedor en recuperación de contraseña sale como 500

`PasswordRecoveryUseCase.java:33` y `PasswordResetUseCase.java:39` relanzan la
`UpstreamAuthException` cruda. `GlobalExceptionHandler` no declara ningún manejador para esa
excepción, así que cae en el catch-all y se responde **500 INTERNAL_ERROR**.

Esperado: `RATE_LIMITED` a 429 y `UNAVAILABLE` a 502, que es lo que sí hacen `LoginUseCase`,
`LogoutUseCase` y `UserProvisioningService`.

Hay una segunda consecuencia, peor que el código de estado. El Javadoc de la clase promete que la
recuperación responde siempre igual para no delatar si un correo existe. Con el proveedor limitando
por volumen, la respuesta pasa de 202 a 500 y **la anti-enumeración se rompe**.

### D2 — Un enlace de verificación caducado dice «usuario no encontrado»

En `GoTrueClient.java:245`, la regla `body.contains("not found") || status == 404` se evalúa **antes**
que la de `expired` y antes del `switch (context)`. GoTrue responde 404 a un OTP caducado o ya
consumido, así que quien pincha un enlace vencido recibe `USER_NOT_FOUND` en vez de `TOKEN_EXPIRED`.

Arreglo: subir la comprobación de `expired` y el `switch` por encima de la regla del 404, o acotar
`status == 404` a los contextos administrativos.

### D3 — Una respuesta inesperada del proveedor se convierte en 500

Dos casos en `GoTrueClient`, los dos por la misma causa: el `try` solo captura
`RestClientResponseException` y `ResourceAccessException`, de modo que cualquier otra excepción
escapa sin mapear.

- `GoTrueClient.java:97` — `Long.parseLong` sobre `expires_in`. Un valor no numérico, o decimal como
  `3600.0` que es JSON perfectamente válido, lanza `NumberFormatException` cruda.
- `GoTrueClient.java:59` y `:120` — `UUID.fromString` sobre el `id` devuelto. Un identificador
  malformado lanza `IllegalArgumentException` cruda.

### D4 — Un correo ausente se convierte en la cadena literal `"null"`

`GoTrueClient.java:121` hace `String.valueOf(user.get("email"))` sin pasar por `requireField`, al
contrario que el `id` de la línea 120. Si la respuesta no trae correo, `ConfirmedUser.email()` vale
`"null"`, cuatro caracteres, y eso viaja como si fuera una dirección. No es una excepción: es
corrupción silenciosa.

### D5 — El rol del JWT se normaliza sin `Locale`

`SupabaseJwtAuthConverter.java:32` hace `roleValue.toUpperCase()` sin `Locale`. Bajo locale turco,
`admin` se convierte en `ADMİN` con i sin punto, con lo que `ROLE_ADMİN` no coincide con `ROLE_ADMIN`
y la autorización falla en silencio.

No es un descuido aislado: las otras once normalizaciones del proyecto sí usan `Locale.ROOT`. El
mismo patrón aparece en `GlobalExceptionHandler.java:86` y `SecurityConfig.java:106`, donde un
`toLowerCase()` sin locale altera el URI del tipo de error.

### D6 — El dominio depende de Spring, y ArchUnit no lo ve

`Client.java` importa `ErrorCode`, y `ErrorCode.java:3` importa `org.springframework.http.HttpStatus`.
El dominio queda acoplado al framework, contra lo que fija el ADR-0001.

La regla `DOMAIN_IS_FREE_OF_FRAMEWORKS` no lo detecta porque `dependOnClassesThat()` solo inspecciona
dependencias **directas**. La violación existe y el build sigue en verde, que es el peor de los casos:
una regla que da confianza sin darla.

### D7 — Registro concurrente: 500 en vez de 409, y sin compensación

`RegisterClientUseCase.java:46-75` comprueba y luego actúa: `existsByEmail` / `existsByDocument` y
después `save`. Dos peticiones simultáneas con el mismo correo pasan las dos comprobaciones.

La `DataIntegrityViolationException` que sale de la restricción única no está mapeada, así que se
responde **500** en lugar de 409. Y hay un segundo efecto: como el identificador del cliente viene
asignado, el INSERT se ejecuta al confirmar la transacción, **después** de que el método retorne, de
modo que el `catch` de la línea 72 no se dispara y **la compensación no ocurre**: queda un usuario
huérfano en el proveedor de identidad.

Este último punto es el único de la lista que **no está verificado con una prueba**: requiere una de
integración contra la restricción real. Queda como la primera tarea pendiente.

### Hallazgos menores

| Dónde | Qué |
|---|---|
| `RegisterClientUseCase.java:45` | El correo se pasa a minúsculas pero no se hace `trim()`, al contrario que nombre, documento, teléfono y ciudad |
| `RegisterClientUseCase.java:72-75` | Si la compensación falla, su excepción sustituye a la original y se pierde el motivo real |
| `RegisterClientUseCase.java:69` | Llamada HTTP al proveedor **dentro** de `@Transactional`: retiene conexión del pool, y el correo sale aunque la transacción revierta |
| `LoginRequest.java:11` | `password` sin `@Size`; `RegisterClientRequest` y `PasswordResetRequest` sí topan en 72 |
| `RegisterClientRequest.java:22` | El patrón del documento acepta una cadena formada solo por guiones |
| `AuthController.java:90` | `UUID.fromString(jwt.getSubject())` sin guarda: un `sub` malformado da 500 |
| `GlobalExceptionHandler.java:47` | Dos violaciones sobre el mismo campo: la segunda sobrescribe a la primera y el cliente solo ve una |
| `GlobalExceptionHandler.java:87` | `URI.create(request.getRequestURI())` sin codificar: un carácter ilegal rompe el propio manejador |
| `GlobalExceptionHandler.java:62` vs `:36` | `handleValidation` publica `details` aunque esté vacío; `handleBusiness` lo omite |
| `LoginUseCase.java:51`, `GoTrueClient.java:234` | Correo del usuario y cuerpo completo de la respuesta del proveedor en logs de nivel `WARN` |
| `SupabaseProperties.java:6` | `secret-key` sin validación y con valor por defecto vacío: la aplicación arranca sin credencial y falla en caliente |
| `AuthPolicyProperties.java:6` | `lockWindowMinutes` sin cota: un valor negativo desplaza la ventana al futuro y **nadie se bloquea nunca** |
| `UpstreamAuthException.java:3`, `BusinessException.java:7,10` | Clases serializables sin `serialVersionUID`; `details` no transitorio con tipo no serializable (`javac -Xlint:all`) |

### Comprobado y correcto

Tres cosas que se sospechaban y **no** son defectos, verificadas expresamente:

- **No hay escalada de privilegios por `user_metadata`.** El conversor solo lee `app_metadata`, y si
  el rol aparece en ambos gana `app_metadata`. Comprobado en unitario y de extremo a extremo contra
  la cadena de filtros real.
- **El filtro de trazas no fuga contexto entre peticiones.** El `finally` limpia el MDC también
  cuando la cadena lanza, y la cabecera `X-Trace-Id` se devuelve incluso en respuestas fallidas.
- **El manejador de errores no filtra información interna.** Se le pasó una excepción cuyo mensaje
  contenía una cadena de conexión con contraseña y la respuesta no lleva ni el mensaje, ni la clase
  de la excepción, ni la traza.

## Criterios de aceptación que siguen sin verificar

| Criterio | Por qué no se pudo cerrar |
|---|---|
| Validación real del JWT: emisor, expiración, JWKS | Las pruebas usan el soporte de Spring Security, que salta el decoder. Un JWKS mal configurado solo se vería en producción |
| «Enlace de un solo uso» | Se prueba el token caducado, no el reúso del mismo token, que es la propiedad que da nombre al criterio |
| El cierre de sesión invalida la sesión | Se verifica que se invoca `signOut`, no que un token posterior sea rechazado |
| Un cliente no verificado no confirma reservas | La invariante existe en `Client.canConfirmBooking()` y está probada, pero **ningún código de producción la consulta**: no hay flujo de reservas todavía |
| MFA obligatorio para administradores | No implementado, diferido en el ADR-0003 |
| Correo o documento duplicado en concurrencia | Ver D7 |

HU-002, HU-003 y HU-004 no tienen implementación en esta rama: 16 criterios de aceptación sin código
y, por tanto, sin prueba posible. Se registran como deuda visible, no como fallo del Sprint 1.

## Mejoras de diseño propuestas

Huecos que hoy dificultan probar. Cada uno está redactado como card, con su criterio de aceptación.

**QA-01 — El dominio incumple ADR-0001 y la regla que debería impedirlo no lo ve.**
Ver D6. *Criterio:* el dominio de `identity` y `auth` no depende de Spring ni directa ni
transitivamente, y existe una regla ArchUnit que falla si alguien reintroduce el acoplamiento.
*Esfuerzo:* M.

**QA-02 — Las políticas de negocio son `static` y no se pueden sustituir.**
`PasswordPolicy.violations()` y `AgePolicy.isAdult()` son estáticos en clases `final`, invocados
desde tres casos de uso. No se puede probar `RegisterClientUseCase` con una política de edad falsa:
toda prueba arrastra la regla real. *Criterio:* ambas se inyectan como colaboradores y existe una
prueba que sustituye `AgePolicy` por un doble. *Esfuerzo:* M.

**QA-03 — Los controladores dependen de clases concretas, no de interfaces.**
`AuthController` y `RegistrationController` importan las implementaciones de los casos de uso,
mientras que `auth` sí publica `UserProvisioning` como interfaz. La asimetría no tiene motivo.
*Criterio:* cada caso de uso consumido por un controlador se expone tras una interfaz, y una regla
ArchUnit lo obliga. *Esfuerzo:* M.

**QA-04 — La clasificación de errores del proveedor es privada e inalcanzable.**
`GoTrueClient.mapError` concentra doce decisiones tras un método privado; solo se llega por HTTP
simulado, que es la razón de que la clase estuviera al 13 % de ramas. *Criterio:* la traducción
(estado, cuerpo) a `UpstreamAuthError` se extrae a un componente probable directamente.
*Esfuerzo:* M.

**QA-05 — `Instant.now()` y `UUID.randomUUID()` fuera del `Clock` inyectado.**
`GlobalExceptionHandler.java:90`, `SecurityConfig.java:110` y `TraceIdFilter.java:28`, pese a existir
`ClockConfig`. El `timestamp` y el `traceId` de una respuesta de error no son aseverables.
*Criterio:* las tres reciben `Clock` por constructor y una prueba con `Clock.fixed` asevera el
`timestamp` exacto. *Esfuerzo:* S.

**QA-06 — ArchUnit no cubre el sentido `api → infrastructure` ni los ciclos.**
Solo existe la regla inversa. Nada impide que un controlador importe un `*Adapter` o un `*Entity`.
*Criterio:* se añaden `API_DOES_NOT_DEPEND_ON_INFRASTRUCTURE`, una `layeredArchitecture()` completa
y `slices().should().beFreeOfCycles()`. *Esfuerzo:* S.

**QA-07 — Las reglas entre módulos están cableadas a `identity` y `auth`.**
Cuando entren `catalog` y `booking`, previstos en `componentes.md`, no habrá ninguna regla que los
cubra. *Criterio:* las reglas se reescriben de forma genérica para que cualquier módulo nuevo quede
protegido sin editar el test. *Esfuerzo:* M.

**QA-08 — El catch-all del manejador de errores puede estar tapando los códigos de Spring MVC.**
`GlobalExceptionHandler` declara `@ExceptionHandler(Exception.class)` con `@Order(HIGHEST_PRECEDENCE)`.
Queda la sospecha de que un método no permitido, un tipo de medio no soportado o una ruta inexistente
se respondan como 500 en vez de 405, 415 y 404. *Criterio:* una prueba de integración que confirme
o descarte cada uno de los tres casos. *Esfuerzo:* S.
