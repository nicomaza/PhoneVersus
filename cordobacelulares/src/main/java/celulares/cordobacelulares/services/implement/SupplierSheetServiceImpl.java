package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.config.SupplierSheetProperties;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetPreviewProductResponse;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetPreviewResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.services.SupplierSheetService;
import celulares.cordobacelulares.utils.TiendaPorteTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class SupplierSheetServiceImpl implements SupplierSheetService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupplierSheetServiceImpl.class);
    private static final int MIN_COLUMNS = 4;

    private static final Map<String, BrandMapping> BRAND_MAPPINGS = brandMappings();

    private final SupplierSheetProperties properties;
    private final HttpClient httpClient;
    private final CatalogProductPolicy catalogProductPolicy;
    private final Object cacheLock = new Object();

    private List<SupplierSheetProduct> cachedProducts = List.of();
    private SupplierSheetStats cachedStats = SupplierSheetStats.empty();
    private Instant cacheExpiresAt = Instant.EPOCH;
    private boolean cacheLoaded = false;

    public SupplierSheetServiceImpl(
            SupplierSheetProperties properties,
            HttpClient tiendaPorteHttpClient,
            CatalogProductPolicy catalogProductPolicy
    ) {
        this.properties = properties;
        this.httpClient = tiendaPorteHttpClient;
        this.catalogProductPolicy = catalogProductPolicy;
    }

    @Override
    public List<SupplierSheetProduct> getProducts() {
        return loadProducts().snapshot().products();
    }

    @Override
    public int reload() {
        if (!properties.isEnabled()) {
            synchronized (cacheLock) {
                cachedProducts = List.of();
                cachedStats = SupplierSheetStats.empty();
                cacheLoaded = true;
                cacheExpiresAt = Instant.now().plus(cacheTtl());
            }
            return 0;
        }
        synchronized (cacheLock) {
            cachedProducts = List.of();
            cachedStats = SupplierSheetStats.empty();
            cacheLoaded = false;
            cacheExpiresAt = Instant.EPOCH;
        }
        return refreshWithFallback().products().size();
    }

    @Override
    public SupplierSheetPreviewResponse preview() {
        LoadResult loadResult = loadProducts();
        SupplierSheetSnapshot snapshot = loadResult.snapshot();
        List<SupplierSheetPreviewProductResponse> sample = snapshot.products().stream()
                .limit(20)
                .map(product -> new SupplierSheetPreviewProductResponse(
                        product.originalBrand(),
                        product.responseBrand(),
                        product.modelName(),
                        CatalogProductOrigin.GOOGLE_SHEET,
                        product.colors(),
                        product.priceUsd()
                ))
                .toList();

        return new SupplierSheetPreviewResponse(
                properties.isEnabled(),
                properties.getCsvUrl() != null && !properties.getCsvUrl().isBlank(),
                loadResult.loadedFromCache(),
                snapshot.stats().totalRows(),
                snapshot.products().size(),
                snapshot.stats().ignoredRows(),
                snapshot.stats().byCategory(),
                sample
        );
    }

    private LoadResult loadProducts() {
        if (!properties.isEnabled()) {
            return new LoadResult(new SupplierSheetSnapshot(List.of(), SupplierSheetStats.empty()), false);
        }
        synchronized (cacheLock) {
            if (cacheLoaded && Instant.now().isBefore(cacheExpiresAt)) {
                return new LoadResult(new SupplierSheetSnapshot(cachedProducts, cachedStats), true);
            }
        }
        return new LoadResult(refreshWithFallback(), false);
    }

    private SupplierSheetSnapshot refreshWithFallback() {
        try {
            LOGGER.info("Supplier Sheet enabled={}, url configurada={}", properties.isEnabled(), sanitizeUrl(properties.getCsvUrl()));
            ParseResult parseResult = parseCsv(downloadCsv());
            List<SupplierSheetProduct> products = mergeDuplicateRows(parseResult.products());
            SupplierSheetStats stats = SupplierSheetStats.from(parseResult.totalRows(), parseResult.ignoredRows(), products);
            LOGGER.info(
                    "Supplier Sheet parseado: filas={}, validas={}, ignoradas={}, porCategoria={}",
                    stats.totalRows(),
                    products.size(),
                    stats.ignoredRows(),
                    stats.byCategory()
            );
            synchronized (cacheLock) {
                cachedProducts = List.copyOf(products);
                cachedStats = stats;
                cacheLoaded = true;
                cacheExpiresAt = Instant.now().plus(cacheTtl());
                return new SupplierSheetSnapshot(cachedProducts, cachedStats);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("No se pudo cargar el Google Sheet de proveedor externo: {}", ex.getMessage());
            synchronized (cacheLock) {
                return cacheLoaded
                        ? new SupplierSheetSnapshot(cachedProducts, cachedStats)
                        : new SupplierSheetSnapshot(List.of(), SupplierSheetStats.empty());
            }
        }
    }

    private String downloadCsv() {
        String csvUrl = properties.getCsvUrl();
        if (csvUrl == null || csvUrl.isBlank()) {
            throw new IllegalStateException("supplier-sheet.csv-url no configurado");
        }

        RuntimeException lastError = null;
        for (String candidateUrl : candidateCsvUrls(csvUrl)) {
            try {
                String body = downloadCsv(candidateUrl);
                if (looksLikeHtml(body)) {
                    LOGGER.warn("Supplier Sheet en {} parece HTML/login de Google, no CSV", sanitizeUrl(candidateUrl));
                    throw new IllegalStateException("No se pudo leer Supplier Sheet como CSV. Verificar que el Google Sheet sea publico o publicado como CSV.");
                }
                return body;
            } catch (RuntimeException ex) {
                lastError = ex;
                LOGGER.warn("No se pudo descargar Supplier Sheet desde {}: {}", sanitizeUrl(candidateUrl), ex.getMessage());
            }
        }
        throw lastError == null ? new IllegalStateException("No se pudo descargar Supplier Sheet") : lastError;
    }

    private String downloadCsv(String csvUrl) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(csvUrl.trim()))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "text/csv,text/plain,*/*")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("estado HTTP " + response.statusCode());
            }
            String body = response.body() == null ? "" : response.body();
            if (body.isBlank()) {
                throw new IllegalStateException("CSV vacio");
            }
            LOGGER.info("Supplier Sheet descargado desde {}: {} bytes", sanitizeUrl(csvUrl), body.getBytes(StandardCharsets.UTF_8).length);
            return body;
        } catch (HttpTimeoutException ex) {
            throw new IllegalStateException("timeout al descargar CSV", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("error de IO al descargar CSV", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("descarga interrumpida", ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("URL de CSV invalida", ex);
        }
    }

    private ParseResult parseCsv(String csv) {
        List<SupplierSheetProduct> products = new ArrayList<>();
        List<List<String>> rows = csvRecords(csv);
        int ignoredRows = 0;
        for (List<String> row : rows) {
            SupplierSheetProduct product = parseRow(row);
            if (product != null) {
                products.add(product);
            } else {
                ignoredRows++;
            }
        }
        return new ParseResult(products, rows.size(), ignoredRows);
    }

    private SupplierSheetProduct parseRow(List<String> row) {
        if (row.size() < MIN_COLUMNS) {
            return null;
        }
        String brandValue = clean(row.get(0));
        String modelValue = clean(row.get(1));
        String colorsValue = row.get(2);
        String priceValue = row.get(3);

        if (brandValue.isBlank() || modelValue.isBlank()) {
            return null;
        }

        BrandMapping brandMapping = mapBrand(brandValue);
        if (brandMapping == null) {
            return null;
        }

        BigDecimal priceUsd = parseUsdPrice(priceValue);
        if (priceUsd == null) {
            return null;
        }

        String modelName = buildModelName(brandMapping.originalBrand(), brandMapping.responseBrand(), modelValue);
        if (catalogProductPolicy.isExcludedProduct(modelName)) {
            return null;
        }

        return new SupplierSheetProduct(
                brandMapping.originalBrand(),
                brandMapping.responseBrand(),
                modelName,
                parseColors(colorsValue),
                priceUsd
        );
    }

    private BrandMapping mapBrand(String rawBrand) {
        String brandKey = TiendaPorteTextUtils.normalize(rawBrand);
        return BRAND_MAPPINGS.get(brandKey);
    }

    private String buildModelName(String originalBrand, String responseBrand, String modelValue) {
        String cleanModel = clean(modelValue);
        String prefix = ("REDMI".equals(originalBrand) || "POCO".equals(originalBrand)) ? originalBrand : responseBrand;
        if (TiendaPorteTextUtils.normalize(cleanModel).startsWith(TiendaPorteTextUtils.normalize(prefix))) {
            return cleanModel;
        }
        return prefix + " " + cleanModel;
    }

    private BigDecimal parseUsdPrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("-") || trimmed.contains("-")) {
            return null;
        }
        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.isBlank()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(digits).setScale(2, RoundingMode.HALF_UP);
            return price.signum() <= 0 ? null : price;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> parseColors(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        List<String> colors = new ArrayList<>();
        for (String color : value.split(",")) {
            String cleanColor = clean(color);
            if (!cleanColor.isBlank()) {
                colors.add(cleanColor);
            }
        }
        return List.copyOf(colors);
    }

    private List<SupplierSheetProduct> mergeDuplicateRows(List<SupplierSheetProduct> products) {
        Map<String, SupplierSheetProduct> productsByKey = new LinkedHashMap<>();
        for (SupplierSheetProduct product : products) {
            String key = TiendaPorteTextUtils.normalize(product.responseBrand())
                    + "|"
                    + TiendaPorteTextUtils.normalize(product.modelName())
                    + "|"
                    + product.priceUsd().toPlainString();

            SupplierSheetProduct current = productsByKey.get(key);
            if (current == null) {
                productsByKey.put(key, product);
                continue;
            }

            productsByKey.put(key, new SupplierSheetProduct(
                    current.originalBrand(),
                    current.responseBrand(),
                    current.modelName(),
                    mergeColors(current.colors(), product.colors()),
                    current.priceUsd()
            ));
        }
        return new ArrayList<>(productsByKey.values());
    }

    private List<String> mergeColors(List<String> left, List<String> right) {
        Set<String> merged = new LinkedHashSet<>();
        if (left != null) {
            left.stream().map(this::clean).filter(value -> !value.isBlank()).forEach(merged::add);
        }
        if (right != null) {
            right.stream().map(this::clean).filter(value -> !value.isBlank()).forEach(merged::add);
        }
        return List.copyOf(merged);
    }

    private List<List<String>> csvRecords(String csv) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;

        for (int i = 0; i < csv.length(); i++) {
            char current = csv.charAt(i);
            if (quoted) {
                if (current == '"') {
                    if (i + 1 < csv.length() && csv.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    cell.append(current);
                }
                continue;
            }

            if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (current == '\n') {
                row.add(cell.toString());
                cell.setLength(0);
                records.add(row);
                row = new ArrayList<>();
            } else if (current == '\r') {
                row.add(cell.toString());
                cell.setLength(0);
                records.add(row);
                row = new ArrayList<>();
                if (i + 1 < csv.length() && csv.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                cell.append(current);
            }
        }

        if (!cell.isEmpty() || !row.isEmpty()) {
            row.add(cell.toString());
            records.add(row);
        }

        return records;
    }

    private Duration cacheTtl() {
        return Duration.ofMinutes(Math.max(1, properties.getCacheMinutes()));
    }

    private String clean(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").trim();
    }

    private boolean looksLikeHtml(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        String lower = body.substring(0, Math.min(body.length(), 5000)).toLowerCase(Locale.ROOT);
        return lower.contains("<html")
                || lower.contains("<!doctype html")
                || lower.contains("accounts.google.com")
                || lower.contains("servicelogin")
                || lower.contains("google sheets");
    }

    private List<String> candidateCsvUrls(String configuredUrl) {
        List<String> candidates = new ArrayList<>();
        String trimmed = configuredUrl.trim();
        candidates.add(trimmed);
        String fallback = buildGvizFallbackUrl(trimmed);
        if (fallback != null && candidates.stream().noneMatch(fallback::equals)) {
            candidates.add(fallback);
        }
        return candidates;
    }

    private String buildGvizFallbackUrl(String configuredUrl) {
        String marker = "/spreadsheets/d/";
        int start = configuredUrl.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int idStart = start + marker.length();
        int idEnd = configuredUrl.indexOf('/', idStart);
        if (idEnd < 0) {
            idEnd = configuredUrl.indexOf('?', idStart);
        }
        if (idEnd < 0) {
            idEnd = configuredUrl.length();
        }
        String spreadsheetId = configuredUrl.substring(idStart, idEnd);
        if (spreadsheetId.isBlank()) {
            return null;
        }
        String gid = "0";
        int gidIndex = configuredUrl.indexOf("gid=");
        if (gidIndex >= 0) {
            int gidStart = gidIndex + 4;
            int gidEnd = configuredUrl.indexOf('&', gidStart);
            if (gidEnd < 0) {
                gidEnd = configuredUrl.indexOf('#', gidStart);
            }
            if (gidEnd < 0) {
                gidEnd = configuredUrl.length();
            }
            String configuredGid = configuredUrl.substring(gidStart, gidEnd);
            if (!configuredGid.isBlank()) {
                gid = configuredGid;
            }
        }
        return "https://docs.google.com/spreadsheets/d/" + spreadsheetId + "/gviz/tq?tqx=out:csv&gid=" + gid;
    }

    private String sanitizeUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.trim();
    }

    private static Map<String, BrandMapping> brandMappings() {
        Map<String, BrandMapping> mappings = new HashMap<>();
        putMapping(mappings, "IPHONE", "IPHONE");
        putMapping(mappings, "XIAOMI", "XIAOMI");
        putMapping(mappings, "REDMI", "XIAOMI");
        putMapping(mappings, "POCO", "XIAOMI");
        putMapping(mappings, "SAMSUNG", "SAMSUNG");
        putMapping(mappings, "MOTOROLA", "MOTOROLA");
        putMapping(mappings, "REALME", "REALME");
        putMapping(mappings, "INFINIX", "INFINIX");
        putMapping(mappings, "PRODUCTOS APPLE", "PRODUCTOS APPLE");
        putMapping(mappings, "ARTICULOS VARIOS", "ARTICULOS VARIOS");
        putMapping(mappings, "PERFUMES", "PERFUMES");
        putMapping(mappings, "HUAWEI", "HUAWEI");
        putMapping(mappings, "HONOR", "HONOR");
        putMapping(mappings, "OPPO", "OPPO");
        return Map.copyOf(mappings);
    }

    private static void putMapping(Map<String, BrandMapping> mappings, String originalBrand, String responseBrand) {
        mappings.put(
                TiendaPorteTextUtils.normalize(originalBrand),
                new BrandMapping(originalBrand.toUpperCase(Locale.ROOT), responseBrand)
        );
    }

    private record BrandMapping(String originalBrand, String responseBrand) {
    }

    private record ParseResult(List<SupplierSheetProduct> products, int totalRows, int ignoredRows) {
    }

    private record LoadResult(SupplierSheetSnapshot snapshot, boolean loadedFromCache) {
    }

    private record SupplierSheetSnapshot(List<SupplierSheetProduct> products, SupplierSheetStats stats) {
    }

    private record SupplierSheetStats(int totalRows, int ignoredRows, Map<String, Integer> byCategory) {

        private static SupplierSheetStats empty() {
            return new SupplierSheetStats(0, 0, Map.of());
        }

        private static SupplierSheetStats from(int totalRows, int ignoredRows, List<SupplierSheetProduct> products) {
            Map<String, Integer> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            for (SupplierSheetProduct product : products) {
                byCategory.merge(product.responseBrand(), 1, Integer::sum);
            }
            return new SupplierSheetStats(totalRows, ignoredRows, Map.copyOf(byCategory));
        }
    }
}
