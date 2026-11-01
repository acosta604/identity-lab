package co.com.lab.model.user;

/** Usuario interno. Su id es el "sub" del token propio (nunca el oid de Entra). */
public record User(String id, String name, String email) {
}
