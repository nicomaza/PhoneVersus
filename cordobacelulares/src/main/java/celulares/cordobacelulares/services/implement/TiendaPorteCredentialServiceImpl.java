package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialCreateRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialPatchRequest;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialResponse;
import celulares.cordobacelulares.dtos.tiendaporte.credentials.TiendaPorteCredentialUpdateRequest;
import celulares.cordobacelulares.entities.TiendaPorteCredential;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.repository.TiendaPorteCredentialRepository;
import celulares.cordobacelulares.services.TiendaPorteAuthenticationInvalidator;
import celulares.cordobacelulares.services.TiendaPorteCredentialService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class TiendaPorteCredentialServiceImpl implements TiendaPorteCredentialService {

    private static final Set<String> PATCH_FIELDS = Set.of("username", "password", "activa");

    private final TiendaPorteCredentialRepository repository;
    private final TiendaPorteCredentialCipher credentialCipher;
    private final TiendaPorteAuthenticationInvalidator authenticationInvalidator;

    public TiendaPorteCredentialServiceImpl(
            TiendaPorteCredentialRepository repository,
            TiendaPorteCredentialCipher credentialCipher,
            TiendaPorteAuthenticationInvalidator authenticationInvalidator
    ) {
        this.repository = repository;
        this.credentialCipher = credentialCipher;
        this.authenticationInvalidator = authenticationInvalidator;
    }

    @Override
    @Transactional
    public synchronized TiendaPorteCredentialResponse create(TiendaPorteCredentialCreateRequest request) {
        requireRequest(request);
        String username = requireText(request.getUsername(), "username es obligatorio");
        String password = requireText(request.getPassword(), "password es obligatoria");
        Boolean active = requireBoolean(request.getActiva(), "activa es obligatoria");

        TiendaPorteCredential credential = new TiendaPorteCredential();
        credential.setUsername(username);
        credential.setEncryptedPassword(credentialCipher.encrypt(password));
        credential.setActiva(active);

        TiendaPorteCredential saved = repository.save(credential);
        if (Boolean.TRUE.equals(saved.getActiva())) {
            repository.deactivateOtherActiveCredentials(saved.getId());
            authenticationInvalidator.invalidateAuthentication();
        }
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TiendaPorteCredentialResponse> getAll() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TiendaPorteCredentialResponse getById(Long id) {
        return toResponse(findExisting(id));
    }

    @Override
    @Transactional(readOnly = true)
    public TiendaPorteCredentialResponse getActive() {
        return repository.findFirstByActivaTrueOrderByUpdatedAtDescIdDesc()
                .map(this::toResponse)
                .orElseThrow(() -> new ApiNotFoundException("No existe una credencial activa para Tienda Porte"));
    }

    @Override
    @Transactional
    public synchronized TiendaPorteCredentialResponse update(Long id, TiendaPorteCredentialUpdateRequest request) {
        requireRequest(request);
        TiendaPorteCredential credential = findExisting(id);
        boolean wasActive = Boolean.TRUE.equals(credential.getActiva());
        boolean usernameChanged = !Objects.equals(credential.getUsername(), request.getUsername());
        boolean passwordChanged = hasText(request.getPassword());

        credential.setUsername(requireText(request.getUsername(), "username es obligatorio"));
        if (passwordChanged) {
            credential.setEncryptedPassword(credentialCipher.encrypt(request.getPassword().trim()));
        }
        credential.setActiva(requireBoolean(request.getActiva(), "activa es obligatoria"));

        TiendaPorteCredential saved = repository.save(credential);
        if (Boolean.TRUE.equals(saved.getActiva())) {
            repository.deactivateOtherActiveCredentials(saved.getId());
        }
        invalidateIfActiveChanged(wasActive, Boolean.TRUE.equals(saved.getActiva()), usernameChanged, passwordChanged);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public synchronized TiendaPorteCredentialResponse patch(Long id, TiendaPorteCredentialPatchRequest request) {
        requireRequest(request);
        TiendaPorteCredential credential = findExisting(id);
        validatePatchFields(request);

        boolean wasActive = Boolean.TRUE.equals(credential.getActiva());
        boolean usernameChanged = false;
        boolean passwordChanged = false;

        if (request.hasField("username")) {
            String username = readRequiredText(request.getField("username"), "username es obligatorio");
            usernameChanged = !Objects.equals(credential.getUsername(), username);
            credential.setUsername(username);
        }
        if (request.hasField("password")) {
            String password = readOptionalText(request.getField("password"));
            if (hasText(password)) {
                credential.setEncryptedPassword(credentialCipher.encrypt(password.trim()));
                passwordChanged = true;
            }
        }
        if (request.hasField("activa")) {
            credential.setActiva(readRequiredBoolean(request.getField("activa"), "activa es obligatoria"));
        }

        TiendaPorteCredential saved = repository.save(credential);
        if (Boolean.TRUE.equals(saved.getActiva())) {
            repository.deactivateOtherActiveCredentials(saved.getId());
        }
        invalidateIfActiveChanged(wasActive, Boolean.TRUE.equals(saved.getActiva()), usernameChanged, passwordChanged);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        TiendaPorteCredential credential = findExisting(id);
        boolean wasActive = Boolean.TRUE.equals(credential.getActiva());
        repository.delete(credential);
        if (wasActive) {
            authenticationInvalidator.invalidateAuthentication();
        }
    }

    private TiendaPorteCredential findExisting(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ApiNotFoundException("Credencial de Tienda Porte no encontrada"));
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new TiendaPorteBadRequestException("Datos invalidos");
        }
    }

    private void validatePatchFields(TiendaPorteCredentialPatchRequest request) {
        for (String fieldName : request.fieldNames()) {
            if (!PATCH_FIELDS.contains(fieldName)) {
                throw new TiendaPorteBadRequestException("Campo invalido: " + fieldName);
            }
        }
    }

    private void invalidateIfActiveChanged(boolean wasActive, boolean isActive, boolean usernameChanged, boolean passwordChanged) {
        if (isActive || wasActive && (usernameChanged || passwordChanged || !isActive)) {
            authenticationInvalidator.invalidateAuthentication();
        }
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new TiendaPorteBadRequestException(message);
        }
        return value.trim();
    }

    private Boolean requireBoolean(Boolean value, String message) {
        if (value == null) {
            throw new TiendaPorteBadRequestException(message);
        }
        return value;
    }

    private String readRequiredText(JsonNode node, String message) {
        if (node == null || node.isNull() || !node.isTextual() || !hasText(node.asText())) {
            throw new TiendaPorteBadRequestException(message);
        }
        return node.asText().trim();
    }

    private String readOptionalText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new TiendaPorteBadRequestException("password debe ser texto");
        }
        return node.asText();
    }

    private Boolean readRequiredBoolean(JsonNode node, String message) {
        if (node == null || node.isNull() || !node.isBoolean()) {
            throw new TiendaPorteBadRequestException(message);
        }
        return node.asBoolean();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private TiendaPorteCredentialResponse toResponse(TiendaPorteCredential credential) {
        return new TiendaPorteCredentialResponse(
                credential.getId(),
                credential.getUsername(),
                credential.getActiva(),
                credential.getCreatedAt(),
                credential.getUpdatedAt()
        );
    }
}
