package celulares.cordobacelulares.controller;

import celulares.cordobacelulares.config.BombaiSecurityProperties;
import celulares.cordobacelulares.security.AdminSecurityConfig;
import celulares.cordobacelulares.services.HomeBannerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminHomeBannerController.class, PublicHomeBannerController.class})
@Import({AdminSecurityConfig.class, BombaiSecurityProperties.class})
@TestPropertySource(properties = {
        "bombai.security.username=test-admin",
        "bombai.security.password=test-password-secure"
})
class HomeBannerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HomeBannerService service;

    @Test
    void publicEndpointDoesNotRequireAuthentication() throws Exception {
        when(service.listActive()).thenReturn(List.of());

        mockMvc.perform(get("/api/public/home-banners"))
                .andExpect(status().isOk());
    }

    @Test
    void adminReadRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/home-banners"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminWriteRequiresAuthentication() throws Exception {
        MockMultipartFile image = new MockMultipartFile(
                "desktopImage", "banner.png", "image/png", new byte[]{1}
        );

        mockMvc.perform(multipart("/api/admin/home-banners")
                        .file(image)
                        .param("title", "Banner")
                        .param("altText", "Descripcion"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void configuredAdministratorCanAccessAdminEndpoint() throws Exception {
        when(service.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/home-banners")
                        .with(httpBasic("test-admin", "test-password-secure")))
                .andExpect(status().isOk());
    }
}
