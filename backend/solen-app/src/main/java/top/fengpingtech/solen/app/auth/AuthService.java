package top.fengpingtech.solen.app.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import top.fengpingtech.solen.app.config.AuthProperties;
import top.fengpingtech.solen.app.domain.DeviceDomain;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.List;

@Component
public class AuthService {
    public static final String ROLE_ADMIN = "ADMIN";

    private final AntMatchService antMatchService;

    private final AuthProperties authProperties;

    public AuthService(AntMatchService antMatchService, AuthProperties authProperties) {
        this.antMatchService = antMatchService;
        this.authProperties = authProperties;
    }

    public boolean canVisit(DeviceDomain conn) {
        Tenant tenant = getTenant();

        if (tenant == null) {
            return false;
        }

        if (tenant.getRoles().contains(ROLE_ADMIN)) {
            return true;
        }

        return tenant.getDevicePatterns() != null
                && antMatchService.antMatch(tenant.getDevicePatterns(), conn.getDeviceId());
    }

    public Tenant getTenant() {
        String principal = SecurityContext.getPrincipal();
        if (principal == null) {
            principal = getPrincipalFromAuthentication();
        }

        if (principal == null) {
            return null;
        }

        String resolvedPrincipal = principal;

        return authProperties.getTenants()
                .stream()
                .filter(t -> t.getAppKey().equalsIgnoreCase(resolvedPrincipal))
                .findFirst()
                .orElse(null);
    }

    public void fillAuthPredicate(Path<String> devicePath, CriteriaBuilder cb, List<Predicate> list) {
        Tenant tenant = getTenant();

        if (tenant == null || tenant.getDevicePatterns() == null || tenant.getDevicePatterns().isEmpty()) {
            list.add(cb.disjunction());
            return;
        }

        Predicate[] patternPredicates = tenant.getDevicePatterns().stream()
                .map(s -> s.replace("**", "%"))
                .map(s -> cb.like(devicePath, s))
                .toArray(javax.persistence.criteria.Predicate[]::new);
        list.add(cb.or(patternPredicates));
    }

    private String getPrincipalFromAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }

        if (principal instanceof String) {
            String value = (String) principal;
            return "anonymousUser".equalsIgnoreCase(value) ? null : value;
        }

        return principal == null ? null : principal.toString();
    }
}
