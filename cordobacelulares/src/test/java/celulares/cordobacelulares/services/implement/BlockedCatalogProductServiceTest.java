package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.admin.BlockedCatalogProductResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteCategory;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.entities.BlockedCatalogProduct;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.repository.BlockedCatalogProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockedCatalogProductServiceTest {

    private final BlockedCatalogProductRepository repository = mock(BlockedCatalogProductRepository.class);
    private final TiendaPorteCatalogCacheService cacheService = mock(TiendaPorteCatalogCacheService.class);
    private final CatalogProductPolicy catalogProductPolicy = mock(CatalogProductPolicy.class);
    private final BlockedCatalogProductService service = new BlockedCatalogProductService(
            repository,
            cacheService,
            catalogProductPolicy
    );

    @Test
    void blockPersistsProductFromCurrentSnapshotWithoutRefreshingProvider() {
        TiendaPorteExternalProduct product = product(123L, "SAMSUNG A56", "SAMSUNG", "320");
        when(repository.findByOrigenAndExternalProductId(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE, 123L))
                .thenReturn(Optional.empty());
        when(cacheService.getCurrentSnapshotProducts()).thenReturn(List.of(product));
        when(catalogProductPolicy.resolveCanonicalCategory(product, null)).thenReturn("SAMSUNG");
        when(repository.saveAndFlush(any(BlockedCatalogProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BlockedCatalogProductResponse response = service.block(123L);

        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getMarca()).isEqualTo("SAMSUNG");
        assertThat(response.getModelo()).isEqualTo("SAMSUNG A56");
        assertThat(response.getPrecioUsd()).isEqualByComparingTo("320.00");
        assertThat(response.getOrigen()).isEqualTo(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE);
        assertThat(response.getBlockedAt()).isNotNull();

        ArgumentCaptor<BlockedCatalogProduct> captor = ArgumentCaptor.forClass(BlockedCatalogProduct.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getNormalizedModelKey()).isEqualTo("samsung|samsunga56");
        verify(cacheService).getCurrentSnapshotProducts();
        verify(cacheService, never()).getProducts();
    }

    @Test
    void blockIsIdempotentWhenProductIsAlreadyBlocked() {
        BlockedCatalogProduct blocked = blocked(123L, "SAMSUNG", "SAMSUNG A56", "320", Instant.parse("2026-07-12T12:00:00Z"));
        when(repository.findByOrigenAndExternalProductId(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE, 123L))
                .thenReturn(Optional.of(blocked));

        BlockedCatalogProductResponse response = service.block(123L);

        assertThat(response.getId()).isEqualTo(123L);
        assertThat(response.getModelo()).isEqualTo("SAMSUNG A56");
        assertThat(response.getBlockedAt()).isEqualTo(Instant.parse("2026-07-12T12:00:00Z"));
        verify(cacheService, never()).getCurrentSnapshotProducts();
        verify(repository, never()).saveAndFlush(any(BlockedCatalogProduct.class));
    }

    @Test
    void getAllPrefersCurrentSnapshotDataWhenProductStillExists() {
        Instant blockedAt = Instant.parse("2026-07-12T12:00:00Z");
        BlockedCatalogProduct stored = blocked(123L, "SAMSUNG", "SAMSUNG A56", "320", blockedAt);
        TiendaPorteExternalProduct current = product(123L, "SAMSUNG A56 5G", "SAMSUNG", "330");

        when(repository.findAllByOrigenOrderByBlockedAtDescIdDesc(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE))
                .thenReturn(List.of(stored));
        when(cacheService.getCurrentSnapshotProducts()).thenReturn(List.of(current));
        when(catalogProductPolicy.resolveCanonicalCategory(current, null)).thenReturn("SAMSUNG");

        List<BlockedCatalogProductResponse> response = service.getAll();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getModelo()).isEqualTo("SAMSUNG A56 5G");
        assertThat(response.get(0).getPrecioUsd()).isEqualByComparingTo("330.00");
        assertThat(response.get(0).getBlockedAt()).isEqualTo(blockedAt);
        verify(cacheService, never()).getProducts();
    }

    @Test
    void blockRejectsInvalidIdsAndMissingSnapshotProducts() {
        assertThatThrownBy(() -> service.block(0L))
                .isInstanceOf(TiendaPorteBadRequestException.class);

        when(repository.findByOrigenAndExternalProductId(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE, 999L))
                .thenReturn(Optional.empty());
        when(cacheService.getCurrentSnapshotProducts()).thenReturn(List.of());

        assertThatThrownBy(() -> service.block(999L))
                .isInstanceOf(ApiNotFoundException.class);
        verify(repository, never()).saveAndFlush(any(BlockedCatalogProduct.class));
    }

    @Test
    void currentFilterReturnsBlockedIdsAndNormalizedKeys() {
        when(repository.findExternalProductIdsByOrigen(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE))
                .thenReturn(Set.of(123L, 456L));
        when(repository.findNormalizedModelKeysByOrigen(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE))
                .thenReturn(Set.of("samsung|samsunga56"));

        BlockedCatalogProductService.BlockedCatalogFilter filter = service.currentFilter();

        assertThat(filter.externalProductIds()).containsExactlyInAnyOrder(123L, 456L);
        assertThat(filter.normalizedModelKeys()).containsExactly("samsung|samsunga56");
    }

    private BlockedCatalogProduct blocked(Long externalProductId, String marca, String modelo, String precioUsd, Instant blockedAt) {
        BlockedCatalogProduct blocked = new BlockedCatalogProduct();
        blocked.setOrigen(BlockedCatalogProductService.ORIGIN_TIENDA_PORTE);
        blocked.setExternalProductId(externalProductId);
        blocked.setMarca(marca);
        blocked.setModelo(modelo);
        blocked.setPrecioUsd(new BigDecimal(precioUsd));
        blocked.setNormalizedModelKey(service.normalizedModelKey(marca, modelo));
        blocked.setBlockedAt(blockedAt);
        return blocked;
    }

    private TiendaPorteExternalProduct product(Long id, String name, String categoryName, String priceUsd) {
        TiendaPorteCategory category = new TiendaPorteCategory();
        category.setName(categoryName);

        TiendaPorteProductReference reference = new TiendaPorteProductReference();
        reference.setId(id);
        reference.setName(name);
        reference.setPriceUsd(priceUsd);
        reference.setCategory(category);

        TiendaPorteExternalProduct product = new TiendaPorteExternalProduct();
        product.setName(name);
        product.setCategory(category);
        product.setProductReference(reference);
        return product;
    }
}
