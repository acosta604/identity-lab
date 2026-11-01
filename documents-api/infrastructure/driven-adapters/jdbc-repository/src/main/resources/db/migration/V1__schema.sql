create table documents (
    id               bigserial primary key,
    tenant_id        text not null,
    organization_id  text not null,
    team_id          text,               -- null = documento de toda la organizacion
    owner_id         text not null,
    title            text not null,
    body             text,
    created_at       timestamptz not null default now()
);
create index documents_tenant_org_idx on documents (tenant_id, organization_id);
