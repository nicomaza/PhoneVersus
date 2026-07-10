package celulares.cordobacelulares.dtos.suppliersheet;

import java.math.BigDecimal;
import java.util.List;

public record SupplierSheetProduct(
        String originalBrand,
        String responseBrand,
        String modelName,
        List<String> colors,
        BigDecimal priceUsd
) {
}
