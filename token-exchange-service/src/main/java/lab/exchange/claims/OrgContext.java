package lab.exchange.claims;

import java.util.List;

/**
 * Contexto activo de la persona: lo que termina en los claims tid, org, teams y roles.
 *
 * @param tenantId    tenant de la organizacion activa
 * @param orgId       organizacion activa
 * @param orgRole     rol en la organizacion (member | admin)
 * @param teams       equipos de esa organizacion con su rol (member | lead)
 * @param tenantRoles roles a nivel tenant (member | admin)
 */
public record OrgContext(
        String tenantId,
        String orgId,
        String orgRole,
        List<TeamRole> teams,
        List<String> tenantRoles) {

    public record TeamRole(String id, String role) {
    }
}
