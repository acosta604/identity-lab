package co.com.lab.openfga;

import co.com.lab.model.principal.Relationship;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente minimo de la API HTTP de OpenFGA. Se usa la API directa (en vez del SDK) para que se vea
 * exactamente el JSON que viaja, incluidas las tuplas contextuales.
 */
@Component
public class FgaClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private volatile String storeId;
    private volatile String modelId;

    public FgaClient(@Value("${lab.fga.api-url}") String apiUrl) {
        this.rest = RestClient.create(apiUrl);
    }

    void configure(String storeId, String modelId) {
        this.storeId = storeId;
        this.modelId = modelId;
    }

    boolean ready() {
        return storeId != null && modelId != null;
    }

    boolean check(Relationship question, List<Relationship> contextual) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tuple_key", tuple(question));
        body.put("contextual_tuples", Map.of("tuple_keys", contextual.stream().map(FgaClient::tuple).toList()));
        body.put("authorization_model_id", modelId);
        return Boolean.TRUE.equals(post("/stores/" + storeId + "/check", body).get("allowed"));
    }

    void write(List<Relationship> writes) {
        change("writes", storeId, modelId, writes);
    }

    void delete(List<Relationship> deletes) {
        change("deletes", storeId, modelId, deletes);
    }

    // ---- Administracion: solo la usa FgaBootstrap ----

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> listStores() {
        return (List<Map<String, Object>>) get("/stores?page_size=100").getOrDefault("stores", List.of());
    }

    String createStore(String name) {
        return (String) post("/stores", Map.of("name", name)).get("id");
    }

    /** El modelo se envia tal cual (JSON del archivo), sin reinterpretarlo. */
    String writeModel(String storeId, String modelJson) {
        Map<String, Object> response = rest.post().uri("/stores/" + storeId + "/authorization-models")
                .contentType(MediaType.APPLICATION_JSON)
                .body(modelJson)
                .retrieve()
                .body(JSON_MAP);
        return response == null ? null : (String) response.get("authorization_model_id");
    }

    @SuppressWarnings("unchecked")
    String latestModel(String storeId) {
        List<Map<String, Object>> models = (List<Map<String, Object>>)
                get("/stores/" + storeId + "/authorization-models?page_size=1").getOrDefault("authorization_models", List.of());
        return models.isEmpty() ? null : (String) models.getFirst().get("id");
    }

    void seed(String storeId, String modelId, List<Relationship> tuples) {
        change("writes", storeId, modelId, tuples);
    }

    private void change(String kind, String store, String model, List<Relationship> tuples) {
        if (tuples.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(kind, Map.of("tuple_keys", tuples.stream().map(FgaClient::tuple).toList()));
        body.put("authorization_model_id", model);
        post("/stores/" + store + "/write", body);
    }

    private static Map<String, String> tuple(Relationship r) {
        return Map.of("user", r.user(), "relation", r.relation(), "object", r.object());
    }

    private Map<String, Object> post(String path, Object body) {
        Map<String, Object> response = rest.post().uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JSON_MAP);
        return response == null ? Map.of() : response;
    }

    private Map<String, Object> get(String path) {
        Map<String, Object> response = rest.get().uri(path).retrieve().body(JSON_MAP);
        return response == null ? Map.of() : response;
    }
}
