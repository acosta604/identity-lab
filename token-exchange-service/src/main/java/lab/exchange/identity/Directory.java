package lab.exchange.identity;

import lab.exchange.claims.OrgContext;
import lab.exchange.claims.UserInfo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Lecturas de la base "exchange": usuarios internos, vinculos con Entra y membresias.
 * Es la fuente de verdad de los claims tid, org, teams y roles.
 */
@Component
public class Directory {

    private final JdbcClient jdbc;

    public Directory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** (emisor, oid/sub externo) -> usuario interno activo. */
    public Optional<String> linkedUser(String issuer, String externalSub) {
        return jdbc.sql("""
                        select u.id
                          from subject_links l
                          join users u on u.id = l.user_id
                         where l.issuer = :iss and l.external_sub = :ext and u.status = 'ACTIVE'
                        """)
                .param("iss", issuer)
                .param("ext", externalSub)
                .query(String.class)
                .optional();
    }

    public Optional<UserInfo> user(String userId) {
        return jdbc.sql("select id, name, email from users where id = :id and status = 'ACTIVE'")
                .param("id", userId)
                .query((rs, i) -> new UserInfo(rs.getString("id"), rs.getString("name"), rs.getString("email")))
                .optional();
    }

    /**
     * Resuelve el contexto activo. Si no se pide organizacion, toma la primera de la persona
     * (opcionalmente dentro del tenant pedido). Vacio = no es miembro activo de esa organizacion.
     */
    public Optional<OrgContext> context(String userId, String tenant, String organization) {
        record Membership(String orgId, String tenantId, String role) {
        }
        Optional<Membership> membership = jdbc.sql("""
                        select o.id, o.tenant_id, om.role
                          from org_memberships om
                          join organizations o on o.id = om.organization_id
                          join tenants t on t.id = o.tenant_id
                         where om.user_id = :user
                           and om.status = 'ACTIVE'
                           and t.status = 'ACTIVE'
                           and (cast(:org as text) is null or o.id = :org)
                           and (cast(:tenant as text) is null or o.tenant_id = :tenant)
                         order by o.id
                         limit 1
                        """)
                .param("user", userId)
                .param("org", organization)
                .param("tenant", tenant)
                .query((rs, i) -> new Membership(rs.getString(1), rs.getString(2), rs.getString(3)))
                .optional();

        return membership.map(m -> new OrgContext(
                m.tenantId(),
                m.orgId(),
                m.role(),
                teams(userId, m.orgId()),
                tenantRoles(userId, m.tenantId())));
    }

    /** Organizaciones disponibles, para el selector del front. */
    public List<OrgSummary> organizations(String userId) {
        return jdbc.sql("""
                        select o.tenant_id, o.id, o.name, om.role
                          from org_memberships om
                          join organizations o on o.id = om.organization_id
                         where om.user_id = :user and om.status = 'ACTIVE'
                         order by o.tenant_id, o.id
                        """)
                .param("user", userId)
                .query((rs, i) -> new OrgSummary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .list();
    }

    public record OrgSummary(String tenant, String id, String name, String role) {
    }

    private List<OrgContext.TeamRole> teams(String userId, String orgId) {
        return jdbc.sql("""
                        select t.id, tm.role
                          from team_memberships tm
                          join teams t on t.id = tm.team_id
                         where tm.user_id = :user and t.organization_id = :org and tm.status = 'ACTIVE'
                         order by t.id
                        """)
                .param("user", userId)
                .param("org", orgId)
                .query((rs, i) -> new OrgContext.TeamRole(rs.getString(1), rs.getString(2)))
                .list();
    }

    private List<String> tenantRoles(String userId, String tenantId) {
        return jdbc.sql("select role from tenant_roles where user_id = :user and tenant_id = :tenant order by role")
                .param("user", userId)
                .param("tenant", tenantId)
                .query(String.class)
                .list();
    }
}
