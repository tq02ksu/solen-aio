package top.fengpingtech.solen.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.lang.NonNull;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityContextFilterTest {

    private final SecurityContextFilter filter = new SecurityContextFilter();

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void shouldExposePrincipalDuringFilterChainAndClearAfterwards() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization-Principal", "test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> principalInChain = new AtomicReference<>();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(
                    @NonNull javax.servlet.ServletRequest request, @NonNull javax.servlet.ServletResponse response) {
                principalInChain.set(SecurityContext.getPrincipal());
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals("test", principalInChain.get());
        assertNull(SecurityContext.getPrincipal());
    }
}
