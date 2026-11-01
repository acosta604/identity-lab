package co.com.lab.model.document;

import co.com.lab.model.principal.Relationship;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Documento de una organizacion o de un equipo.
 *
 * @param teamId null si es un documento de toda la organizacion
 */
public record Document(long id, String tenantId, String organizationId, String teamId, String ownerId,
                       String title, String body, Instant createdAt) {

    public String authzObject() {
        return "document:" + id;
    }

    public boolean belongsToTenant(String tenant) {
        return tenantId.equals(tenant);
    }

    /** Relaciones que se guardan en el sistema de autorizacion al crear el documento. */
    public List<Relationship> resourceRelationships() {
        List<Relationship> result = new ArrayList<>();
        if (teamId != null) {
            result.add(new Relationship("team:" + teamId, "team", authzObject()));
        } else {
            result.add(new Relationship("organization:" + organizationId, "organization", authzObject()));
        }
        result.add(new Relationship("user:" + ownerId, "owner", authzObject()));
        return List.copyOf(result);
    }
}
