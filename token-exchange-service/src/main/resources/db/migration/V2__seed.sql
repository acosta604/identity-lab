-- Mock de Entra: iss = http://localhost:8090/entra (como lo ve el navegador o curl desde tu maquina).
-- Las llaves se bajan por la red interna de Docker. En el mock la persona viene en "sub";
-- con Entra real se usa 'oid' y expected_tid = el tenant de Azure del banco.
insert into trusted_issuers (issuer, jwks_uri, audience, subject_claim, expected_tid) values
    ('http://localhost:8090/entra', 'http://mock-entra:8080/entra/jwks', 'token-exchange-service', 'sub', 'lab-azure-tenant');

insert into users (id, name, email) values
    ('alice', 'Alice', 'alice@lab.local'),
    ('bob',   'Bob',   'bob@lab.local'),
    ('carol', 'Carol', 'carol@lab.local'),
    ('dave',  'Dave',  'dave@lab.local');

insert into subject_links (issuer, external_sub, user_id) values
    ('http://localhost:8090/entra', 'alice', 'alice'),
    ('http://localhost:8090/entra', 'bob',   'bob'),
    ('http://localhost:8090/entra', 'carol', 'carol'),
    ('http://localhost:8090/entra', 'dave',  'dave');

insert into tenants (id, name) values
    ('acme',   'ACME'),
    ('globex', 'Globex');

insert into organizations (id, tenant_id, name) values
    ('acme-ventas',    'acme',   'ACME Ventas'),
    ('acme-marketing', 'acme',   'ACME Marketing'),
    ('globex-main',    'globex', 'Globex');

insert into teams (id, organization_id, name) values
    ('acme-ventas-norte', 'acme-ventas', 'Ventas Norte'),
    ('acme-ventas-sur',   'acme-ventas', 'Ventas Sur');

insert into org_memberships (user_id, organization_id, role) values
    ('alice', 'acme-ventas',    'member'),
    ('alice', 'acme-marketing', 'member'),
    ('bob',   'acme-ventas',    'admin'),
    ('carol', 'acme-marketing', 'admin'),
    ('dave',  'acme-ventas',    'member');

insert into team_memberships (user_id, team_id, role) values
    ('alice', 'acme-ventas-norte', 'lead'),
    ('alice', 'acme-ventas-sur',   'member'),
    ('bob',   'acme-ventas-sur',   'member');

insert into allowed_audiences (audience, max_ttl_seconds) values
    ('documents-api', 600),
    ('reports-api',   300);
