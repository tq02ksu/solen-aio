package top.fengpingtech.solen.app.auth;

import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

public class SecurityContextFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;

        try {
            String principal = request.getHeader(SecurityContext.HEADER_PRINCIPAL_NAME);
            if (principal != null) {
                SecurityContext.setPrincipal(principal);
            }
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            SecurityContext.clear();
        }
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }
}
