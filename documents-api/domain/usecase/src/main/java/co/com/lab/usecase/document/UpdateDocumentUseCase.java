package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.document.gateways.DocumentRepository;
import co.com.lab.model.document.Document;
import co.com.lab.model.document.DocumentPermission;
import co.com.lab.model.principal.Principal;

public class UpdateDocumentUseCase {

    private final DocumentRepository documents;
    private final AuthorizationGuard guard;

    public UpdateDocumentUseCase(DocumentRepository documents, AuthorizationGateway authorization) {
        this.documents = documents;
        this.guard = new AuthorizationGuard(authorization);
    }

    public record Command(String title, String body) {
    }

    public Document execute(Principal principal, long id, Command command) {
        Document document = GetDocumentUseCase.loadInTenant(documents, principal, id);
        guard.require(principal, DocumentPermission.EDIT.relation(), document.authzObject());
        String title = command.title() == null || command.title().isBlank() ? document.title() : command.title();
        String body = command.body() == null ? document.body() : command.body();
        documents.update(id, title, body);
        return documents.findById(id).orElseThrow();
    }
}
