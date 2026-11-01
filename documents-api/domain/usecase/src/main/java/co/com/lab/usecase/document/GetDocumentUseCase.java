package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.exception.DocumentNotFoundException;
import co.com.lab.model.document.Document;
import co.com.lab.model.document.DocumentPermission;
import co.com.lab.model.principal.Principal;

/**
 * Lee un documento. Otro tenant = 404 (no se revela que existe). Dentro del tenant, decide OpenFGA,
 * aunque sea de otra organizacion: asi se ve que el 403 viene de que la tupla no llego en el token.
 */
public class GetDocumentUseCase {

    private final DocumentRepository documents;
    private final AuthorizationGuard guard;

    public GetDocumentUseCase(DocumentRepository documents, AuthorizationGateway authorization) {
        this.documents = documents;
        this.guard = new AuthorizationGuard(authorization);
    }

    public Document execute(Principal principal, long id) {
        Document document = loadInTenant(documents, principal, id);
        guard.require(principal, DocumentPermission.VIEW.relation(), document.authzObject());
        return document;
    }

    static Document loadInTenant(DocumentRepository documents, Principal principal, long id) {
        return documents.findById(id)
                .filter(d -> d.belongsToTenant(principal.tenant()))
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }
}
