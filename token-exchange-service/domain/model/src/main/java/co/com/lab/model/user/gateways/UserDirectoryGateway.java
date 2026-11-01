package co.com.lab.model.user.gateways;

import co.com.lab.model.accesscontext.AccessContext;
import co.com.lab.model.accesscontext.OrganizationSummary;
import co.com.lab.model.user.User;

import java.util.List;
import java.util.Optional;

/** Usuarios internos, vinculos con identidades externas y membresias. */
public interface UserDirectoryGateway {

    /** (emisor, oid de Entra) -> id del usuario interno activo. */
    Optional<String> findLinkedUser(String issuer, String externalSubject);

    Optional<User> findActiveUser(String userId);

    /**
     * Contexto activo. Si organizationId es null toma la primera organizacion de la persona
     * (dentro del tenant, si se indica). Vacio = no es miembro activo.
     */
    Optional<AccessContext> resolveContext(String userId, String tenantId, String organizationId);

    List<OrganizationSummary> organizationsOf(String userId);
}
