package release_mail_generator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Genera un identificador corto por solicitud, lo expone como cabecera de
 * respuesta {@code X-Request-Id}, lo guarda como atributo de la petición (para
 * que las vistas de error lo muestren) y lo añade al MDC de SLF4J (para que
 * aparezca en cada línea de log mientras se procesa la solicitud).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ATTRIBUTE = "requestId";
    public static final String RESPONSE_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_KEY, requestId);
        request.setAttribute(REQUEST_ATTRIBUTE, requestId);
        response.setHeader(RESPONSE_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
