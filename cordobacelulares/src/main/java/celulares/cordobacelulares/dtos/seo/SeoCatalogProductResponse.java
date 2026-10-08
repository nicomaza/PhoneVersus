package celulares.cordobacelulares.dtos.seo;

public record SeoCatalogProductResponse(
        String slug,
        String marca,
        String modelo,
        boolean active
) {
}
