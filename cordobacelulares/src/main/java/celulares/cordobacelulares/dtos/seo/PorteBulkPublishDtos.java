package celulares.cordobacelulares.dtos.seo;

import java.util.List;

public final class PorteBulkPublishDtos {

    private PorteBulkPublishDtos() {}

    public record PreviewResponse(
            int eligibleToPublish,
            int alreadyPublished,
            int withoutValidPorteSheet,
            int inactiveProducts,
            int skippedOtherReasons
    ) {
        public int totalEvaluated() {
            return eligibleToPublish + alreadyPublished + withoutValidPorteSheet
                    + inactiveProducts + skippedOtherReasons;
        }
    }

    public record PublishResponse(
            int totalEvaluated,
            int published,
            int skipped,
            int errors,
            List<ErrorDetail> errorDetails
    ) {}

    public record ErrorDetail(String slug, String message) {}
}
