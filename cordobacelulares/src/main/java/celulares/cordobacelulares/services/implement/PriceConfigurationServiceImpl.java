package celulares.cordobacelulares.services.implement;

import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationCreateRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationPatchRequest;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationResponse;
import celulares.cordobacelulares.dtos.priceconfiguration.PriceConfigurationUpdateRequest;
import celulares.cordobacelulares.entities.PriceConfiguration;
import celulares.cordobacelulares.exceptions.ApiConflictException;
import celulares.cordobacelulares.exceptions.ApiNotFoundException;
import celulares.cordobacelulares.exceptions.TiendaPorteBadRequestException;
import celulares.cordobacelulares.repository.PriceConfigurationRepository;
import celulares.cordobacelulares.services.PriceConfigurationService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;

@Service
public class PriceConfigurationServiceImpl implements PriceConfigurationService {

    private static final Set<String> PATCH_FIELDS = Set.of(
            "dolarBillete",
            "usdt",
            "transferenciaBancaria",
            "tarjeta3Pagos",
            "tarjeta6Pagos",
            "tarjeta12Pagos"
    );

    private final PriceConfigurationRepository repository;

    public PriceConfigurationServiceImpl(PriceConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PriceConfigurationResponse create(PriceConfigurationCreateRequest request) {
        requireRequest(request);
        if (repository.existsById(PriceConfiguration.FIXED_ID)) {
            throw new ApiConflictException("La configuracion de cotizaciones ya existe");
        }

        PriceConfiguration configuration = new PriceConfiguration();
        configuration.setId(PriceConfiguration.FIXED_ID);
        applyValues(configuration, request);
        try {
            return toResponse(repository.save(configuration));
        } catch (DataIntegrityViolationException ex) {
            throw new ApiConflictException("La configuracion de cotizaciones ya existe");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PriceConfigurationResponse get() {
        return toResponse(findExisting());
    }

    @Override
    @Transactional(readOnly = true)
    public PriceConfiguration getRequiredForCatalog() {
        return repository.findById(PriceConfiguration.FIXED_ID)
                .orElseThrow(() -> new ApiConflictException("No existe una configuracion de cotizaciones"));
    }

    @Override
    @Transactional
    public PriceConfigurationResponse update(PriceConfigurationUpdateRequest request) {
        requireRequest(request);
        PriceConfiguration configuration = findExisting();
        applyValues(configuration, request);
        return toResponse(repository.save(configuration));
    }

    @Override
    @Transactional
    public PriceConfigurationResponse patch(PriceConfigurationPatchRequest request) {
        requireRequest(request);
        PriceConfiguration configuration = findExisting();
        validatePatchFields(request);

        if (request.hasField("dolarBillete")) {
            configuration.setDolarBillete(readNullableMoney(request.getField("dolarBillete"), "dolarBillete"));
        }
        if (request.hasField("usdt")) {
            configuration.setUsdt(readNullableMoney(request.getField("usdt"), "usdt"));
        }
        if (request.hasField("transferenciaBancaria")) {
            configuration.setTransferenciaBancaria(readNullableMoney(request.getField("transferenciaBancaria"), "transferenciaBancaria"));
        }
        if (request.hasField("tarjeta3Pagos")) {
            configuration.setTarjeta3Pagos(readNullableMoney(request.getField("tarjeta3Pagos"), "tarjeta3Pagos"));
        }
        if (request.hasField("tarjeta6Pagos")) {
            configuration.setTarjeta6Pagos(readNullableMoney(request.getField("tarjeta6Pagos"), "tarjeta6Pagos"));
        }
        if (request.hasField("tarjeta12Pagos")) {
            configuration.setTarjeta12Pagos(readNullableMoney(request.getField("tarjeta12Pagos"), "tarjeta12Pagos"));
        }

        return toResponse(repository.save(configuration));
    }

    @Override
    @Transactional
    public void delete() {
        PriceConfiguration configuration = findExisting();
        repository.delete(configuration);
    }

    private PriceConfiguration findExisting() {
        return repository.findById(PriceConfiguration.FIXED_ID)
                .orElseThrow(() -> new ApiNotFoundException("No existe la configuracion de cotizaciones"));
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new TiendaPorteBadRequestException("Datos invalidos");
        }
    }

    private void applyValues(PriceConfiguration configuration, PriceConfigurationCreateRequest request) {
        configuration.setDolarBillete(validateMoney(request.getDolarBillete(), "dolarBillete"));
        configuration.setUsdt(validateMoney(request.getUsdt(), "usdt"));
        configuration.setTransferenciaBancaria(validateMoney(request.getTransferenciaBancaria(), "transferenciaBancaria"));
        configuration.setTarjeta3Pagos(validateMoney(request.getTarjeta3Pagos(), "tarjeta3Pagos"));
        configuration.setTarjeta6Pagos(validateMoney(request.getTarjeta6Pagos(), "tarjeta6Pagos"));
        configuration.setTarjeta12Pagos(validateMoney(request.getTarjeta12Pagos(), "tarjeta12Pagos"));
    }

    private void applyValues(PriceConfiguration configuration, PriceConfigurationUpdateRequest request) {
        configuration.setDolarBillete(validateMoney(request.getDolarBillete(), "dolarBillete"));
        configuration.setUsdt(validateMoney(request.getUsdt(), "usdt"));
        configuration.setTransferenciaBancaria(validateMoney(request.getTransferenciaBancaria(), "transferenciaBancaria"));
        configuration.setTarjeta3Pagos(validateMoney(request.getTarjeta3Pagos(), "tarjeta3Pagos"));
        configuration.setTarjeta6Pagos(validateMoney(request.getTarjeta6Pagos(), "tarjeta6Pagos"));
        configuration.setTarjeta12Pagos(validateMoney(request.getTarjeta12Pagos(), "tarjeta12Pagos"));
    }

    private void validatePatchFields(PriceConfigurationPatchRequest request) {
        for (String fieldName : request.fieldNames()) {
            if (!PATCH_FIELDS.contains(fieldName)) {
                throw new TiendaPorteBadRequestException("Campo invalido: " + fieldName);
            }
        }
    }

    private BigDecimal readNullableMoney(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                return validateMoney(node.decimalValue(), fieldName);
            }
            if (node.isTextual() && !node.asText().isBlank()) {
                return validateMoney(new BigDecimal(node.asText().trim()), fieldName);
            }
        } catch (NumberFormatException ex) {
            throw new TiendaPorteBadRequestException(fieldName + " debe ser numerico");
        }
        throw new TiendaPorteBadRequestException(fieldName + " debe ser numerico");
    }

    private BigDecimal validateMoney(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new TiendaPorteBadRequestException("Configuracion invalida: " + fieldName + " no puede ser negativo");
        }
        return value;
    }

    private PriceConfigurationResponse toResponse(PriceConfiguration configuration) {
        return new PriceConfigurationResponse(
                configuration.getId(),
                configuration.getDolarBillete(),
                configuration.getUsdt(),
                configuration.getTransferenciaBancaria(),
                configuration.getTarjeta3Pagos(),
                configuration.getTarjeta6Pagos(),
                configuration.getTarjeta12Pagos(),
                configuration.getCreatedAt(),
                configuration.getUpdatedAt()
        );
    }
}
