alter table organization_policies
    add column default_closure_status varchar(20) not null default 'COMPLETED',
    add column auto_close_delay_hours integer not null default 24,
    add constraint ck_organization_policies_closure_status check (default_closure_status in ('COMPLETED', 'NO_SHOW')),
    add constraint ck_organization_policies_auto_close_hours check (auto_close_delay_hours > 0);
