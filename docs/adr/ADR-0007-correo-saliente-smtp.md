# ADR-0007: El correo propio de la plataforma sale por SMTP con Spring Mail

- **Estado:** Aceptado
- **Fecha:** 2026-10-25
- **Prioridad:** Media
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software

## Contexto

Hasta el Sprint 1 todos los correos los enviaba Supabase Auth (GoTrue): la
verificación de la cuenta y la recuperación de la contraseña. GoTrue solo envía
esos correos de autenticación; no sirve para mensajes de la plataforma.

HU-002 pide el primero de esos mensajes: "cuando un administrador aprueba mi
proveedor, recibo una notificación por correo". HU-015 pedirá después
recordatorios y confirmaciones de reservas, así que la decisión no es solo de
este sprint.

## Decisión

1. **Un módulo `notification`** con la estructura de los demás. Los otros
   módulos envían a través de su fachada `EmailNotifications` y nunca tocan el
   transporte. El puerto `EmailSender` aísla el proveedor de correo.
2. **SMTP con Spring Mail.** Sirve cualquier servidor SMTP; para el MVP, Gmail
   con una contraseña de aplicación, que es gratis y llega a cualquier
   destinatario sin verificar un dominio. Se configura con las variables
   `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`,
   `SPRING_MAIL_PASSWORD` y, opcionalmente, `APP_MAIL_FROM`. STARTTLS y
   autenticación están activados en `application.yml`, con tiempos de espera
   de 5 s.
3. **Sin SMTP, el correo va al log.** Sin `SPRING_MAIL_HOST`, Spring Boot no
   crea el `JavaMailSender` y `MailConfig` instala `LoggingEmailSender`, que
   registra destinatario y asunto, pero no el cuerpo. Así el desarrollo local,
   las pruebas y un despliegue sin configurar funcionan igual, sin enviar nada.
4. **El correo se envía después del commit.** Un caso de uso publica un evento
   y un `@TransactionalEventListener(AFTER_COMMIT)` envía el correo. Si el
   envío falla, se registra en el log y la operación de negocio queda en pie.
   Esto también evita una llamada SMTP dentro de una transacción, que retiene
   una conexión del pool (el mismo hallazgo de QA sobre el registro de
   clientes).

## Alternativas consideradas

1. **Resend u otro servicio con API HTTP:** API cómoda y plan gratuito, pero
   para escribir a cualquier destinatario exige verificar un dominio propio,
   que el proyecto no tiene. Cambiar a Resend más adelante es otro adaptador de
   `EmailSender`.
2. **Las plantillas de correo de Supabase:** solo cubren los eventos de
   autenticación; no hay forma de enviar un mensaje arbitrario.
3. **Diferir el correo y solo registrarlo en el log:** dejaba sin cumplir un
   escenario de HU-002 y aplazaba una decisión que HU-015 necesita de todas
   formas.

## Consecuencias

- (+) HU-002 cumple su escenario de notificación, y HU-015 tiene el canal listo.
- (+) Sin credenciales SMTP la aplicación arranca y funciona igual; nada
  depende del correo para completar una operación.
- (−) Gmail limita los envíos diarios de una cuenta personal (del orden de
  cientos). Basta para el MVP; con volumen real hay que pasar a un proveedor
  transaccional.
- (−) El envío es síncrono, después del commit y en el hilo de la petición: un
  SMTP lento retrasa la respuesta, aunque como mucho los 5 s del tiempo de
  espera. Si HU-015 lo hace frecuente, se pasa a envío asíncrono o con cola.
- (−) Un correo que falla no se reintenta; queda registrado en el log.

Ver `notification/` (el módulo), `ProviderApprovalMailer` (el primer uso) y
`docs/operations/runbook.md` (cómo configurar las variables).
