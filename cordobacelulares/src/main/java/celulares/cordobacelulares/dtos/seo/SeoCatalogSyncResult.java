package celulares.cordobacelulares.dtos.seo;

public record SeoCatalogSyncResult(
        int found,
        int created,
        int reactivated,
        int deactivated,
        int slugCollisions,
        long historicalCount,
        long activeCount,
        boolean deactivationApplied,
        boolean skipped
) {

    public static SeoCatalogSyncResult skippedResult() {
        return new SeoCatalogSyncResult(0, 0, 0, 0, 0, 0, 0, false, true);
    }
}
