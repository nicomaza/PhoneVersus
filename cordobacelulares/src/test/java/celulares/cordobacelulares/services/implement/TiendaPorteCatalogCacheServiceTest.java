package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheRefreshResponse;
import celulares.cordobacelulares.dtos.tiendaporte.cache.CatalogCacheStatusResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import celulares.cordobacelulares.exceptions.TiendaPorteRateLimitException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TiendaPorteCatalogCacheServiceTest {

    private final TiendaPorteClient client = mock(TiendaPorteClient.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-07-11T10:00:00Z"));
    private final ExecutorService refreshExecutor = Executors.newSingleThreadExecutor();
    private final TiendaPorteCatalogCacheService cacheService = new TiendaPorteCatalogCacheService(
            client,
            properties(),
            clock,
            refreshExecutor,
            false
    );

    @Test
    void statusResponseSerializesInstantsAsIsoStrings() throws JsonProcessingException {
        CatalogCacheStatusResponse status = new CatalogCacheStatusResponse(
                true,
                true,
                false,
                false,
                2,
                412,
                Instant.parse("2026-07-11T16:31:41.123Z"),
                Instant.parse("2026-07-11T16:31:41.123Z"),
                Instant.parse("2026-07-11T16:34:41.123Z"),
                null,
                null
        );

        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(status);

        assertThat(json).contains("\"lastSuccessfulRefreshAt\":\"2026-07-11T16:31:41.123Z\"");
        assertThat(json).contains("\"expiresAt\":\"2026-07-11T16:34:41.123Z\"");
        assertThat(json).doesNotContain("\"lastSuccessfulRefreshAt\":178");
    }

    @AfterEach
    void shutdown() {
        refreshExecutor.shutdownNow();
    }

    @Test
    void firstRequestLoadsSnapshot() {
        when(client.getProducts()).thenReturn(List.of(product("REALME C75X", "REALME")));

        List<TiendaPorteExternalProduct> products = cacheService.getProducts();

        assertThat(products).hasSize(1);
        assertThat(cacheService.status().initialized()).isTrue();
        assertThat(cacheService.status().fresh()).isTrue();
        assertThat(cacheService.status().productCount()).isEqualTo(1);
        verify(client).getProducts();
    }

    @Test
    void currentSnapshotReadDoesNotInitializeOrRefreshCache() {
        List<TiendaPorteExternalProduct> products = cacheService.getCurrentSnapshotProducts();

        assertThat(products).isEmpty();
        assertThat(cacheService.status().initialized()).isFalse();
        verify(client, never()).getProducts();
    }

    @Test
    void statusCalculatesExpirationAndFreshUsingThreeMinuteTtl() {
        when(client.getProducts()).thenReturn(List.of(product("REALME C75X", "REALME")));

        cacheService.getProducts();
        CatalogCacheStatusResponse status = cacheService.status();

        assertThat(status.lastSuccessfulRefreshAt()).isEqualTo(Instant.parse("2026-07-11T10:00:00Z"));
        assertThat(status.expiresAt()).isEqualTo(Instant.parse("2026-07-11T10:03:00Z"));
        assertThat(status.fresh()).isTrue();
        assertThat(status.stale()).isFalse();
    }

    @Test
    void statusIsStaleAfterExpiration() {
        when(client.getProducts()).thenReturn(List.of(product("REALME C75X", "REALME")));

        cacheService.getProducts();
        clock.advance(Duration.ofMinutes(4));
        CatalogCacheStatusResponse status = cacheService.status();

        assertThat(status.fresh()).isFalse();
        assertThat(status.stale()).isTrue();
        assertThat(status.expiresAt()).isEqualTo(Instant.parse("2026-07-11T10:03:00Z"));
    }

    @Test
    void freshSnapshotIsReusedWithinTtl() {
        when(client.getProducts()).thenReturn(List.of(product("REALME C75X", "REALME")));

        cacheService.getProducts();
        clock.advance(Duration.ofMinutes(2));
        cacheService.getProducts();

        verify(client, times(1)).getProducts();
    }

    @Test
    void adminRefreshIncrementsVersionAndUpdatesAttemptTimestamps() {
        when(client.getProducts())
                .thenReturn(List.of(product("REALME C75X", "REALME")))
                .thenReturn(List.of(product("REALME C85", "REALME")));

        cacheService.getProducts();
        clock.advance(Duration.ofMinutes(1));
        CatalogCacheRefreshResponse response = cacheService.refreshFromAdmin();

        assertThat(response.refreshStarted()).isTrue();
        awaitVersion(2);
        CatalogCacheStatusResponse status = cacheService.status();
        assertThat(status.version()).isEqualTo(2);
        assertThat(status.lastAttemptAt()).isEqualTo(Instant.parse("2026-07-11T10:01:00Z"));
        assertThat(status.lastSuccessfulRefreshAt()).isEqualTo(Instant.parse("2026-07-11T10:01:00Z"));
        assertThat(status.productCount()).isEqualTo(1);
    }

    @Test
    void staleSnapshotReturnsPreviousCopyAndRefreshesInBackground() {
        when(client.getProducts())
                .thenReturn(List.of(product("REALME C75X", "REALME")))
                .thenReturn(List.of(product("REALME C85", "REALME")));

        List<TiendaPorteExternalProduct> first = cacheService.getProducts();
        clock.advance(Duration.ofMinutes(4));
        List<TiendaPorteExternalProduct> stale = cacheService.getProducts();

        assertThat(stale).extracting(TiendaPorteExternalProduct::getName)
                .containsExactly(first.get(0).getName());
        awaitVersion(2);
        assertThat(cacheService.getProducts()).extracting(TiendaPorteExternalProduct::getName)
                .containsExactly("REALME C85");
        verify(client, times(2)).getProducts();
    }

    @Test
    void concurrentColdRequestsShareSingleProviderCall() {
        AtomicInteger calls = new AtomicInteger();
        when(client.getProducts()).thenAnswer(invocation -> {
            calls.incrementAndGet();
            Thread.sleep(100);
            return List.of(product("REALME C75X", "REALME"));
        });
        ExecutorService callers = Executors.newFixedThreadPool(12);

        try {
            List<CompletableFuture<List<TiendaPorteExternalProduct>>> futures = java.util.stream.IntStream.range(0, 50)
                    .mapToObj(index -> CompletableFuture.supplyAsync(cacheService::getProducts, callers))
                    .toList();
            List<List<TiendaPorteExternalProduct>> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();

            assertThat(results).hasSize(50);
            assertThat(results).allSatisfy(products -> assertThat(products).hasSize(1));
            assertThat(calls).hasValue(1);
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void rateLimitKeepsPreviousSnapshotAndBlocksAdminRefreshDuringCooldown() {
        Instant nextAllowed = clock.instant().plus(Duration.ofMinutes(5));
        when(client.getProducts())
                .thenReturn(List.of(product("REALME C75X", "REALME")))
                .thenThrow(new TiendaPorteRateLimitException("rate limited", 429, nextAllowed, nextAllowed));

        cacheService.getProducts();
        clock.advance(Duration.ofMinutes(4));
        List<TiendaPorteExternalProduct> stale = cacheService.getProducts();
        awaitNotRefreshing();

        assertThat(stale).extracting(TiendaPorteExternalProduct::getName).containsExactly("REALME C75X");
        assertThat(cacheService.status().lastError()).isEqualTo("Proveedor limitado temporalmente");
        CatalogCacheRefreshResponse refreshResponse = cacheService.refreshFromAdmin();
        assertThat(refreshResponse.blockedByCooldown()).isTrue();
        verify(client, times(2)).getProducts();
    }

    @Test
    void adminRefreshDoesNotDuplicateActiveRefresh() throws InterruptedException {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.getProducts()).thenAnswer(invocation -> {
            entered.countDown();
            release.await(3, TimeUnit.SECONDS);
            return List.of(product("REALME C75X", "REALME"));
        });

        CatalogCacheRefreshResponse first = cacheService.refreshFromAdmin();
        assertThat(first.refreshStarted()).isTrue();
        assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();

        CatalogCacheRefreshResponse second = cacheService.refreshFromAdmin();
        assertThat(second.alreadyRunning()).isTrue();

        release.countDown();
        awaitNotRefreshing();
        verify(client, times(1)).getProducts();
    }

    @Test
    void noSnapshotAndProviderFailurePropagatesError() {
        when(client.getProducts()).thenThrow(new TiendaPorteIntegrationException("No se pudo obtener el catalogo del proveedor externo"));

        assertThatThrownBy(cacheService::getProducts)
                .isInstanceOf(TiendaPorteIntegrationException.class);
        assertThat(cacheService.status().initialized()).isFalse();
    }

    private void awaitVersion(long version) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (cacheService.status().version() >= version && !cacheService.status().refreshing()) {
                return;
            }
            sleep();
        }
        throw new AssertionError("Snapshot version " + version + " was not reached");
    }

    private void awaitNotRefreshing() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (!cacheService.status().refreshing()) {
                return;
            }
            sleep();
        }
        throw new AssertionError("Cache refresh did not finish");
    }

    private void sleep() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private TiendaPorteProperties properties() {
        TiendaPorteProperties properties = new TiendaPorteProperties();
        properties.setApiBaseUrl("https://api.example.test");
        properties.setAuthBaseUrl("https://auth.example.test");
        properties.setProductsLimit(50);
        properties.setConnectTimeoutSeconds(1);
        properties.setRequestTimeoutSeconds(1);
        properties.getCatalogCache().setTtlMs(180000);
        properties.getCatalogCache().setRefreshMs(180000);
        properties.getCatalogCache().setInitialDelayMs(5000);
        return properties;
    }

    private TiendaPorteExternalProduct product(String name, String categoryName) {
        TiendaPorteCategory category = new TiendaPorteCategory();
        category.setName(categoryName);

        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId((long) name.hashCode());
        reference.setName(name);
        reference.setPriceUsd("100");
        reference.setCategory(category);

        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setCategory(category);
        product.setProductReference(reference);
        product.setColorStock(Map.of("Black", 1));
        return product;
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
