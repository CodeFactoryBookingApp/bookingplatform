# ADR-0009: Aplicación inmediata de los cambios de rol

- **Estado:** Aceptado

## Decisión

Supabase Auth conserva el rol de plataforma en `app_metadata.role`. Al cambiarlo, la API guarda primero una revisión pendiente en PostgreSQL. Luego actualiza el rol y la revisión en GoTrue y confirma el cambio junto con su evento de auditoría. El usuario afectado debe iniciar sesión de nuevo. Mientras la operación esté pendiente, la API rechaza sus tokens. Después exige la revisión nueva y una sesión autenticada después del cambio, según el campo `amr` firmado por Supabase. El primer inicio de sesión puede tener que esperar al siguiente segundo por la precisión de ese campo.

Los cambios se serializan con un bloqueo transaccional de PostgreSQL. Antes de quitar un rol de administrador, la API comprueba que quede al menos otra cuenta administradora confirmada, sin bloqueo en Supabase y sin un cambio pendiente. La consulta incluye las páginas necesarias del listado de usuarios. La suspensión de un perfil de cliente no se permite mientras su cuenta tenga rol de administrador.

Si falla Supabase con una operación pendiente, un administrador vuelve a enviar el mismo cambio para reintentar con la misma revisión. El primer administrador se crea con el procedimiento del runbook. Los cambios posteriores de rol se hacen por la API para que se apliquen la protección y la invalidación de sesiones.
