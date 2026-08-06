package celulares.cordobacelulares.integration.liberadosya.matching;

import java.util.List;
import java.util.Set;

public record ProductIdentity(
        String originalName,
        String normalizedName,
        String brand,
        Set<String> brandKeys,
        List<String> modelTokens,
        List<String> variantTokens,
        String networkGeneration,
        Long ramGb,
        Long storageGb,
        Set<String> remainingTokens
) {

    public String variantKey() {
        return String.join(" ", variantTokens);
    }

    public String modelKey() {
        return String.join(" ", modelTokens);
    }
}
