package co.com.lab.model.document.gateways;

import co.com.lab.model.document.Document;

import java.util.List;
import java.util.Optional;

/** Puerto de salida: persistencia de documentos. */
public interface DocumentRepository {

    Optional<Document> findById(long id);

    List<Document> findByOrganization(String tenantId, String organizationId);

    Document create(NewDocument document);

    void update(long id, String title, String body);

    void delete(long id);

    record NewDocument(String tenantId, String organizationId, String teamId, String ownerId,
                       String title, String body) {
    }
}
