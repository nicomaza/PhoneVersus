package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.HomeBannerMediaProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HomeBannerMediaStorageTest {

    @TempDir
    Path tempDir;

    private HomeBannerMediaStorage storage;

    @BeforeEach
    void setUp() {
        HomeBannerMediaProperties properties = new HomeBannerMediaProperties();
        properties.setDirectory(tempDir);
        properties.setMaxSizeBytes(1024);
        storage = new HomeBannerMediaStorage(properties);
        storage.initialize();
    }

    @Test
    void storesLoadsAndDeletesAValidImage() throws Exception {
        HomeBannerMediaStorage.StoredMedia stored = storage.store(png("desktop.png"));

        assertThat(stored.storedFilename()).matches("[a-f0-9-]{36}\\.png");
        assertThat(stored.publicPath()).isEqualTo("/home-banner-media/" + stored.storedFilename());
        assertThat(storage.load(stored.storedFilename()).exists()).isTrue();

        storage.deleteQuietly(stored.storedFilename());

        assertThat(Files.exists(tempDir.resolve(stored.storedFilename()))).isFalse();
    }

    @Test
    void rejectsExecutableContentAndExtensionSpoofing() throws Exception {
        MockMultipartFile executable = new MockMultipartFile(
                "image", "malware.png", "image/png", "MZ executable".getBytes()
        );
        MockMultipartFile wrongExtension = new MockMultipartFile(
                "image", "banner.exe", "image/png", pngBytes()
        );

        assertThatThrownBy(() -> storage.store(executable)).isInstanceOf(ApiBadRequestException.class);
        assertThatThrownBy(() -> storage.store(wrongExtension)).isInstanceOf(ApiBadRequestException.class);
        assertThat(Files.list(tempDir)).isEmpty();
    }

    @Test
    void rejectsImagesOverConfiguredLimit() {
        MockMultipartFile oversized = new MockMultipartFile(
                "image", "banner.png", "image/png", new byte[1025]
        );

        assertThatThrownBy(() -> storage.store(oversized))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("tamano maximo");
    }

    private MockMultipartFile png(String filename) throws Exception {
        return new MockMultipartFile("image", filename, "image/png", pngBytes());
    }

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
