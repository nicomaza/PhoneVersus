package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.SeoProductMediaProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import celulares.cordobacelulares.exceptions.ApiInternalException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Service
public class SeoProductMediaStorage {

    private static final byte[] PNG_SIGNATURE = new byte[]{
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    private final Path root;
    private final long maxSizeBytes;

    public SeoProductMediaStorage(SeoProductMediaProperties properties) {
        this.root = properties.getDirectory().toAbsolutePath().normalize();
        this.maxSizeBytes = properties.getMaxSizeBytes();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo preparar el directorio de imagenes de productos");
        }
    }

    public StoredMedia store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiBadRequestException("La imagen es obligatoria");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new ApiBadRequestException("La imagen supera el tamano maximo permitido");
        }

        DetectedImage detected;
        try (InputStream input = file.getInputStream()) {
            detected = detectImage(input.readNBytes(16));
        } catch (IOException ex) {
            throw new ApiBadRequestException("No se pudo leer la imagen", ex);
        }

        String storedFilename = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + detected.extension();
        Path target = safePath(storedFilename);
        try (InputStream input = file.getInputStream()) {
            Files.copy(input, target);
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo guardar la imagen del producto");
        }

        return new StoredMedia(
                storedFilename,
                "/product-media/" + storedFilename,
                safeOriginalFilename(file.getOriginalFilename()),
                detected.mimeType()
        );
    }

    public StoredMedia storeDownloaded(byte[] bytes, String originalFilename, String declaredContentType) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiBadRequestException("La imagen descargada esta vacia");
        }
        if (bytes.length > maxSizeBytes) {
            throw new ApiBadRequestException("La imagen supera el tamano maximo permitido");
        }
        DetectedImage detected = detectImage(java.util.Arrays.copyOf(bytes, Math.min(bytes.length, 16)));
        String normalizedDeclared = declaredContentType == null
                ? "" : declaredContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!normalizedDeclared.isBlank() && !detected.mimeType().equals(normalizedDeclared)) {
            throw new ApiBadRequestException("El MIME declarado no coincide con el contenido de la imagen");
        }

        String storedFilename = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + detected.extension();
        Path target = safePath(storedFilename);
        try {
            Files.write(target, bytes);
        } catch (IOException ex) {
            throw new ApiInternalException("No se pudo guardar la imagen del producto");
        }
        return new StoredMedia(
                storedFilename,
                "/product-media/" + storedFilename,
                safeOriginalFilename(originalFilename),
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
            throw new ApiInternalException("No se pudo leer la imagen del producto");
        }
    }

    public void deleteQuietly(String storedFilename) {
        try {
            Files.deleteIfExists(safePath(storedFilename));
        } catch (RuntimeException | IOException ignored) {
            // La persistencia del contenido no debe quedar bloqueada por la limpieza del archivo.
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
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return new DetectedImage(".jpg", "image/jpeg");
        }
        if (startsWith(bytes, PNG_SIGNATURE)) {
            return new DetectedImage(".png", "image/png");
        }
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) {
            return new DetectedImage(".webp", "image/webp");
        }
        throw new ApiBadRequestException("El archivo no es una imagen JPEG, PNG o WEBP valida");
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
