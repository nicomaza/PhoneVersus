package celulares.cordobacelulares.dtos.suppliersheet;

import java.math.BigDecimal;
import java.util.List;

public record SupplierSheetPreviewProductResponse(
        String brand,
        String category,
        String modelName,
        List<String> colors,
        BigDecimal priceUsd
) {
}
