package co.com.lab.model.principal;

import java.util.Objects;

/** Relacion "user tiene relation sobre object" (una tupla en el lenguaje de OpenFGA). */
public record Relationship(String user, String relation, String object) {

    public Relationship {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(object, "object");
    }

    @Override
    public String toString() {
        return user + " " + relation + " " + object;
    }
}
