package co.com.lab.model.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(long id) {
        super("Documento " + id + " no encontrado");
    }
}
