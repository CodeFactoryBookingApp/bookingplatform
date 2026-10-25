-- =====================================================================
--  V6: bandeja de correo dentro de la aplicación (ADR-0007)
--
--  El plan gratuito de Render bloquea la salida a los puertos SMTP, así
--  que en producción la plataforma no puede enviar correo propio sin un
--  proveedor externo. Mientras tanto, cada correo se guarda aquí y su
--  destinatario lo lee con GET /api/v1/notifications/emails.
--
--  Los correos de autenticación (verificación, recuperación) no pasan
--  por aquí: los sigue enviando Supabase.
-- =====================================================================

create table sent_emails (
    id         uuid         primary key default gen_random_uuid(),
    recipient  varchar(160) not null,
    subject    varchar(200) not null,
    body       text         not null,
    sent_at    timestamptz  not null default now(),
    constraint ck_sent_emails_recipient check (length(btrim(recipient)) > 0),
    constraint ck_sent_emails_subject   check (length(btrim(subject)) > 0)
);

-- La bandeja de una persona, lo más reciente primero
create index ix_sent_emails_recipient on sent_emails (lower(recipient), sent_at desc);

comment on table sent_emails is 'Correos de la plataforma guardados en vez de enviados por SMTP (ADR-0007)';

-- Como toda tabla de public (ADR-0002): RLS, y fuera del Data API de Supabase
alter table sent_emails enable row level security;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        revoke all on public.sent_emails from anon, authenticated;
    end if;
end $$;
