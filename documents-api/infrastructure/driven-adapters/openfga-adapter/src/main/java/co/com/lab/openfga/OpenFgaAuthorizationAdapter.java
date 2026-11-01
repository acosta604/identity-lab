package co.com.lab.openfga;

import co.com.lab.model.authorization.gateways.AuthorizationGateway;
import co.com.lab.model.principal.Principal;
import co.com.lab.model.principal.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Implementa la autorizacion con OpenFGA. Las membresias de la persona (del token) viajan como
 * tuplas contextuales en cada Check; en OpenFGA solo se guardan estructura y recursos.
 */
@Component
public class OpenFgaAuthorizationAdapter implements AuthorizationGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenFgaAuthorizationAdapter.class);

    private final FgaClient fga;

    public OpenFgaAuthorizationAdapter(FgaClient fga) {
        this.fga = fga;
    }

    @Override
    public boolean isAllowed(Principal principal, String relation, String object) {
        if (!fga.ready()) {
            throw new IllegalStateException("OpenFGA aun no esta listo");
        }
        Relationship question = new Relationship(principal.userRef(), relation, object);
        List<Relationship> contextual = principal.membershipRelationships();
        boolean allowed = fga.check(question, contextual);
        log.info("Check {} -> {} | tuplas contextuales: {}", question, allowed ? "PERMITIDO" : "DENEGADO", contextual);
        return allowed;
    }

    @Override
    public void write(List<Relationship> relationships) {
        fga.write(relationships);
    }

    @Override
    public void delete(List<Relationship> relationships) {
        fga.delete(relationships);
    }
}
