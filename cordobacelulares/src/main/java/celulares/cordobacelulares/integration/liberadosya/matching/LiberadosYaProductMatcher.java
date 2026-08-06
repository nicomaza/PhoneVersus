package celulares.cordobacelulares.integration.liberadosya.matching;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class LiberadosYaProductMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaProductMatcher.class);
    private static final int MAX_EXACT_MATCH_SCORE = 155;

    private final LiberadosYaProperties properties;
    private final LiberadosYaProductIdentityParser identityParser;

    public LiberadosYaProductMatcher(LiberadosYaProperties properties, LiberadosYaProductIdentityParser identityParser) {
        this.properties = properties;
        this.identityParser = identityParser;
    }

    public LiberadosYaMatchResult match(String brand, String model, LiberadosYaCatalogIndex index) {
        ProductIdentity query = identityParser.parseQuery(brand, model);
        List<ScoredCandidate> scored = new ArrayList<>();
        for (IndexedLiberadosYaProduct candidate : index.candidates(query)) {
            CandidateScore score = score(query, candidate);
            if (!score.discarded()) {
                scored.add(new ScoredCandidate(candidate.product(), candidate.identity(), score.score(), score.reason()));
            } else {
                LOGGER.debug(
                        "LiberadosYa match discarded. cid={} query={} candidate={} reason={}",
                        TiendaPorteDiagnostics.currentCorrelationId(),
                        query.normalizedName(),
                        candidate.product().getNombre(),
                        score.reason()
                );
            }
        }

        scored.sort(Comparator
                .comparingInt(ScoredCandidate::score).reversed()
                .thenComparing(candidate -> safe(candidate.product().getSlug())));

        if (scored.isEmpty()) {
            logDiagnostics("no-candidates", query, index, scored);
            LOGGER.info(
                    "LiberadosYa match not found. cid={} query={} reason=no-candidates",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    query.normalizedName()
            );
            return LiberadosYaMatchResult.notFound(0, "no-candidates");
        }

        ScoredCandidate best = scored.get(0);
        ScoredCandidate second = scored.size() > 1 ? scored.get(1) : null;
        int secondScore = second == null ? 0 : second.score();
        if (best.score() < properties.getMinimumMatchScore()) {
            logDiagnostics("below-threshold", query, index, scored);
            LOGGER.info(
                    "LiberadosYa match below threshold. cid={} query={} best={} score={} threshold={} maxPossibleScore={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    query.normalizedName(),
                    best.product().getNombre(),
                    best.score(),
                    properties.getMinimumMatchScore(),
                    MAX_EXACT_MATCH_SCORE
            );
            return LiberadosYaMatchResult.notFound(best.score(), "below-threshold");
        }
        if (second != null && best.score() - second.score() < properties.getAmbiguityThreshold()) {
            logDiagnostics("ambiguous", query, index, scored);
            LOGGER.warn(
                    "LiberadosYa match ambiguous. cid={} query={} best={} bestScore={} second={} secondScore={} threshold={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    query.normalizedName(),
                    best.product().getNombre(),
                    best.score(),
                    second.product().getNombre(),
                    second.score(),
                    properties.getAmbiguityThreshold()
            );
            return LiberadosYaMatchResult.ambiguous(best.score(), secondScore, "scores-too-close");
        }

        LOGGER.debug(
                "LiberadosYa match selected. cid={} query={} candidate={} score={} secondScore={} threshold={} maxPossibleScore={} reason={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                query.normalizedName(),
                best.product().getNombre(),
                best.score(),
                secondScore,
                properties.getMinimumMatchScore(),
                MAX_EXACT_MATCH_SCORE,
                best.reason()
        );
        return LiberadosYaMatchResult.matched(best.product(), best.score(), secondScore, best.reason());
    }

    private CandidateScore score(ProductIdentity query, IndexedLiberadosYaProduct candidate) {
        ProductIdentity identity = candidate.identity();
        if (!identityParser.brandCompatible(query, identity)) {
            return CandidateScore.discard("brand-incompatible");
        }
        if (!modelCompatible(query, identity)) {
            return CandidateScore.discard("model-incompatible");
        }
        if (!memoryCompatible(query, identity)) {
            return CandidateScore.discard("memory-incompatible");
        }
        if (!networkCompatible(query, identity)) {
            return CandidateScore.discard("network-incompatible");
        }
        if (!variantCompatible(query, identity)) {
            return CandidateScore.discard("variant-incompatible");
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        score += add(reasons, 25, "brand");
        if (query.modelTokens().equals(identity.modelTokens())) {
            score += add(reasons, 35, "model-exact");
        } else {
            score += add(reasons, 25, "model-compatible");
        }
        if (identity.modelTokens().containsAll(query.modelTokens())) {
            score += add(reasons, 20, "model-tokens");
        }
        if (query.ramGb() != null && Objects.equals(query.ramGb(), identity.ramGb())) {
            score += add(reasons, 15, "ram");
        }
        if (query.storageGb() != null && Objects.equals(query.storageGb(), identity.storageGb())) {
            score += add(reasons, 15, "storage");
        }
        if (query.networkGeneration() != null && Objects.equals(query.networkGeneration(), identity.networkGeneration())) {
            score += add(reasons, 15, "network");
        } else if (query.networkGeneration() == null) {
            if (identity.networkGeneration() == null) {
                score += add(reasons, 5, "network-unspecified");
            } else if ("4G".equals(identity.networkGeneration())) {
                score += add(reasons, 4, "network-4g-inferred");
            }
        }
        if (query.variantKey().equals(identity.variantKey())) {
            score += add(reasons, 20, "variant");
        }
        if (slugContainsAll(candidate.product().getSlug(), query.modelTokens())) {
            score += add(reasons, 5, "slug");
        }
        if (specsContainMemory(candidate.product(), query)) {
            score += add(reasons, 5, "specs");
        }

        int missingImportantTokens = missingImportantTokens(query, identity);
        if (missingImportantTokens > 0) {
            score -= missingImportantTokens * 15;
            reasons.add("missing-important=" + missingImportantTokens);
        }
        return CandidateScore.keep(Math.max(0, score), String.join(",", reasons));
    }

    private boolean modelCompatible(ProductIdentity query, ProductIdentity candidate) {
        if (query.modelTokens().isEmpty()) {
            return false;
        }
        Set<String> candidateTokens = new LinkedHashSet<>(candidate.modelTokens());
        candidateTokens.addAll(candidate.remainingTokens());
        for (String token : query.modelTokens()) {
            if (!candidateTokens.contains(token)) {
                return false;
            }
        }
        List<String> queryNumbers = query.modelTokens().stream().filter(this::isNumericModelToken).toList();
        List<String> candidateNumbers = candidate.modelTokens().stream().filter(this::isNumericModelToken).toList();
        return candidateNumbers.containsAll(queryNumbers);
    }

    private boolean memoryCompatible(ProductIdentity query, ProductIdentity candidate) {
        return ramCompatible(query, candidate) && storageCompatible(query, candidate);
    }

    private boolean ramCompatible(ProductIdentity query, ProductIdentity candidate) {
        return query.ramGb() == null || candidate.ramGb() == null || Objects.equals(query.ramGb(), candidate.ramGb());
    }

    private boolean storageCompatible(ProductIdentity query, ProductIdentity candidate) {
        return query.storageGb() == null || candidate.storageGb() == null || Objects.equals(query.storageGb(), candidate.storageGb());
    }

    private boolean networkCompatible(ProductIdentity query, ProductIdentity candidate) {
        if (query.networkGeneration() == null) {
            return true;
        }
        return Objects.equals(query.networkGeneration(), candidate.networkGeneration());
    }

    private boolean variantCompatible(ProductIdentity query, ProductIdentity candidate) {
        return Objects.equals(query.variantKey(), candidate.variantKey());
    }

    private int missingImportantTokens(ProductIdentity query, ProductIdentity candidate) {
        Set<String> candidateTokens = new LinkedHashSet<>(candidate.modelTokens());
        candidateTokens.addAll(candidate.remainingTokens());
        int missing = 0;
        for (String token : query.modelTokens()) {
            if (!candidateTokens.contains(token)) {
                missing++;
            }
        }
        return missing;
    }

    private boolean slugContainsAll(String slug, List<String> tokens) {
        String safeSlug = safe(slug).toUpperCase();
        for (String token : tokens) {
            if (!safeSlug.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private boolean specsContainMemory(LiberadosYaProduct product, ProductIdentity query) {
        if (product.getEspecificaciones() == null || product.getEspecificaciones().isEmpty()) {
            return false;
        }
        String specs = product.getEspecificaciones().toString().toUpperCase();
        boolean ramMatches = query.ramGb() == null || specs.contains(query.ramGb() + "GB") || specs.contains(query.ramGb() + " GB");
        boolean storageMatches = query.storageGb() == null || specs.contains(query.storageGb() + "GB") || specs.contains(query.storageGb() + " GB");
        return ramMatches && storageMatches;
    }

    private int add(List<String> reasons, int value, String reason) {
        reasons.add(reason + "+" + value);
        return value;
    }

    private boolean isNumericModelToken(String token) {
        return token != null && token.matches("\\d+|[A-Z]+\\d+|\\d+[A-Z]+");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void logDiagnostics(
            String outcome,
            ProductIdentity query,
            LiberadosYaCatalogIndex index,
            List<ScoredCandidate> scored
    ) {
        List<IndexedLiberadosYaProduct> all = index.allProducts();
        List<IndexedLiberadosYaProduct> afterBrand = all.stream()
                .filter(candidate -> identityParser.brandCompatible(query, candidate.identity()))
                .toList();
        List<IndexedLiberadosYaProduct> afterModel = afterBrand.stream()
                .filter(candidate -> modelCompatible(query, candidate.identity()))
                .toList();
        List<IndexedLiberadosYaProduct> afterRam = afterModel.stream()
                .filter(candidate -> ramCompatible(query, candidate.identity()))
                .toList();
        List<IndexedLiberadosYaProduct> afterStorage = afterRam.stream()
                .filter(candidate -> storageCompatible(query, candidate.identity()))
                .toList();
        List<IndexedLiberadosYaProduct> afterNetwork = afterStorage.stream()
                .filter(candidate -> networkCompatible(query, candidate.identity()))
                .toList();
        List<IndexedLiberadosYaProduct> afterVariant = afterNetwork.stream()
                .filter(candidate -> variantCompatible(query, candidate.identity()))
                .toList();
        ScoredCandidate best = scored.isEmpty() ? null : scored.get(0);
        LOGGER.info(
                "LiberadosYa match diagnostics. cid={} outcome={} query={} initialCandidates={} afterBrand={} afterModel={} afterRam={} afterStorage={} afterNetwork={} afterVariant={} maxPossibleScore={} threshold={} best={} bestScore={} bestReason={}",
                TiendaPorteDiagnostics.currentCorrelationId(),
                outcome,
                query.normalizedName(),
                all.size(),
                afterBrand.size(),
                afterModel.size(),
                afterRam.size(),
                afterStorage.size(),
                afterNetwork.size(),
                afterVariant.size(),
                MAX_EXACT_MATCH_SCORE,
                properties.getMinimumMatchScore(),
                best == null ? null : best.product().getNombre(),
                best == null ? 0 : best.score(),
                best == null ? null : best.reason()
        );
    }

    private record CandidateScore(boolean discarded, int score, String reason) {

        private static CandidateScore discard(String reason) {
            return new CandidateScore(true, 0, reason);
        }

        private static CandidateScore keep(int score, String reason) {
            return new CandidateScore(false, score, reason);
        }
    }

    private record ScoredCandidate(
            LiberadosYaProduct product,
            ProductIdentity identity,
            int score,
            String reason
    ) {
    }
}
