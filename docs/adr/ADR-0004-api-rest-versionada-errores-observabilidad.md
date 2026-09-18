# ADR-0004: API REST versionada por path con errores ProblemDetail y observabilidad por traceId

- **Estado:** Aceptado
- **Fecha:** 2026-09-18
- **Prioridad:** Media-Alta
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software y BD

## Contexto

El caso exige: contratos REST bien definidos, versionado explícito, respuestas
uniformes de error con códigos HTTP correctos y estructura estándar
(`errorCode`, `message`, `details`, `traceId`), validación robusta de payloads y
logs estructurados correlacionados por `traceId`.

## Decisión

1. **Versionado por path:** prefijo `/api/v1`. Romper contratos → `/api/v2`;
   cambios aditivos no cambian la versión.
2. **Contrato documentado:** OpenAPI 3 con springdoc 3.x (Swagger UI en
   `/swagger-ui.html`), esquema de seguridad Bearer JWT.
3. **Errores uniformes:** RFC 9457 `application/problem+json` desde un único
   `GlobalExceptionHandler`, con propiedades extendidas:

   ```json
   {
     "type": "https://bookingplatform.codefactory.com/errors/duplicate_email",
     "title": "Conflict",
     "status": 409,
     "detail": "Email is already registered",
     "instance": "/api/v1/registrations",
     "errorCode": "DUPLICATE_EMAIL",
     "traceId": "6f1c...-...",
     "timestamp": "2026-09-18T12:00:00Z",
     "details": { "campo": "motivo" }
   }
   ```

   Catálogo de `errorCode` centralizado en el enum compartido `ErrorCode`; cada
   error de negocio lo lanza como `BusinessException`.
4. **Validación:** Jakarta Bean Validation en los DTO `record` de entrada
   (`api/dto`) + reglas de negocio en dominio/casos de uso (edad, unicidad,
   política de contraseña). Los errores de validación devuelven 400 con
   `details` campo→mensaje.
5. **Observabilidad:** filtro `TraceIdFilter` acepta o genera `X-Trace-Id`, lo
   expone en la respuesta y lo pone en MDC; el patrón de log local lo incluye y
   el perfil `cloud` emite **logs estructurados JSON (ECS)** con el mismo campo.
   Actuator expone `health`/`info`.

## Alternativas consideradas

- **Versionado por header o query param:** más flexible pero menos visible y
  más difícil de documentar en Swagger para un equipo en formación.
- **Formato de error ad-hoc (JSON propio):** incumpliría el estándar; RFC 9457
  está soportado nativamente por Spring (`ProblemDetail`).

## Consecuencias

- (+) Contrato estable, documentado y consistente; el traceId permite correlar
  un error del cliente con los logs del servidor.
- (+) Un solo punto (handler global) define la forma de todos los errores.
- (−) El versionado por path exige disciplina para no romper v1.
- (−) Los logs JSON solo se activan en el perfil cloud (local se prioriza
  legibilidad).
