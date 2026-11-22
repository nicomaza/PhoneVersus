package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class TiendaPorteProductImageDownloaderTest {

    @Test
    void rejectsNonHttpsAndUnlistedHostsBeforeDownloading() {
        TiendaPorteProperties properties = new TiendaPorteProperties();
        properties.getProductSync().setAllowedImageHosts(java.util.List.of("cdn.tiendaporte.com"));
        TiendaPorteProductImageDownloader downloader = new TiendaPorteProductImageDownloader(
                properties, mock(SeoProductMediaStorage.class), 1024, mock(HttpClient.class)
        );

        assertThatThrownBy(() -> downloader.validatedUri("http://cdn.tiendaporte.com/image.png"))
                .isInstanceOf(ApiBadRequestException.class);
        assertThatThrownBy(() -> downloader.validatedUri("https://127.0.0.1/image.png"))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("Dominio");
        assertThatThrownBy(() -> downloader.validatedUri("https://evil.example/image.png"))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("Dominio");
    }

    @Test
    void resolvesRelativeProviderImagesBeforeApplyingHostValidation() {
        TiendaPorteProperties properties = new TiendaPorteProperties();
        properties.setApiBaseUrl("https://api.tiendaporte.com");
        properties.getProductSync().setAllowedImageHosts(java.util.List.of("api.tiendaporte.com"));
        TiendaPorteProductImageDownloader downloader = new TiendaPorteProductImageDownloader(
                properties, mock(SeoProductMediaStorage.class), 1024, mock(HttpClient.class)
        );

        assertThat(downloader.validatedUri("/productReferences/archivo.webp").toString())
                .isEqualTo("https://api.tiendaporte.com/productReferences/archivo.webp");
    }
}
