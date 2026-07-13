package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.admin.AdminCatalogPageResponse;
import celulares.cordobacelulares.dtos.tiendaporte.admin.AdminCatalogProductRowResponse;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.internal.TiendaPorteCalculatedPrices;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.services.PriceConfigurationService;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AdminCatalogService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdminCatalogService.class);
    private static final String ORIGIN_TIENDA_PORTE = "TIENDA_PORTE";
    private static final String DEFAULT_COLOR = "Sin color informado";
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final TiendaPorteCatalogCacheService catalogCacheService;
    private final PriceConfigurationService priceConfigurationService;
    private final DollarQuotationResolver dollarQuotationResolver;
    private final TiendaPortePriceCalculator priceCalculator;
    private final CatalogProductPolicy catalogProductPolicy;
    private final BlockedCatalogProductService blockedCatalogProductService;

    // TODO: proteger esta ruta y estos endpoints mediante Nginx Basic Auth.
    public AdminCatalogService(
            TiendaPorteCatalogCacheService catalogCacheService,
            PriceConfigurationService priceConfigurationService,
            DollarQuotationResolver dollarQuotationResolver,
            TiendaPortePriceCalculator priceCalculator,
            CatalogProductPolicy catalogProductPolicy,
            BlockedCatalogProductService blockedCatalogProductService
    ) {
        this.catalogCacheService = catalogCacheService;
        this.priceConfigurationService = priceConfigurationService;
        this.dollarQuotationResolver = dollarQuotationResolver;
        this.priceCalculator = priceCalculator;
        this.catalogProductPolicy = catalogProductPolicy;
        this.blockedCatalogProductService = blockedCatalogProductService;
    }

    public AdminCatalogPageResponse getProducts(
            String texto,
            String categoria,
            Integer page,
            Integer limit,
            String sort,
            String direction,
            String origen,
            String stock
    ) {
        validateOrigin(origen);
        int safePage = validatePage(page);
        int safeLimit = validateLimit(limit);
        String canonicalCategory = canonicalCategory(categoria);
        String normalizedQuery = TiendaPorteTextUtils.normalize(texto);
        StockFilter stockFilter = StockFilter.from(stock);

        PriceConfiguration configuration = priceConfigurationService.getRequiredForCatalog();
        BigDecimal dolarBilleteAplicado = dollarQuotationResolver.resolve(null, null, configuration);
        List<AdminCatalogProductRowResponse> rows = flattenRows(configuration, dolarBilleteAplicado).stream()
                .filter(row -> canonicalCategory == null || canonicalCategory.equals(row.getMarca()))
                .filter(row -> stockFilter.matches(row.getCantidad()))
                .filter(row -> matchesQuery(row, normalizedQuery))
                .sorted(comparator(sort, direction))
                .toList();

        int total = rows.size();
        int totalPages = totalPages(total, safeLimit);
        int fromIndex = Math.max(0, (safePage - 1) * safeLimit);
        List<AdminCatalogProductRowResponse> data = fromIndex >= total
                ? List.of()
                : rows.subList(fromIndex, Math.min(total, fromIndex + safeLimit));
        return new AdminCatalogPageResponse(
                safePage,
                safeLimit,
                total,
                totalPages,
                safePage < totalPages,
                data
        );
    }

    private List<AdminCatalogProductRowResponse> flattenRows(
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado
    ) {
        List<AdminCatalogProductRowResponse> rows = new ArrayList<>();
        Set<Long> blockedExternalProductIds = blockedCatalogProductService.currentFilter().externalProductIds();
        List<TiendaPorteExternalProduct> publishableCandidates = catalogCacheService.getProducts()
                .stream()
                .filter(product -> !blockedCatalogProductService.isBlockedTiendaPorteProduct(product, blockedExternalProductIds))
                .toList();
        CatalogProductPolicy.SelectionResult selectionResult = catalogProductPolicy.selectPublishableTiendaPorteProducts(publishableCandidates, null);
        logProcessingSummary(selectionResult.stats());
        for (CatalogProductPolicy.ProductSelection selection : selectionResult.selections()) {
            TiendaPorteExternalProduct product = selection.product();
            String brandName = selection.canonicalCategory();
            TiendaPorteProductReference reference = product.getProductReference();
            String modelName = firstNonBlank(reference == null ? null : reference.getName(), product.getName());
            if (modelName == null) {
                continue;
            }
            Long id = reference == null ? null : reference.getId();
            BigDecimal fallbackPriceUsd = parseNonNegativeMoney(reference == null ? null : reference.getPriceUsd());
            Map<String, BigDecimal> colorPrices = colorPricesByColor(reference);
            Map<String, Integer> colorStock = product.getColorStock();

            if (colorStock == null || colorStock.isEmpty()) {
                rows.add(toRow(id, brandName, modelName.trim(), DEFAULT_COLOR, null, fallbackPriceUsd, configuration, dolarBilleteAplicado));
                continue;
            }

            for (Map.Entry<String, Integer> entry : colorStock.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                BigDecimal priceUsd = colorPrices.getOrDefault(colorKey(entry.getKey()), fallbackPriceUsd);
                rows.add(toRow(id, brandName, modelName.trim(), entry.getKey().trim(), normalizeStock(entry.getValue()), priceUsd, configuration, dolarBilleteAplicado));
            }
        }
        return rows;
    }

    private void logProcessingSummary(CatalogProductPolicy.SelectionStats stats) {
        if (stats == null) {
            return;
        }
        LOGGER.info(
                "Admin catalog processing summary: totalReceived={} excludedProducts={} excludedCajaManchada={} preservedArticulosVarios={} deduplicatedProducts={} totalPublished={}",
                stats.totalReceived(),
                stats.excludedProducts(),
                stats.excludedCajaManchada(),
                stats.preservedArticulosVarios(),
                stats.deduplicatedProducts(),
                stats.totalPublished()
        );
    }

    private AdminCatalogProductRowResponse toRow(
            Long id,
            String brandName,
            String modelName,
            String color,
            Integer stock,
            BigDecimal priceUsd,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado
    ) {
        TiendaPorteCalculatedPrices prices = priceCalculator.calculate(priceUsd, configuration, dolarBilleteAplicado);
        return new AdminCatalogProductRowResponse(
                id,
                brandName,
                modelName,
                color,
                stock,
                ORIGIN_TIENDA_PORTE,
                prices.getPrecioUsd(),
                prices.getPrecioPesos(),
                prices.getPrecioTransferenciaBancaria(),
                prices.getPrecioTarjeta3Pagos(),
                prices.getPrecioTarjeta6Pagos(),
                prices.getPrecioTarjeta12Pagos()
        );
    }

    private boolean matchesQuery(AdminCatalogProductRowResponse row, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return true;
        }
        return TiendaPorteTextUtils.normalize(row.getId() == null ? "sin id" : row.getId().toString()).contains(normalizedQuery)
                || TiendaPorteTextUtils.normalize(row.getMarca()).contains(normalizedQuery)
                || TiendaPorteTextUtils.normalize(row.getModelo()).contains(normalizedQuery)
                || TiendaPorteTextUtils.normalize(row.getColor()).contains(normalizedQuery);
    }

    private Comparator<AdminCatalogProductRowResponse> comparator(String sort, String direction) {
        Comparator<AdminCatalogProductRowResponse> comparator = switch (sort == null ? "" : sort.trim()) {
            case "id" -> Comparator.comparing(AdminCatalogProductRowResponse::getId, Comparator.nullsLast(Long::compareTo));
            case "marca" -> Comparator.comparing(AdminCatalogProductRowResponse::getMarca, String.CASE_INSENSITIVE_ORDER);
            case "modelo" -> Comparator.comparing(AdminCatalogProductRowResponse::getModelo, String.CASE_INSENSITIVE_ORDER);
            case "color" -> Comparator.comparing(AdminCatalogProductRowResponse::getColor, String.CASE_INSENSITIVE_ORDER);
            case "cantidad" -> Comparator.comparing(AdminCatalogProductRowResponse::getCantidad, Comparator.nullsLast(Integer::compareTo));
            case "precioUsd" -> Comparator.comparing(AdminCatalogProductRowResponse::getPrecioUsd, Comparator.nullsLast(BigDecimal::compareTo));
            case "precioPesos" -> Comparator.comparing(AdminCatalogProductRowResponse::getPrecioPesos, Comparator.nullsLast(BigDecimal::compareTo));
            default -> Comparator.comparing(AdminCatalogProductRowResponse::getMarca, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AdminCatalogProductRowResponse::getModelo, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AdminCatalogProductRowResponse::getColor, String.CASE_INSENSITIVE_ORDER);
        };
        if ("desc".equalsIgnoreCase(direction)) {
            return comparator.reversed();
        }
        return comparator;
    }

    private void validateOrigin(String origen) {
        if (origen != null && !origen.isBlank() && !ORIGIN_TIENDA_PORTE.equalsIgnoreCase(origen.trim())) {
            throw new TiendaPorteBadRequestException("origen no permitido: " + origen);
        }
    }

    private String canonicalCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String canonical = catalogProductPolicy.canonicalizeRequestedCategory(category);
        if (canonical == null) {
            throw new TiendaPorteBadRequestException("categoria no permitida: " + category);
        }
        return canonical;
    }

    private int validatePage(Integer page) {
        int safePage = page == null ? DEFAULT_PAGE : page;
        if (safePage < 1) {
            throw new TiendaPorteBadRequestException("page debe ser mayor o igual a 1");
        }
        return safePage;
    }

    private int validateLimit(Integer limit) {
        int safeLimit = limit == null ? DEFAULT_LIMIT : limit;
        if (safeLimit < 1) {
            throw new TiendaPorteBadRequestException("limit debe ser mayor o igual a 1");
        }
        return Math.min(safeLimit, MAX_LIMIT);
    }

    private int totalPages(int total, int limit) {
        if (total <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) total / limit);
    }

    private Map<String, BigDecimal> colorPricesByColor(TiendaPorteProductReference productReference) {
        Map<String, Object> colorPrices = productReference == null ? null : productReference.getColorPrices();
        if (colorPrices == null || colorPrices.isEmpty()) {
            return Map.of();
        }
        Map<String, BigDecimal> parsed = new HashMap<>();
        for (Map.Entry<String, Object> entry : colorPrices.entrySet()) {
            BigDecimal price = parseNonNegativeMoney(entry.getValue());
            if (price != null && entry.getKey() != null && !entry.getKey().isBlank()) {
                parsed.put(colorKey(entry.getKey()), price);
            }
        }
        return parsed;
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

    private Integer normalizeStock(Integer stock) {
        return stock == null || stock < 0 ? 0 : stock;
    }

    private String colorKey(String color) {
        String normalized = TiendaPorteTextUtils.normalize(color);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return color == null ? "" : color.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private enum StockFilter {
        ALL,
        WITH_STOCK,
        WITHOUT_STOCK,
        UNKNOWN;

        private static StockFilter from(String value) {
            if (value == null || value.isBlank() || "todos".equalsIgnoreCase(value)) {
                return ALL;
            }
            return switch (TiendaPorteTextUtils.normalize(value)) {
                case "constock", "withstock" -> WITH_STOCK;
                case "sinstock", "withoutstock", "zerostock" -> WITHOUT_STOCK;
                case "sindato", "unknown" -> UNKNOWN;
                default -> throw new TiendaPorteBadRequestException("stock no permitido: " + value);
            };
        }

        private boolean matches(Integer stock) {
            return switch (this) {
                case ALL -> true;
                case WITH_STOCK -> stock != null && stock > 0;
                case WITHOUT_STOCK -> stock != null && stock <= 0;
                case UNKNOWN -> stock == null;
            };
        }
    }
}
