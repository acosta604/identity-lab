package co.com.lab.model.exception;

/** Los claims no forman un contexto valido (falta un dato o trae un rol fuera del vocabulario). */
public class InvalidPrincipalException extends RuntimeException {

    public InvalidPrincipalException(String message) {
        super(message);
    }
}
