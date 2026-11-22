package celulares.cordobacelulares.services;

import celulares.cordobacelulares.config.SeoProductMediaProperties;
import celulares.cordobacelulares.config.TiendaPorteProperties;
import celulares.cordobacelulares.exceptions.ApiBadRequestException;
import celulares.cordobacelulares.exceptions.TiendaPorteIntegrationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class TiendaPorteProductImageDownloader {

    private static final int MAX_REDIRECTS = 3;
    private static final Logger LOGGER = LoggerFactory.getLogger(TiendaPorteProductImageDownloader.class);
    private final TiendaPorteProperties properties;
    private final SeoProductMediaStorage mediaStorage;
    private final long maxSizeBytes;
    private final HttpClient httpClient;

    @Autowired
    public TiendaPorteProductImageDownloader(
            TiendaPorteProperties properties,
            SeoProductMediaStorage mediaStorage,
            SeoProductMediaProperties mediaProperties
    ) {
        this(
                properties,
                mediaStorage,
                mediaProperties.getMaxSizeBytes(),
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(Math.max(1, properties.getConnectTimeoutSeconds())))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build()
        );
    }

    TiendaPorteProductImageDownloader(
            TiendaPorteProperties properties,
            SeoProductMediaStorage mediaStorage,
            long maxSizeBytes,
            HttpClient httpClient
    ) {
        this.properties = properties;
        this.mediaStorage = mediaStorage;
        this.maxSizeBytes = maxSizeBytes;
        this.httpClient = httpClient;
    }

    public SeoProductMediaStorage.StoredMedia download(String sourceUrl) {
        URI current = validatedUri(sourceUrl);
        try {
            int attempts = Math.max(1, properties.getProductSync().getMaxRetries() + 1);
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    SeoProductMediaStorage.StoredMedia stored = downloadFollowingRedirects(current);
                    LOGGER.debug("Imagen de Tienda Porte almacenada. origen={} archivo={}",
                            safeTarget(current), stored.storedFilename());
                    return stored;
                } catch (RetryableDownloadException ex) {
                    lastFailure = ex;
                    if (attempt < attempts) sleep(ex.retryAfter());
                }
            }
            throw lastFailure == null
                    ? new TiendaPorteIntegrationException("No se pudo descargar la imagen de Tienda Porte")
                    : lastFailure;
        } catch (RuntimeException ex) {
            LOGGER.warn("Fallo la descarga de imagen de Tienda Porte. origen={} error={}",
                    safeTarget(current), safeMessage(ex));
            throw ex;
        }
    }

    private SeoProductMediaStorage.StoredMedia downloadFollowingRedirects(URI initialUri) {
        URI current = initialUri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validateNetworkTarget(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(Math.max(1, properties.getRequestTimeoutSeconds())))
                    .header("Accept", "image/jpeg,image/png,image/webp")
                    .GET()
                    .build();
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException ex) {
                throw new RetryableDownloadException("No se pudo descargar la imagen de Tienda Porte", ex, Duration.ofSeconds(1));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TiendaPorteIntegrationException("Se interrumpio la descarga de imagen", ex);
            }

            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                String location = response.headers().firstValue("Location")
                        .orElseThrow(() -> new ApiBadRequestException("Redireccion de imagen sin destino"));
                current = validatedUri(current.resolve(location).toString());
                continue;
            }
            if (status == 429 || status >= 500) {
                closeQuietly(response.body());
                throw new RetryableDownloadException(
                        "Tienda Porte no pudo entregar la imagen (status " + status + ")",
                        null,
                        retryAfter(response)
                );
            }
            if (status < 200 || status >= 300) {
                closeQuietly(response.body());
                throw new ApiBadRequestException("La imagen remota no esta disponible (status " + status + ")");
            }

            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength > maxSizeBytes) {
                closeQuietly(response.body());
                throw new ApiBadRequestException("La imagen remota supera el tamano maximo permitido");
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!isAllowedMime(contentType)) {
                closeQuietly(response.body());
                throw new ApiBadRequestException("La URL remota no devolvio una imagen permitida");
            }
            try (InputStream body = response.body()) {
                byte[] bytes = body.readNBytes(Math.toIntExact(maxSizeBytes + 1));
                if (bytes.length > maxSizeBytes) {
                    throw new ApiBadRequestException("La imagen remota supera el tamano maximo permitido");
                }
                return mediaStorage.storeDownloaded(bytes, filename(current), contentType);
            } catch (IOException ex) {
                throw new RetryableDownloadException("No se pudo leer la imagen remota", ex, Duration.ofSeconds(1));
            }
        }
        throw new ApiBadRequestException("La imagen remota excedio el limite de redirecciones");
    }

    URI validatedUri(String value) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
            if (!uri.isAbsolute()) {
                if (uri.getRawAuthority() != null) {
                    throw new ApiBadRequestException("URL de imagen relativa invalida");
                }
                String baseValue = properties.getApiBaseUrl().trim();
                URI base = URI.create(baseValue.endsWith("/") ? baseValue : baseValue + "/");
                uri = base.resolve(uri);
            }
        } catch (IllegalArgumentException ex) {
            throw new ApiBadRequestException("URL de imagen invalida", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null) {
            throw new ApiBadRequestException("La imagen debe usar una URL HTTPS publica");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = properties.getProductSync().getAllowedImageHosts().stream()
                .map(item -> item == null ? "" : item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> !item.isBlank() && (host.equals(item) || host.endsWith("." + item)));
        if (!allowed) throw new ApiBadRequestException("Dominio de imagen no permitido");
        return uri;
    }

    private String safeTarget(URI uri) {
        if (uri == null) return "desconocido";
        String host = uri.getHost() == null ? "sin-dominio" : uri.getHost();
        String path = uri.getPath() == null || uri.getPath().isBlank() ? "/" : uri.getPath();
        return host + path;
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) return error == null ? "Error no especificado" : error.getClass().getSimpleName();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private void validateNetworkTarget(URI uri) {
        try {
            for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
                byte[] raw = address.getAddress();
                boolean uniqueLocalV6 = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
                if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || address.isMulticastAddress() || uniqueLocalV6) {
                    throw new ApiBadRequestException("La imagen remota apunta a una red privada");
                }
            }
        } catch (IOException ex) {
            throw new ApiBadRequestException("No se pudo validar el dominio de la imagen", ex);
        }
    }

    private Duration retryAfter(HttpResponse<?> response) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        try {
            long seconds = Long.parseLong(value);
            return Duration.ofSeconds(Math.min(60, Math.max(1, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                Instant target = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                return Duration.ofSeconds(Math.min(60, Math.max(1, Duration.between(Instant.now(), target).toSeconds())));
            } catch (RuntimeException ignoredDate) {
                return Duration.ofSeconds(1);
            }
        }
    }

    private boolean isAllowedMime(String value) {
        String normalized = value == null ? "" : value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return List.of("image/jpeg", "image/png", "image/webp").contains(normalized);
    }

    private String filename(URI uri) {
        String path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) return "imagen-porte";
        String value = path.substring(path.lastIndexOf('/') + 1);
        return value.isBlank() ? "imagen-porte" : value;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(Math.max(1, duration.toMillis()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TiendaPorteIntegrationException("Se interrumpio el reintento de imagen", ex);
        }
    }

    private void closeQuietly(InputStream stream) {
        try {
            if (stream != null) stream.close();
        } catch (IOException ignored) {
        }
    }

    private static class RetryableDownloadException extends TiendaPorteIntegrationException {
        private final Duration retryAfter;

        private RetryableDownloadException(String message, Throwable cause, Duration retryAfter) {
            super(message, cause);
            this.retryAfter = retryAfter == null ? Duration.ofSeconds(1) : retryAfter;
        }

        private Duration retryAfter() { return retryAfter; }
    }
}
