package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;

public record IndexedLiberadosYaProduct(
        LiberadosYaProduct product,
        ProductIdentity identity
) {
}
