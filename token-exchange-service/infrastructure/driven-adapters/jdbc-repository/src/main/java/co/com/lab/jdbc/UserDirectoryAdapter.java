package co.com.lab.jdbc;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.accesscontext.OrganizationSummary;
import co.com.lab.model.user.User;
import co.com.lab.model.user.gateways.UserDirectoryGateway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Usuarios, vinculos con Entra y membresias en la base "exchange". */
@Repository
public class UserDirectoryAdapter implements UserDirectoryGateway {

    private final JdbcClient jdbc;

    public UserDirectoryAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<String> findLinkedUser(String issuer, String externalSubject) {
        return jdbc.sql("""
                        select u.id
                          from subject_links l
                          join users u on u.id = l.user_id
                         where l.issuer = :iss and l.external_sub = :ext and u.status = 'ACTIVE'
                        """)
                .param("iss", issuer)
                .param("ext", externalSubject)
                .query(String.class)
                .optional();
    }

    @Override
    public Optional<User> findActiveUser(String userId) {
        return jdbc.sql("select id, name, email from users where id = :id and status = 'ACTIVE'")
                .param("id", userId)
                .query((rs, i) -> new User(rs.getString("id"), rs.getString("name"), rs.getString("email")))
                .optional();
    }

    @Override
    public Optional<AccessContext> resolveContext(String userId, String tenantId, String organizationId) {
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
                .param("org", organizationId)
                .param("tenant", tenantId)
                .query((rs, i) -> new Membership(rs.getString(1), rs.getString(2), rs.getString(3)))
                .optional();

        return membership.map(m -> new AccessContext(
                m.tenantId(), m.orgId(), m.role(), teams(userId, m.orgId()), tenantRoles(userId, m.tenantId())));
    }

    @Override
    public List<OrganizationSummary> organizationsOf(String userId) {
        return jdbc.sql("""
                        select o.tenant_id, o.id, o.name, om.role
                          from org_memberships om
                          join organizations o on o.id = om.organization_id
                         where om.user_id = :user and om.status = 'ACTIVE'
                         order by o.tenant_id, o.id
                        """)
                .param("user", userId)
                .query((rs, i) -> new OrganizationSummary(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4)))
                .list();
    }

    private List<AccessContext.TeamMembership> teams(String userId, String organizationId) {
        return jdbc.sql("""
                        select t.id, tm.role
                          from team_memberships tm
                          join teams t on t.id = tm.team_id
                         where tm.user_id = :user and t.organization_id = :org and tm.status = 'ACTIVE'
                         order by t.id
                        """)
                .param("user", userId)
                .param("org", organizationId)
                .query((rs, i) -> new AccessContext.TeamMembership(rs.getString(1), rs.getString(2)))
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
