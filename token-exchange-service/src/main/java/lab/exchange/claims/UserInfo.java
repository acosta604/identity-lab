package lab.exchange.claims;

/** Usuario interno: su id es el "sub" del token propio. */
public record UserInfo(String id, String name, String email) {
}
