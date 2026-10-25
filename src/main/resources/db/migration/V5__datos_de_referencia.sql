-- =====================================================================
--  V5: catálogos que la aplicación necesita para funcionar
--
--  Un proveedor no se puede registrar sin una categoría y sin la ciudad
--  de su sede (HU-002), ni un servicio sin moneda (HU-004). Hasta ahora
--  estas filas sólo existían en seed.sql, que no se ejecuta en Supabase.
--
--  Son datos de referencia, no de prueba: van en todas las bases. El
--  `on conflict do nothing` respeta las filas que alguien ya hubiera
--  cargado a mano con el mismo nombre.
-- =====================================================================

insert into organization_categories (name) values
    ('Salud'),
    ('Belleza'),
    ('Deporte'),
    ('Bienestar'),
    ('Otros')
on conflict do nothing;

-- Las capitales de departamento y las ciudades de sus áreas metropolitanas
-- más grandes. Se amplía con otra migración cuando haga falta.
insert into cities (name, region) values
    ('Bogotá',        'Bogotá D.C.'),
    ('Soacha',        'Cundinamarca'),
    ('Chía',          'Cundinamarca'),
    ('Medellín',      'Antioquia'),
    ('Envigado',      'Antioquia'),
    ('Bello',         'Antioquia'),
    ('Itagüí',        'Antioquia'),
    ('Rionegro',      'Antioquia'),
    ('Cali',          'Valle del Cauca'),
    ('Palmira',       'Valle del Cauca'),
    ('Barranquilla',  'Atlántico'),
    ('Cartagena',     'Bolívar'),
    ('Bucaramanga',   'Santander'),
    ('Floridablanca', 'Santander'),
    ('Cúcuta',        'Norte de Santander'),
    ('Pereira',       'Risaralda'),
    ('Manizales',     'Caldas'),
    ('Armenia',       'Quindío'),
    ('Ibagué',        'Tolima'),
    ('Santa Marta',   'Magdalena'),
    ('Villavicencio', 'Meta'),
    ('Pasto',         'Nariño'),
    ('Neiva',         'Huila'),
    ('Montería',      'Córdoba'),
    ('Valledupar',    'Cesar'),
    ('Popayán',       'Cauca'),
    ('Tunja',         'Boyacá'),
    ('Sincelejo',     'Sucre'),
    ('Riohacha',      'La Guajira'),
    ('Quibdó',        'Chocó'),
    ('Florencia',     'Caquetá'),
    ('Yopal',         'Casanare')
on conflict do nothing;

insert into currencies (code, name) values
    ('COP', 'Peso colombiano')
on conflict do nothing;
