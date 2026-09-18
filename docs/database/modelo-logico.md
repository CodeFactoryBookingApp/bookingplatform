# Modelo lógico — Plataforma de Reservas de Servicios

Modelo entidad-relación del dominio completo del caso. Las tablas marcadas con
**(S1)** pertenecen al modelo físico inicial del Sprint 1; el resto se implementará
en sprints posteriores conforme a las HU-002/003/004 y de reservas.

Las credenciales (email, password, confirmación de correo) viven en `auth.users`
de Supabase; el backend guarda únicamente perfiles y datos de negocio enlazados
por `user_id` (UUID).

```mermaid
erDiagram
    AUTH_USERS ||--o| CLIENT : "perfil (S1)"
    AUTH_USERS ||--o| PROVIDER_OWNER : "duenio de negocio"
    AUTH_USERS ||--o| PROVIDER_STAFF : "personal de sede"
    AUTH_USERS ||--o| ADMIN_USER : "administrador"

    PROVIDER ||--|{ LOCATION : "tiene sedes"
    PROVIDER ||--|| OPERATING_RULES : "reglas de operacion"
    PROVIDER ||--|{ SERVICE : "ofrece"
    PROVIDER ||--|{ RESOURCE_TYPE : "define"
    PROVIDER ||--o{ PROVIDER_STAFF : "emplea"
    PROVIDER ||--o| PROVIDER_OWNER : "pertenece"

    SERVICE ||--|{ SERVICE_LOCATION : "se presta en"
    LOCATION ||--|{ SERVICE_LOCATION : "alberga"
    SERVICE ||--|{ SERVICE_RESOURCE_TYPE : "requiere"
    RESOURCE_TYPE ||--|{ SERVICE_RESOURCE_TYPE : "es requerido por"
    RESOURCE_TYPE ||--|{ RESOURCE : "instancias"
    LOCATION ||--o{ RESOURCE : "posee"
    LOCATION ||--o{ SCHEDULE_TEMPLATE : "agenda"
    RESOURCE ||--o{ SCHEDULE_TEMPLATE : "disponibilidad"

    CLIENT ||--o{ BOOKING : "reserva"
    SERVICE ||--o{ BOOKING : "es reservado"
    LOCATION ||--o{ BOOKING : "atiende"
    RESOURCE ||--o{ BOOKING : "asignado"
    BOOKING ||--|| BOOKING_CONDITION_SNAPSHOT : "condiciones vigentes"
    BOOKING ||--o{ BOOKING_STATUS_CHANGE : "historia de estados"

    AUTH_USERS {
        uuid id PK "identidad Supabase"
        string email UK
        string password_hash "gestionado por GoTrue"
        boolean email_confirmed_at
        jsonb app_metadata "rol aplicacion: CLIENT/PROVIDER/ADMIN"
    }

    CLIENT {
        uuid id PK "= auth_users.id (S1)"
        string full_name "(S1)"
        string document UK "(S1)"
        date birth_date "(S1)"
        string email UK "(S1)"
        string phone "(S1)"
        string city "(S1)"
        string notification_channel "EMAIL/SMS/WHATSAPP (S1)"
        string status "PENDING_VERIFICATION/ACTIVE/SUSPENDED (S1)"
        timestamp created_at "(S1)"
        timestamp updated_at "(S1)"
    }

    LOGIN_ATTEMPT {
        bigint id PK "(S1)"
        string email "(S1)"
        boolean success "(S1)"
        timestamp attempted_at "(S1)"
    }

    PROVIDER {
        uuid id PK
        uuid user_id FK "duenio"
        string legal_name "razon social"
        string nit UK
        string category
        string contact_email
        string contact_phone
        string timezone
        string status "PENDING_APROBACION/ACTIVO/SUSPENDIDO"
    }

    PROVIDER_OWNER {
        uuid id PK
        uuid user_id FK
        uuid provider_id FK
    }

    PROVIDER_STAFF {
        uuid id PK
        uuid user_id FK
        uuid provider_id FK
        string role
    }

    ADMIN_USER {
        uuid id PK
        uuid user_id FK
        boolean mfa_enabled
    }

    LOCATION {
        uuid id PK
        uuid provider_id FK
        string name
        string address
        string city
        string timezone
        boolean active
    }

    OPERATING_RULES {
        uuid id PK
        uuid provider_id FK
        int min_lead_time_hours "anticipacion minima"
        int max_lead_time_hours "anticipacion maxima (>= minima)"
        int free_cancellation_window_hours "ventana sin penalidad"
    }

    SERVICE {
        uuid id PK
        uuid provider_id FK
        string name "unico por proveedor"
        string description
        int duration_minutes
        int prep_minutes
        int cleanup_minutes
        numeric price
        string capacity_type "INDIVIDUAL/GRUPAL"
        int max_capacity "GRUPAL exige > 1"
        boolean active
    }

    SERVICE_LOCATION {
        uuid service_id FK
        uuid location_id FK
    }

    RESOURCE_TYPE {
        uuid id PK
        uuid provider_id FK
        string name "ej: sala, especialista, equipo"
    }

    SERVICE_RESOURCE_TYPE {
        uuid service_id FK
        uuid resource_type_id FK
        int quantity_required
    }

    RESOURCE {
        uuid id PK
        uuid resource_type_id FK
        uuid location_id FK
        string name
        boolean active
    }

    SCHEDULE_TEMPLATE {
        uuid id PK
        uuid location_id FK
        uuid resource_id FK "opcional"
        int day_of_week
        time start_time
        time end_time
        boolean active
    }

    BOOKING {
        uuid id PK
        uuid client_id FK
        uuid service_id FK
        uuid location_id FK
        uuid resource_id FK "opcional"
        timestamp starts_at
        timestamp ends_at
        int attendees
        numeric total_price "snapshot"
        string status "RESERVED/CONFIRMED/CANCELLED/COMPLETED/NO_SHOW"
    }

    BOOKING_CONDITION_SNAPSHOT {
        uuid booking_id PK
        int min_lead_time_hours "condiciones al momento de reservar"
        int max_lead_time_hours
        int free_cancellation_window_hours
        int duration_minutes
    }

    BOOKING_STATUS_CHANGE {
        uuid id PK
        uuid booking_id FK
        string from_status
        string to_status
        string reason "obligatorio en acciones administrativas"
        uuid changed_by FK
        timestamp changed_at
    }
```

## Decisiones de modelado

| Decisión | Justificación |
|---|---|
| `clients.id` = `auth.users.id` (UUID) | Un solo identificador para identidad y perfil; sin tabla `users` propia que duplicaría credenciales. |
| Perfil de proveedor separado de `auth.users` | El negocio (NIT, sedes, reglas) es de la empresa; la cuenta es de la persona dueña/encargada. |
| `booking_condition_snapshot` | Regla HU-002/HU-004: los cambios de parámetros o duración aplican solo a nuevas reservas; las vigentes conservan sus condiciones. |
| `booking_status_change.reason` | HU-003: aprobar/suspender/reactivar exigen motivo y traza de auditoría. |
| `login_attempts` en BD propia | GoTrue solo aplica rate limiting por IP; el bloqueo por cuenta (HU-021) es regla de negocio local. |
| Estados como `varchar` + enums en código | Legibilidad en consultas/reportes; Spring mapea enums `EnumType.STRING`. |

Ver `schema.sql` (modelo físico inicial Sprint 1), `rls.sql` (seguridad Supabase)
y `consultas-clave.md` (preguntas de negocio y su SQL).
