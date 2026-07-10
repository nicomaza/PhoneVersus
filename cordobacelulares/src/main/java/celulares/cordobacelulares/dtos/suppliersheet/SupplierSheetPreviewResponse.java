package celulares.cordobacelulares.dtos.suppliersheet;

import java.util.List;
import java.util.Map;

public record SupplierSheetPreviewResponse(
        boolean enabled,
        boolean configured,
        boolean loadedFromCache,
        int totalRows,
        int totalParsed,
        int ignoredRows,
        Map<String, Integer> byCategory,
        List<SupplierSheetPreviewProductResponse> sample
) {
}
