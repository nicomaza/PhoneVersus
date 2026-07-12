package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductsResponse;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteRateLimitException;
import celulares.cordobacelulares.exceptions.TiendaPorteTimeoutException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.ConnectException;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TiendaPorteClientDiagnosticsTest {

    private final TiendaPorteAuthService authService = mock(TiendaPorteAuthService.class);
    private final StubHttpClient httpClient = new StubHttpClient();
    private final TiendaPorteClient client = new TiendaPorteClient(properties(), authService, new ObjectMapper(), httpClient);

    @Test
    void returnsProductsWhenProviderRespondsWithValidCatalog() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(200, productsJson(1, 1));

        List<TiendaPorteExternalProduct> products = client.getProductsByCategory("REALME", 50);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getName()).isEqualTo("REALME C75X");
    }

    @Test
    void treatsEmptyDataWithZeroTotalPagesAsValidEmptyCatalog() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(200, emptyCatalogJson(0));

        List<TiendaPorteExternalProduct> products = client.getProductsByCategory("REALME", 50);

        assertThat(products).isEmpty();
    }

    @Test
    void retriesOnceWhenProviderRespondsUnauthorizedAndSecondAttemptWorks() {
        when(authService.getAccessToken()).thenReturn("old-token", "new-token");
        httpClient.enqueueJson(401, "{\"message\":\"unauthorized\"}");
        httpClient.enqueueJson(200, emptyCatalogJson(1));

        TiendaPorteProductsResponse response = client.getProductsByCategoryPage("REALME", 1, 50);

        assertThat(response.getData()).isEmpty();
        verify(authService).invalidateAuthentication();
    }

    @Test
    void failsWhenProviderRespondsUnauthorizedTwice() {
        when(authService.getAccessToken()).thenReturn("old-token", "new-token");
        httpClient.enqueueJson(401, "{\"message\":\"unauthorized\"}");
        httpClient.enqueueJson(401, "{\"message\":\"unauthorized\"}");

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteIntegrationException.class)
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void failsWhenProviderRespondsRateLimited() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(429, "{\"message\":\"too many requests\"}");

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteRateLimitException.class);
    }

    @Test
    void parsesRetryAfterSecondsWhenProviderRespondsRateLimited() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(429, "{\"message\":\"too many requests\"}", Map.of("Retry-After", List.of("120")));
        Instant before = Instant.now().plusSeconds(119);

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteRateLimitException.class)
                .satisfies(exception -> {
                    TiendaPorteRateLimitException rateLimit = (TiendaPorteRateLimitException) exception;
                    assertThat(rateLimit.getRetryAfter()).isAfterOrEqualTo(before);
                    assertThat(rateLimit.getNextAllowedRefreshAt()).isEqualTo(rateLimit.getRetryAfter());
                });
    }

    @Test
    void parsesRetryAfterHttpDateWhenProviderRespondsRateLimited() {
        when(authService.getAccessToken()).thenReturn("token");
        Instant retryAt = Instant.parse("2026-07-11T14:10:00Z");
        String retryAfter = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(retryAt, ZoneOffset.UTC));
        httpClient.enqueueJson(429, "{\"message\":\"too many requests\"}", Map.of("Retry-After", List.of(retryAfter)));

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteRateLimitException.class)
                .satisfies(exception -> {
                    TiendaPorteRateLimitException rateLimit = (TiendaPorteRateLimitException) exception;
                    assertThat(rateLimit.getRetryAfter()).isEqualTo(retryAt);
                    assertThat(rateLimit.getNextAllowedRefreshAt()).isEqualTo(retryAt);
                });
    }

    @Test
    void usesDefaultCooldownWhenRateLimitHasNoRetryAfter() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(429, "{\"message\":\"too many requests\"}");
        Instant before = Instant.now().plusSeconds(179);

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteRateLimitException.class)
                .satisfies(exception -> {
                    TiendaPorteRateLimitException rateLimit = (TiendaPorteRateLimitException) exception;
                    assertThat(rateLimit.getRetryAfter()).isNull();
                    assertThat(rateLimit.getNextAllowedRefreshAt()).isAfterOrEqualTo(before);
                });
    }

    @Test
    void failsWhenProviderRespondsServerError() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(500, "{\"message\":\"server error\"}");

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteIntegrationException.class);
    }

    @Test
    void preservesJacksonCauseWhenProviderReturnsInvalidJson() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(200, "{");

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteIntegrationException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void failsWhenProviderReturnsHtmlInsteadOfJson() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueue(200, "text/html", "<html><body>Bad Gateway</body></html>");

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteIntegrationException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void mapsHttpTimeoutToGatewayTimeoutException() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueException(new HttpTimeoutException("timeout"));

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteTimeoutException.class);
    }

    @Test
    void preservesConnectionFailureCause() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueException(new ConnectException("connection refused"));

        assertThatThrownBy(() -> client.getProductsByCategoryPage("REALME", 1, 50))
                .isInstanceOf(TiendaPorteIntegrationException.class)
                .hasRootCauseInstanceOf(ConnectException.class);
    }

    @Test
    void encodesCategorySpacesAsPercent20() {
        when(authService.getAccessToken()).thenReturn("token");
        httpClient.enqueueJson(200, emptyCatalogJson(1));

        client.getProductsByCategoryPage("PRODUCTOS APPLE", 1, 50);

        String uri = httpClient.requests().get(0).uri().toString();
        assertThat(uri).contains("category=PRODUCTOS%20APPLE");
        assertThat(uri).doesNotContain("PRODUCTOS+APPLE");
    }

    private TiendaPorteProperties properties() {
        TiendaPorteProperties properties = new TiendaPorteProperties();
        properties.setApiBaseUrl("https://api.example.test");
        properties.setAuthBaseUrl("https://auth.example.test");
        properties.setProductsLimit(50);
        properties.setConnectTimeoutSeconds(1);
        properties.setRequestTimeoutSeconds(1);
        return properties;
    }

    private String productsJson(int total, int totalPages) {
        return """
                {
                  "data": [
                    {
                      "name": "REALME C75X",
                      "productReference": {
                        "id": 1,
                        "name": "REALME C75X",
                        "priceUsd": "100",
                        "Category": { "name": "REALME" }
                      },
                      "Category": { "name": "REALME" },
                      "colorStock": { "Black": 1 }
                    }
                  ],
                  "meta": { "total": %d, "totalPages": %d }
                }
                """.formatted(total, totalPages);
    }

    private String emptyCatalogJson(int totalPages) {
        return """
                {
                  "data": [],
                  "meta": { "total": 0, "totalPages": %d }
                }
                """.formatted(totalPages);
    }

    private static class StubHttpClient extends HttpClient {

        private final ArrayDeque<Object> responses = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        private void enqueueJson(int status, String body) {
            enqueue(status, "application/json", body);
        }

        private void enqueueJson(int status, String body, Map<String, List<String>> headers) {
            enqueue(status, "application/json", body, headers);
        }

        private void enqueue(int status, String contentType, String body) {
            enqueue(status, contentType, body, Map.of());
        }

        private void enqueue(int status, String contentType, String body, Map<String, List<String>> headers) {
            responses.add(new StubHttpResponse(status, contentType, body, headers));
        }

        private void enqueueException(IOException exception) {
            responses.add(exception);
        }

        private List<HttpRequest> requests() {
            return requests;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            requests.add(request);
            Object next = responses.removeFirst();
            if (next instanceof IOException ioException) {
                throw ioException;
            }
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) next;
            return response;
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException();
        }
    }

    private record StubHttpResponse(
            int statusCode,
            String contentType,
            String body,
            Map<String, List<String>> extraHeaders
    ) implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            Map<String, List<String>> headers = new java.util.LinkedHashMap<>(extraHeaders);
            headers.put("Content-Type", List.of(contentType));
            return HttpHeaders.of(headers, (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://api.example.test/user/products");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
