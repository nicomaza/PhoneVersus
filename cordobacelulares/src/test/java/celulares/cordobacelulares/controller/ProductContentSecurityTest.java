package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.config.BombaiSecurityProperties;
import celulares.cordobacelulares.dtos.seo.PublishedProductContent;
import celulares.cordobacelulares.security.AdminSecurityConfig;
import celulares.cordobacelulares.services.SeoPorteSyncService;
import celulares.cordobacelulares.services.SeoProductContentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminProductContentController.class, PublicProductContentController.class})
@Import({AdminSecurityConfig.class, BombaiSecurityProperties.class})
@TestPropertySource(properties = {
        "bombai.security.username=test-admin",
        "bombai.security.password=test-password-secure"
})
class ProductContentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SeoProductContentService contentService;

    @MockBean
    private SeoPorteSyncService syncService;

    @Test
    void syncEndpointsRequireAdministratorAuthentication() throws Exception {
        mockMvc.perform(post("/api/admin/product-content/porte/sync"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/product-content/porte/sync/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicFeaturesReturnsOnlyPublishedSnapshotWithoutAuthentication() throws Exception {
        when(contentService.published("samsung-galaxy-s25")).thenReturn(Optional.of(
                new PublishedProductContent(
                        "descripcion privada no expuesta", "", "",
                        List.of(new PublishedProductContent.Highlight("AMOLED")),
                        List.of(),
                        List.of(new PublishedProductContent.Image("/product-media/a.webp", "Frente", true))
                )
        ));

        mockMvc.perform(get("/api/public/product-content/products/samsung-galaxy-s25/features"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.highlights[0].text").value("AMOLED"))
                .andExpect(jsonPath("$.images[0].publicPath").value("/product-media/a.webp"))
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void publicFeaturesReturnsNotFoundWhenThereIsNoPublication() throws Exception {
        when(contentService.published("sin-publicar")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/product-content/products/sin-publicar/features"))
                .andExpect(status().isNotFound());
    }
}
