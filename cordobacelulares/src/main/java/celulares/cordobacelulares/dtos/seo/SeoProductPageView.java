package celulares.cordobacelulares.dtos.seo;

import java.util.List;

public record SeoProductPageView(
        String slug,
        String marca,
        String modelo,
        boolean available,
        String title,
        String description,
        String canonicalUrl,
        String intro,
        String precioEfectivo,
        String precioTransferencia,
        String precioTarjeta3Pagos,
        String precioTarjeta6Pagos,
        String cuotaTarjeta6,
        List<String> colores,
        String conditionLabel,
        String whatsappWarning,
        String whatsappUrl,
        String productJsonLd,
        String breadcrumbJsonLd,
        boolean enriched,
        String visibleDescription,
        List<PublishedProductContent.Image> images,
        List<PublishedProductContent.Highlight> highlights,
        List<PublishedProductContent.SpecSection> specSections,
        String sourceUrl,
        String sourceName,
        String ogImage
) {

    public SeoProductPageView {
        images = images == null ? List.of() : List.copyOf(images);
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        specSections = specSections == null ? List.of() : List.copyOf(specSections);
    }

    public SeoProductPageView(
            String slug,
            String marca,
            String modelo,
            boolean available,
            String title,
            String description,
            String canonicalUrl,
            String intro,
            String precioEfectivo,
            String precioTransferencia,
            String precioTarjeta3Pagos,
            String precioTarjeta6Pagos,
            String cuotaTarjeta6,
            List<String> colores,
            String conditionLabel,
            String whatsappWarning,
            String whatsappUrl,
            String productJsonLd,
            String breadcrumbJsonLd
    ) {
        this(
                slug, marca, modelo, available, title, description, canonicalUrl, intro,
                precioEfectivo, precioTransferencia, precioTarjeta3Pagos, precioTarjeta6Pagos,
                cuotaTarjeta6, colores, conditionLabel, whatsappWarning, whatsappUrl,
                productJsonLd, breadcrumbJsonLd, false, intro, List.of(), List.of(), List.of(),
                null, null, null
        );
    }

    public boolean hasAnyPrice() {
        return precioEfectivo != null
                || precioTransferencia != null
                || precioTarjeta3Pagos != null
                || precioTarjeta6Pagos != null;
    }
}
