package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogProductResponse;
import celulares.cordobacelulares.dtos.seo.SeoCatalogSyncResult;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.entities.SeoCatalogProduct;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.repository.SeoCatalogProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeoCatalogServiceTest {

    private final SeoCatalogProductRepository repository = mock(SeoCatalogProductRepository.class);
    private final List<SeoCatalogProduct> store = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private SeoCatalogService service;

    @BeforeEach
    void setUp() {
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(store));
        when(repository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<SeoCatalogProduct> products = invocation.getArgument(0);
            List<SeoCatalogProduct> saved = new ArrayList<>();
            for (SeoCatalogProduct product : products) {
                if (product.getId() == null) {
                    product.setId(sequence.incrementAndGet());
                }
                if (!store.contains(product)) {
                    store.add(product);
                }
                saved.add(product);
            }
            return saved;
        });
        when(repository.findBySlug(any())).thenAnswer(invocation -> store.stream()
                .filter(product -> product.getSlug().equals(invocation.getArgument(0)))
                .findFirst());
        when(repository.findAllByActiveTrueOrderBySlugAsc()).thenAnswer(invocation -> store.stream()
                .filter(SeoCatalogProduct::isActive)
                .sorted(Comparator.comparing(SeoCatalogProduct::getSlug))
                .toList());
        when(repository.count()).thenAnswer(invocation -> (long) store.size());
        when(repository.countByActiveTrue()).thenAnswer(invocation -> store.stream()
                .filter(SeoCatalogProduct::isActive)
                .count());

        service = new SeoCatalogService(
                repository,
                Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void productDisappearsAndReturnsWithSameHistoricalRecord() {
        service.synchronize(catalog("XIAOMI", "REDMI NOTE 15 6GB 128GB"), true);
        SeoCatalogProduct original = store.get(0);
        Long originalId = original.getId();

        SeoCatalogSyncResult disappearance = service.synchronize(
                catalog("SAMSUNG", "SAMSUNG GALAXY S25 8GB 256GB"),
                true
        );

        assertThat(disappearance.deactivated()).isEqualTo(1);
        assertThat(original.isActive()).isFalse();

        SeoCatalogSyncResult returnResult = service.synchronize(
                List.of(
                        brand("XIAOMI", "REDMI NOTE 15 6GB 128GB"),
                        brand("SAMSUNG", "SAMSUNG GALAXY S25 8GB 256GB")
                ),
                true
        );

        assertThat(returnResult.reactivated()).isEqualTo(1);
        assertThat(original.isActive()).isTrue();
        assertThat(original.getId()).isEqualTo(originalId);
        assertThat(store).hasSize(2);
    }

    @Test
    void emptyCatalogDoesNotDeactivateHistoricalProducts() {
        service.synchronize(catalog("MOTOROLA", "MOTOROLA MOTO G86 8GB 256GB"), true);

        SeoCatalogSyncResult result = service.synchronize(List.of(), true);

        assertThat(result.skipped()).isTrue();
        assertThat(store.get(0).isActive()).isTrue();
    }

    @Test
    void unverifiedCatalogUpsertsCurrentProductsWithoutDeactivatingMissingOnes() {
        service.synchronize(catalog("XIAOMI", "POCO C81 PRO 4GB 128GB"), true);

        SeoCatalogSyncResult result = service.synchronize(
                catalog("SAMSUNG", "SAMSUNG GALAXY A56 8GB 256GB"),
                false
        );

        assertThat(result.deactivationApplied()).isFalse();
        assertThat(store).allMatch(SeoCatalogProduct::isActive);
    }

    @Test
    void detectsCollisionWithoutInventingSuffixes() {
        SeoCatalogSyncResult result = service.synchronize(
                List.of(
                        brand("MARCA A", "MODELO A+B"),
                        brand("MARCA B", "MODELO A B")
                ),
                true
        );

        assertThat(result.slugCollisions()).isEqualTo(1);
        assertThat(store).singleElement()
                .extracting(SeoCatalogProduct::getSlug)
                .isEqualTo("modelo-a-b");
    }

    @Test
    void historicalInactiveProductIsReturnedAndOnlyUnknownSlugIsNotFound() {
        SeoCatalogProduct historical = historical("poco-c81", false);
        store.add(historical);

        SeoCatalogProductResponse response = service.findBySlug("poco-c81");

        assertThat(response.active()).isFalse();
        assertThat(response.slug()).isEqualTo("poco-c81");
        assertThatThrownBy(() -> service.findBySlug("never-seen"))
                .isInstanceOf(ApiNotFoundException.class);
    }

    @Test
    void activeSlugsExcludeInactiveHistory() {
        store.add(historical("activo", true));
        store.add(historical("inactivo", false));

        assertThat(service.activeSlugs()).containsExactly("activo");
    }

    private List<TiendaPorteBrandResponse> catalog(String brand, String model) {
        return List.of(brand(brand, model));
    }

    private TiendaPorteBrandResponse brand(String brand, String model) {
        TiendaPorteModelResponse modelResponse = new TiendaPorteModelResponse();
        modelResponse.setModeloNombre(model);
        modelResponse.setOrigen(CatalogProductOrigin.TIENDA_PORTE);
        return new TiendaPorteBrandResponse(brand, List.of(modelResponse));
    }

    private SeoCatalogProduct historical(String slug, boolean active) {
        Instant now = Instant.parse("2026-10-07T12:00:00Z");
        return new SeoCatalogProduct(
                sequence.incrementAndGet(),
                slug,
                "MARCA",
                slug.toUpperCase(),
                CatalogProductOrigin.TIENDA_PORTE,
                now,
                now,
                active
        );
    }
}
