package lab.commons;

/**
 * Nombres de las cookies del laboratorio.
 * Con {@code secure=true} se usa el prefijo {@code __Host-}, que obliga a Secure, Path=/ y sin Domain.
 * Los navegadores aceptan cookies Secure en http://localhost, asi que funciona en local.
 */
public final class CookieNames {

    public static final String XSRF = "XSRF-TOKEN";
    public static final String XSRF_HEADER = "X-XSRF-TOKEN";

    private CookieNames() {
    }

    /** Sesion propia del servicio de exchange (secreto opaco). */
    public static String session(boolean secure) {
        return secure ? "__Host-sid" : "sid";
    }

    /** JWT propio con los claims de usuario, organizacion, equipos y rol. */
    public static String access(boolean secure) {
        return secure ? "__Host-at" : "at";
    }
}
