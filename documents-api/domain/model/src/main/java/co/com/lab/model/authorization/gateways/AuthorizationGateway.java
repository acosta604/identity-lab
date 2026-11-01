package co.com.lab.model.authorization.gateways;

import co.com.lab.model.principal.Principal;
import co.com.lab.model.principal.Relationship;

import java.util.List;

/**
 * Puerto de salida: sistema de autorizacion (OpenFGA en infraestructura).
 * La implementacion envia {@link Principal#membershipRelationships()} como tuplas contextuales.
 */
public interface AuthorizationGateway {

    boolean isAllowed(Principal principal, String relation, String object);

    /** Guarda relaciones de recursos (estructura, duenos). Nunca membresias de personas. */
    void write(List<Relationship> relationships);

    void delete(List<Relationship> relationships);
}
