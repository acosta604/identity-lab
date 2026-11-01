package lab.exchange.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Deja rastro de cada exchange, refresh y rechazo en la tabla exchange_audit. */
@Component
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);

    private final JdbcClient jdbc;

    public AuditLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(Event event) {
        log.info("audit result={} user={} tenant={} org={} aud={} detail={}",
                event.result(), event.userId(), event.tenantId(), event.organizationId(), event.audience(), event.detail());
        jdbc.sql("""
                        insert into exchange_audit (user_id, tenant_id, organization_id, idp, audience, result, detail)
                        values (:user, :tenant, :org, :idp, :aud, :result, :detail)
                        """)
                .param("user", event.userId())
                .param("tenant", event.tenantId())
                .param("org", event.organizationId())
                .param("idp", event.idp())
                .param("aud", event.audience())
                .param("result", event.result())
                .param("detail", event.detail())
                .update();
    }

    public record Event(String result, String userId, String tenantId, String organizationId,
                        String idp, String audience, String detail) {

        public static Event of(String result) {
            return new Event(result, null, null, null, null, null, null);
        }

        public Event withResult(String v) {
            return new Event(v, userId, tenantId, organizationId, idp, audience, detail);
        }

        public Event user(String v) {
            return new Event(result, v, tenantId, organizationId, idp, audience, detail);
        }

        public Event context(String tenant, String org) {
            return new Event(result, userId, tenant, org, idp, audience, detail);
        }

        public Event idp(String v) {
            return new Event(result, userId, tenantId, organizationId, v, audience, detail);
        }

        public Event audience(String v) {
            return new Event(result, userId, tenantId, organizationId, idp, v, detail);
        }

        public Event detail(String v) {
            return new Event(result, userId, tenantId, organizationId, idp, audience, v);
        }
    }
}
