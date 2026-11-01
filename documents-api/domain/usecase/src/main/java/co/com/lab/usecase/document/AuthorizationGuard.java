package co.com.lab.usecase.document;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.exception.AccessDeniedException;
import co.com.lab.model.principal.Principal;

/** Helper de los casos de uso: exige una relacion o lanza AccessDeniedException. */
final class AuthorizationGuard {

    private final AuthorizationGateway authorization;

    AuthorizationGuard(AuthorizationGateway authorization) {
        this.authorization = authorization;
    }

    public boolean allows(Principal principal, String relation, String object) {
        return authorization.isAllowed(principal, relation, object);
    }

    public void require(Principal principal, String relation, String object) {
        if (!authorization.isAllowed(principal, relation, object)) {
            throw new AccessDeniedException(principal.userRef(), relation, object);
        }
    }
}
