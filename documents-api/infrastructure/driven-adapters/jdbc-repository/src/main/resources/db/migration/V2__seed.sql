-- Deben coincidir con openfga/seed-tuples.txt.
insert into documents (id, tenant_id, organization_id, team_id, owner_id, title, body) values
    (42, 'acme', 'acme-ventas',    'acme-ventas-norte', 'system', 'Plan comercial Norte', 'Documento del equipo Ventas Norte'),
    (43, 'acme', 'acme-ventas',    'acme-ventas-sur',   'bob',    'Plan comercial Sur',   'Documento del equipo Ventas Sur, creado por Bob'),
    (44, 'acme', 'acme-ventas',    null,                'system', 'Politica de precios',  'Documento de toda la organizacion Ventas'),
    (77, 'acme', 'acme-marketing', null,                'system', 'Campana Q4',           'Documento de la organizacion Marketing');

select setval('documents_id_seq', 1000);
