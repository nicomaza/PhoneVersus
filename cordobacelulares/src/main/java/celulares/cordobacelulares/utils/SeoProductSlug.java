package celulares.cordobacelulares.utils;

import java.text.Normalizer;
import java.util.Locale;

public final class SeoProductSlug {

    private SeoProductSlug() {
    }

    public static String fromModel(String modelName) {
        if (modelName == null) {
            return "";
        }

        return Normalizer.normalize(modelName.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
