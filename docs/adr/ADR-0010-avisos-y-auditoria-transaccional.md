# ADR-0010: Registro transaccional de avisos y auditoría de reservas

- **Estado:** Aceptado

## Decisión

PostgreSQL registra los cambios de reserva y genera sus avisos dentro de la transacción que guarda
la reserva. Así, cualquier escritor del motor puede usar las tablas existentes. Si la operación se
revierte, tampoco queda una fila de auditoría ni un aviso pendiente. El motor puede llamar a
`AuditActor.identify(userId, role)` al inicio de la escritura para atribuir el cambio a la persona
autorizada. Los cambios sin actor identificado se registran como acciones de sistema. Cuando el
historial de reserva ya trae `changed_by`, se registra además esa atribución si el escritor no
proporcionó el contexto.

Un proceso de Spring revisa la cola cada 15 segundos por defecto. La inserción en la bandeja y la
marca de entrega se confirman juntas. Los avisos de reserva se asocian al UUID del cliente y al
del propietario del proveedor. La dirección registrada se conserva para los correos anteriores,
como el aviso de aprobación, que se consultan como en el ADR-0007.

Los recordatorios se programan para reservas confirmadas. Cancelar o cambiar la cita retira los
recordatorios pendientes del horario anterior. El proceso aplica la preferencia individual y, si
la franja de silencio termina antes de la cita, pospone el recordatorio hasta ese momento. Los
avisos esenciales se entregan sin esa espera. La entrega de este sprint usa la bandeja de la app.

HU-018 busca por el código único de la reserva. La base asigna el código por defecto y conserva el
UUID como código de las reservas anteriores a la migración. HU-020 usa la moneda guardada en la
reserva para evitar mezclar importes. Para las reservas anteriores se inicializa con la moneda
actual del servicio, que es el dato disponible al migrarlas.
