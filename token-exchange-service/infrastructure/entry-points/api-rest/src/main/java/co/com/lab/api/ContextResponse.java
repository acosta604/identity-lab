package co.com.lab.api;

import co.com.lab.model.accesscontext.AccessContext;

import java.util.List;

/** Contexto activo tal como lo ve el front (los mismos datos que van en los claims). */
public record ContextResponse(String tenant, Organization organization, List<Team> teams, List<String> roles) {

    public record Organization(String id, String role) {
    }

    public record Team(String id, String role) {
    }

    public static ContextResponse of(AccessContext c) {
        return new ContextResponse(
                c.tenantId(),
                new Organization(c.organizationId(), c.organizationRole()),
                c.teams().stream().map(t -> new Team(t.teamId(), t.role())).toList(),
                c.tenantRoles());
    }
}
