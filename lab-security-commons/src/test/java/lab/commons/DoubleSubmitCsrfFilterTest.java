package lab.commons;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DoubleSubmitCsrfFilterTest {

    private final DoubleSubmitCsrfFilter filter = new DoubleSubmitCsrfFilter(Set.of("__Host-sid", "__Host-at"));

    @Test
    void permitePostSinCookiesDeCredenciales() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/documents");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void rechazaPostConCookieYSinHeader() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/documents");
        req.setCookies(new Cookie("__Host-at", "jwt"), new Cookie(CookieNames.XSRF, "abc"));
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void permitePostConHeaderIgualALaCookie() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/documents");
        req.setCookies(new Cookie("__Host-at", "jwt"), new Cookie(CookieNames.XSRF, "abc"));
        req.addHeader(CookieNames.XSRF_HEADER, "abc");
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
    }

    @Test
    void permiteGetSiempre() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/documents");
        req.setCookies(new Cookie("__Host-at", "jwt"));
        var res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        assertThat(res.getStatus()).isEqualTo(200);
    }
}
