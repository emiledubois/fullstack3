package com.ecommerce.auth.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Genera o valida el X-Correlation-Id de cada petición y lo expone vía MDC
 * ("correlationId") para que aparezca en cada línea de log de la petición
 * (ver observability-correlation-ids.md §5.2). Corre antes que
 * InternalAuthFilter (HIGHEST_PRECEDENCE + 10) para que incluso una petición
 * rechazada quede trazada.
 *
 * Nunca confía en el valor entrante sin validar: un valor ausente,
 * malformado o de más de 64 caracteres se descarta y se genera un UUID
 * nuevo — este valor se logueará textualmente en cada línea, así que es
 * superficie de inyección de logs (CRLF/tamaño) si no se valida (OWASP A09).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String incoming = request.getHeader(HEADER);
        String correlationId = (incoming != null && VALID.matcher(incoming).matches())
                ? incoming
                : UUID.randomUUID().toString();

        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
