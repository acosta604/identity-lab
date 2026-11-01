package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.document.Document;
import co.com.lab.model.document.DocumentPermission;
import co.com.lab.model.principal.Principal;

import java.util.List;

/** Documentos de la organizacion activa que la persona puede ver. */
public class ListDocumentsUseCase {

    private final DocumentRepository documents;
    private final AuthorizationGuard guard;

    public ListDocumentsUseCase(DocumentRepository documents, AuthorizationGateway authorization) {
        this.documents = documents;
        this.guard = new AuthorizationGuard(authorization);
    }

    public List<Document> execute(Principal principal) {
        return documents.findByOrganization(principal.tenant(), principal.orgId()).stream()
                .filter(d -> guard.allows(principal, DocumentPermission.VIEW.relation(), d.authzObject()))
                .toList();
    }
}
