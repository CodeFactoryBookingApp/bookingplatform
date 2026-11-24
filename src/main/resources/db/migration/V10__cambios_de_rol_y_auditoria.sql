-- El cambio queda pendiente antes de llamar a Supabase. Durante ese
-- intervalo se rechazan las sesiones del usuario afectado.
create table platform_role_changes (
    user_id uuid primary key,
    revision uuid not null,
    target_role varchar(12) not null check (target_role in ('CLIENT', 'PROVIDER', 'ADMIN')),
    completed_at timestamptz
);

alter table platform_role_changes enable row level security;

create table audit_events (
    id uuid primary key default gen_random_uuid(),
    occurred_at timestamptz not null,
    actor_id uuid not null,
    actor_role varchar(30) not null,
    action varchar(64) not null,
    resource_type varchar(40) not null,
    resource_id uuid not null,
    organization_id uuid,
    result varchar(40) not null
);

create index ix_audit_events_time on audit_events (occurred_at desc, id desc);
create index ix_audit_events_scope on audit_events (organization_id, occurred_at desc, id desc);
alter table audit_events enable row level security;

create function fn_audit_events_inmutable() returns trigger
language plpgsql as $$
begin
    raise exception 'Los eventos de auditoría son inmutables';
end;
$$;

create trigger trg_audit_events_inmutable
    before update or delete or truncate on audit_events
    for each statement execute function fn_audit_events_inmutable();

-- Conserva el historial de las operaciones administrativas aunque cambie
-- o desaparezca la fila de origen. El motivo se queda en su tabla original.
create function fn_auditar_estado_de_cuenta() returns trigger
language plpgsql as $$
begin
    if new.changed_by is not null then
        insert into audit_events (occurred_at, actor_id, actor_role, action, resource_type,
                                  resource_id, organization_id, result)
        values (new.changed_at, new.changed_by, 'ADMIN', 'ACCOUNT_STATUS_' || new.to_status,
                case new.subject_type when 'CLIENT' then 'USER' else 'ORGANIZATION' end,
                coalesce(new.client_id, new.organization_id), new.organization_id, 'SUCCESS');
    end if;
    return new;
end;
$$;

create trigger trg_auditar_estado_de_cuenta
    after insert on account_status_changes
    for each row execute function fn_auditar_estado_de_cuenta();

do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on platform_role_changes, audit_events from anon;
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        revoke all on platform_role_changes, audit_events from authenticated;
    end if;
end
$$;
