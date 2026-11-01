-- Emisores cuyos tokens se aceptan como subject_token (Entra / mock de Entra).
create table trusted_issuers (
    issuer         text primary key,           -- "iss" exacto del token
    jwks_uri       text not null,              -- de donde bajar las llaves (puede ser una URL interna)
    audience       text not null,              -- "aud" que debe traer el token (emitido para este servicio)
    subject_claim  text not null default 'oid',-- claim que identifica a la persona: 'oid' en Entra
    expected_tid   text,                       -- tenant de Azure esperado; null = no se valida
    enabled        boolean not null default true
);

create table users (
    id      text primary key,                  -- es el "sub" del token propio
    name    text not null,
    email   text not null,
    status  text not null default 'ACTIVE'
);
create unique index users_email_uq on users (lower(email));

-- Identidad externa (emisor + oid) -> usuario interno.
create table subject_links (
    issuer        text not null references trusted_issuers (issuer),
    external_sub  text not null,
    user_id       text not null references users (id),
    primary key (issuer, external_sub)
);

create table tenants (
    id      text primary key,
    name    text not null,
    status  text not null default 'ACTIVE'
);

-- Claim "roles": roles a nivel tenant.
create table tenant_roles (
    user_id    text not null references users (id),
    tenant_id  text not null references tenants (id),
    role       text not null check (role in ('member', 'admin')),
    primary key (user_id, tenant_id, role)
);

create table organizations (
    id         text primary key,
    tenant_id  text not null references tenants (id),
    name       text not null
);

-- Claim "org".
create table org_memberships (
    user_id          text not null references users (id),
    organization_id  text not null references organizations (id),
    role             text not null check (role in ('member', 'admin')),
    status           text not null default 'ACTIVE',
    primary key (user_id, organization_id)
);

create table teams (
    id               text primary key,
    organization_id  text not null references organizations (id),
    name             text not null
);

-- Claim "teams".
create table team_memberships (
    user_id  text not null references users (id),
    team_id  text not null references teams (id),
    role     text not null check (role in ('member', 'lead')),
    status   text not null default 'ACTIVE',
    primary key (user_id, team_id)
);

-- APIs para las que se puede pedir token y su vida maxima.
create table allowed_audiences (
    audience         text primary key,
    max_ttl_seconds  int not null
);

-- Llaves de firma del token propio. En produccion van a un KMS / Key Vault, nunca a la base.
create table signing_keys (
    kid          text primary key,
    private_jwk  text not null,
    public_jwk   text not null,
    active       boolean not null,
    created_at   timestamptz not null default now()
);

-- Sesion propia: nace en el exchange y vive sin volver a Entra.
create table sessions (
    id               uuid primary key,          -- va en el claim "sid"
    user_id          text not null references users (id),
    secret_hash      bytea not null unique,     -- SHA-256 del secreto de la cookie
    previous_hash    bytea,                     -- secreto anterior: si vuelve a llegar, hubo robo
    tenant_id        text not null,
    organization_id  text not null,
    idp              text not null,
    created_at       timestamptz not null default now(),
    last_seen_at     timestamptz not null default now(),
    expires_at       timestamptz not null,
    revoked_at       timestamptz,
    user_agent       text
);
create index sessions_user_idx on sessions (user_id) where revoked_at is null;

create table exchange_audit (
    id               bigserial primary key,
    user_id          text,
    tenant_id        text,
    organization_id  text,
    idp              text,
    audience         text,
    result           text not null,
    detail           text,
    at               timestamptz not null default now()
);
