package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;

public record LiberadosYaMatchResult(
        Status status,
        LiberadosYaProduct product,
        int bestScore,
        int secondScore,
        String reason
) {

    public static LiberadosYaMatchResult matched(LiberadosYaProduct product, int bestScore, int secondScore, String reason) {
        return new LiberadosYaMatchResult(Status.MATCHED, product, bestScore, secondScore, reason);
    }

    public static LiberadosYaMatchResult notFound(int bestScore, String reason) {
        return new LiberadosYaMatchResult(Status.NOT_FOUND, null, bestScore, 0, reason);
    }

    public static LiberadosYaMatchResult ambiguous(int bestScore, int secondScore, String reason) {
        return new LiberadosYaMatchResult(Status.AMBIGUOUS, null, bestScore, secondScore, reason);
    }

    public enum Status {
        MATCHED,
        NOT_FOUND,
        AMBIGUOUS
    }
}
