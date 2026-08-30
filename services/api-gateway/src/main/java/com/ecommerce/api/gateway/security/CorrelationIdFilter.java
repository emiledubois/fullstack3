package com.ecommerce.api.gateway.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Genera o valida el X-Correlation-Id de cada petición y lo expone vía MDC
 * ("correlationId") para que aparezca en cada línea de log de la petición,
 * en el propio gateway y — al envolver la petición — en cualquier
 * microservicio/loopback proxeado por HandlerFunctions.http (ver
 * observability-correlation-ids.md §5.2). Es el primer Filter servlet
 * global de api-gateway (AuthFilter/InternalTokenIssuerFilter son
 * HandlerFilterFunction por-ruta, no jakarta.servlet.Filter), así que Spring
 * Boot lo registra automáticamente para toda ruta, incluida
 * webhookFlowRoute (sin authFilter/internalTokenIssuerFilter).
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
            chain.doFilter(new CorrelationIdRequestWrapper(request, correlationId), response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Hace que getHeader/getHeaders/getHeaderNames devuelvan el valor
     * canónico (validado o generado) en vez del valor crudo del cliente,
     * para que RouterFunction/HandlerFunctions.http (que construyen su
     * ServerRequest a partir de este mismo HttpServletRequest) lo reenvíen
     * al destino proxeado sin cambios en GatewayConfig.
     */
    private static class CorrelationIdRequestWrapper extends HttpServletRequestWrapper {

        private final String correlationId;

        CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            if (HEADER.equalsIgnoreCase(name)) {
                return correlationId;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (HEADER.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(correlationId));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(HEADER::equalsIgnoreCase)) {
                names.add(HEADER);
            }
            return Collections.enumeration(names);
        }
    }
}
