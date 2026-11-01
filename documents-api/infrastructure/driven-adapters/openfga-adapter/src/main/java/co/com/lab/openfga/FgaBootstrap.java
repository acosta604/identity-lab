package co.com.lab.openfga;

import co.com.lab.model.principal.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Al arrancar: busca el store por nombre (o lo crea), carga el modelo (openfga/model.json) y, si el
 * store es nuevo, escribe las tuplas de estructura y recursos (openfga/seed-tuples.txt).
 * Para empezar de cero: docker compose down -v.
 */
@Component
public class FgaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FgaBootstrap.class);

    private final FgaClient fga;
    private final String storeName;
    private final Path openfgaDir;

    public FgaBootstrap(FgaClient fga,
                        @Value("${lab.fga.store-name:identity-lab}") String storeName,
                        @Value("${lab.fga.files-dir:../openfga}") String filesDir) {
        this.fga = fga;
        this.storeName = storeName;
        this.openfgaDir = Path.of(filesDir);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= 30; attempt++) {
            try {
                bootstrap();
                return;
            } catch (IOException e) {
                throw e; // archivos faltantes: no tiene sentido reintentar
            } catch (Exception e) {
                last = e;
                log.info("OpenFGA aun no responde (intento {}/30): {}", attempt, e.getMessage());
                Thread.sleep(2000);
            }
        }
        throw new IllegalStateException("No se pudo inicializar OpenFGA", last);
    }

    private void bootstrap() throws IOException {
        String storeId = fga.listStores().stream()
                .filter(s -> storeName.equals(s.get("name")))
                .map(s -> (String) s.get("id"))
                .findFirst()
                .orElse(null);
        boolean created = false;
        if (storeId == null) {
            storeId = fga.createStore(storeName);
            created = true;
            log.info("Store de OpenFGA creado: {} ({})", storeName, storeId);
        }
        String modelId = created ? null : fga.latestModel(storeId);
        if (modelId == null) {
            modelId = fga.writeModel(storeId, Files.readString(openfgaDir.resolve("model.json")));
            log.info("Modelo de autorizacion cargado: {}", modelId);
            List<Relationship> seed = readSeed(openfgaDir.resolve("seed-tuples.txt"));
            for (int i = 0; i < seed.size(); i += 50) {
                fga.seed(storeId, modelId, seed.subList(i, Math.min(i + 50, seed.size())));
            }
            log.info("Tuplas semilla escritas: {}", seed.size());
        }
        fga.configure(storeId, modelId);
        log.info("OpenFGA listo: store={} model={}", storeId, modelId);
    }

    static List<Relationship> readSeed(Path file) throws IOException {
        return Files.readAllLines(file).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("\\s+"))
                .map(p -> new Relationship(p[0], p[1], p[2]))
                .toList();
    }
}
