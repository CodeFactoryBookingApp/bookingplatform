# ADR-0007: El correo de la plataforma llega a una bandeja dentro de la aplicación

- **Estado:** Aceptado
- **Fecha:** 2026-10-25
- **Prioridad:** Media
- **Decisores:** Equipo de desarrollo — rol Arquitecto de Software, con el Product Owner

## Contexto

Hasta el Sprint 1 todos los correos los enviaba Supabase Auth (GoTrue): la
verificación de la cuenta y la recuperación de la contraseña. GoTrue solo envía
esos correos de autenticación; no sirve para mensajes de la plataforma.

HU-002 pide el primero de esos mensajes: "cuando un administrador aprueba mi
proveedor, recibo una notificación por correo". HU-015 pedirá después
recordatorios y confirmaciones de reservas.

La primera idea fue SMTP con una cuenta de Gmail. No funciona en producción:
desde el 26 de septiembre de 2025, el plan gratuito de Render bloquea la salida
a los puertos SMTP (25, 465 y 587). Las alternativas que sí salen por HTTP
(Brevo, Resend, SendGrid) exigen crear una cuenta, verificar un remitente o un
dominio y gestionar una clave. Para el MVP y su demostración, el equipo prefiere
no depender de un servicio externo más.

## Decisión

1. **Un módulo `notification`** con la estructura de los demás. Los otros
   módulos envían a través de su fachada `EmailNotifications`, y el puerto
   `EmailSender` aísla el transporte.
2. **Sin SMTP configurado, el correo se guarda en una bandeja de la
   aplicación** (tabla `sent_emails`, V6). Su destinatario lo lee con
   `GET /api/v1/notifications/emails`, que devuelve los correos dirigidos al
   email de su token, del más reciente al más antiguo. Es lo que ocurre en
   Render: no hay nada que configurar.
3. **SMTP queda como opción.** Con `SPRING_MAIL_HOST` (y `SPRING_MAIL_PORT`,
   `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `APP_MAIL_FROM`), Spring
   Boot crea el `JavaMailSender` y `MailConfig` envía por SMTP. Sirve en local
   o en un plan de pago.
4. **El correo se envía después del commit.** Un caso de uso publica un evento
   y un `@TransactionalEventListener(AFTER_COMMIT)` lo envía. Si falla, se
   registra en el log y la operación de negocio queda en pie. Como en ese
   momento la transacción de negocio ya confirmó pero sigue ligada al hilo, la
   bandeja escribe en una transacción propia (`REQUIRES_NEW`).

## Alternativas consideradas

1. **Gmail por SMTP:** gratis y sin dominio, pero bloqueado en el plan
   gratuito de Render; solo funcionaría en local.
2. **Un proveedor con API HTTP (Brevo, Resend):** es el destino si la
   plataforma necesita correo real, y es otro adaptador de `EmailSender`, sin
   tocar los casos de uso. Hoy exigiría cuentas y claves que el equipo no
   quiere gestionar para el MVP.
3. **Solo registrar el correo en el log:** no requiere nada, pero no se puede
   mostrar en una demostración ni lo puede consultar el destinatario.

## Consecuencias

- (+) HU-002 cumple su escenario de notificación sin configurar nada, y el
  mensaje se puede mostrar: el proveedor abre su bandeja y ve la aprobación.
- (+) HU-015 tiene el canal listo, y pasar a correo real es un adaptador más.
- (−) En producción el correo no sale de la plataforma: el destinatario tiene
  que entrar a leerlo. Es una bandeja de notificaciones, no correo real.
- (−) La bandeja crece sin límite; no hay borrado ni paginación. Para el MVP es
  aceptable.
- (−) Un correo que falla no se reintenta; queda registrado en el log.

Ver `notification/` (el módulo), `ProviderApprovalMailer` (el primer uso) y
`ProviderOnboardingIT` (la aprobación llega a la bandeja del proveedor).
