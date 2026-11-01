package co.com.lab.model.audit;

/**
 * Rastro de exchanges, refresh y rechazos.
 *
 * @param result ISSUED, ISSUED_S2S, BAD_SUBJECT, NOT_LINKED, NO_MEMBERSHIP, REFRESHED, REFRESH_REJECTED,
 *               CONTEXT_CHANGED, LOGOUT
 */
public record AuditEvent(String result, String userId, String tenantId, String organizationId,
                         String idp, String audience, String detail) {

    public static AuditEvent of(String result) {
        return new AuditEvent(result, null, null, null, null, null, null);
    }

    public AuditEvent withResult(String v) {
        return new AuditEvent(v, userId, tenantId, organizationId, idp, audience, detail);
    }

    public AuditEvent user(String v) {
        return new AuditEvent(result, v, tenantId, organizationId, idp, audience, detail);
    }

    public AuditEvent context(String tenant, String organization) {
        return new AuditEvent(result, userId, tenant, organization, idp, audience, detail);
    }

    public AuditEvent idp(String v) {
        return new AuditEvent(result, userId, tenantId, organizationId, v, audience, detail);
    }

    public AuditEvent audience(String v) {
        return new AuditEvent(result, userId, tenantId, organizationId, idp, v, detail);
    }

    public AuditEvent detail(String v) {
        return new AuditEvent(result, userId, tenantId, organizationId, idp, audience, v);
    }
}
