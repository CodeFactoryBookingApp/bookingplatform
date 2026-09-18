# Consultas clave — Plataforma de Reservas

Preguntas de negocio del caso traducidas a SQL. Las consultas 1-5 operan sobre
el modelo físico del Sprint 1; las 6-9 son del modelo lógico completo y se
habilitarán cuando existan las tablas de proveedores/servicios/reservas.

## Sprint 1 (implementadas sobre el esquema actual)

### 1. ¿El correo o el documento ya están registrados? (unicidad HU-001)

```sql
select exists(select 1 from clients where lower(email) = lower(:email))    as email_taken,
       exists(select 1 from clients where upper(document) = upper(:doc))   as document_taken;
```

### 2. ¿Qué clientes llevan más de 24 h pendientes de verificación? (re-engagement)

```sql
select id, full_name, email, city, created_at
from clients
where status = 'PENDING_VERIFICATION'
  and created_at < now() - interval '24 hours'
order by created_at;
```

### 3. ¿Está bloqueada una cuenta por intentos fallidos? (HU-021)

```sql
select count(*) as recent_failures
from login_attempts
where lower(email) = lower(:email)
  and success = false
  and attempted_at > now() - interval '15 minutes';
-- bloqueada si recent_failures >= 5
```

### 4. Historial de intentos de login de un correo (auditoría de seguridad)

```sql
select email, success, attempted_at
from login_attempts
where lower(email) = lower(:email)
order by attempted_at desc
limit 50;
```

### 5. ¿Cuántos clientes hay por ciudad y canal de notificación? (crecimiento)

```sql
select city, notification_channel, count(*) as clients,
       count(*) filter (where status = 'ACTIVE') as active_clients
from clients
group by city, notification_channel
order by clients desc;
```

## Sprints 2+ (sobre el modelo lógico completo)

### 6. Reservas futuras de un cliente (historial y próximas citas)

```sql
select b.id, s.name as service, l.name as location, b.starts_at, b.status
from bookings b
join services s on s.id = b.service_id
join locations l on l.id = b.location_id
where b.client_id = :clientId
  and b.starts_at >= now()
order by b.starts_at;
```

### 7. ¿Qué reservas afecta la suspensión de un proveedor? (HU-003)

```sql
select b.id, c.full_name, c.email, s.name, b.starts_at
from bookings b
join clients c   on c.id = b.client_id
join services s  on s.id = b.service_id
where s.provider_id = :providerId
  and b.starts_at >= now()
  and b.status in ('RESERVED', 'CONFIRMED');
```

### 8. Ocupación de un servicio en una sede y semana (reporte HU de reportes)

```sql
select date_trunc('day', b.starts_at) as day,
       count(*)                       as bookings,
       sum(b.attendees)               as attendees,
       sum(b.total_price)             as revenue
from bookings b
where b.service_id = :serviceId
  and b.location_id = :locationId
  and b.starts_at >= :weekStart and b.starts_at < :weekStart + interval '7 days'
  and b.status in ('CONFIRMED', 'COMPLETED')
group by 1
order by 1;
```

### 9. Disponibilidad real de un recurso en un día (control de disponibilidad)

```sql
select st.start_time, st.end_time,
       coalesce(sum(b.attendees) filter (where b.status in ('RESERVED','CONFIRMED')), 0) as booked
from schedule_templates st
left join bookings b
       on b.resource_id = st.resource_id
      and b.starts_at::date = :day
      and b.starts_at::time >= st.start_time
      and b.starts_at::time <  st.end_time
where st.location_id = :locationId
  and st.resource_id = :resourceId
  and st.day_of_week = extract(isodow from date :day)
  and st.active
group by st.id
order by st.start_time;
```
