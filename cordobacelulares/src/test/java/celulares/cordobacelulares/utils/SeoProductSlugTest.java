package celulares.cordobacelulares.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeoProductSlugTest {

    @Test
    void matchesAngularPublicSlugRules() {
        assertThat(SeoProductSlug.fromModel("REDMI NOTE 15 6GB 128GB"))
                .isEqualTo("redmi-note-15-6gb-128gb");
        assertThat(SeoProductSlug.fromModel("  Teléfono / Edición + Pro  "))
                .isEqualTo("telefono-edicion-pro");
        assertThat(SeoProductSlug.fromModel("---POCO---C81---"))
                .isEqualTo("poco-c81");
    }
}
