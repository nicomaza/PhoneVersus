package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCsrfResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteSessionResponse;
import celulares.cordobacelulares.entities.TiendaPorteCredential;
import celulares.cordobacelulares.exceptions.TiendaPorteAuthenticationException;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteTimeoutException;
import celulares.cordobacelulares.repository.TiendaPorteCredentialRepository;
import celulares.cordobacelulares.services.TiendaPorteAuthenticationInvalidator;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TiendaPorteAuthService implements TiendaPorteAuthenticationInvalidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteAuthService.class);
    private static final String AUTH_ERROR_MESSAGE = "No se pudo autenticar con el proveedor externo";
    private static final Duration EXPIRATION_MARGIN = Duration.ofSeconds(60);

    private final TiendaPorteProperties properties;
    private final TiendaPorteCredentialRepository credentialRepository;
    private final TiendaPorteCredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final Object tokenLock = new Object();

    private volatile String accessToken;
    private volatile Instant expiresAt;

    public TiendaPorteAuthService(
            TiendaPorteProperties properties,
            TiendaPorteCredentialRepository credentialRepository,
            TiendaPorteCredentialCipher credentialCipher,
            ObjectMapper objectMapper,
            HttpClient tiendaPorteHttpClient,
            CookieManager tiendaPorteCookieManager
    ) {
        this.properties = properties;
        this.credentialRepository = credentialRepository;
        this.credentialCipher = credentialCipher;
        this.objectMapper = objectMapper;
        this.httpClient = tiendaPorteHttpClient;
        this.cookieManager = tiendaPorteCookieManager;
    }

    public String getAccessToken() {
        String currentToken = accessToken;
        Instant currentExpiration = expiresAt;
        if (isTokenValid(currentToken, currentExpiration)) {
            return currentToken;
        }

        synchronized (tokenLock) {
            currentToken = accessToken;
            currentExpiration = expiresAt;
            if (isTokenValid(currentToken, currentExpiration)) {
                return currentToken;
            }
            authenticate();
            return accessToken;
        }
    }

    public void invalidateAuthentication() {
        synchronized (tokenLock) {
            accessToken = null;
            expiresAt = null;
            cookieManager.getCookieStore().removeAll();
        }
    }

    private void authenticate() {
        TiendaPorteCredential credential = activeCredential();
        String username = credential.getUsername();
        String plainPassword = decryptPassword(credential);
        if (isBlank(username) || isBlank(plainPassword)) {
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }

        LOGGER.info(
                "Iniciando autenticacion con Tienda Porte. cid={} username={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                TiendaPorteDiagnostics.maskUsername(username)
        );
        cookieManager.getCookieStore().removeAll();

        String csrfToken = fetchCsrfToken();
        postCredentials(csrfToken, username, plainPassword);
        TiendaPorteSessionResponse session = fetchSession();

        if (session.getUser() == null || isBlank(session.getUser().getAccessToken())) {
            LOGGER.error("Tienda Porte session sin accessToken. cid={}", TiendaPorteDiagnostics.currentCorrelationId());
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }

        Instant expiration = parseExpiration(session.getExpires());
        accessToken = session.getUser().getAccessToken();
        expiresAt = expiration;
        LOGGER.info(
                "Autenticacion con Tienda Porte completada. cid={} tokenObtenido={} expiresAt={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                true,
                expiration
        );
    }

    private TiendaPorteCredential activeCredential() {
        try {
            return credentialRepository.findFirstByActivaTrueOrderByUpdatedAtDescIdDesc()
                    .orElseThrow(() -> new TiendaPorteAuthenticationException("No existe una credencial activa para Tienda Porte"));
        } catch (TiendaPorteAuthenticationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private String decryptPassword(TiendaPorteCredential credential) {
        try {
            return credentialCipher.decrypt(credential.getEncryptedPassword());
        } catch (RuntimeException ex) {
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private String fetchCsrfToken() {
        HttpRequest request = HttpRequest.newBuilder(buildAuthUri("/api/auth/csrf"))
                .timeout(requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        LOGGER.info("Solicitando CSRF a Tienda Porte. cid={} target={}", TiendaPorteDiagnostics.currentCorrelationId(), TiendaPorteDiagnostics.safeUriHostAndPath(request.uri()));
        HttpResponse<String> response = send(request, "csrf", AUTH_ERROR_MESSAGE);
        if (!isSuccess(response.statusCode())) {
            LOGGER.error(
                    "Tienda Porte rechazo la obtencion de CSRF. cid={} status={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    response.statusCode(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }
        try {
            TiendaPorteCsrfResponse csrfResponse = objectMapper.readValue(response.body(), TiendaPorteCsrfResponse.class);
            if (csrfResponse == null || isBlank(csrfResponse.getCsrfToken())) {
                LOGGER.error("Respuesta CSRF invalida de Tienda Porte. cid={} bodyPreview={}", TiendaPorteDiagnostics.currentCorrelationId(), TiendaPorteDiagnostics.safeBodyPreview(response.body()));
                throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
            }
            LOGGER.info("CSRF recibido desde Tienda Porte. cid={} tokenObtenido={}", TiendaPorteDiagnostics.currentCorrelationId(), true);
            return csrfResponse.getCsrfToken();
        } catch (JsonProcessingException ex) {
            LOGGER.error(
                    "No se pudo parsear CSRF de Tienda Porte. cid={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body()),
                    ex
            );
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private void postCredentials(String csrfToken, String username, String plainPassword) {
        String body = formEncode(loginFields(csrfToken, username, plainPassword));
        HttpRequest request = HttpRequest.newBuilder(buildAuthUri("/api/auth/callback/credentials"))
                .timeout(requestTimeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", normalizedBaseUrl(properties.getAuthBaseUrl()))
                .header("Referer", normalizedBaseUrl(properties.getAuthBaseUrl()) + "/")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        LOGGER.info(
                "Enviando credenciales a Tienda Porte. cid={} username={} target={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                TiendaPorteDiagnostics.maskUsername(username),
                TiendaPorteDiagnostics.safeUriHostAndPath(request.uri())
        );
        HttpResponse<String> response = send(request, "login", AUTH_ERROR_MESSAGE);
        if (!isSuccess(response.statusCode())) {
            LOGGER.error(
                    "Tienda Porte rechazo las credenciales. cid={} status={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    response.statusCode(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }
        LOGGER.info("Credenciales aceptadas por Tienda Porte. cid={} status={}", TiendaPorteDiagnostics.currentCorrelationId(), response.statusCode());
    }

    private TiendaPorteSessionResponse fetchSession() {
        HttpRequest request = HttpRequest.newBuilder(buildAuthUri("/api/auth/session"))
                .timeout(requestTimeout())
                .header("Accept", "application/json")
                .GET()
                .build();
        LOGGER.info("Consultando sesion de Tienda Porte. cid={} target={}", TiendaPorteDiagnostics.currentCorrelationId(), TiendaPorteDiagnostics.safeUriHostAndPath(request.uri()));
        HttpResponse<String> response = send(request, "session", AUTH_ERROR_MESSAGE);
        if (!isSuccess(response.statusCode())) {
            LOGGER.error(
                    "Tienda Porte rechazo la consulta de sesion. cid={} status={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    response.statusCode(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }
        try {
            return objectMapper.readValue(response.body(), TiendaPorteSessionResponse.class);
        } catch (JsonProcessingException ex) {
            LOGGER.error(
                    "No se pudo parsear sesion de Tienda Porte. cid={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body()),
                    ex
            );
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private HttpResponse<String> send(HttpRequest request, String operation, String errorMessage) {
        long startNanos = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            LOGGER.info(
                    "Tienda Porte auth response. cid={} operation={} status={} contentType={} durationMs={} bodySize={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    operation,
                    response.statusCode(),
                    response.headers().firstValue("Content-Type").orElse("-"),
                    durationMs,
                    TiendaPorteDiagnostics.bodySize(response.body())
            );
            return response;
        } catch (HttpTimeoutException ex) {
            throw new TiendaPorteTimeoutException("El proveedor externo no respondio dentro del tiempo permitido", ex);
        } catch (IOException ex) {
            throw new TiendaPorteIntegrationException(errorMessage, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TiendaPorteIntegrationException(errorMessage, ex);
        }
    }

    private Map<String, String> loginFields(String csrfToken, String username, String plainPassword) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("redirect", "false");
        fields.put("username", username);
        fields.put("password", plainPassword);
        fields.put("code", "");
        fields.put("callbackUrl", "/");
        fields.put("csrfToken", csrfToken);
        fields.put("json", "true");
        return fields;
    }

    private String formEncode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private URI buildAuthUri(String path) {
        try {
            return URI.create(normalizedBaseUrl(properties.getAuthBaseUrl()) + path);
        } catch (IllegalArgumentException ex) {
            throw new TiendaPorteIntegrationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private String normalizedBaseUrl(String baseUrl) {
        if (isBlank(baseUrl)) {
            throw new TiendaPorteIntegrationException(AUTH_ERROR_MESSAGE);
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds()));
    }

    private Instant parseExpiration(String value) {
        if (isBlank(value)) {
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE);
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            LOGGER.error("No se pudo parsear expiracion de sesion de Tienda Porte. cid={} expires={}", TiendaPorteDiagnostics.currentCorrelationId(), value, ex);
            throw new TiendaPorteAuthenticationException(AUTH_ERROR_MESSAGE, ex);
        }
    }

    private boolean isTokenValid(String token, Instant expiration) {
        return !isBlank(token)
                && expiration != null
                && Instant.now().plus(EXPIRATION_MARGIN).isBefore(expiration);
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
