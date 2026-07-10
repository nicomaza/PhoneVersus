package celulares.cordobacelulares.dtos.suppliersheet;

import celulares.cordobacelulares.dtos.tiendaporte.response.CatalogProductOrigin;

import java.math.BigDecimal;
import java.util.List;

public record SupplierSheetPreviewProductResponse(
        String brand,
        String category,
        String modelName,
        CatalogProductOrigin origen,
        List<String> colors,
        BigDecimal priceUsd
) {
}
