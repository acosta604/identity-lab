package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.Document;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.exception.AccessDeniedException;
import co.com.lab.model.exception.DocumentNotFoundException;
import co.com.lab.model.principal.Principal;
import co.com.lab.model.principal.Relationship;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los casos de uso con dobles en memoria. El doble de autorizacion permite solo lo que esta en
 * "allowed" y registra que relaciones de recurso se escriben.
 */
class DocumentUseCasesTest {

    private final Map<Long, Document> docs = new LinkedHashMap<>();
    private final Set<String> allowed = new HashSet<>();
    private final List<Relationship> written = new ArrayList<>();
    private final List<Relationship> deleted = new ArrayList<>();

    private final DocumentRepository repo = new DocumentRepository() {
        long next = 1000;

        @Override
        public Optional<Document> findById(long id) {
            return Optional.ofNullable(docs.get(id));
        }

        @Override
        public List<Document> findByOrganization(String tenantId, String organizationId) {
            return docs.values().stream()
                    .filter(d -> d.tenantId().equals(tenantId) && d.organizationId().equals(organizationId))
                    .toList();
        }

        @Override
        public Document create(NewDocument d) {
            Document doc = new Document(next++, d.tenantId(), d.organizationId(), d.teamId(), d.ownerId(), d.title(),
                    d.body(), Instant.EPOCH);
            docs.put(doc.id(), doc);
            return doc;
        }

        @Override
        public void update(long id, String title, String body) {
            Document d = docs.get(id);
            docs.put(id, new Document(id, d.tenantId(), d.organizationId(), d.teamId(), d.ownerId(), title, body,
                    d.createdAt()));
        }

        @Override
        public void delete(long id) {
            docs.remove(id);
        }
    };

    private final AuthorizationGateway authz = new AuthorizationGateway() {
        @Override
        public boolean isAllowed(Principal principal, String relation, String object) {
            return allowed.contains(principal.userId() + " " + relation + " " + object);
        }

        @Override
        public void write(List<Relationship> relationships) {
            written.addAll(relationships);
        }

        @Override
        public void delete(List<Relationship> relationships) {
            deleted.addAll(relationships);
        }
    };

    private final Principal alice = new Principal("alice", "acme", "acme-ventas", "member",
            List.of(new Principal.TeamMembership("acme-ventas-norte", "lead")), List.of());

    DocumentUseCasesTest() {
        docs.put(42L, new Document(42, "acme", "acme-ventas", "acme-ventas-norte", "system", "Norte", "", Instant.EPOCH));
        docs.put(44L, new Document(44, "acme", "acme-ventas", null, "system", "Precios", "", Instant.EPOCH));
        docs.put(77L, new Document(77, "acme", "acme-marketing", null, "system", "Q4", "", Instant.EPOCH));
        docs.put(90L, new Document(90, "globex", "globex-main", null, "system", "Otro tenant", "", Instant.EPOCH));
    }

    @Test
    void listarSoloDevuelveLoQueOpenFgaPermiteVer() {
        allowed.add("alice viewer document:42");
        List<Document> result = new ListDocumentsUseCase(repo, authz).execute(alice);
        assertEquals(List.of(42L), result.stream().map(Document::id).toList());
    }

    @Test
    void otroTenantEs404YOtraOrganizacionLaDecideOpenFga() {
        var get = new GetDocumentUseCase(repo, authz);
        assertThrows(DocumentNotFoundException.class, () -> get.execute(alice, 90));
        assertThrows(AccessDeniedException.class, () -> get.execute(alice, 77));
    }

    @Test
    void editarRequiereEditor() {
        var update = new UpdateDocumentUseCase(repo, authz);
        assertThrows(AccessDeniedException.class,
                () -> update.execute(alice, 42, new UpdateDocumentUseCase.Command("x", null)));
        allowed.add("alice editor document:42");
        assertEquals("x", update.execute(alice, 42, new UpdateDocumentUseCase.Command("x", null)).title());
    }

    @Test
    void crearEnUnEquipoEscribeLasRelacionesDelRecurso() {
        allowed.add("alice member team:acme-ventas-norte");
        Document d = new CreateDocumentUseCase(repo, authz)
                .execute(alice, new CreateDocumentUseCase.Command("Nuevo", "...", "acme-ventas-norte"));

        assertEquals("acme-ventas", d.organizationId());
        assertEquals(List.of(
                new Relationship("team:acme-ventas-norte", "team", "document:" + d.id()),
                new Relationship("user:alice", "owner", "document:" + d.id())), written);
    }

    @Test
    void crearEnUnEquipoAjenoSeNiega() {
        assertThrows(AccessDeniedException.class, () -> new CreateDocumentUseCase(repo, authz)
                .execute(alice, new CreateDocumentUseCase.Command("Nuevo", "...", "acme-ventas-sur")));
        assertTrue(written.isEmpty());
    }

    @Test
    void borrarRequiereCanDeleteYLimpiaLasRelaciones() {
        var delete = new DeleteDocumentUseCase(repo, authz);
        assertThrows(AccessDeniedException.class, () -> delete.execute(alice, 44));
        allowed.add("alice can_delete document:44");
        delete.execute(alice, 44);
        assertTrue(docs.get(44L) == null);
        assertEquals(new Relationship("organization:acme-ventas", "organization", "document:44"), deleted.getFirst());
    }
}
