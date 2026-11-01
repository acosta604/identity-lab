package co.com.lab.model.accesscontext;

/** Organizacion a la que pertenece la persona, para el selector del front. */
public record OrganizationSummary(String tenantId, String organizationId, String name, String role) {
}
