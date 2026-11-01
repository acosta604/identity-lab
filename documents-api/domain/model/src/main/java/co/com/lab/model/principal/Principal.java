package co.com.lab.model.principal;

import co.com.lab.model.exception.InvalidPrincipalException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Quien hace la peticion y en que contexto: viene de los claims del token propio.
 * <p>
 * Regla de dominio: las membresias de la persona (tenant, organizacion, equipos) no se guardan en el
 * sistema de autorizacion; se derivan de aqui en cada decision ({@link #membershipRelationships()}).
 *
 * @param userId  sub
 * @param tenant  tenant activo (tid)
 * @param orgId   organizacion activa
 * @param orgRole rol en la organizacion
 * @param teams   equipos de esa organizacion con su rol
 * @param roles   roles a nivel tenant
 */
public record Principal(String userId, String tenant, String orgId, String orgRole,
                        List<TeamMembership> teams, List<String> roles) {

    public static final Set<String> ORG_ROLES = Set.of("member", "admin");
    public static final Set<String> TEAM_ROLES = Set.of("member", "lead");
    public static final Set<String> TENANT_ROLES = Set.of("member", "admin");

    public record TeamMembership(String teamId, String role) {
    }

    public Principal {
        require(userId, "sub");
        require(tenant, "tid");
        require(orgId, "org.id");
        vocabulary(orgRole, ORG_ROLES, "org.role");
        teams = List.copyOf(teams == null ? List.of() : teams);
        roles = List.copyOf(roles == null ? List.of() : roles);
        for (TeamMembership t : teams) {
            require(t.teamId(), "teams[].id");
            vocabulary(t.role(), TEAM_ROLES, "teams[].role");
        }
        for (String r : roles) {
            vocabulary(r, TENANT_ROLES, "roles[]");
        }
    }

    public String userRef() {
        return "user:" + userId;
    }

    /**
     * Relaciones de la persona que viajan como tuplas contextuales. Mapeo mecanico:
     * tid -> member tenant; roles[] -> rol sobre tenant; org -> rol sobre organization;
     * teams[] -> rol sobre team.
     */
    public List<Relationship> membershipRelationships() {
        List<Relationship> result = new ArrayList<>();
        result.add(new Relationship(userRef(), "member", "tenant:" + tenant));
        for (String role : roles) {
            if (!"member".equals(role)) {
                result.add(new Relationship(userRef(), role, "tenant:" + tenant));
            }
        }
        result.add(new Relationship(userRef(), orgRole, "organization:" + orgId));
        for (TeamMembership t : teams) {
            result.add(new Relationship(userRef(), t.role(), "team:" + t.teamId()));
        }
        return List.copyOf(result);
    }

    private static void require(String value, String claim) {
        if (value == null || value.isBlank()) {
            throw new InvalidPrincipalException("Falta el claim " + claim);
        }
    }

    private static void vocabulary(String value, Set<String> allowed, String claim) {
        require(value, claim);
        if (!allowed.contains(value)) {
            throw new InvalidPrincipalException("Rol desconocido en " + claim + ": " + value);
        }
    }
}
