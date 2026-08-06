package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.SupplierSheetProperties;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;

class SupplierSheetServiceImplTest {

    private final CatalogProductPolicy catalogProductPolicy = new CatalogProductPolicy(new CatalogCategoryResolver());

    @Test
    void importsUnregisteredBrandAndAssignsGenericCategory() {
        SupplierSheetProduct product = products("NUEVA MARCA,Equipo Inteligente 8GB,,$ 75").get(0);

        assertThat(product.originalBrand()).isEqualTo("NUEVA MARCA");
        assertThat(product.responseBrand()).isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
        assertThat(product.modelName()).isEqualTo("NUEVA MARCA Equipo Inteligente 8GB");
        assertThat(catalogProductPolicy.resolveCanonicalCategory(product, null))
                .isEqualTo(CatalogCategoryResolver.ARTICULOS_VARIOS);
    }

    @Test
    void importsProductWithEmptyColor() {
        SupplierSheetProduct product = products("MARCA NUEVA,Producto sin color,,$ 50").get(0);

        assertThat(product.colors()).isEmpty();
        assertThat(product.priceUsd()).isEqualByComparingTo("50.00");
    }

    @Test
    void parsesPriceWithCurrencyWhitespaceAndThousandsSeparator() {
        SupplierSheetProduct product = products("MARCA NUEVA,Producto premium,,\"$ 1,250\"").get(0);

        assertThat(product.priceUsd()).isEqualByComparingTo("1250.00");
    }

    @Test
    void continuesReadingValidRowsAfterEmptyRows() {
        List<SupplierSheetProduct> products = products("""
                MARCA,MODELO,COLOR,PRECIO

                ,,,

                MARCA NUEVA,Producto posterior,, $ 50
                """);

        assertThat(products).singleElement()
                .satisfies(product -> assertThat(product.modelName()).isEqualTo("MARCA NUEVA Producto posterior"));
    }

    @Test
    void importsTheThreeReportedProductsWithExactTextColorsAndPrices() {
        List<SupplierSheetProduct> products = products("""
                MARCA,MODELO,COLOR,PRECIO
                AMAZON,Amazon Fire Tv Stick 4K Select 8GB Wi Fi 5,,$ 50
                AMAZON,Amazon Kindle Gen 11 – 16GB,Matcha,$ 149
                ANTHBOT,Anthbot Genie 600 – Robot Cortacésped Inteligente,,"$ 1,250"
                """);

        assertThat(products).hasSize(3);
        assertThat(products.get(0).modelName()).isEqualTo("Amazon Fire Tv Stick 4K Select 8GB Wi Fi 5");
        assertThat(products.get(0).colors()).isEmpty();
        assertThat(products.get(0).priceUsd()).isEqualByComparingTo("50.00");
        assertThat(products.get(1).modelName()).isEqualTo("Amazon Kindle Gen 11 – 16GB");
        assertThat(products.get(1).colors()).containsExactly("Matcha");
        assertThat(products.get(1).priceUsd()).isEqualByComparingTo("149.00");
        assertThat(products.get(2).modelName()).isEqualTo("Anthbot Genie 600 – Robot Cortacésped Inteligente");
        assertThat(products.get(2).colors()).isEmpty();
        assertThat(products.get(2).priceUsd()).isEqualByComparingTo("1250.00");
    }

    @Test
    void importsAllReportedSonyRowsInSonyWithPricesAndOptionalColors() {
        List<SupplierSheetProduct> products = products("""
                MARCA,MODELO,COLOR,PRECIO
                SONY,EA Sports FC 26 \u2013 PS5,F\u00edsico,$ 53
                SONY,JOYSTICK PS5 INAL\u00c1MBRICO SONY PLAYSTATION 5 GOD OF WAR EDITION LIMITED 20th ANNIVERSARY,Red/white,$ 132
                SONY,PLAYSTATION 5 SLIM 825GB PS5 DIGITAL + DUALSENSE,White,$ 725
                SONY,PS5 CON LECTORA 1TB,GRAN TURISMO + ASTRO BOT,$ 845
                SONY,PlayStation 3 500GB Con flash incluido + juegos digitales,,$ 275
                SONY,PlayStation 5 Pro 2TB Digital,,"$ 1,300"
                SONY,PlayStation 5 Slim 825GB Digital en stock,,$ 735
                SONY,joystick PS5 dualsence,Colores Varios Consultar,$ 100
                """);

        assertThat(products).hasSize(8);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.originalBrand()).isEqualTo("SONY");
            assertThat(product.responseBrand()).isEqualTo(CatalogCategoryResolver.SONY);
            assertThat(catalogProductPolicy.resolveCanonicalCategory(product, null))
                    .isEqualTo(CatalogCategoryResolver.SONY);
        });
        assertThat(products).extracting(SupplierSheetProduct::priceUsd)
                .contains(new java.math.BigDecimal("53.00"), new java.math.BigDecimal("1300.00"));
        assertThat(products).filteredOn(product -> product.colors().isEmpty()).hasSize(3);
    }

    @Test
    void mergesDuplicateRowsWithoutDroppingTheirColors() {
        List<SupplierSheetProduct> products = products("""
                MARCA NUEVA,Producto 16GB,Negro,$ 100
                MARCA NUEVA,Producto 16GB,Azul,$ 100
                """);

        assertThat(products).singleElement().satisfies(product -> {
            assertThat(product.colors()).containsExactly("Negro", "Azul");
            assertThat(product.priceUsd()).isEqualByComparingTo("100.00");
        });
    }

    @Test
    void reloadInvalidatesTheCachedSheetSnapshot() {
        QueueHttpClient httpClient = new QueueHttpClient(
                "MARCA NUEVA,Producto,,$ 50",
                "MARCA NUEVA,Producto,,$ 149"
        );
        SupplierSheetServiceImpl service = service(httpClient);

        assertThat(service.getProducts().get(0).priceUsd()).isEqualByComparingTo("50.00");
        assertThat(service.getProducts().get(0).priceUsd()).isEqualByComparingTo("50.00");
        assertThat(service.reload()).isEqualTo(1);
        assertThat(service.getProducts().get(0).priceUsd()).isEqualByComparingTo("149.00");
        assertThat(httpClient.requestCount()).isEqualTo(2);
    }

    private List<SupplierSheetProduct> products(String csv) {
        return service(new QueueHttpClient(csv)).getProducts();
    }

    private SupplierSheetServiceImpl service(HttpClient httpClient) {
        SupplierSheetProperties properties = new SupplierSheetProperties();
        properties.setCsvUrl("https://example.test/catalog.csv");
        properties.setCacheMinutes(5);
        return new SupplierSheetServiceImpl(properties, httpClient, catalogProductPolicy);
    }

    private static class QueueHttpClient extends HttpClient {

        private final ArrayDeque<String> bodies = new ArrayDeque<>();
        private int requestCount;

        private QueueHttpClient(String... bodies) {
            this.bodies.addAll(List.of(bodies));
        }

        private int requestCount() {
            return requestCount;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            requestCount++;
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) new CsvHttpResponse(request, bodies.removeFirst());
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

    private record CsvHttpResponse(HttpRequest request, String body) implements HttpResponse<String> {

        @Override
        public int statusCode() {
            return 200;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of("Content-Type", List.of("text/csv")), (left, right) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
