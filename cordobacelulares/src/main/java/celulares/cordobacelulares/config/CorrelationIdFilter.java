package celulares.cordobacelulares.config;

import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = TiendaPorteDiagnostics.sanitizeCorrelationId(request.getHeader(TiendaPorteDiagnostics.CORRELATION_ID_HEADER));
        String correlationId = incoming == null ? TiendaPorteDiagnostics.newCorrelationId() : incoming;
        long startNanos = System.nanoTime();
        MDC.put(TiendaPorteDiagnostics.CORRELATION_ID_KEY, correlationId);
        response.setHeader(TiendaPorteDiagnostics.CORRELATION_ID_HEADER, correlationId);
        try {
            LOGGER.info(
                    "HTTP request started cid={} method={} path={}",
                    correlationId,
                    request.getMethod(),
                    request.getRequestURI()
            );
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            LOGGER.info(
                    "HTTP request finished cid={} method={} path={} status={} durationMs={}",
                    correlationId,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs
            );
            MDC.remove(TiendaPorteDiagnostics.CORRELATION_ID_KEY);
        }
    }
}
