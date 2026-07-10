package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductsResponse;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteTimeoutException;
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
        LOGGER.info("Consultando pagina {} del catalogo de Tienda Porte{}", page, categoryLogSuffix(category));
        HttpRequest request = HttpRequest.newBuilder(buildProductsUri(page, limit, category))
                .timeout(requestTimeout())
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 401) {
            throw new UnauthorizedExternalResponseException();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.warn("Tienda Porte respondio con estado {} al consultar productos", response.statusCode());
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }

        TiendaPorteProductsResponse productsResponse = parseProductsResponse(response.body());
        if (productsResponse == null || productsResponse.getData() == null) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        LOGGER.info("Tienda Porte devolvio {} productos en pagina {}", productsResponse.getData().size(), page);
        return productsResponse;
    }

    private TiendaPorteProductsResponse parseProductsResponse(String body) {
        try {
            return objectMapper.readValue(body, TiendaPorteProductsResponse.class);
        } catch (JsonProcessingException ex) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE, ex);
        }
    }

    private int validateTotalPages(TiendaPorteProductsResponse response) {
        Integer totalPages = response.getMeta() == null ? null : response.getMeta().getTotalPages();
        if (totalPages == null || totalPages <= 0) {
            throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
        }
        return totalPages;
    }

    private <T> T executeWithSingleRetry(Function<String, T> operation) {
        String token = authService.getAccessToken();
        try {
            return operation.apply(token);
        } catch (UnauthorizedExternalResponseException ex) {
            authService.invalidateAuthentication();
            String renewedToken = authService.getAccessToken();
            try {
                return operation.apply(renewedToken);
            } catch (UnauthorizedExternalResponseException secondEx) {
                authService.invalidateAuthentication();
                throw new TiendaPorteIntegrationException(PRODUCTS_ERROR_MESSAGE);
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
    }

    private String encodeQueryParam(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String categoryLogSuffix(String category) {
        return category == null || category.isBlank() ? "" : " para categoria " + category;
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

    private static class UnauthorizedExternalResponseException extends RuntimeException {
    }
}
