package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.entities.TiendaPorteCredential;
import celulares.cordobacelulares.exceptions.TiendaPorteAuthenticationException;
import celulares.cordobacelulares.repository.TiendaPorteCredentialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TiendaPorteAuthServiceDiagnosticsTest {

    private final TiendaPorteCredentialRepository repository = mock(TiendaPorteCredentialRepository.class);
    private final TiendaPorteCredentialCipher cipher = mock(TiendaPorteCredentialCipher.class);
    private final StubHttpClient httpClient = new StubHttpClient();
    private final TiendaPorteAuthService authService = new TiendaPorteAuthService(
            properties(),
            repository,
            cipher,
            new ObjectMapper(),
            httpClient,
            new CookieManager()
    );

    @Test
    void failsWhenActiveCredentialDoesNotExist() {
        when(repository.findFirstByActivaTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.empty());

        assertThatThrownBy(authService::getAccessToken)
                .isInstanceOf(TiendaPorteAuthenticationException.class)
                .hasMessageContaining("No existe una credencial activa");
    }

    @Test
    void preservesCauseWhenPasswordCannotBeDecrypted() {
        TiendaPorteCredential credential = credential();
        when(repository.findFirstByActivaTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(credential));
        when(cipher.decrypt("enc")).thenThrow(new IllegalStateException("bad cipher"));

        assertThatThrownBy(authService::getAccessToken)
                .isInstanceOf(TiendaPorteAuthenticationException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsWhenCsrfResponseDoesNotContainToken() {
        validCredential();
        httpClient.enqueueJson(200, "{}");

        assertThatThrownBy(authService::getAccessToken)
                .isInstanceOf(TiendaPorteAuthenticationException.class);
    }

    @Test
    void failsWhenSessionDoesNotContainAccessToken() {
        validCredential();
        httpClient.enqueueJson(200, "{\"csrfToken\":\"csrf\"}");
        httpClient.enqueueJson(200, "{}");
        httpClient.enqueueJson(200, "{\"user\":{},\"expires\":\"2030-01-01T00:00:00Z\"}");

        assertThatThrownBy(authService::getAccessToken)
                .isInstanceOf(TiendaPorteAuthenticationException.class);
    }

    @Test
    void preservesCauseWhenSessionExpirationIsInvalid() {
        validCredential();
        httpClient.enqueueJson(200, "{\"csrfToken\":\"csrf\"}");
        httpClient.enqueueJson(200, "{}");
        httpClient.enqueueJson(200, "{\"user\":{\"accessToken\":\"token\"},\"expires\":\"invalid\"}");

        assertThatThrownBy(authService::getAccessToken)
                .isInstanceOf(TiendaPorteAuthenticationException.class)
                .hasRootCauseInstanceOf(RuntimeException.class);
    }

    private void validCredential() {
        TiendaPorteCredential credential = credential();
        when(repository.findFirstByActivaTrueOrderByUpdatedAtDescIdDesc()).thenReturn(Optional.of(credential));
        when(cipher.decrypt("enc")).thenReturn("password");
    }

    private TiendaPorteCredential credential() {
        TiendaPorteCredential credential = new TiendaPorteCredential();
        credential.setUsername("usuario@example.test");
        credential.setEncryptedPassword("enc");
        credential.setActiva(true);
        return credential;
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

    private static class StubHttpClient extends HttpClient {

        private final ArrayDeque<HttpResponse<String>> responses = new ArrayDeque<>();

        private void enqueueJson(int status, String body) {
            responses.add(new StubHttpResponse(status, body));
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responses.removeFirst();
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
            return Redirect.NORMAL;
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

    private record StubHttpResponse(int statusCode, String body) implements HttpResponse<String> {

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
            return HttpHeaders.of(Map.of("Content-Type", List.of("application/json")), (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://auth.example.test/api/auth/session");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
