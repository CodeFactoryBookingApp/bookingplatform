-- =====================================================================
--  V4: un proveedor se identifica con tipo y número (HU-002)
--
--  HU-002 dice "NIT o documento": una empresa tiene NIT, pero una
--  persona natural que presta servicios se identifica con su cédula,
--  su cédula de extranjería o su pasaporte. La columna `nit` sólo
--  admitía lo primero, y su nombre lo decía.
--
--  La unicidad pasa a ser por (tipo, número): el mismo número puede
--  existir como NIT y como cédula de dos personas distintas.
--  Las organizaciones que ya existen quedan como NIT, que es lo que eran.
-- =====================================================================

alter table organizations rename column nit to identification_number;

alter table organizations
    add column identification_type varchar(20) not null default 'NIT';

alter table organizations
    add constraint ck_organizations_identification_type
        check (identification_type in ('NIT', 'CC', 'CE', 'PASAPORTE'));

alter table organizations rename constraint ck_organizations_nit to ck_organizations_identification_number;

alter table organizations drop constraint uk_organizations_nit;
alter table organizations
    add constraint uk_organizations_identification unique (identification_type, identification_number);

comment on column organizations.identification_type   is 'NIT para empresas; CC, CE o PASAPORTE para personas naturales (HU-002)';
comment on column organizations.identification_number is 'Número del documento. Único junto con su tipo (HU-002)';
