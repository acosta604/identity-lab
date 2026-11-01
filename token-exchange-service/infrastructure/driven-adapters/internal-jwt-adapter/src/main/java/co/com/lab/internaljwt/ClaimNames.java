package co.com.lab.internaljwt;

/**
 * Nombres de los claims propios. Es el unico lugar donde se definen en este servicio; cuando el equipo
 * acuerde los definitivos se cambian aqui y en documents-api (JwtPrincipalMapper).
 */
public final class ClaimNames {

    /** {name, email} para mostrar y auditar. */
    public static final String USER = "usr";
    /** Tenant activo. */
    public static final String TENANT = "tid";
    /** Organizacion activa: {id, role}. */
    public static final String ORGANIZATION = "org";
    /** Equipos de la organizacion activa: [{id, role}]. */
    public static final String TEAMS = "teams";
    /** Roles a nivel tenant. */
    public static final String ROLES = "roles";
    /** Emisor que autentico a la persona (Entra / mock). */
    public static final String IDP = "idp";
    /** Sesion propia. */
    public static final String SESSION_ID = "sid";
    /** Siempre "access". */
    public static final String TOKEN_USE = "token_use";
    /** Version del esquema de claims. */
    public static final String CLAIMS_VERSION = "cv";
    /** Actor en servicio a servicio (RFC 8693). */
    public static final String ACTOR = "act";

    public static final int CURRENT_VERSION = 1;

    private ClaimNames() {
    }
}
