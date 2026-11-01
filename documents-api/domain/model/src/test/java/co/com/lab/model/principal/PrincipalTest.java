package co.com.lab.model.principal;

import co.com.lab.model.exception.InvalidPrincipalException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrincipalTest {

    @Test
    void losClaimsSeConviertenEnTuplasContextuales() {
        Principal alice = new Principal("alice", "acme", "acme-ventas", "member",
                List.of(new Principal.TeamMembership("acme-ventas-norte", "lead"),
                        new Principal.TeamMembership("acme-ventas-sur", "member")),
                List.of());

        assertEquals(List.of(
                new Relationship("user:alice", "member", "tenant:acme"),
                new Relationship("user:alice", "member", "organization:acme-ventas"),
                new Relationship("user:alice", "lead", "team:acme-ventas-norte"),
                new Relationship("user:alice", "member", "team:acme-ventas-sur")),
                alice.membershipRelationships());
    }

    @Test
    void elRolAdminDeTenantGeneraSuTupla() {
        Principal root = new Principal("root", "acme", "acme-ventas", "admin", List.of(), List.of("admin"));
        assertEquals(new Relationship("user:root", "admin", "tenant:acme"), root.membershipRelationships().get(1));
    }

    @Test
    void rechazaRolesFueraDelVocabulario() {
        assertThrows(InvalidPrincipalException.class, () -> new Principal("alice", "acme", "acme-ventas", "owner",
                List.of(), List.of()));
        assertThrows(InvalidPrincipalException.class, () -> new Principal("alice", "acme", "acme-ventas", "member",
                List.of(new Principal.TeamMembership("t", "owner")), List.of()));
        assertThrows(InvalidPrincipalException.class, () -> new Principal("alice", null, "acme-ventas", "member",
                List.of(), List.of()));
    }
}
