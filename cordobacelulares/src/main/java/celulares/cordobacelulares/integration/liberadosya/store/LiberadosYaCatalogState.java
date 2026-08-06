package celulares.cordobacelulares.integration.liberadosya.store;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogSnapshot;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaCatalogIndex;

public record LiberadosYaCatalogState(
        LiberadosYaCatalogSnapshot snapshot,
        LiberadosYaCatalogIndex index
) {

    public boolean available() {
        return snapshot != null && snapshot.getProducts() != null && !snapshot.getProducts().isEmpty();
    }
}
