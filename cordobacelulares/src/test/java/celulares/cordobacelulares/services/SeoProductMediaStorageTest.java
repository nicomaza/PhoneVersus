package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.SeoProductMediaProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeoProductMediaStorageTest {

    @TempDir
    Path tempDirectory;

    private SeoProductMediaStorage storage;

    @BeforeEach
    void setUp() {
        SeoProductMediaProperties properties = new SeoProductMediaProperties();
        properties.setDirectory(tempDirectory);
        properties.setMaxSizeBytes(1024);
        storage = new SeoProductMediaStorage(properties);
        storage.initialize();
    }

    @Test
    void storesValidPngUsingGeneratedSafeFilename() {
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1, 2, 3};
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../foto.png", "application/octet-stream", png
        );

        SeoProductMediaStorage.StoredMedia result = storage.store(file);

        assertThat(result.storedFilename()).matches("[a-f0-9-]{36}\\.png");
        assertThat(result.originalFilename()).isEqualTo("foto.png");
        assertThat(storage.load(result.storedFilename()).exists()).isTrue();
    }

    @Test
    void rejectsInvalidMimeEvenWhenBrowserClaimsPng() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fake.png", "image/png", "not-an-image".getBytes()
        );

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("JPEG, PNG o WEBP");
    }

    @Test
    void rejectsTraversalAndNonGeneratedNames() {
        assertThatThrownBy(() -> storage.load("../../application.properties"))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("Nombre de imagen invalido");
    }
}
