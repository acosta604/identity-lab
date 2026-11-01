package lab.exchange.api;

import lab.exchange.claims.OrgContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Responses {

    private Responses() {
    }

    static Map<String, Object> context(OrgContext c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tenant", c.tenantId());
        m.put("organization", Map.of("id", c.orgId(), "role", c.orgRole()));
        List<Map<String, String>> teams = c.teams().stream()
                .map(t -> Map.of("id", t.id(), "role", t.role()))
                .toList();
        m.put("teams", teams);
        m.put("roles", c.tenantRoles());
        return m;
    }
}
