package co.com.lab.model.audit.gateways;

import co.com.lab.model.audit.AuditEvent;

public interface AuditGateway {

    void record(AuditEvent event);
}
