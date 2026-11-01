package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.document.Document;
import co.com.lab.model.document.DocumentPermission;
import co.com.lab.model.principal.Principal;

public class DeleteDocumentUseCase {

    private final DocumentRepository documents;
    private final AuthorizationGateway authorization;
    private final AuthorizationGuard guard;

    public DeleteDocumentUseCase(DocumentRepository documents, AuthorizationGateway authorization) {
        this.documents = documents;
        this.authorization = authorization;
        this.guard = new AuthorizationGuard(authorization);
    }

    public void execute(Principal principal, long id) {
        Document document = GetDocumentUseCase.loadInTenant(documents, principal, id);
        guard.require(principal, DocumentPermission.DELETE.relation(), document.authzObject());
        documents.delete(id);
        authorization.delete(document.resourceRelationships());
    }
}
