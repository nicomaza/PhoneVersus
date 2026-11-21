package celulares.cordobacelulares.dtos.seo;

import java.util.List;

public record PublishedProductContent(
        String description,
        String sourceUrl,
        String sourceName,
        List<Highlight> highlights,
        List<SpecSection> specSections,
        List<Image> images
) {
    public PublishedProductContent {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        specSections = specSections == null ? List.of() : List.copyOf(specSections);
        images = images == null ? List.of() : List.copyOf(images);
    }

    public record Highlight(String text) {}
    public record SpecSection(String title, List<Spec> specs) {
        public SpecSection {
            specs = specs == null ? List.of() : List.copyOf(specs);
        }
    }
    public record Spec(String label, String value) {}
    public record Image(String publicPath, String altText, boolean primaryImage) {}
}
