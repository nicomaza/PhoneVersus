package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.internal.TiendaPorteCalculatedPrices;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteExternalProduct;
import celulares.cordobacelulares.dtos.tiendaporte.external.TiendaPorteProductReference;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteCategoryPageResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteColorStockResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.services.PriceConfigurationService;
import celulares.cordobacelulares.services.SupplierSheetService;
import celulares.cordobacelulares.services.TiendaPorteService;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TiendaPorteServiceImpl implements TiendaPorteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteServiceImpl.class);
    private static final String DEFAULT_BRAND = "OTROS";
    private static final BigDecimal MONEY_ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    private static final List<String> XIAOMI_FAMILY_PREFIXES = List.of("xiaomi", "redmi", "poco");
    private static final int NO_MATCH = Integer.MAX_VALUE;
    private static final int DEFAULT_CATEGORY_PAGE = 1;
    private static final int DEFAULT_CATEGORY_LIMIT = 50;
    private static final int MAX_CATEGORY_LIMIT = 100;
    private static final List<String> MAIN_TIENDA_PORTE_CATEGORIES = List.of(
            "ARTICULOS VARIOS",
            "INFINIX",
            "IPHONE",
            "MOTOROLA",
            "PRODUCTOS APPLE",
            "PERFUMES",
            "REALME",
            "XIAOMI",
            "SAMSUNG"
    );
    private static final List<String> ALLOWED_CATEGORIES = List.of(
            "ARTICULOS VARIOS",
            "HUAWEI",
            "HONOR",
            "INFINIX",
            "IPHONE",
            "MOTOROLA",
            "OPPO",
            "PRODUCTOS APPLE",
            "PERFUMES",
            "REALME",
            "XIAOMI",
            "SAMSUNG",
            "OTROS"
    );
    private static final List<String> SUPPLIER_ONLY_CATEGORIES = List.of("HUAWEI", "HONOR", "OPPO");
    private final TiendaPorteCatalogCacheService catalogCacheService;
    private final PriceConfigurationService priceConfigurationService;
    private final SupplierSheetService supplierSheetService;
    private final DollarQuotationResolver dollarQuotationResolver;
    private final TiendaPortePriceCalculator priceCalculator;
    private final CatalogProductPolicy catalogProductPolicy;
    private final BlockedCatalogProductService blockedCatalogProductService;

    public TiendaPorteServiceImpl(
            TiendaPorteCatalogCacheService catalogCacheService,
            PriceConfigurationService priceConfigurationService,
            SupplierSheetService supplierSheetService,
            DollarQuotationResolver dollarQuotationResolver,
            TiendaPortePriceCalculator priceCalculator,
            CatalogProductPolicy catalogProductPolicy,
            BlockedCatalogProductService blockedCatalogProductService
    ) {
        this.catalogCacheService = catalogCacheService;
        this.priceConfigurationService = priceConfigurationService;
        this.supplierSheetService = supplierSheetService;
        this.dollarQuotationResolver = dollarQuotationResolver;
        this.priceCalculator = priceCalculator;
        this.catalogProductPolicy = catalogProductPolicy;
        this.blockedCatalogProductService = blockedCatalogProductService;
    }

    @Override
    public List<TiendaPorteBrandResponse> getAll(BigDecimal cotizacionDolar, String cotizacionDolarHeader) {
        PriceConfiguration configuration = priceConfigurationService.getRequiredForCatalog();
        BigDecimal dolarBilleteAplicado = dollarQuotationResolver.resolve(cotizacionDolarHeader, cotizacionDolar, configuration);
        BlockedCatalogProductService.BlockedCatalogFilter blockedFilter = blockedCatalogProductService.currentFilter();
        List<TiendaPorteExternalProduct> products = filterBlockedProducts(catalogCacheService.getProducts(), blockedFilter.externalProductIds());
        List<TiendaPorteBrandResponse> catalog = buildCatalog(products, configuration, dolarBilleteAplicado, null);
        return addSupplierSheetMissingProducts(catalog, configuration, dolarBilleteAplicado, null, blockedFilter.normalizedModelKeys());
    }

    @Override
    public List<TiendaPorteBrandResponse> search(String texto, BigDecimal cotizacionDolar, String cotizacionDolarHeader) {
        String normalizedQuery = TiendaPorteTextUtils.normalize(texto);
        if (normalizedQuery.isBlank()) {
            throw new TiendaPorteBadRequestException("El parametro texto es obligatorio y debe contener letras o numeros");
        }

        PriceConfiguration configuration = priceConfigurationService.getRequiredForCatalog();
        BigDecimal dolarBilleteAplicado = dollarQuotationResolver.resolve(cotizacionDolarHeader, cotizacionDolar, configuration);
        BlockedCatalogProductService.BlockedCatalogFilter blockedFilter = blockedCatalogProductService.currentFilter();
        List<TiendaPorteExternalProduct> products = filterBlockedProducts(catalogCacheService.getProducts(), blockedFilter.externalProductIds());
        List<TiendaPorteBrandResponse> catalog = buildCatalog(products, configuration, dolarBilleteAplicado, null);
        catalog = addSupplierSheetMissingProducts(catalog, configuration, dolarBilleteAplicado, null, blockedFilter.normalizedModelKeys());
        return filterBySearch(catalog, normalizedQuery);
    }

    @Override
    public List<TiendaPorteBrandResponse> getAllowedCategories(BigDecimal cotizacionDolar, String cotizacionDolarHeader) {
        PriceConfiguration configuration = priceConfigurationService.getRequiredForCatalog();
        BigDecimal dolarBilleteAplicado = dollarQuotationResolver.resolve(cotizacionDolarHeader, cotizacionDolar, configuration);
        BlockedCatalogProductService.BlockedCatalogFilter blockedFilter = blockedCatalogProductService.currentFilter();
        List<TiendaPorteExternalProduct> products = filterBlockedProducts(catalogCacheService.getProducts(), blockedFilter.externalProductIds());
        List<TiendaPorteBrandResponse> catalog = buildCatalog(mainCategoryProducts(products), configuration, dolarBilleteAplicado, null);
        return addSupplierSheetMissingProducts(catalog, configuration, dolarBilleteAplicado, null, blockedFilter.normalizedModelKeys());
    }

    @Override
    public TiendaPorteCategoryPageResponse getByCategory(
            String categoria,
            Integer page,
            Integer limit,
            BigDecimal cotizacionDolar,
            String cotizacionDolarHeader
    ) {
        String normalizedCategory = normalizeAllowedCategory(categoria);
        int safePage = validatePage(page);
        int safeLimit = validateLimit(limit);

        PriceConfiguration configuration = priceConfigurationService.getRequiredForCatalog();
        BigDecimal dolarBilleteAplicado = dollarQuotationResolver.resolve(cotizacionDolarHeader, cotizacionDolar, configuration);
        BlockedCatalogProductService.BlockedCatalogFilter blockedFilter = blockedCatalogProductService.currentFilter();
        if (isSupplierOnlyCategory(normalizedCategory)) {
            return supplierOnlyCategoryResponse(normalizedCategory, safePage, safeLimit, configuration, dolarBilleteAplicado, blockedFilter.normalizedModelKeys());
        }

        List<TiendaPorteExternalProduct> products = productsByCategory(
                filterBlockedProducts(catalogCacheService.getProducts(), blockedFilter.externalProductIds()),
                normalizedCategory
        );
        List<TiendaPorteBrandResponse> data = buildCatalog(products, configuration, dolarBilleteAplicado, null);
        data = addSupplierSheetMissingProducts(data, configuration, dolarBilleteAplicado, normalizedCategory, blockedFilter.normalizedModelKeys());

        int total = countModels(data);
        int totalPages = totalPages(total, safeLimit);
        return new TiendaPorteCategoryPageResponse(
                normalizedCategory,
                safePage,
                safeLimit,
                total,
                totalPages,
                safePage < totalPages,
                paginateCatalog(data, safePage, safeLimit)
        );
    }

    private String normalizeAllowedCategory(String categoria) {
        if (isBlank(categoria)) {
            throw new TiendaPorteBadRequestException("La categoria es obligatoria");
        }
        String allowedCategory = catalogProductPolicy.canonicalizeRequestedCategory(categoria);
        if (allowedCategory == null || !ALLOWED_CATEGORIES.contains(allowedCategory)) {
            throw new TiendaPorteBadRequestException("Categoria no permitida: " + categoria);
        }
        return allowedCategory;
    }

    private boolean isSupplierOnlyCategory(String category) {
        return SUPPLIER_ONLY_CATEGORIES.contains(category);
    }

    private TiendaPorteCategoryPageResponse supplierOnlyCategoryResponse(
            String category,
            int page,
            int limit,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado,
            Set<String> blockedNormalizedModelKeys
    ) {
        List<TiendaPorteBrandResponse> sheetData = addSupplierSheetMissingProducts(List.of(), configuration, dolarBilleteAplicado, category, blockedNormalizedModelKeys);
        int total = countModels(sheetData);
        int totalPages = totalPages(total, limit);
        return new TiendaPorteCategoryPageResponse(
                category,
                page,
                limit,
                total,
                totalPages,
                page < totalPages,
                paginateCatalog(sheetData, page, limit)
        );
    }

    private List<TiendaPorteExternalProduct> mainCategoryProducts(List<TiendaPorteExternalProduct> products) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .filter(product -> product != null && !catalogProductPolicy.isExcludedProduct(product))
                .filter(product -> MAIN_TIENDA_PORTE_CATEGORIES.contains(brandName(product, null)))
                .toList();
    }

    private List<TiendaPorteExternalProduct> productsByCategory(List<TiendaPorteExternalProduct> products, String category) {
        if (products == null || products.isEmpty()) {
            return List.of();
        }
        return products.stream()
                .filter(product -> product != null && !catalogProductPolicy.isExcludedProduct(product))
                .filter(product -> category.equals(brandName(product, null)))
                .toList();
    }

    private int countModels(List<TiendaPorteBrandResponse> data) {
        if (data == null || data.isEmpty()) {
            return 0;
        }
        return data.stream()
                .filter(brand -> brand != null && brand.getModelos() != null)
                .mapToInt(brand -> brand.getModelos().size())
                .sum();
    }

    private int validatePage(Integer page) {
        int safePage = page == null ? DEFAULT_CATEGORY_PAGE : page;
        if (safePage < 1) {
            throw new TiendaPorteBadRequestException("page debe ser mayor o igual a 1");
        }
        return safePage;
    }

    private int validateLimit(Integer limit) {
        int safeLimit = limit == null ? DEFAULT_CATEGORY_LIMIT : limit;
        if (safeLimit < 1) {
            throw new TiendaPorteBadRequestException("limit debe ser mayor o igual a 1");
        }
        return Math.min(safeLimit, MAX_CATEGORY_LIMIT);
    }

    private int totalPages(int total, int limit) {
        if (total <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) total / limit);
    }

    private List<TiendaPorteBrandResponse> paginateCatalog(List<TiendaPorteBrandResponse> catalog, int page, int limit) {
        if (catalog == null || catalog.isEmpty()) {
            return List.of();
        }
        List<ModelPageEntry> entries = new ArrayList<>();
        for (TiendaPorteBrandResponse brand : catalog) {
            if (brand == null || brand.getModelos() == null) {
                continue;
            }
            for (TiendaPorteModelResponse model : brand.getModelos()) {
                if (model != null) {
                    entries.add(new ModelPageEntry(brand.getMarca(), model));
                }
            }
        }
        int fromIndex = Math.max(0, (page - 1) * limit);
        if (fromIndex >= entries.size()) {
            return List.of();
        }
        int toIndex = Math.min(entries.size(), fromIndex + limit);
        Map<String, List<TiendaPorteModelResponse>> modelsByBrand = new LinkedHashMap<>();
        for (ModelPageEntry entry : entries.subList(fromIndex, toIndex)) {
            modelsByBrand.computeIfAbsent(entry.brandName(), key -> new ArrayList<>()).add(entry.model());
        }
        return modelsByBrand.entrySet().stream()
                .map(entry -> new TiendaPorteBrandResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    List<TiendaPorteBrandResponse> buildCatalog(
            List<TiendaPorteExternalProduct> products,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado,
            String requestedCategory
    ) {
        Map<String, BrandAccumulator> brandsByKey = new HashMap<>();
        Map<String, ModelAccumulator> modelsByKey = new HashMap<>();
        if (products == null) {
            return List.of();
        }

        CatalogProductPolicy.SelectionResult selectionResult = catalogProductPolicy.selectPublishableTiendaPorteProducts(products, requestedCategory);
        logProcessingSummary(selectionResult.stats());

        for (CatalogProductPolicy.ProductSelection selection : selectionResult.selections()) {
            TiendaPorteExternalProduct product = selection.product();
            String modelName = modelName(product);
            if (isBlank(modelName)) {
                continue;
            }

            String brandName = selection.canonicalCategory();
            String brandKey = normalizedKey(brandName, DEFAULT_BRAND);
            List<ModelPriceGroup> modelPriceGroups = priceGroups(product, priceUsd(product));
            for (ModelPriceGroup priceGroup : modelPriceGroups) {
                String modelKey = modelKey(product, brandKey, modelName, priceGroup.priceUsd());
                ModelAccumulator model = modelsByKey.get(modelKey);
                if (model == null) {
                    BrandAccumulator brand = brandsByKey.computeIfAbsent(brandKey, key -> new BrandAccumulator(brandName));
                    model = new ModelAccumulator(modelName, priceGroup.priceUsd());
                    brand.modelsByKey.put(modelKey, model);
                    modelsByKey.put(modelKey, model);
                }
                model.mergeColors(priceGroup.colors());
            }
        }

        return brandsByKey.values().stream()
                .sorted(Comparator.comparing(BrandAccumulator::getBrandName, String.CASE_INSENSITIVE_ORDER))
                .map(brand -> brand.toResponse(configuration, dolarBilleteAplicado, priceCalculator))
                .toList();
    }

    private void logProcessingSummary(CatalogProductPolicy.SelectionStats stats) {
        if (stats == null) {
            return;
        }
        LOGGER.info(
                "Catalog processing summary: totalReceived={} excludedProducts={} excludedCajaManchada={} preservedArticulosVarios={} deduplicatedProducts={} totalPublished={}",
                stats.totalReceived(),
                stats.excludedProducts(),
                stats.excludedCajaManchada(),
                stats.preservedArticulosVarios(),
                stats.deduplicatedProducts(),
                stats.totalPublished()
        );
    }

    private List<TiendaPorteBrandResponse> addSupplierSheetMissingProducts(
            List<TiendaPorteBrandResponse> catalog,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado,
            String categoryFilter,
            Set<String> blockedNormalizedModelKeys
    ) {
        List<SupplierSheetProduct> supplierProducts = supplierSheetService.getProducts();
        if (supplierProducts.isEmpty()) {
            return catalog;
        }

        List<TiendaPorteBrandResponse> response = mutableCatalog(catalog);
        Map<String, TiendaPorteBrandResponse> brandsByKey = brandsByKey(response);
        Map<String, Set<String>> duplicateKeysByBrand = duplicateKeysByBrand(response);
        String normalizedCategoryFilter = TiendaPorteTextUtils.normalize(categoryFilter);
        Set<String> addedSupplierKeys = new LinkedHashSet<>();
        int added = 0;

        for (SupplierSheetProduct product : supplierProducts) {
            if (catalogProductPolicy.isExcludedProduct(product)) {
                continue;
            }

            String brandName = supplierBrandName(product);
            if (isBlockedSupplierProduct(product, brandName, blockedNormalizedModelKeys)) {
                continue;
            }

            String brandKey = normalizedKey(brandName, DEFAULT_BRAND);
            if (!normalizedCategoryFilter.isBlank() && !brandKey.equals(normalizedCategoryFilter)) {
                continue;
            }

            Set<String> productKeys = duplicateKeys(brandName, product.originalBrand(), product.modelName());
            Set<String> existingKeys = duplicateKeysByBrand.computeIfAbsent(brandKey, key -> new LinkedHashSet<>());
            if (productKeys.stream().anyMatch(existingKeys::contains)) {
                continue;
            }
            if (!addedSupplierKeys.add(supplierUniqueKey(brandName, product.modelName(), product.priceUsd()))) {
                continue;
            }

            TiendaPorteBrandResponse brandResponse = brandsByKey.computeIfAbsent(
                    brandKey,
                    key -> {
                        TiendaPorteBrandResponse created = new TiendaPorteBrandResponse(brandName, new ArrayList<>());
                        response.add(created);
                        return created;
                    }
            );
            brandResponse.getModelos().add(toSupplierModelResponse(product, configuration, dolarBilleteAplicado));
            existingKeys.addAll(productKeys);
            added++;
        }

        LOGGER.info("Supplier Sheet: {} productos faltantes agregados al catalogo{}", added, categoryFilter == null ? "" : " para categoria " + categoryFilter);
        return sortCatalog(response);
    }

    private List<TiendaPorteExternalProduct> filterBlockedProducts(List<TiendaPorteExternalProduct> products, Set<Long> blockedExternalProductIds) {
        if (products == null || products.isEmpty() || blockedExternalProductIds == null || blockedExternalProductIds.isEmpty()) {
            return products == null ? List.of() : products;
        }
        return products.stream()
                .filter(product -> !blockedCatalogProductService.isBlockedTiendaPorteProduct(product, blockedExternalProductIds))
                .toList();
    }

    private boolean isBlockedSupplierProduct(SupplierSheetProduct product, String brandName, Set<String> blockedNormalizedModelKeys) {
        if (blockedNormalizedModelKeys == null || blockedNormalizedModelKeys.isEmpty()) {
            return false;
        }
        String normalizedModelKey = blockedCatalogProductService.normalizedModelKey(brandName, product == null ? null : product.modelName());
        return !normalizedModelKey.isBlank() && blockedNormalizedModelKeys.contains(normalizedModelKey);
    }

    private List<TiendaPorteBrandResponse> mutableCatalog(List<TiendaPorteBrandResponse> catalog) {
        if (catalog == null || catalog.isEmpty()) {
            return new ArrayList<>();
        }
        List<TiendaPorteBrandResponse> response = new ArrayList<>();
        for (TiendaPorteBrandResponse brand : catalog) {
            if (brand == null) {
                continue;
            }
            List<TiendaPorteModelResponse> models = brand.getModelos() == null
                    ? new ArrayList<>()
                    : new ArrayList<>(brand.getModelos());
            response.add(new TiendaPorteBrandResponse(brand.getMarca(), models));
        }
        return response;
    }

    private Map<String, TiendaPorteBrandResponse> brandsByKey(List<TiendaPorteBrandResponse> catalog) {
        Map<String, TiendaPorteBrandResponse> brandsByKey = new LinkedHashMap<>();
        for (TiendaPorteBrandResponse brand : catalog) {
            if (brand != null) {
                brandsByKey.put(normalizedKey(brand.getMarca(), DEFAULT_BRAND), brand);
            }
        }
        return brandsByKey;
    }

    private Map<String, Set<String>> duplicateKeysByBrand(List<TiendaPorteBrandResponse> catalog) {
        Map<String, Set<String>> keysByBrand = new HashMap<>();
        for (TiendaPorteBrandResponse brand : catalog) {
            if (brand == null || brand.getModelos() == null) {
                continue;
            }
            String brandKey = normalizedKey(brand.getMarca(), DEFAULT_BRAND);
            Set<String> brandKeys = keysByBrand.computeIfAbsent(brandKey, key -> new LinkedHashSet<>());
            for (TiendaPorteModelResponse model : brand.getModelos()) {
                if (model != null) {
                    brandKeys.addAll(duplicateKeys(brand.getMarca(), brand.getMarca(), model.getModeloNombre()));
                }
            }
        }
        return keysByBrand;
    }

    private String supplierBrandName(SupplierSheetProduct product) {
        return catalogProductPolicy.resolveCanonicalCategory(product, null);
    }

    private Set<String> duplicateKeys(String responseBrand, String originalBrand, String modelName) {
        Set<String> keys = new LinkedHashSet<>();
        String normalizedModel = TiendaPorteTextUtils.normalize(modelName);
        if (normalizedModel.isBlank()) {
            return keys;
        }

        String responseBrandKey = TiendaPorteTextUtils.normalize(responseBrand);
        String originalBrandKey = TiendaPorteTextUtils.normalize(originalBrand);
        String strippedModel = stripKnownBrandPrefix(normalizedModel, responseBrandKey, originalBrandKey);

        addKey(keys, normalizedModel);
        addKey(keys, strippedModel);
        addPrefixedKey(keys, responseBrandKey, strippedModel);
        addPrefixedKey(keys, originalBrandKey, strippedModel);

        if (isXiaomiFamily(responseBrandKey) || isXiaomiFamily(originalBrandKey) || startsWithXiaomiFamilyPrefix(normalizedModel)) {
            for (String prefix : XIAOMI_FAMILY_PREFIXES) {
                addPrefixedKey(keys, prefix, strippedModel);
            }
        }

        return keys;
    }

    private String supplierUniqueKey(String brandName, String modelName, BigDecimal priceUsd) {
        return normalizedKey(brandName, DEFAULT_BRAND)
                + "|"
                + TiendaPorteTextUtils.normalize(modelName)
                + "|"
                + normalizedPriceKey(priceUsd)
                + "|"
                + CatalogProductOrigin.GOOGLE_SHEET;
    }

    private String stripKnownBrandPrefix(String normalizedModel, String responseBrandKey, String originalBrandKey) {
        List<String> prefixes = new ArrayList<>();
        if (responseBrandKey != null && !responseBrandKey.isBlank()) {
            prefixes.add(responseBrandKey);
        }
        if (originalBrandKey != null && !originalBrandKey.isBlank()) {
            prefixes.add(originalBrandKey);
        }
        prefixes.addAll(XIAOMI_FAMILY_PREFIXES);

        return prefixes.stream()
                .distinct()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(prefix -> normalizedModel.startsWith(prefix) && normalizedModel.length() > prefix.length())
                .findFirst()
                .map(prefix -> normalizedModel.substring(prefix.length()))
                .orElse(normalizedModel);
    }

    private boolean startsWithXiaomiFamilyPrefix(String normalizedModel) {
        return XIAOMI_FAMILY_PREFIXES.stream().anyMatch(prefix -> normalizedModel.startsWith(prefix) && normalizedModel.length() > prefix.length());
    }

    private boolean isXiaomiFamily(String normalizedBrand) {
        return XIAOMI_FAMILY_PREFIXES.contains(normalizedBrand);
    }

    private void addPrefixedKey(Set<String> keys, String prefix, String value) {
        if (prefix != null && !prefix.isBlank() && value != null && !value.isBlank()) {
            addKey(keys, prefix + value);
        }
    }

    private void addKey(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) {
            keys.add(key);
        }
    }

    private TiendaPorteModelResponse toSupplierModelResponse(
            SupplierSheetProduct product,
            PriceConfiguration configuration,
            BigDecimal dolarBilleteAplicado
    ) {
        TiendaPorteCalculatedPrices prices = priceCalculator.calculate(product.priceUsd(), configuration, dolarBilleteAplicado);
        List<TiendaPorteColorStockResponse> colors = product.colors() == null
                ? List.of()
                : product.colors().stream()
                .filter(color -> !isBlank(color))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(color -> new TiendaPorteColorStockResponse(color.trim(), null))
                .toList();

        return new TiendaPorteModelResponse(
                product.modelName(),
                CatalogProductOrigin.GOOGLE_SHEET,
                colors,
                prices.getPrecioUsd(),
                prices.getPrecioPesos(),
                prices.getPrecioUsdt(),
                prices.getPrecioTransferenciaBancaria(),
                prices.getPrecioTarjeta3Pagos(),
                prices.getPrecioTarjeta6Pagos(),
                prices.getPrecioTarjeta12Pagos()
        );
    }

    private List<TiendaPorteBrandResponse> sortCatalog(List<TiendaPorteBrandResponse> catalog) {
        return catalog.stream()
                .filter(brand -> brand != null && !isBlank(brand.getMarca()))
                .sorted(Comparator.comparing(TiendaPorteBrandResponse::getMarca, String.CASE_INSENSITIVE_ORDER))
                .map(brand -> new TiendaPorteBrandResponse(
                        brand.getMarca(),
                        sortModels(brand.getModelos())
                ))
                .toList();
    }

    private List<TiendaPorteModelResponse> sortModels(List<TiendaPorteModelResponse> models) {
        if (models == null || models.isEmpty()) {
            return List.of();
        }
        return models.stream()
                .filter(model -> model != null && !isBlank(model.getModeloNombre()))
                .sorted(Comparator.comparing(TiendaPorteModelResponse::getPrecioUsd, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TiendaPorteModelResponse::getModeloNombre, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(this::firstColorName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String firstColorName(TiendaPorteModelResponse model) {
        if (model == null || model.getColores() == null || model.getColores().isEmpty()) {
            return "";
        }
        return model.getColores().stream()
                .map(TiendaPorteColorStockResponse::getColor)
                .filter(color -> !isBlank(color))
                .min(String.CASE_INSENSITIVE_ORDER)
                .orElse("");
    }

    private List<TiendaPorteBrandResponse> filterBySearch(List<TiendaPorteBrandResponse> catalog, String normalizedQuery) {
        List<SearchMatch> matches = new ArrayList<>();
        for (TiendaPorteBrandResponse brand : catalog) {
            for (TiendaPorteModelResponse model : brand.getModelos()) {
                int priority = searchPriority(brand.getMarca(), model.getModeloNombre(), normalizedQuery);
                if (priority != NO_MATCH) {
                    matches.add(new SearchMatch(priority, brand.getMarca(), model));
                }
            }
        }

        matches.sort(Comparator.comparingInt(SearchMatch::priority)
                .thenComparing(SearchMatch::brandName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(match -> match.model().getPrecioUsd(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(match -> match.model().getModeloNombre(), String.CASE_INSENSITIVE_ORDER));

        Map<String, TiendaPorteBrandResponse> responseByBrand = new LinkedHashMap<>();
        for (SearchMatch match : matches) {
            TiendaPorteBrandResponse brandResponse = responseByBrand.computeIfAbsent(
                    match.brandName(),
                    brandName -> new TiendaPorteBrandResponse(brandName, new ArrayList<>())
            );
            brandResponse.getModelos().add(match.model());
        }
        return new ArrayList<>(responseByBrand.values());
    }

    private int searchPriority(String brand, String model, String normalizedQuery) {
        String normalizedBrand = TiendaPorteTextUtils.normalize(brand);
        String normalizedModel = TiendaPorteTextUtils.normalize(model);

        if (normalizedBrand.equals(normalizedQuery) || normalizedModel.equals(normalizedQuery)) {
            return 1;
        }
        if (normalizedBrand.startsWith(normalizedQuery) || normalizedModel.startsWith(normalizedQuery)) {
            return 2;
        }
        if (normalizedBrand.contains(normalizedQuery) || normalizedModel.contains(normalizedQuery)) {
            return 3;
        }
        if (normalizedQuery.length() >= 4 && isApproximateMatch(brand, model, normalizedQuery)) {
            return 4;
        }
        return NO_MATCH;
    }

    private boolean isApproximateMatch(String brand, String model, String normalizedQuery) {
        int maxDistance = TiendaPorteTextUtils.maxAllowedDistance(normalizedQuery);
        return TiendaPorteTextUtils.approximateCandidates(brand, model, normalizedQuery).stream()
                .filter(candidate -> candidate.length() >= Math.max(3, normalizedQuery.length() - maxDistance))
                .anyMatch(candidate -> TiendaPorteTextUtils.isWithinLevenshteinDistance(normalizedQuery, candidate, maxDistance));
    }

    private String brandName(TiendaPorteExternalProduct product, String requestedCategory) {
        return catalogProductPolicy.resolveCanonicalCategory(product, requestedCategory);
    }

    private String modelName(TiendaPorteExternalProduct product) {
        String productReferenceName = product.getProductReference() == null ? null : product.getProductReference().getName();
        String selected = firstNonBlank(productReferenceName, product.getName());
        return selected == null ? null : selected.trim();
    }

    private BigDecimal priceUsd(TiendaPorteExternalProduct product) {
        TiendaPorteProductReference productReference = product.getProductReference();
        BigDecimal price = parseNonNegativeMoney(productReference == null ? null : productReference.getPriceUsd());
        return price == null ? MONEY_ZERO : price;
    }

    private List<ModelPriceGroup> priceGroups(TiendaPorteExternalProduct product, BigDecimal fallbackPriceUsd) {
        BigDecimal safeFallbackPriceUsd = normalizeMoney(fallbackPriceUsd);
        List<ColorStockValue> colors = colorStockValues(product.getColorStock());
        if (colors.isEmpty()) {
            return List.of(new ModelPriceGroup(safeFallbackPriceUsd, List.of()));
        }

        Map<String, BigDecimal> colorPricesByColor = colorPricesByColor(product.getProductReference());
        Map<BigDecimal, List<ColorStockValue>> colorsByPrice = new HashMap<>();
        for (ColorStockValue color : colors) {
            BigDecimal price = priceForColor(color.colorName(), colorPricesByColor, safeFallbackPriceUsd);
            colorsByPrice.computeIfAbsent(price, key -> new ArrayList<>()).add(color);
        }

        return colorsByPrice.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ModelPriceGroup(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<ColorStockValue> colorStockValues(Map<String, Integer> colorStock) {
        if (colorStock == null || colorStock.isEmpty()) {
            return List.of();
        }
        List<ColorStockValue> colors = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : colorStock.entrySet()) {
            if (!isBlank(entry.getKey())) {
                colors.add(new ColorStockValue(entry.getKey().trim(), normalizeStock(entry.getValue())));
            }
        }
        return colors;
    }

    private Map<String, BigDecimal> colorPricesByColor(TiendaPorteProductReference productReference) {
        Map<String, Object> colorPrices = productReference == null ? null : productReference.getColorPrices();
        if (colorPrices == null || colorPrices.isEmpty()) {
            return Map.of();
        }

        Map<String, BigDecimal> parsedPrices = new HashMap<>();
        for (Map.Entry<String, Object> entry : colorPrices.entrySet()) {
            if (isBlank(entry.getKey())) {
                continue;
            }
            BigDecimal price = parseNonNegativeMoney(entry.getValue());
            if (price != null) {
                parsedPrices.put(colorKey(entry.getKey()), price);
            }
        }
        return parsedPrices;
    }

    private BigDecimal priceForColor(
            String colorName,
            Map<String, BigDecimal> colorPricesByColor,
            BigDecimal fallbackPriceUsd
    ) {
        BigDecimal colorPrice = colorPricesByColor.get(colorKey(colorName));
        return colorPrice == null ? fallbackPriceUsd : colorPrice;
    }

    private BigDecimal parseNonNegativeMoney(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number || value instanceof CharSequence) {
            return parseNonNegativeMoney(value.toString());
        }
        return null;
    }

    private BigDecimal parseNonNegativeMoney(String value) {
        if (isBlank(value)) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value.trim());
            if (price.signum() < 0) {
                return null;
            }
            return price.setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            return MONEY_ZERO;
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static int normalizeStock(Integer rawStock) {
        return rawStock == null || rawStock < 0 ? 0 : rawStock;
    }

    private static String colorKey(String colorName) {
        if (colorName == null) {
            return "";
        }
        String normalized = TiendaPorteTextUtils.normalize(colorName);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return colorName.trim().toLowerCase(Locale.ROOT);
    }

    private String modelKey(TiendaPorteExternalProduct product, String brandKey, String modelName, BigDecimal priceUsd) {
        TiendaPorteProductReference productReference = product.getProductReference();
        String priceKey = normalizedPriceKey(priceUsd);
        if (productReference != null && productReference.getId() != null) {
            return brandKey + "|ref:" + productReference.getId() + "|" + priceKey;
        }
        return brandKey + "|name:" + normalizedKey(modelName, modelName) + "|" + priceKey;
    }

    private String normalizedPriceKey(BigDecimal priceUsd) {
        return normalizeMoney(priceUsd).toPlainString();
    }

    private String normalizedKey(String value, String fallback) {
        String normalized = TiendaPorteTextUtils.normalize(value);
        if (!normalized.isBlank()) {
            return normalized;
        }
        return fallback == null ? "" : fallback.trim().toLowerCase(Locale.ROOT);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static class BrandAccumulator {

        private final String brandName;
        private final Map<String, ModelAccumulator> modelsByKey = new HashMap<>();

        private BrandAccumulator(String brandName) {
            this.brandName = brandName;
        }

        private String getBrandName() {
            return brandName;
        }

        private TiendaPorteBrandResponse toResponse(
                PriceConfiguration configuration,
                BigDecimal dolarBilleteAplicado,
                TiendaPortePriceCalculator priceCalculator
        ) {
            List<TiendaPorteModelResponse> models = modelsByKey.values().stream()
                    .sorted(Comparator.comparing(ModelAccumulator::getPriceUsd)
                            .thenComparing(ModelAccumulator::getModelName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(ModelAccumulator::firstColorName, String.CASE_INSENSITIVE_ORDER))
                    .map(model -> model.toResponse(configuration, dolarBilleteAplicado, priceCalculator))
                    .toList();
            return new TiendaPorteBrandResponse(brandName, models);
        }
    }

    private static class ModelAccumulator {

        private final String modelName;
        private final Map<String, ColorAccumulator> colorsByKey = new HashMap<>();
        private final BigDecimal priceUsd;

        private ModelAccumulator(String modelName, BigDecimal priceUsd) {
            this.modelName = modelName;
            this.priceUsd = normalizeMoney(priceUsd);
        }

        private String getModelName() {
            return modelName;
        }

        private BigDecimal getPriceUsd() {
            return priceUsd;
        }

        private String firstColorName() {
            return colorsByKey.values().stream()
                    .map(ColorAccumulator::getColorName)
                    .min(String.CASE_INSENSITIVE_ORDER)
                    .orElse("");
        }

        private void mergeColors(List<ColorStockValue> colors) {
            if (colors == null) {
                return;
            }
            colors.forEach(color -> mergeColor(color.colorName(), color.stock()));
        }

        private void mergeColor(String colorName, int stock) {
            String colorKey = colorKey(colorName);
            ColorAccumulator current = colorsByKey.get(colorKey);
            if (current == null) {
                colorsByKey.put(colorKey, new ColorAccumulator(colorName, stock));
                return;
            }
            current.stock = Math.max(current.stock, stock);
        }

        private TiendaPorteModelResponse toResponse(
                PriceConfiguration configuration,
                BigDecimal dolarBilleteAplicado,
                TiendaPortePriceCalculator priceCalculator
        ) {
            TiendaPorteCalculatedPrices prices = priceCalculator.calculate(priceUsd, configuration, dolarBilleteAplicado);
            List<TiendaPorteColorStockResponse> colors = colorsByKey.values().stream()
                    .sorted(Comparator.comparing(ColorAccumulator::getColorName, String.CASE_INSENSITIVE_ORDER))
                    .map(ColorAccumulator::toResponse)
                    .toList();
            return new TiendaPorteModelResponse(
                    modelName,
                    CatalogProductOrigin.TIENDA_PORTE,
                    colors,
                    prices.getPrecioUsd(),
                    prices.getPrecioPesos(),
                    prices.getPrecioUsdt(),
                    prices.getPrecioTransferenciaBancaria(),
                    prices.getPrecioTarjeta3Pagos(),
                    prices.getPrecioTarjeta6Pagos(),
                    prices.getPrecioTarjeta12Pagos()
            );
        }
    }

    private static class ColorAccumulator {

        private final String colorName;
        private int stock;

        private ColorAccumulator(String colorName, int stock) {
            this.colorName = colorName;
            this.stock = stock;
        }

        private String getColorName() {
            return colorName;
        }

        private TiendaPorteColorStockResponse toResponse() {
            return new TiendaPorteColorStockResponse(colorName, stock);
        }
    }

    private record ModelPriceGroup(BigDecimal priceUsd, List<ColorStockValue> colors) {
    }

    private record ColorStockValue(String colorName, int stock) {
    }

    private record SearchMatch(int priority, String brandName, TiendaPorteModelResponse model) {
    }

    private record ModelPageEntry(String brandName, TiendaPorteModelResponse model) {
    }
}
