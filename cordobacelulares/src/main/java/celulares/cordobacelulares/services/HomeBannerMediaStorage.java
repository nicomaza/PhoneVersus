package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.HomeBannerMediaProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import celulares.cordobacelulares.exceptions.ApiInternalException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class HomeBannerMediaStorage {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };
    private static final Set<String> ALLOWED_DECLARED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final Path root;
    private final long maxSizeBytes;

    public HomeBannerMediaStorage(HomeBannerMediaProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
        this.maxSizeBytes = properties.getMaxSizeBytes();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo preparar el directorio de banners", ex);
        }
    }

    public StoredMedia store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiBadRequestException("La imagen es obligatoria");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ApiBadRequestException("La imagen supera el tamano maximo permitido");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ApiBadRequestException("No se pudo leer la imagen", ex);
        }
        if (bytes.length == 0 || bytes.length > maxSizeBytes) {
            throw new ApiBadRequestException("La imagen supera el tamano maximo permitido");
        }

        DetectedImage detected = detectImage(bytes);
        validateExtension(file.getOriginalFilename(), detected.extension());
        validateDeclaredType(file.getContentType(), detected.mimeType());

        String storedFilename = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + detected.extension();
        try {
            Files.write(safePath(storedFilename), bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo guardar la imagen del banner", ex);
        }

        return new StoredMedia(
                storedFilename,
                "/home-banner-media/" + storedFilename,
                safeOriginalFilename(file.getOriginalFilename()),
                detected.mimeType()
        );
    }

    public Resource load(String storedFilename) {
        Path target = safePath(storedFilename);
        if (!Files.isRegularFile(target)) {
            throw new ApiBadRequestException("Imagen no encontrada");
        }
        try {
            return new UrlResource(target.toUri());
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo leer la imagen del banner", ex);
        }
    }

    public void deleteQuietly(String storedFilename) {
        if (storedFilename == null) return;
        try {
            Files.deleteIfExists(safePath(storedFilename));
        } catch (RuntimeException | IOException ignored) {
            // Una falla de limpieza no debe deshacer una operacion de base de datos ya confirmada.
        }
    }

    public String contentType(String storedFilename) {
        if (storedFilename != null && storedFilename.endsWith(".jpg")) return "image/jpeg";
        if (storedFilename != null && storedFilename.endsWith(".png")) return "image/png";
        if (storedFilename != null && storedFilename.endsWith(".webp")) return "image/webp";
        throw new ApiBadRequestException("Tipo de imagen invalido");
    }

    Path safePath(String storedFilename) {
        if (storedFilename == null || !storedFilename.matches("[a-f0-9-]{36}\\.(jpg|png|webp)")) {
            throw new ApiBadRequestException("Nombre de imagen invalido");
        }
        Path resolved = root.resolve(storedFilename).normalize();
        if (!resolved.startsWith(root)) {
            throw new ApiBadRequestException("Ruta de imagen invalida");
        }
        return resolved;
    }

    private DetectedImage detectImage(byte[] bytes) {
        if (bytes.length >= 4 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff
                && isDecodable(bytes)) {
            return new DetectedImage(".jpg", "image/jpeg");
        }
        if (startsWith(bytes, PNG_SIGNATURE) && isDecodable(bytes)) {
            return new DetectedImage(".png", "image/png");
        }
        if (validWebp(bytes)) {
            return new DetectedImage(".webp", "image/webp");
        }
        throw new ApiBadRequestException("El archivo no es una imagen JPEG, PNG o WEBP valida");
    }

    private boolean isDecodable(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean validWebp(byte[] bytes) {
        if (bytes.length < 20 || !ascii(bytes, 0, "RIFF") || !ascii(bytes, 8, "WEBP")) return false;
        long declaredSize = Integer.toUnsignedLong(littleEndianInt(bytes, 4)) + 8L;
        if (declaredSize != bytes.length) return false;
        return ascii(bytes, 12, "VP8 ") || ascii(bytes, 12, "VP8L") || ascii(bytes, 12, "VP8X");
    }

    private int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | ((bytes[offset + 3] & 0xff) << 24);
    }

    private void validateExtension(String originalFilename, String detectedExtension) {
        String extension = extensionOf(originalFilename);
        boolean matches = extension.equals(detectedExtension)
                || (detectedExtension.equals(".jpg") && extension.equals(".jpeg"));
        if (!matches) {
            throw new ApiBadRequestException("La extension del archivo no coincide con su contenido");
        }
    }

    private void validateDeclaredType(String declaredType, String detectedType) {
        if (declaredType == null || declaredType.isBlank() || declaredType.equals("application/octet-stream")) return;
        String normalized = declaredType.toLowerCase(Locale.ROOT);
        if (!ALLOWED_DECLARED_TYPES.contains(normalized) || !normalized.equals(detectedType)) {
            throw new ApiBadRequestException("El tipo declarado del archivo no coincide con su contenido");
        }
    }

    private String extensionOf(String filename) {
        if (filename == null) return "";
        String normalized = filename.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        int dot = baseName.lastIndexOf('.');
        return dot < 0 ? "" : baseName.substring(dot);
    }

    private boolean startsWith(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) return false;
        }
        return true;
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private String safeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "imagen";
        String normalized = originalFilename.replace('\\', '/');
        String baseName = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (baseName.isBlank()) return "imagen";
        return baseName.substring(0, Math.min(baseName.length(), 512));
    }

    public record StoredMedia(String storedFilename, String publicPath, String originalFilename, String mimeType) {}
    private record DetectedImage(String extension, String mimeType) {}
}
