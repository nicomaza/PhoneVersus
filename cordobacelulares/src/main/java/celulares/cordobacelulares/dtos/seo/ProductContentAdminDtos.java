package celulares.cordobacelulares.dtos.seo;

import celulares.cordobacelulares.entities.SeoProductContentStatus;

import java.time.Instant;
import java.util.List;

public final class ProductContentAdminDtos {

    private ProductContentAdminDtos() {}

    public record SaveRequest(
            String description,
            String sourceUrl,
            String sourceName,
            List<HighlightInput> highlights,
            List<SpecSectionInput> specSections
    ) {}

    public record HighlightInput(Long id, String text) {
        public HighlightInput(String text) { this(null, text); }
    }
    public record SpecSectionInput(Long id, String title, List<SpecInput> specs) {
        public SpecSectionInput(String title, List<SpecInput> specs) { this(null, title, specs); }
    }
    public record SpecInput(Long id, String label, String value) {
        public SpecInput(String label, String value) { this(null, label, value); }
    }
    public record ImageAltRequest(String altText) {}
    public record ImageOrderRequest(List<Long> imageIds) {}

    public record ListItem(
            Long seoCatalogProductId,
            String marca,
            String modelo,
            String slug,
            boolean active,
            SeoProductContentStatus status,
            long imageCount,
            Instant updatedAt,
            boolean hasUnpublishedChanges
    ) {}

    public record PageResponse(
            List<ListItem> data,
            List<String> brands,
            int page,
            int limit,
            long total,
            int totalPages,
            boolean hasNext
    ) {}

    public record DetailResponse(
            Long seoCatalogProductId,
            String marca,
            String modelo,
            String slug,
            boolean active,
            SeoProductContentStatus status,
            String description,
            String sourceUrl,
            String sourceName,
            List<HighlightResponse> highlights,
            List<SpecSectionResponse> specSections,
            List<ImageResponse> images,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt,
            boolean hasUnpublishedChanges
    ) {}

    public record HighlightResponse(Long id, String text, int sortOrder) {}
    public record SpecSectionResponse(Long id, String title, int sortOrder, List<SpecResponse> specs) {}
    public record SpecResponse(Long id, String label, String value, int sortOrder) {}
    public record ImageResponse(
            Long id,
            String publicPath,
            String originalFilename,
            String altText,
            int sortOrder,
            boolean primaryImage,
            Instant createdAt
    ) {}
}
