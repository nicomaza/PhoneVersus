package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.seo.SeoCatalogProductResponse;
import celulares.cordobacelulares.dtos.seo.SeoProductPageView;
import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteBrandResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteColorStockResponse;
import celulares.cordobacelulares.dtos.tiendaporte.response.TiendaPorteModelResponse;
import celulares.cordobacelulares.exceptions.ApiInternalException;
import celulares.cordobacelulares.services.implement.CatalogProductPolicy;
import celulares.cordobacelulares.services.implement.ProductType;
import celulares.cordobacelulares.utils.SeoProductSlug;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SeoProductPageService {

    private static final String SITE_URL = "https://cordobacelulares.com";
    private static final String WHATSAPP_PHONE = "5493512129922";
    private static final BigDecimal SIX = BigDecimal.valueOf(6);
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final SeoCatalogService seoCatalogService;
    private final TiendaPorteService tiendaPorteService;
    private final SeoProductContentService productContentService;
    private final CatalogProductPolicy catalogProductPolicy;
    private final ObjectMapper objectMapper;

    public SeoProductPageService(
            SeoCatalogService seoCatalogService,
            TiendaPorteService tiendaPorteService,
            SeoProductContentService productContentService,
            CatalogProductPolicy catalogProductPolicy,
            ObjectMapper objectMapper
    ) {
        this.seoCatalogService = seoCatalogService;
        this.tiendaPorteService = tiendaPorteService;
        this.productContentService = productContentService;
        this.catalogProductPolicy = catalogProductPolicy;
        this.objectMapper = objectMapper;
    }

    public SeoProductPageView resolve(String requestedSlug) {
        SeoCatalogProductResponse historical = seoCatalogService.findBySlug(requestedSlug);
        String canonicalSlug = normalizedKnownSlug(historical.slug());

        if (!historical.active()) {
            return buildView(historical, canonicalSlug, null);
        }

        CurrentProduct current = findCurrentProduct(
                historical,
                tiendaPorteService.getAllowedCategories(null, null)
        );
        return buildView(historical, canonicalSlug, current);
    }

    private SeoProductPageView buildView(
            SeoCatalogProductResponse historical,
            String canonicalSlug,
            CurrentProduct current
    ) {
        boolean available = current != null;
        String brand = available ? cleanBrand(current.brand()) : cleanBrand(historical.marca());
        String model = available ? clean(current.model().getModeloNombre()) : clean(historical.modelo());
        ProductKind kind = productKind(brand, model);
        PublishedProductContent enriched = productContentService.published(canonicalSlug).orElse(null);
        String canonical = SITE_URL + "/celulares/" + canonicalSlug;
        String title = available
                ? model + ": precio en Córdoba | Córdoba Celulares"
                : model + " | Córdoba Celulares";
        String defaultDescription = available
                ? activeDescription(model, kind)
                : model + " de " + brand + ": producto no disponible actualmente en Córdoba Celulares.";
        String enrichedDescription = enriched == null ? "" : clean(enriched.description());
        String description = enrichedDescription.isBlank() ? defaultDescription : enrichedDescription;
        List<String> colors = available ? colorNames(current.model().getColores()) : List.of();

        BigDecimal cash = available ? current.model().getPrecioPesos() : null;
        BigDecimal transfer = available ? current.model().getPrecioTransferenciaBancaria() : null;
        BigDecimal card3 = available ? current.model().getPrecioTarjeta3Pagos() : null;
        BigDecimal card6 = available ? current.model().getPrecioTarjeta6Pagos() : null;

        List<PublishedProductContent.Image> images = enriched == null ? List.of() : enriched.images();
        List<String> absoluteImages = images.stream()
                .map(PublishedProductContent.Image::publicPath)
                .map(path -> path.startsWith("http://") || path.startsWith("https://") ? path : SITE_URL + path)
                .toList();
        String productJsonLd = productJsonLd(
                model,
                brand,
                canonical,
                available,
                cash,
                enrichedDescription.isBlank() ? null : enrichedDescription,
                absoluteImages
        );
        String breadcrumbJsonLd = breadcrumbJsonLd(model, brand, canonical);
        String whatsappUrl = buildWhatsappUrl(brand, model, current, colors);

        return new SeoProductPageView(
                canonicalSlug,
                brand,
                model,
                available,
                title,
                description,
                canonical,
                available ? kind.intro : null,
                formatMoneyOrNull(cash),
                formatMoneyOrNull(transfer),
                formatMoneyOrNull(card3),
                formatMoneyOrNull(card6),
                formatInstallmentOrNull(card6),
                colors,
                kind.conditionLabel,
                available ? kind.whatsappWarning : null,
                whatsappUrl,
                productJsonLd,
                breadcrumbJsonLd,
                enriched != null,
                enrichedDescription.isBlank() ? (available ? kind.intro : null) : enrichedDescription,
                images,
                enriched == null ? List.of() : enriched.highlights(),
                enriched == null ? List.of() : enriched.specSections(),
                enriched == null ? null : enriched.sourceUrl(),
                enriched == null ? null : enriched.sourceName(),
                absoluteImages.isEmpty() ? null : absoluteImages.get(0)
        );
    }

    private CurrentProduct findCurrentProduct(
            SeoCatalogProductResponse historical,
            List<TiendaPorteBrandResponse> catalog
    ) {
        if (catalog == null || catalog.isEmpty()) {
            return null;
        }

        List<CurrentProduct> slugMatches = new ArrayList<>();
        for (TiendaPorteBrandResponse brandResponse : catalog) {
            if (brandResponse == null || brandResponse.getModelos() == null) {
                continue;
            }
            String brand = cleanBrand(brandResponse.getMarca());
            for (TiendaPorteModelResponse model : brandResponse.getModelos()) {
                if (model == null || !historical.slug().equals(SeoProductSlug.fromModel(model.getModeloNombre()))) {
                    continue;
                }
                CurrentProduct candidate = new CurrentProduct(brand, model);
                if (sameIdentity(historical, candidate)) {
                    return candidate;
                }
                slugMatches.add(candidate);
            }
        }

        return slugMatches.size() == 1 ? slugMatches.get(0) : null;
    }

    private boolean sameIdentity(SeoCatalogProductResponse historical, CurrentProduct current) {
        return normalizeIdentity(historical.marca()).equals(normalizeIdentity(current.brand()))
                && normalizeIdentity(historical.modelo()).equals(
                normalizeIdentity(current.model().getModeloNombre())
        );
    }

    private String activeDescription(String model, ProductKind kind) {
        return switch (kind) {
            case PHONE -> "Consultá el precio actualizado del " + model
                    + " en Córdoba Celulares. Equipo nuevo, liberado y original. Efectivo, transferencia y tarjeta.";
            case MISCELLANEOUS -> "Consultá el precio actualizado del " + model
                    + " en Córdoba Celulares. Dispositivo nuevo, sellado de fábrica y con garantía.";
            case PERFUME -> "Consultá el precio actualizado del " + model
                    + " en Córdoba Celulares. En caja sellada de fábrica, sin abrir.";
            case OTHER -> "Consultá el precio actualizado del " + model
                    + " en Córdoba Celulares. Producto nuevo, original y con garantía.";
        };
    }

    private List<String> colorNames(List<TiendaPorteColorStockResponse> colors) {
        if (colors == null || colors.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (TiendaPorteColorStockResponse item : colors) {
            String color = item == null ? "" : clean(item.getColor());
            if (!color.isBlank()) {
                unique.putIfAbsent(color.toLowerCase(ARGENTINA), color);
            }
        }
        return List.copyOf(unique.values());
    }

    private String buildWhatsappUrl(
            String brand,
            String model,
            CurrentProduct current,
            List<String> colors
    ) {
        List<String> lines = new ArrayList<>();
        String sheetMarker = current != null
                && current.model().getOrigen() == CatalogProductOrigin.GOOGLE_SHEET
                ? " 📲"
                : "";
        lines.add("Hola! Quiero consultar disponibilidad del " + brand + " " + model + "." + sheetMarker);

        if (current != null) {
            addPriceLine(lines, "Efectivo", current.model().getPrecioPesos());
            addPriceLine(lines, "Transferencia", current.model().getPrecioTransferenciaBancaria());
            String installment = formatInstallmentOrNull(current.model().getPrecioTarjeta6Pagos());
            if (installment != null) {
                lines.add("- 6 cuotas sin interes de: " + installment);
            }
            if (productKind(brand, model) != ProductKind.PERFUME && !colors.isEmpty()) {
                lines.add("Colores: " + String.join(", ", colors));
            }
        }

        String encodedMessage = URLEncoder.encode(String.join("\n", lines), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + WHATSAPP_PHONE + "?text=" + encodedMessage;
    }

    private void addPriceLine(List<String> lines, String label, BigDecimal price) {
        String formatted = formatMoneyOrNull(price);
        if (formatted != null) {
            lines.add("- " + label + ": " + formatted);
        }
    }

    private String productJsonLd(
            String model,
            String brand,
            String canonical,
            boolean available,
            BigDecimal cashPrice,
            String enrichedDescription,
            List<String> images
    ) {
        Map<String, Object> product = new LinkedHashMap<>();
        product.put("@context", "https://schema.org");
        product.put("@type", "Product");
        product.put("name", model);
        product.put("brand", Map.of("@type", "Brand", "name", brand));
        if (enrichedDescription != null && !enrichedDescription.isBlank()) {
            product.put("description", enrichedDescription);
        }
        if (images != null && !images.isEmpty()) {
            product.put("image", images);
        }

        if (available && isRealPrice(cashPrice)) {
            Map<String, Object> offer = new LinkedHashMap<>();
            offer.put("@type", "Offer");
            offer.put("url", canonical);
            offer.put("priceCurrency", "ARS");
            offer.put("price", exactPrice(cashPrice));
            product.put("offers", offer);
        }
        return serializeForHtmlScript(product);
    }

    private String breadcrumbJsonLd(String model, String brand, String canonical) {
        List<Map<String, Object>> items = new ArrayList<>();
        items.add(breadcrumbItem(1, "Inicio", SITE_URL + "/"));
        items.add(breadcrumbItem(2, "Celulares", SITE_URL + "/listapreciosactual"));
        items.add(breadcrumbItem(3, brand, null));
        items.add(breadcrumbItem(4, model, canonical));

        Map<String, Object> breadcrumbs = new LinkedHashMap<>();
        breadcrumbs.put("@context", "https://schema.org");
        breadcrumbs.put("@type", "BreadcrumbList");
        breadcrumbs.put("itemListElement", items);
        return serializeForHtmlScript(breadcrumbs);
    }

    private Map<String, Object> breadcrumbItem(int position, String name, String item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@type", "ListItem");
        result.put("position", position);
        result.put("name", name);
        if (item != null) {
            result.put("item", item);
        }
        return result;
    }

    private String serializeForHtmlScript(Object value) {
        try {
            return objectMapper.writeValueAsString(value)
                    .replace("&", "\\u0026")
                    .replace("<", "\\u003c")
                    .replace(">", "\\u003e")
                    .replace("\u2028", "\\u2028")
                    .replace("\u2029", "\\u2029");
        } catch (JsonProcessingException ex) {
            throw new ApiInternalException("No se pudieron generar los datos estructurados del producto");
        }
    }

    private String formatMoneyOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(ARGENTINA);
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(0);
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return "$ " + formatter.format(value);
    }

    private String formatInstallmentOrNull(BigDecimal card6Total) {
        if (card6Total == null) {
            return null;
        }
        BigDecimal installment = card6Total.divide(SIX, 8, RoundingMode.HALF_UP);
        return formatMoneyOrNull(installment);
    }

    private boolean isRealPrice(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private String exactPrice(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String normalizedKnownSlug(String storedSlug) {
        String normalized = SeoProductSlug.fromModel(storedSlug);
        if (normalized.isBlank() || !normalized.equals(storedSlug)) {
            throw new ApiInternalException("El slug SEO histórico no es válido");
        }
        return normalized;
    }

    private String cleanBrand(String value) {
        String result = clean(value);
        return result.isBlank() ? "Sin marca" : result;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeIdentity(String value) {
        return clean(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private ProductKind productKind(String brand, String model) {
        return switch (SeoProductSlug.fromModel(brand)) {
            case "perfumes" -> ProductKind.PERFUME;
            case "articulos-varios" -> ProductKind.MISCELLANEOUS;
            default -> catalogProductPolicy.resolveProductType(model, brand) == ProductType.PHONE
                    ? ProductKind.PHONE
                    : ProductKind.OTHER;
        };
    }

    private record CurrentProduct(String brand, TiendaPorteModelResponse model) {
    }

    private enum ProductKind {
        PHONE(
                "Celular nuevo, liberado y original. Precio actualizado en Córdoba Celulares.",
                "Equipo nuevo, liberado, original, sellado de fábrica y con garantía.",
                null
        ),
        MISCELLANEOUS(
                "Precio actualizado en Córdoba Celulares.",
                "Dispositivos nuevos, sellados de fábrica y con garantía.",
                "Este artículo en particular requiere pago anticipado. ¿Desea continuar?"
        ),
        PERFUME(
                "Precio actualizado en Córdoba Celulares.",
                "En caja sellada de fábrica, sin abrir.",
                "Los perfumes requieren pago completo anticipado. ¿Desea continuar?"
        ),
        OTHER(
                "Producto nuevo, original y con garantía. Precio actualizado en Córdoba Celulares.",
                "Producto nuevo, original y con garantía.",
                null
        );

        private final String intro;
        private final String conditionLabel;
        private final String whatsappWarning;

        ProductKind(String intro, String conditionLabel, String whatsappWarning) {
            this.intro = intro;
            this.conditionLabel = conditionLabel;
            this.whatsappWarning = whatsappWarning;
        }
    }
}
