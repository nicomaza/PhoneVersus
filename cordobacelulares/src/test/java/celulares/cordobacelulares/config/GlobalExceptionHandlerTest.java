package celulares.cordobacelulares.config;

import celulares.cordobacelulares.dtos.common.ErrorApi;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsSafeBadGatewayBodyAndCorrelationHeader() {
        MDC.put(TiendaPorteDiagnostics.CORRELATION_ID_KEY, "abc123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tiendaporte/equipos/categorias-principales");
        TiendaPorteIntegrationException exception = new TiendaPorteIntegrationException(
                "No se pudo obtener el catalogo del proveedor externo",
                new IllegalStateException("technical root cause")
        );

        ResponseEntity<ErrorApi> response = handler.handleTiendaPorteIntegrationException(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getHeaders().getFirst(TiendaPorteDiagnostics.CORRELATION_ID_HEADER)).isEqualTo("abc123");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("No se pudo obtener el catalogo del proveedor externo");
        assertThat(response.getBody().getMessage()).doesNotContain("IllegalStateException");
        assertThat(response.getBody().getMessage()).doesNotContain("technical root cause");
    }

    @Test
    void logsFullExceptionForTiendaPorteFailures() {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        MDC.put(TiendaPorteDiagnostics.CORRELATION_ID_KEY, "cid-log");

        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tiendaporte/equipos/categorias-principales");
            TiendaPorteIntegrationException exception = new TiendaPorteIntegrationException(
                    "No se pudo obtener el catalogo del proveedor externo",
                    new IllegalStateException("technical root cause")
            );

            handler.handleTiendaPorteIntegrationException(exception, request);

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("cid=cid-log")
                        .contains("path=/api/tiendaporte/equipos/categorias-principales")
                        .contains("exception=TiendaPorteIntegrationException")
                        .contains("rootCause=IllegalStateException: technical root cause");
                assertThat(event.getThrowableProxy()).isNotNull();
                assertThat(event.getThrowableProxy().getClassName()).isEqualTo(TiendaPorteIntegrationException.class.getName());
                assertThat(event.getThrowableProxy().getCause()).isNotNull();
                assertThat(event.getThrowableProxy().getCause().getClassName()).isEqualTo(IllegalStateException.class.getName());
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
