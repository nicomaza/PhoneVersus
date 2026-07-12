package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductsResponse;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteRateLimitException;
import celulares.cordobacelulares.exceptions.TiendaPorteTimeoutException;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class TiendaPorteClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteClient.class);
    private static final String PRODUCTS_ERROR_MESSAGE = "No se pudo obtener el catalogo del proveedor externo";
    private static final String PRODUCTS_SORT = "price_asc";
    private static final int MAX_EXTERNAL_PAGES = 100;

    private final TiendaPorteProperties properties;
    private final TiendaPorteAuthService authService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public TiendaPorteClient(
            TiendaPorteProperties properties,
            TiendaPorteAuthService authService,
            ObjectMapper objectMapper,
            HttpClient tiendaPorteHttpClient
    ) {
        this.properties = properties;
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.httpClient = tiendaPorteHttpClient;
    }

    public List<TiendaPorteExternalProduct> getProducts() {
        return executeWithSingleRetry(this::fetchAllPages);
    }

    public TiendaPorteProductsResponse getProductsByCategoryPage(String category, int page, int limit) {
        return executeWithSingleRetry(token -> fetchPage(page, limit, token, category));
    }

    public List<TiendaPorteExternalProduct> getProductsByCategory(String category, int limit) {
        return executeWithSingleRetry(token -> fetchAllPagesByCategory(token, category, limit));
    }

    private List<TiendaPorteExternalProduct> fetchAllPages(String token) {
        int limit = Math.max(1, properties.getProductsLimit());
        TiendaPorteProductsResponse firstPage = fetchPage(1, limit, token, null);
        int totalPages = validateTotalPages(firstPage);
        if (totalPages > MAX_EXTERNAL_PAGES) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }

        List<TiendaPorteExternalProduct> products = new ArrayList<>(firstPage.getData());
        for (int page = 2; page <= totalPages; page++) {
            TiendaPorteProductsResponse response = fetchPage(page, limit, token, null);
            products.addAll(response.getData());
        }
        return products;
    }

    private List<TiendaPorteExternalProduct> fetchAllPagesByCategory(String token, String category, int requestedLimit) {
        int limit = Math.max(1, requestedLimit);
        TiendaPorteProductsResponse firstPage = fetchPage(1, limit, token, category);
        int totalPages = validateTotalPages(firstPage);
        if (totalPages > MAX_EXTERNAL_PAGES) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }

        List<TiendaPorteExternalProduct> products = new ArrayList<>(firstPage.getData());
        for (int page = 2; page <= totalPages; page++) {
            TiendaPorteProductsResponse response = fetchPage(page, limit, token, category);
            products.addAll(response.getData());
        }
        return products;
    }

    private TiendaPorteProductsResponse fetchPage(int page, int limit, String token, String category) {
        URI uri = buildProductsUri(page, limit, category);
        LOGGER.info(
                "Tienda Porte request. cid={} operation=products category={} page={} limit={} externalTarget={} timeoutSeconds={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                safeCategory(category),
                page,
                limit,
                TiendaPorteDiagnostics.safeUriHostAndPath(uri),
                Math.max(1, properties.getRequestTimeoutSeconds())
        );

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        long startNanos = System.nanoTime();
        HttpResponse<String> response = send(request);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        String contentType = response.headers().firstValue("Content-Type").orElse("-");
        int bodySize = TiendaPorteDiagnostics.bodySize(response.body());
        LOGGER.info(
                "Tienda Porte response. cid={} category={} page={} status={} contentType={} durationMs={} bodySize={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                safeCategory(category),
                page,
                response.statusCode(),
                contentType,
                durationMs,
                bodySize
        );
        if (response.statusCode() == 401) {
            throw new UnauthorizedExternalResponseException(category, page, limit);
        }
        if (response.statusCode() == 429) {
            TiendaPorteRateLimitException rateLimitException = rateLimitException(response);
            LOGGER.warn(
                    "Tienda Porte limito la consulta de productos. cid={} category={} page={} limit={} status={} retryAfter={} nextAllowedRefreshAt={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    safeCategory(category),
                    page,
                    limit,
                    response.statusCode(),
                    rateLimitException.getRetryAfter(),
                    rateLimitException.getNextAllowedRefreshAt(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw rateLimitException;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.error(
                    "Tienda Porte fallo al consultar productos. cid={} category={} page={} limit={} status={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    safeCategory(category),
                    page,
                    limit,
                    response.statusCode(),
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }

        TiendaPorteProductsResponse productsResponse = parseProductsResponse(response.body(), contentType);
        if (productsResponse == null || productsResponse.getData() == null) {
            LOGGER.error(
                    "Tienda Porte devolvio una respuesta de catalogo invalida. cid={} category={} page={} contentType={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    safeCategory(category),
                    page,
                    contentType,
                    TiendaPorteDiagnostics.safeBodyPreview(response.body())
            );
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        annotateSourceCategories(productsResponse.getData(), category);
        LOGGER.info(
                "Tienda Porte products parsed. cid={} category={} page={} products={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                safeCategory(category),
                page,
                productsResponse.getData().size()
        );
        return productsResponse;
    }

    private void annotateSourceCategories(List<TiendaPorteExternalProduct> products, String requestedCategory) {
        if (products == null || products.isEmpty()) {
            return;
        }
        String sourceCategory = requestedCategory == null || requestedCategory.isBlank() ? null : requestedCategory.trim();
        for (TiendaPorteExternalProduct product : products) {
            if (product == null) {
                continue;
            }
            if (sourceCategory != null) {
                product.setSourceCategory(sourceCategory);
            }
            if (product.getOriginalCategory() == null || product.getOriginalCategory().isBlank()) {
                product.setOriginalCategory(categoryName(product));
            }
        }
    }

    private String categoryName(TiendaPorteExternalProduct product) {
        if (product == null || product.getCategory() == null) {
            return null;
        }
        return product.getCategory().getName();
    }

    private TiendaPorteProductsResponse parseProductsResponse(String body, String contentType) {
        try {
            return objectMapper.readValue(body, TiendaPorteProductsResponse.class);
        } catch (JsonProcessingException ex) {
            LOGGER.error(
                    "No se pudo parsear respuesta de productos de Tienda Porte. cid={} contentType={} bodyPreview={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    contentType,
                    TiendaPorteDiagnostics.safeBodyPreview(body),
                    ex
            );
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, ex);
        }
    }

    private int validateTotalPages(TiendaPorteProductsResponse response) {
        Integer totalPages = response.getMeta() == null ? null : response.getMeta().getTotalPages();
        if (totalPages == null || totalPages < 0) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        if (totalPages == 0) {
            if (response.getData() != null && response.getData().isEmpty()) {
                return 0;
            }
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        return totalPages;
    }

    private <T> T executeWithSingleRetry(Function<String, T> operation) {
        String token = authService.getAccessToken();
        try {
            return operation.apply(token);
        } catch (UnauthorizedExternalResponseException ex) {
            LOGGER.warn(
                    "Tienda Porte respondio 401. cid={} category={} page={}. Se invalida autenticacion y se reintenta una vez.",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    safeCategory(ex.category),
                    ex.page
            );
            authService.invalidateAuthentication();
            String renewedToken = authService.getAccessToken();
            try {
                return operation.apply(renewedToken);
            } catch (UnauthorizedExternalResponseException secondEx) {
                authService.invalidateAuthentication();
                LOGGER.error(
                        "Tienda Porte volvio a responder 401 despues de renovar autenticacion. cid={} category={} page={}",
                        TiendaPorteDiagnostics.currentCorrelationId(),
                        safeCategory(secondEx.category),
                        secondEx.page,
                        secondEx
                );
                throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, secondEx);
            }
        }
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException ex) {
            throw new TiendaPorteTimeoutException("El proveedor externo no respondio dentro del tiempo permitido", ex);
        } catch (IOException ex) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, ex);
        }
    }

    private URI buildProductsUri(int page, int limit, String category) {
        try {
            StringBuilder uri = new StringBuilder(normalizedBaseUrl(properties.getApiBaseUrl()))
                    .append("/user/products?page=")
                    .append(page)
                    .append("&limit=")
                    .append(limit);
            if (category != null && !category.isBlank()) {
                uri.append("&category=")
                        .append(encodeQueryParam(category.trim()));
            }
            uri.append("&sort=")
                    .append(PRODUCTS_SORT);
            return URI.create(uri.toString());
        } catch (IllegalArgumentException ex) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, ex);
        }
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String normalizedBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
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

    private TiendaPorteRateLimitException rateLimitException(HttpResponse<String> response) {
        Instant now = Instant.now();
        Instant retryAfter = response.headers()
                .firstValue("Retry-After")
                .map(value -> parseRetryAfter(value, now))
                .orElse(null);
        Instant nextAllowedRefreshAt = retryAfter == null ? now.plus(defaultRateLimitCooldown()) : retryAfter;
        return new TiendaPorteRateLimitException(
                "El proveedor externo limito temporalmente las solicitudes",
                response.statusCode(),
                retryAfter,
                nextAllowedRefreshAt
        );
    }

    private Instant parseRetryAfter(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            long seconds = Long.parseLong(trimmed);
            if (seconds < 0) {
                return null;
            }
            return now.plusSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // Retry-After also allows an HTTP-date.
        }
        try {
            return ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Duration defaultRateLimitCooldown() {
        return Duration.ofMillis(Math.max(1, properties.getCatalogCache().getTtlMs()));
    }

    private String safeCategory(String category) {
        return category == null || category.isBlank() ? "-" : category;
    }

    private static class UnauthorizedExternalResponseException extends RuntimeException {
        private final String category;
        private final int page;

        private UnauthorizedExternalResponseException(String category, int page, int limit) {
            super("Tienda Porte products responded 401 for category=" + (category == null ? "-" : category) + ", page=" + page + ", limit=" + limit);
            this.category = category;
            this.page = page;
        }
    }
}
