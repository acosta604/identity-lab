package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.document.Document;
import co.com.lab.model.principal.Principal;

/**
 * Crea un documento en la organizacion activa, o en uno de sus equipos.
 * Para crear en un equipo hay que ser member/lead del equipo; para la organizacion, member/admin.
 */
public class CreateDocumentUseCase {

    private final DocumentRepository documents;
    private final AuthorizationGateway authorization;
    private final AuthorizationGuard guard;

    public CreateDocumentUseCase(DocumentRepository documents, AuthorizationGateway authorization) {
        this.documents = documents;
        this.authorization = authorization;
        this.guard = new AuthorizationGuard(authorization);
    }

    public record Command(String title, String body, String teamId) {
    }

    public Document execute(Principal principal, Command command) {
        if (command.title() == null || command.title().isBlank()) {
            throw new IllegalArgumentException("title es obligatorio");
        }
        String teamId = command.teamId() == null || command.teamId().isBlank() ? null : command.teamId();
        if (teamId != null) {
            guard.require(principal, "member", "team:" + teamId);
        } else {
            guard.require(principal, "member", "organization:" + principal.orgId());
        }

        Document created = documents.create(new DocumentRepository.NewDocument(
                principal.tenant(), principal.orgId(), teamId, principal.userId(), command.title(), command.body()));
        // No es atomico con la base: en produccion se usaria un outbox. Para el lab basta este orden.
        authorization.write(created.resourceRelationships());
        return created;
    }
}
