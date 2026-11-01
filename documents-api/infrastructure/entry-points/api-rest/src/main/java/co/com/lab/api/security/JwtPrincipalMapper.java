package co.com.lab.api.security;

import co.com.lab.model.exception.InvalidPrincipalException;
import co.com.lab.model.principal.Principal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Traduce los claims del JWT propio al {@link Principal} del dominio. Es el unico lugar de este servicio
 * que conoce los nombres de los claims (sub, tid, org, teams, roles).
 */
public final class JwtPrincipalMapper {

    private JwtPrincipalMapper() {
    }

    public static Principal toPrincipal(Jwt jwt) {
        Map<String, Object> org = jwt.getClaimAsMap("org");
        if (org == null) {
            throw new InvalidPrincipalException("Falta el claim org");
        }

        List<Principal.TeamMembership> teams = new ArrayList<>();
        Object rawTeams = jwt.getClaim("teams");
        if (rawTeams instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> team)) {
                    throw new InvalidPrincipalException("teams[] debe ser una lista de objetos");
                }
                teams.add(new Principal.TeamMembership(str(team.get("id")), str(team.get("role"))));
            }
        } else if (rawTeams != null) {
            throw new InvalidPrincipalException("teams debe ser una lista");
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        // El constructor de Principal valida datos obligatorios y el vocabulario de roles.
        return new Principal(
                jwt.getSubject(),
                jwt.getClaimAsString("tid"),
                str(org.get("id")),
                str(org.get("role")),
                teams,
                roles == null ? List.of() : roles);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
