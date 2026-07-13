package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.admin.BlockedCatalogProductResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.entities.BlockedCatalogProduct;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.repository.BlockedCatalogProductRepository;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BlockedCatalogProductService {

    public static final String ORIGIN_TIENDA_PORTE = "TIENDA_PORTE";

    private static final String DEFAULT_CATEGORY = "OTROS";

    private final BlockedCatalogProductRepository repository;
    private final TiendaPorteCatalogCacheService catalogCacheService;
    private final CatalogProductPolicy catalogProductPolicy;

    public BlockedCatalogProductService(
            BlockedCatalogProductRepository repository,
            TiendaPorteCatalogCacheService catalogCacheService,
            CatalogProductPolicy catalogProductPolicy
    ) {
        this.repository = repository;
        this.catalogCacheService = catalogCacheService;
        this.catalogProductPolicy = catalogProductPolicy;
    }

    @Transactional(readOnly = true)
    public List<BlockedCatalogProductResponse> getAll() {
        Map<Long, TiendaPorteExternalProduct> currentProducts = currentProductsByExternalId();
        return repository.findAllByOrigenOrderByBlockedAtDescIdDesc(ORIGIN_TIENDA_PORTE)
                .stream()
                .map(blocked -> toResponse(blocked, currentProducts.get(blocked.getExternalProductId())))
                .toList();
    }

    @Transactional
    public BlockedCatalogProductResponse block(Long externalProductId) {
        validateExternalProductId(externalProductId);
        return repository.findByOrigenAndExternalProductId(ORIGIN_TIENDA_PORTE, externalProductId)
                .map(this::toResponse)
                .orElseGet(() -> createBlockedProduct(externalProductId));
    }

    @Transactional
    public void unblock(Long externalProductId) {
        validateExternalProductId(externalProductId);
        repository.deleteByOrigenAndExternalProductId(ORIGIN_TIENDA_PORTE, externalProductId);
    }

    @Transactional(readOnly = true)
    public BlockedCatalogFilter currentFilter() {
        return new BlockedCatalogFilter(blockedExternalProductIds(), blockedNormalizedModelKeys());
    }

    public boolean isBlockedTiendaPorteProduct(TiendaPorteExternalProduct product, Set<Long> blockedExternalProductIds) {
        Long externalProductId = externalProductId(product);
        return externalProductId != null
                && blockedExternalProductIds != null
                && blockedExternalProductIds.contains(externalProductId);
    }

    public String normalizedModelKey(String canonicalCategory, String modelName) {
        String normalizedCategory = TiendaPorteTextUtils.normalize(canonicalCategory);
        String normalizedModel = TiendaPorteTextUtils.normalize(modelName);
        if (normalizedCategory.isBlank() || normalizedModel.isBlank()) {
            return "";
        }
        return normalizedCategory + "|" + normalizedModel;
    }

    private BlockedCatalogProductResponse createBlockedProduct(Long externalProductId) {
        TiendaPorteExternalProduct product = catalogCacheService.getCurrentSnapshotProducts()
                .stream()
                .filter(current -> externalProductId.equals(externalProductId(current)))
                .findFirst()
                .orElseThrow(() -> new ApiNotFoundException("No se encontro el producto " + externalProductId + " en el snapshot actual"));

        String modelName = modelName(product);
        if (modelName == null || modelName.isBlank()) {
            throw new ApiNotFoundException("No se encontro un modelo valido para el producto " + externalProductId);
        }

        String canonicalCategory = catalogProductPolicy.resolveCanonicalCategory(product, null);
        String normalizedModelKey = normalizedModelKey(canonicalCategory == null ? DEFAULT_CATEGORY : canonicalCategory, modelName);
        if (normalizedModelKey.isBlank()) {
            throw new ApiNotFoundException("No se pudo generar una clave de bloqueo para el producto " + externalProductId);
        }

        BlockedCatalogProduct blocked = new BlockedCatalogProduct();
        blocked.setOrigen(ORIGIN_TIENDA_PORTE);
        blocked.setExternalProductId(externalProductId);
        blocked.setMarca(canonicalCategory == null ? DEFAULT_CATEGORY : canonicalCategory);
        blocked.setModelo(modelName.trim());
        blocked.setPrecioUsd(priceUsd(product));
        blocked.setNormalizedModelKey(normalizedModelKey);
        blocked.setBlockedAt(Instant.now());

        try {
            return toResponse(repository.saveAndFlush(blocked));
        } catch (DataIntegrityViolationException ex) {
            return repository.findByOrigenAndExternalProductId(ORIGIN_TIENDA_PORTE, externalProductId)
                    .map(this::toResponse)
                    .orElseThrow(() -> ex);
        }
    }

    private Set<Long> blockedExternalProductIds() {
        return repository.findExternalProductIdsByOrigen(ORIGIN_TIENDA_PORTE)
                .stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> blockedNormalizedModelKeys() {
        return repository.findNormalizedModelKeysByOrigen(ORIGIN_TIENDA_PORTE)
                .stream()
                .filter(key -> key != null && !key.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private Map<Long, TiendaPorteExternalProduct> currentProductsByExternalId() {
        Map<Long, TiendaPorteExternalProduct> productsById = new LinkedHashMap<>();
        for (TiendaPorteExternalProduct product : catalogCacheService.getCurrentSnapshotProducts()) {
            Long id = externalProductId(product);
            if (id != null && id > 0) {
                productsById.putIfAbsent(id, product);
            }
        }
        return productsById;
    }

    private BlockedCatalogProductResponse toResponse(BlockedCatalogProduct blocked) {
        return toResponse(blocked, null);
    }

    private BlockedCatalogProductResponse toResponse(BlockedCatalogProduct blocked, TiendaPorteExternalProduct currentProduct) {
        if (currentProduct == null) {
            return new BlockedCatalogProductResponse(
                    blocked.getExternalProductId(),
                    blocked.getMarca(),
                    blocked.getModelo(),
                    blocked.getPrecioUsd(),
                    blocked.getOrigen(),
                    blocked.getBlockedAt()
            );
        }

        String currentModel = modelName(currentProduct);
        String currentCategory = catalogProductPolicy.resolveCanonicalCategory(currentProduct, null);
        BigDecimal currentPriceUsd = priceUsd(currentProduct);
        return new BlockedCatalogProductResponse(
                blocked.getExternalProductId(),
                currentCategory == null ? blocked.getMarca() : currentCategory,
                currentModel == null || currentModel.isBlank() ? blocked.getModelo() : currentModel.trim(),
                currentPriceUsd == null ? blocked.getPrecioUsd() : currentPriceUsd,
                blocked.getOrigen(),
                blocked.getBlockedAt()
        );
    }

    private void validateExternalProductId(Long externalProductId) {
        if (externalProductId == null || externalProductId <= 0) {
            throw new TiendaPorteBadRequestException("externalProductId debe ser mayor que cero");
        }
    }

    private Long externalProductId(TiendaPorteExternalProduct product) {
        TiendaPorteProductReference reference = product == null ? null : product.getProductReference();
        return reference == null ? null : reference.getId();
    }

    private String modelName(TiendaPorteExternalProduct product) {
        TiendaPorteProductReference reference = product == null ? null : product.getProductReference();
        String referenceName = reference == null ? null : reference.getName();
        String productName = product == null ? null : product.getName();
        return firstNonBlank(referenceName, productName);
    }

    private BigDecimal priceUsd(TiendaPorteExternalProduct product) {
        TiendaPorteProductReference reference = product == null ? null : product.getProductReference();
        return parseNonNegativeMoney(reference == null ? null : reference.getPriceUsd());
    }

    private BigDecimal parseNonNegativeMoney(Object value) {
        if (value == null) {
            return null;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.toString().trim());
            if (parsed.signum() < 0) {
                return null;
            }
            return parsed.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public record BlockedCatalogFilter(Set<Long> externalProductIds, Set<String> normalizedModelKeys) {
    }
}
