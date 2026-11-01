package co.com.lab.model.accesscontext;

import java.util.List;

/**
 * Contexto activo de la persona; es lo que termina en los claims tid, org, teams y roles,
 * y luego en las tuplas contextuales de OpenFGA.
 *
 * @param tenantId         tenant de la organizacion activa
 * @param organizationId   organizacion activa
 * @param organizationRole member | admin
 * @param teams            equipos de esa organizacion (solo esa, para que el token no crezca)
 * @param tenantRoles      roles a nivel tenant (member | admin)
 */
public record AccessContext(
        String tenantId,
        String organizationId,
        String organizationRole,
        List<TeamMembership> teams,
        List<String> tenantRoles) {

    public AccessContext {
        teams = List.copyOf(teams);
        tenantRoles = List.copyOf(tenantRoles);
    }

    /** @param role member | lead */
    public record TeamMembership(String teamId, String role) {
    }
}
