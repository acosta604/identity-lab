package co.com.lab.jdbc;

import co.com.lab.model.audit.AuditEvent;
import co.com.lab.model.audit.gateways.AuditGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class AuditAdapter implements AuditGateway {

    private static final Logger log = LoggerFactory.getLogger(AuditAdapter.class);

    private final JdbcClient jdbc;

    public AuditAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuditEvent e) {
        log.info("audit result={} user={} tenant={} org={} aud={} detail={}",
                e.result(), e.userId(), e.tenantId(), e.organizationId(), e.audience(), e.detail());
        jdbc.sql("""
                        insert into exchange_audit (user_id, tenant_id, organization_id, idp, audience, result, detail)
                        values (:user, :tenant, :org, :idp, :aud, :result, :detail)
                        """)
                .param("user", e.userId())
                .param("tenant", e.tenantId())
                .param("org", e.organizationId())
                .param("idp", e.idp())
                .param("aud", e.audience())
                .param("result", e.result())
                .param("detail", e.detail())
                .update();
    }
}
