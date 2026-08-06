package celulares.cordobacelulares.integration.liberadosya.store;

import celulares.cordobacelulares.integration.liberadosya.config.LiberadosYaProperties;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaCatalogSnapshot;
import celulares.cordobacelulares.integration.liberadosya.dto.LiberadosYaProduct;
import celulares.cordobacelulares.integration.liberadosya.exception.LiberadosYaIntegrationException;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaCatalogIndex;
import celulares.cordobacelulares.integration.liberadosya.matching.LiberadosYaProductIdentityParser;
import celulares.cordobacelulares.integration.liberadosya.scraping.LiberadosYaProductImageSanitizer;
import celulares.cordobacelulares.utils.TiendaPorteDiagnostics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LiberadosYaCatalogStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiberadosYaCatalogStore.class);

    private final LiberadosYaProperties properties;
    private final ObjectMapper objectMapper;
    private final LiberadosYaProductIdentityParser identityParser;
    private final AtomicReference<LiberadosYaCatalogState> current = new AtomicReference<>();

    public LiberadosYaCatalogStore(
            LiberadosYaProperties properties,
            ObjectMapper objectMapper,
            LiberadosYaProductIdentityParser identityParser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.identityParser = identityParser;
    }

    public LiberadosYaCatalogState current() {
        return current.get();
    }

    public boolean loadFromDiskIfPresent() {
        Path path = properties.storagePath();
        if (!Files.exists(path)) {
            LOGGER.info("LiberadosYa snapshot file not found. cid={} path={}", TiendaPorteDiagnostics.currentCorrelationId(), path);
            return false;
        }
        try {
            LiberadosYaCatalogSnapshot snapshot = objectMapper.readValue(path.toFile(), LiberadosYaCatalogSnapshot.class);
            validateSnapshot(snapshot);
            LiberadosYaCatalogState state = state(snapshot);
            current.set(state);
            LOGGER.info(
                    "LiberadosYa snapshot loaded. cid={} path={} generatedAt={} totalProducts={} indexedProducts={} indexedBrands={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    path,
                    snapshot.getGeneratedAt(),
                    snapshot.getTotalProducts(),
                    state.index().indexedProductCount(),
                    state.index().indexedBrandCount()
            );
            return true;
        } catch (IOException | RuntimeException ex) {
            LOGGER.warn(
                    "LiberadosYa snapshot file could not be loaded. cid={} path={} cause={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    path,
                    TiendaPorteDiagnostics.rootCauseLabel(ex)
            );
            return false;
        }
    }

    public LiberadosYaCatalogState replaceSnapshot(LiberadosYaCatalogSnapshot snapshot) {
        validateSnapshot(snapshot);
        LiberadosYaCatalogState state = state(snapshot);
        Path path = properties.storagePath();
        Path parent = path.toAbsolutePath().getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), snapshot);
            moveAtomically(tempPath, path);
            current.set(state);
            LOGGER.info(
                    "LiberadosYa snapshot persisted and swapped. cid={} path={} generatedAt={} totalProducts={} indexedProducts={} indexedBrands={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    path,
                    snapshot.getGeneratedAt(),
                    snapshot.getTotalProducts(),
                    state.index().indexedProductCount(),
                    state.index().indexedBrandCount()
            );
            return state;
        } catch (IOException ex) {
            throw new LiberadosYaIntegrationException("No se pudo persistir el snapshot de LiberadosYa", ex);
        }
    }

    private void moveAtomically(Path tempPath, Path path) throws IOException {
        try {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicMoveFailure) {
            Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private LiberadosYaCatalogState state(LiberadosYaCatalogSnapshot snapshot) {
        return new LiberadosYaCatalogState(snapshot, LiberadosYaCatalogIndex.from(snapshot.getProducts(), identityParser));
    }

    private void validateSnapshot(LiberadosYaCatalogSnapshot snapshot) {
        if (snapshot == null) {
            throw new LiberadosYaIntegrationException("Snapshot de LiberadosYa invalido");
        }
        int previousSchemaVersion = snapshot.getSchemaVersion();
        if (snapshot.getGeneratedAt() == null) {
            snapshot.setGeneratedAt(Instant.now());
        }
        if (snapshot.getProducts() == null) {
            snapshot.setProducts(List.of());
        }
        if (snapshot.getErrors() == null) {
            snapshot.setErrors(List.of());
        }
        int sanitizedProducts = sanitizeProductImages(snapshot.getProducts());
        snapshot.setSchemaVersion(LiberadosYaProductImageSanitizer.SCHEMA_VERSION);
        snapshot.setTotalProducts(snapshot.getProducts().size());
        snapshot.setErrorsCount(snapshot.getErrors().size());
        if (previousSchemaVersion < LiberadosYaProductImageSanitizer.SCHEMA_VERSION || sanitizedProducts > 0) {
            LOGGER.info(
                    "LiberadosYa snapshot image schema enforced. cid={} previousSchemaVersion={} currentSchemaVersion={} sanitizedProducts={}",
                    TiendaPorteDiagnostics.currentCorrelationId(),
                    previousSchemaVersion,
                    snapshot.getSchemaVersion(),
                    sanitizedProducts
            );
        }
    }

    private int sanitizeProductImages(List<LiberadosYaProduct> products) {
        int changed = 0;
        for (LiberadosYaProduct product : products) {
            if (product == null) {
                continue;
            }
            String previousPrimary = product.getImagenPrincipal();
            List<String> previousImages = product.getImagenes() == null ? List.of() : product.getImagenes();
            LiberadosYaProductImageSanitizer.SanitizedImages sanitized =
                    LiberadosYaProductImageSanitizer.sanitizeStoredProductImages(
                            product.getImagenPrincipal(),
                            product.getImagenes(),
                            product.getUrl()
                    );
            product.setImagenPrincipal(sanitized.primaryImage());
            product.setImagenes(sanitized.images());
            if (!sameImages(previousPrimary, previousImages, sanitized)) {
                changed++;
            }
        }
        return changed;
    }

    private boolean sameImages(
            String previousPrimary,
            List<String> previousImages,
            LiberadosYaProductImageSanitizer.SanitizedImages sanitized
    ) {
        String nextPrimary = sanitized.primaryImage();
        List<String> nextImages = sanitized.images() == null ? List.of() : sanitized.images();
        return java.util.Objects.equals(previousPrimary, nextPrimary) && previousImages.equals(nextImages);
    }
}
