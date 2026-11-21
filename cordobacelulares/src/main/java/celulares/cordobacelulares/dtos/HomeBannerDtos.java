package celulares.cordobacelulares.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class HomeBannerDtos {

    private HomeBannerDtos() {}

    public record MetadataRequest(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 500) String altText,
            @Size(max = 2048) String linkUrl
    ) {}

    public record ActivationRequest(boolean active) {}

    public record ReorderRequest(@NotNull List<@NotNull Long> bannerIds) {}

    public record AdminResponse(
            Long id,
            String title,
            String altText,
            String linkUrl,
            boolean active,
            int sortOrder,
            String desktopImageUrl,
            String mobileImageUrl,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PublicResponse(
            String title,
            String altText,
            String linkUrl,
            String desktopImageUrl,
            String mobileImageUrl
    ) {}
}
