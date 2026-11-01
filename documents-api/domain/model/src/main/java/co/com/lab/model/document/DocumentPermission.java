package co.com.lab.model.document;

/** Acciones sobre un documento y la relacion del modelo de autorizacion que las concede. */
public enum DocumentPermission {
    VIEW("viewer"),
    EDIT("editor"),
    DELETE("can_delete");

    private final String relation;

    DocumentPermission(String relation) {
        this.relation = relation;
    }

    public String relation() {
        return relation;
    }
}
