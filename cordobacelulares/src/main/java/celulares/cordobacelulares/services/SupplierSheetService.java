package celulares.cordobacelulares.services;

import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetProduct;
import celulares.cordobacelulares.dtos.suppliersheet.SupplierSheetPreviewResponse;

import java.util.List;

public interface SupplierSheetService {

    List<SupplierSheetProduct> getProducts();

    int reload();

    SupplierSheetPreviewResponse preview();
}
