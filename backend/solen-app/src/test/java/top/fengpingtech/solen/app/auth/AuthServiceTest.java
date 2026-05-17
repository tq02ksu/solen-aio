package top.fengpingtech.solen.app.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import top.fengpingtech.solen.app.config.AuthProperties;
import top.fengpingtech.solen.app.domain.DeviceDomain;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {

    private final AuthService authService = new AuthService(new AntMatchService(), authProperties());

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldResolveTenantFromSpringSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", null, Collections.emptyList())
        );

        Tenant tenant = authService.getTenant();

        assertNotNull(tenant);
        assertEquals("test", tenant.getAppKey());
    }

    @Test
    void shouldDenyVisitWhenTenantMissing() {
        DeviceDomain device = DeviceDomain.builder().deviceId("40623120049").build();

        assertFalse(authService.canVisit(device));
        assertNull(authService.getTenant());
    }

    @Test
    void shouldAllowVisitWhenTenantPatternMatches() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("test", null, Collections.emptyList())
        );
        DeviceDomain device = DeviceDomain.builder().deviceId("40623120049").build();

        assertTrue(authService.canVisit(device));
    }

    @Test
    void shouldReturnNullForUnknownAuthenticatedTenant() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("missing-tenant", null, Collections.emptyList())
        );

        assertNull(authService.getTenant());
    }

    private static AuthProperties authProperties() {
        Tenant tenant = Tenant.builder()
                .appKey("test")
                .secretKey("test")
                .roles(Collections.singletonList("USER"))
                .devicePatterns(Collections.singletonList("406**"))
                .build();

        AuthProperties authProperties = new AuthProperties();
        authProperties.setTenants(Collections.singletonList(tenant));
        return authProperties;
    }
}

