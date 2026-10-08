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
        String breadcrumbJsonLd
) {

    public boolean hasAnyPrice() {
        return precioEfectivo != null
                || precioTransferencia != null
                || precioTarjeta3Pagos != null
                || precioTarjeta6Pagos != null;
    }
}
