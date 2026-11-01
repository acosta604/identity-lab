package co.com.lab.model.exception;

/** El sistema de autorizacion nego la relacion pedida. */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String user, String relation, String object) {
        super(user + " no tiene " + relation + " sobre " + object);
    }
}
